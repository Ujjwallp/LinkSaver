package com.example.autofill

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.example.R
import com.example.data.AppDatabase
import com.example.security.KeyHierarchy
import com.example.security.SecureStorage
import com.example.util.SecureLog as Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class VaultAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val contexts = request.fillContexts
        if (contexts.isEmpty()) {
            return callback.onSuccess(null)
        }

        val structures = contexts.map { it.structure }
        val parsed = AutofillStructureParser.parseFillContexts(structures)

        // Respect Never-Save list and non-login / opt-out screens immediately
        val neverSaveManager = NeverSaveManager(applicationContext)
        if (neverSaveManager.isNeverSaveTarget(parsed.domainHost, parsed.packageName)) {
            return callback.onSuccess(null)
        }

        if (!parsed.shouldOfferFill) {
            return callback.onSuccess(null)
        }

        val targetPasswordNodeId = parsed.newPasswordNodeId ?: parsed.passwordNodeId
        if (parsed.usernameNodeId == null && targetPasswordNodeId == null) {
            return callback.onSuccess(null)
        }

        serviceScope.launch {
            try {
                // Ensure hardware keystore unwrap is attempted on cold service start after reboot/update
                if (!KeyHierarchy.isVaultUnlocked()) {
                    KeyHierarchy.unlockWithHardwareKeystore(applicationContext)
                }
                val isVaultUnlocked = isVaultUnlockedForDirectFill()

                val db = AppDatabase.getDatabase(applicationContext)
                val allLinks = db.linkDao().getAllLinksSnapshot()
                val engine = AppAutofillEngine(applicationContext, neverSaveManager)
                val plan = engine.evaluateFillRequest(
                    parsed = parsed,
                    allLinks = allLinks,
                    isVaultUnlocked = isVaultUnlocked
                )

                val targetReadableName = if (parsed.isWebRequest && parsed.webDomain.isNotBlank()) {
                    parsed.webDomain
                } else {
                    AppIdentityVerifier.resolveAppDisplayInfo(applicationContext, parsed.packageName).displayLabel
                }

                val fillResponseBuilder = FillResponse.Builder()
                var addedAnyDataset = false

                if (plan is AutofillFillPlan.Ready) {
                    // 1. Verified matches (exact package + SHA-256 certificate match, or verified domain match)
                    for ((idx, match) in plan.verifiedMatches.withIndex()) {
                        val remoteViews = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                            val titleText = if (isVaultUnlocked) {
                                match.displayTitle.ifBlank { plan.targetTitle }
                            } else {
                                "Unlock LinkVault to autofill ${plan.targetTitle}"
                            }
                            val subtitleText = if (isVaultUnlocked) {
                                match.username.ifBlank { "Tap to autofill" }
                            } else {
                                "Authentication required"
                            }
                            setTextViewText(R.id.autofill_title, titleText)
                            setTextViewText(R.id.autofill_subtitle, subtitleText)
                        }

                        val datasetBuilder = Dataset.Builder()
                        if (!isVaultUnlocked) {
                            val authIntent = Intent(applicationContext, AutofillConfirmationActivity::class.java).apply {
                                putExtra(AutofillConfirmationActivity.EXTRA_MODE, AutofillConfirmationActivity.MODE_UNLOCK_AND_FILL)
                                putExtra(AutofillConfirmationActivity.EXTRA_CREDENTIAL_ID, match.entity.id)
                                putExtra(AutofillConfirmationActivity.EXTRA_USERNAME_NODE_ID, parsed.usernameNodeId)
                                putExtra(AutofillConfirmationActivity.EXTRA_PASSWORD_NODE_ID, targetPasswordNodeId)
                            }
                            val pendingIntent = createAutofillPendingIntent(
                                requestCode = 1000 + match.entity.id + idx,
                                intent = authIntent,
                                mutable = true
                            )
                            datasetBuilder.setAuthentication(pendingIntent.intentSender)
                            if (parsed.usernameNodeId != null) {
                                datasetBuilder.setValue(parsed.usernameNodeId, null, remoteViews)
                            }
                            if (targetPasswordNodeId != null) {
                                datasetBuilder.setValue(targetPasswordNodeId, null, remoteViews)
                            }
                        } else {
                            val password = engine.decryptPassword(match.entity, isUnlocked = true)
                            if (parsed.usernameNodeId != null) {
                                datasetBuilder.setValue(
                                    parsed.usernameNodeId,
                                    AutofillValue.forText(match.username),
                                    remoteViews
                                )
                            }
                            if (targetPasswordNodeId != null) {
                                datasetBuilder.setValue(
                                    targetPasswordNodeId,
                                    AutofillValue.forText(password),
                                    remoteViews
                                )
                            }
                        }

                        try {
                            fillResponseBuilder.addDataset(datasetBuilder.build())
                            addedAnyDataset = true
                        } catch (e: Exception) {
                            Log.e("VaultAutofillService", "Error building verified dataset", e)
                        }
                    }

                    // 2. Signature-changed warning entries (Step 8A.1):
                    // NEVER fill automatically! Require authentication + explicit user review.
                    for ((idx, warnMatch) in plan.signatureWarningMatches.withIndex()) {
                        val warningViews = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                            setTextViewText(
                                R.id.autofill_title,
                                "This app was updated by a different publisher, tap to review"
                            )
                            setTextViewText(
                                R.id.autofill_subtitle,
                                "${warnMatch.displayTitle} • ${warnMatch.username.ifBlank { parsed.packageName }}"
                            )
                        }

                        val warnIntent = Intent(applicationContext, AutofillConfirmationActivity::class.java).apply {
                            putExtra(AutofillConfirmationActivity.EXTRA_MODE, AutofillConfirmationActivity.MODE_SIGNATURE_WARNING)
                            putExtra(AutofillConfirmationActivity.EXTRA_CREDENTIAL_ID, warnMatch.entity.id)
                            putExtra(AutofillConfirmationActivity.EXTRA_APP_PACKAGE, parsed.packageName)
                            putExtra(AutofillConfirmationActivity.EXTRA_APP_LABEL, plan.targetTitle)
                            putStringArrayListExtra(
                                AutofillConfirmationActivity.EXTRA_CURRENT_SIGNERS,
                                ArrayList(warnMatch.currentAppSigners)
                            )
                            putExtra(AutofillConfirmationActivity.EXTRA_USERNAME_NODE_ID, parsed.usernameNodeId)
                            putExtra(AutofillConfirmationActivity.EXTRA_PASSWORD_NODE_ID, targetPasswordNodeId)
                        }
                        val pendingIntent = createAutofillPendingIntent(
                            requestCode = 5000 + warnMatch.entity.id + idx,
                            intent = warnIntent,
                            mutable = true
                        )

                        val warnDatasetBuilder = Dataset.Builder()
                            .setAuthentication(pendingIntent.intentSender)
                        if (parsed.usernameNodeId != null) {
                            warnDatasetBuilder.setValue(parsed.usernameNodeId, null, warningViews)
                        }
                        if (targetPasswordNodeId != null) {
                            warnDatasetBuilder.setValue(targetPasswordNodeId, null, warningViews)
                        }

                        try {
                            fillResponseBuilder.addDataset(warnDatasetBuilder.build())
                            addedAnyDataset = true
                        } catch (e: Exception) {
                            Log.e("VaultAutofillService", "Error building warning dataset", e)
                        }
                    }
                } else if (plan is AutofillFillPlan.NoMatchingCredentials) {
                    // 2B. Authenticated chooser dataset when no credential is linked to this app/domain yet.
                    // Never fills automatically; requires user authentication and explicit confirmation to link or pick.
                    val currentSigners = if (!parsed.isWebRequest && parsed.packageName.isNotBlank()) {
                        AppIdentityVerifier.getAppSignersSha256(applicationContext, parsed.packageName)
                    } else {
                        emptySet()
                    }
                    val pickViews = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                        setTextViewText(R.id.autofill_title, "LinkVault • $targetReadableName")
                        setTextViewText(R.id.autofill_subtitle, "Tap to choose a saved login or type new to save")
                    }
                    val pickIntent = Intent(applicationContext, AutofillConfirmationActivity::class.java).apply {
                        putExtra(AutofillConfirmationActivity.EXTRA_MODE, AutofillConfirmationActivity.MODE_PICK_OR_SAVE_CREDENTIAL)
                        putExtra(AutofillConfirmationActivity.EXTRA_APP_PACKAGE, if (parsed.isWebRequest) "" else parsed.packageName)
                        putExtra(AutofillConfirmationActivity.EXTRA_APP_LABEL, targetReadableName)
                        putExtra(AutofillConfirmationActivity.EXTRA_TARGET_DOMAIN, if (parsed.isWebRequest) parsed.domainHost else "")
                        putStringArrayListExtra(
                            AutofillConfirmationActivity.EXTRA_CURRENT_SIGNERS,
                            ArrayList(currentSigners)
                        )
                        putExtra(AutofillConfirmationActivity.EXTRA_USERNAME_NODE_ID, parsed.usernameNodeId)
                        putExtra(AutofillConfirmationActivity.EXTRA_PASSWORD_NODE_ID, targetPasswordNodeId)
                    }
                    val pendingIntent = createAutofillPendingIntent(
                        requestCode = 7500 + parsed.packageName.hashCode(),
                        intent = pickIntent,
                        mutable = true
                    )
                    val pickDatasetBuilder = Dataset.Builder()
                        .setAuthentication(pendingIntent.intentSender)
                    if (parsed.usernameNodeId != null) {
                        pickDatasetBuilder.setValue(parsed.usernameNodeId, null, pickViews)
                    }
                    if (targetPasswordNodeId != null) {
                        pickDatasetBuilder.setValue(targetPasswordNodeId, null, pickViews)
                    }
                    try {
                        fillResponseBuilder.addDataset(pickDatasetBuilder.build())
                        addedAnyDataset = true
                    } catch (e: Exception) {
                        Log.e("VaultAutofillService", "Error building chooser dataset", e)
                    }
                }

                // 3. Configure SaveInfo for login / sign-up submission (Step 8C.3 & 8D.1)
                val requiredIds = arrayOfNotNull(parsed.usernameNodeId, targetPasswordNodeId)
                if (requiredIds.isNotEmpty() && (targetPasswordNodeId != null || parsed.isTwoStepUsernameStep)) {
                    val saveType = if (targetPasswordNodeId != null && parsed.usernameNodeId != null) {
                        SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
                    } else if (targetPasswordNodeId != null) {
                        SaveInfo.SAVE_DATA_TYPE_PASSWORD
                    } else {
                        SaveInfo.SAVE_DATA_TYPE_USERNAME
                    }

                    val saveInfoBuilder = SaveInfo.Builder(saveType, requiredIds)
                        // Rule 8C.3: Ensure save prompt appears even when login activity finishes/transitions
                        .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                        .setDescription("Save login for $targetReadableName to LinkVault (100% offline)")

                    if (parsed.confirmPasswordNodeId != null) {
                        saveInfoBuilder.setOptionalIds(arrayOf(parsed.confirmPasswordNodeId))
                    }

                    fillResponseBuilder.setSaveInfo(saveInfoBuilder.build())
                    callback.onSuccess(fillResponseBuilder.build())
                } else if (addedAnyDataset) {
                    callback.onSuccess(fillResponseBuilder.build())
                } else {
                    callback.onSuccess(null)
                }
            } catch (e: Exception) {
                Log.e("VaultAutofillService", "Error handling fill request", e)
                callback.onSuccess(null)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val contexts = request.fillContexts
        if (contexts.isEmpty()) {
            return callback.onSuccess()
        }

        val structures = contexts.map { it.structure }
        val parsed = AutofillStructureParser.parseFillContexts(structures)

        serviceScope.launch {
            try {
                if (!KeyHierarchy.isVaultUnlocked()) {
                    KeyHierarchy.unlockWithHardwareKeystore(applicationContext)
                }
                val db = AppDatabase.getDatabase(applicationContext)
                val prefs = SecureStorage.getEncryptedPrefs(applicationContext, "linksaver_vault_security_encrypted")
                val activeEmail = (prefs.getString("active_user_email", null)
                    ?.takeIf { it.isNotBlank() }
                    ?: prefs.getString("last_active_user_email", "")
                    ?: "")

                val engine = AppAutofillEngine(applicationContext)
                val outcome = engine.processSaveRequest(
                    parsed = parsed,
                    dao = db.linkDao(),
                    userEmail = activeEmail,
                    isVaultUnlocked = true
                )

                // Rule 8B.1: If a credential with the same username already exists for a website/app,
                // offer the user-confirmed cross-link prompt ("Also use this login for [App/Website]?").
                val suggestion = when (outcome) {
                    is AutofillSaveOutcome.SavedNew -> outcome.crossLinkSuggestions.firstOrNull()
                    is AutofillSaveOutcome.UpdatedExisting -> outcome.crossLinkSuggestions.firstOrNull()
                    else -> null
                }

                if (suggestion != null) {
                    val promptIntent = Intent(applicationContext, AutofillConfirmationActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(
                            AutofillConfirmationActivity.EXTRA_MODE,
                            AutofillConfirmationActivity.MODE_POST_SAVE_LINK_PROMPT
                        )
                        putExtra(AutofillConfirmationActivity.EXTRA_CREDENTIAL_ID, suggestion.existingCredentialId)
                        putExtra(AutofillConfirmationActivity.EXTRA_APP_PACKAGE, suggestion.targetAppPackage)
                        putExtra(AutofillConfirmationActivity.EXTRA_APP_LABEL, suggestion.targetAppLabel)
                        putStringArrayListExtra(
                            AutofillConfirmationActivity.EXTRA_CURRENT_SIGNERS,
                            ArrayList(suggestion.targetAppSigners)
                        )
                        putExtra(AutofillConfirmationActivity.EXTRA_TARGET_DOMAIN, suggestion.targetDomain)
                        putExtra(AutofillConfirmationActivity.EXTRA_PROMPT_MESSAGE, suggestion.promptMessage)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val intentSender = createAutofillPendingIntent(
                            requestCode = 9000 + suggestion.existingCredentialId,
                            intent = promptIntent,
                            mutable = false
                        ).intentSender
                        callback.onSuccess(intentSender)
                        return@launch
                    }
                }

                callback.onSuccess()
            } catch (e: Exception) {
                Log.e("VaultAutofillService", "Error saving credential from autofill", e)
                callback.onSuccess()
            }
        }
    }

    private fun createAutofillPendingIntent(
        requestCode: Int,
        intent: Intent,
        mutable: Boolean
    ): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE

        if (Build.VERSION.SDK_INT >= 34) {
            return try {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
                PendingIntent.getActivity(applicationContext, requestCode, intent, flags, options)
            } catch (_: Throwable) {
                PendingIntent.getActivity(applicationContext, requestCode, intent, flags)
            }
        }
        return PendingIntent.getActivity(applicationContext, requestCode, intent, flags)
    }

    private fun isVaultUnlockedForDirectFill(): Boolean {
        val prefs = SecureStorage.getEncryptedPrefs(applicationContext, "linksaver_vault_security_encrypted")
        val lockEnabled = prefs.getBoolean("passcode_enabled", false)
        if (!lockEnabled) return true
        return KeyHierarchy.isVaultUnlocked()
    }

    private fun <T> arrayOfNotNull(vararg elements: T?): Array<T> {
        val list = elements.filterNotNull()
        @Suppress("UNCHECKED_CAST")
        return list.toTypedArray() as Array<T>
    }
}
