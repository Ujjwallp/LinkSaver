package com.example.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.feature.autofill.R
import com.example.data.AppDatabase
import com.example.data.LinkEntity
import com.example.security.BiometricSecurityHelper
import com.example.security.KeyHierarchy
import com.example.security.LockoutManager
import com.example.security.PinSecurity
import com.example.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Internal, non-exported confirmation and authentication Activity for LinkVault Autofill:
 * 1. [MODE_UNLOCK_AND_FILL]: Unlocks the vault via Biometrics or PIN before releasing credentials when locked.
 * 2. [MODE_SIGNATURE_WARNING]: Displays "This app was updated by a different publisher, tap to review"
 *    and requires authentication + explicit user confirmation before filling or trusting the new SHA-256 signer.
 * 3. [MODE_POST_SAVE_LINK_PROMPT]: Prompts "Also use this login for [App/Website]?" with
 *    Yes / No / Always ask after saving a credential.
 * 4. [MODE_PICK_OR_SAVE_CREDENTIAL]: Allows the user to authenticate and explicitly link & fill an existing
 *    credential for a new app/domain, or dismiss to type and save a new login.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillConfirmationActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Protect sensitive confirmation screen from screenshots/recents
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_UNLOCK_AND_FILL
        val credentialId = intent.getIntExtra(EXTRA_CREDENTIAL_ID, -1)
        val appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE).orEmpty()
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty()
        val currentSigners = intent.getStringArrayListExtra(EXTRA_CURRENT_SIGNERS)?.toSet() ?: emptySet()
        val targetDomain = intent.getStringExtra(EXTRA_TARGET_DOMAIN).orEmpty()
        val promptMessage = intent.getStringExtra(EXTRA_PROMPT_MESSAGE).orEmpty()

        @Suppress("DEPRECATION")
        val usernameNodeId = intent.getParcelableExtra<AutofillId>(EXTRA_USERNAME_NODE_ID)
        @Suppress("DEPRECATION")
        val passwordNodeId = intent.getParcelableExtra<AutofillId>(EXTRA_PASSWORD_NODE_ID)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xCC0D0C14)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (mode) {
                            MODE_SIGNATURE_WARNING -> {
                                SignatureMismatchReviewCard(
                                    credentialId = credentialId,
                                    appPackage = appPackage,
                                    appLabel = appLabel,
                                    currentSigners = currentSigners,
                                    usernameNodeId = usernameNodeId,
                                    passwordNodeId = passwordNodeId
                                )
                            }
                            MODE_POST_SAVE_LINK_PROMPT -> {
                                CrossLinkConfirmationCard(
                                    existingCredentialId = credentialId,
                                    appPackage = appPackage,
                                    appLabel = appLabel,
                                    currentSigners = currentSigners,
                                    targetDomain = targetDomain,
                                    promptMessage = promptMessage
                                )
                            }
                            MODE_PICK_OR_SAVE_CREDENTIAL -> {
                                PickOrLinkCredentialCard(
                                    appPackage = appPackage,
                                    appLabel = appLabel,
                                    currentSigners = currentSigners,
                                    targetDomain = targetDomain,
                                    usernameNodeId = usernameNodeId,
                                    passwordNodeId = passwordNodeId
                                )
                            }
                            else -> {
                                UnlockAndFillCard(
                                    credentialId = credentialId,
                                    usernameNodeId = usernameNodeId,
                                    passwordNodeId = passwordNodeId
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun UnlockAndFillCard(
        credentialId: Int,
        usernameNodeId: AutofillId?,
        passwordNodeId: AutofillId?
    ) {
        var pinInput by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf("") }
        val hasBio = remember { isBiometricAvailable() }

        LaunchedEffect(hasBio) {
            if (hasBio) {
                promptBiometricUnlock(
                    onSuccess = {
                        completeAuthenticatedFill(
                            credentialId = credentialId,
                            usernameNodeId = usernameNodeId,
                            passwordNodeId = passwordNodeId,
                            trustNewSigners = false,
                            appPackage = "",
                            appLabel = "",
                            newSigners = emptySet()
                        )
                    },
                    onError = { msg -> errorText = msg }
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1824))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD0BCFF))
                    Text(
                        text = "Authenticate to Autofill",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                }
                Text(
                    text = "Unlock LinkVault to verify your identity before filling credentials.",
                    fontSize = 13.sp,
                    color = Color(0xFFBDB8CE)
                )
                if (hasBio) {
                    OutlinedButton(
                        onClick = {
                            promptBiometricUnlock(
                                onSuccess = {
                                    completeAuthenticatedFill(
                                        credentialId = credentialId,
                                        usernameNodeId = usernameNodeId,
                                        passwordNodeId = passwordNodeId,
                                        trustNewSigners = false,
                                        appPackage = "",
                                        appLabel = "",
                                        newSigners = emptySet()
                                    )
                                },
                                onError = { msg -> errorText = msg }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFFD0BCFF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock with Fingerprint / Biometrics", color = Color(0xFFD0BCFF), fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        errorText = ""
                    },
                    label = { Text("Vault PIN or Passcode") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText.isNotBlank()) {
                    Text(errorText, color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (verifyUserPin(pinInput)) {
                            completeAuthenticatedFill(
                                credentialId = credentialId,
                                usernameNodeId = usernameNodeId,
                                passwordNodeId = passwordNodeId,
                                trustNewSigners = false,
                                appPackage = "",
                                appLabel = "",
                                newSigners = emptySet()
                            )
                        } else {
                            errorText = "Invalid PIN or vault locked out."
                        }
                    }) {
                        Text("Unlock & Fill")
                    }
                }
            }
        }
    }

    @Composable
    private fun PickOrLinkCredentialCard(
        appPackage: String,
        appLabel: String,
        currentSigners: Set<String>,
        targetDomain: String,
        usernameNodeId: AutofillId?,
        passwordNodeId: AutofillId?
    ) {
        val prefs = remember { SecureStorage.getEncryptedPrefs(applicationContext, "linksaver_vault_security_encrypted") }
        val lockEnabled = remember { prefs.getBoolean("passcode_enabled", false) }
        var isAuthenticated by remember { mutableStateOf(!lockEnabled && KeyHierarchy.isVaultUnlocked()) }
        var pinInput by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf("") }
        var credentials by remember { mutableStateOf<List<Pair<LinkEntity, String>>>(emptyList()) }
        val hasBio = remember { isBiometricAvailable() }

        LaunchedEffect(isAuthenticated) {
            if (isAuthenticated) {
                withContext(Dispatchers.IO) {
                    if (!KeyHierarchy.isVaultUnlocked()) {
                        KeyHierarchy.unlockWithHardwareKeystore(applicationContext)
                    }
                    val db = AppDatabase.getDatabase(applicationContext)
                    val engine = AppAutofillEngine(applicationContext)
                    val all = db.linkDao().getAllLinksSnapshot()
                    val credItems = all.filter {
                        it.category == "🔑 Passwords" || it.description == "SECURE_PASSWORD" || it.note.contains("|||")
                    }.map { link ->
                        link to engine.extractUsername(link, isUnlocked = true)
                    }
                    credentials = credItems
                }
            } else if (hasBio) {
                promptBiometricUnlock(
                    onSuccess = { isAuthenticated = true },
                    onError = { msg -> errorText = msg }
                )
            }
        }

        val targetDisplay = appLabel.ifBlank { targetDomain.ifBlank { appPackage.ifBlank { "this screen" } } }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1824))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFD0BCFF))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LinkVault Autofill",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Target: $targetDisplay",
                            fontSize = 12.sp,
                            color = Color(0xFFBDB8CE),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!isAuthenticated) {
                    Text(
                        text = "Authenticate to select a saved login for $targetDisplay.",
                        fontSize = 12.sp,
                        color = Color(0xFFBDB8CE)
                    )
                    if (hasBio) {
                        OutlinedButton(
                            onClick = {
                                promptBiometricUnlock(
                                    onSuccess = { isAuthenticated = true },
                                    onError = { msg -> errorText = msg }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFFD0BCFF))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify with Fingerprint", color = Color(0xFFD0BCFF), fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it
                            errorText = ""
                        },
                        label = { Text("Vault PIN or Passcode") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorText.isNotBlank()) {
                        Text(errorText, color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (verifyUserPin(pinInput)) {
                                isAuthenticated = true
                            } else {
                                errorText = "Authentication failed."
                            }
                        }) {
                            Text("Unlock")
                        }
                    }
                } else {
                    if (credentials.isEmpty()) {
                        Text(
                            text = "No saved logins in LinkVault yet. Dismiss this prompt, type your username and password into $targetDisplay, and submit the form — Android will offer 'Save to LinkVault?'.",
                            fontSize = 12.sp,
                            color = Color(0xFFEAE6F2)
                        )
                    } else {
                        Text(
                            text = "Tap a saved credential below to confirm linking & filling it for $targetDisplay:",
                            fontSize = 12.sp,
                            color = Color(0xFFBDB8CE)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            for ((cred, user) in credentials.take(8)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF262335),
                                    border = BorderStroke(1.dp, Color(0x33D0BCFF)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            completeLinkAndFillCredential(
                                                credentialId = cred.id,
                                                usernameNodeId = usernameNodeId,
                                                passwordNodeId = passwordNodeId,
                                                appPackage = appPackage,
                                                appLabel = appLabel,
                                                currentSigners = currentSigners,
                                                targetDomain = targetDomain
                                            )
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = cred.title.ifBlank { cred.url },
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = user.ifBlank { "Tap to link & autofill" },
                                            fontSize = 11.sp,
                                            color = Color(0xFFD0BCFF),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }) {
                            Text("Type Login to Save New", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SignatureMismatchReviewCard(
        credentialId: Int,
        appPackage: String,
        appLabel: String,
        currentSigners: Set<String>,
        usernameNodeId: AutofillId?,
        passwordNodeId: AutofillId?
    ) {
        val metadataStore = remember { CredentialMetadataStore(applicationContext) }
        val savedMeta = remember(credentialId) { metadataStore.getMetadata(credentialId) }
        var isAuthenticated by remember { mutableStateOf(false) }
        var pinInput by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf("") }
        val hasBio = remember { isBiometricAvailable() }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF24171B))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D))
                    Text(
                        text = "Publisher Certificate Changed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFFFFE082)
                    )
                }

                Text(
                    text = "This app (${appLabel.ifBlank { appPackage }}) was updated by a different publisher or signed with a different key. LinkVault blocked automatic autofill to protect your credentials.",
                    fontSize = 12.sp,
                    color = Color(0xFFE6D7DC)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33000000), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Package: $appPackage", fontSize = 11.sp, color = Color(0xFFFFCC80))
                    Text(
                        "Saved SHA-256: ${savedMeta.appSigners.firstOrNull()?.take(20) ?: "None"}…",
                        fontSize = 11.sp,
                        color = Color(0xFFBDB8CE)
                    )
                    Text(
                        "Current SHA-256: ${currentSigners.firstOrNull()?.take(20) ?: "Unknown"}…",
                        fontSize = 11.sp,
                        color = Color(0xFFFF8A80)
                    )
                }

                if (!isAuthenticated) {
                    if (hasBio) {
                        OutlinedButton(
                            onClick = {
                                promptBiometricUnlock(
                                    onSuccess = { isAuthenticated = true },
                                    onError = { msg -> errorText = msg }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFFFE082).copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFFFFE082))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify with Fingerprint", color = Color(0xFFFFE082), fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it
                            errorText = ""
                        },
                        label = { Text("Enter Vault PIN to Review") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorText.isNotBlank()) {
                        Text(errorText, color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (verifyUserPin(pinInput)) {
                                isAuthenticated = true
                            } else {
                                errorText = "Authentication failed."
                            }
                        }) {
                            Text("Verify Identity")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reject", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                completeAuthenticatedFill(
                                    credentialId = credentialId,
                                    usernameNodeId = usernameNodeId,
                                    passwordNodeId = passwordNodeId,
                                    trustNewSigners = true,
                                    appPackage = appPackage,
                                    appLabel = appLabel,
                                    newSigners = currentSigners
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Trust & Fill", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CrossLinkConfirmationCard(
        existingCredentialId: Int,
        appPackage: String,
        appLabel: String,
        currentSigners: Set<String>,
        targetDomain: String,
        promptMessage: String
    ) {
        val linkManager = remember { AppWebsiteLinkManager(applicationContext) }
        val suggestion = remember {
            AppWebsiteLinkSuggestion(
                existingCredentialId = existingCredentialId,
                existingTitle = appLabel.ifBlank { targetDomain },
                username = "",
                targetAppPackage = appPackage,
                targetAppLabel = appLabel.ifBlank { appPackage },
                targetAppSigners = currentSigners,
                targetDomain = targetDomain,
                promptMessage = promptMessage.ifBlank {
                    "Also use this login for ${targetDomain.ifBlank { appLabel.ifBlank { appPackage } }}?"
                },
                fromBundledCatalog = false
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 390.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1824))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF80DEEA))
                    Text(
                        text = suggestion.promptMessage,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                Text(
                    text = "LinkVault never links apps and websites automatically. Confirm if you want this credential shared between $appPackage and $targetDomain.",
                    fontSize = 12.sp,
                    color = Color(0xFFBDB8CE)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            linkManager.applyUserLinkDecision(suggestion, LinkConfirmationChoice.NO_IGNORE_SUGGESTION)
                            finish()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("No", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            linkManager.applyUserLinkDecision(suggestion, LinkConfirmationChoice.ALWAYS_ASK)
                            finish()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Always Ask", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            linkManager.applyUserLinkDecision(suggestion, LinkConfirmationChoice.YES_LINK_NOW)
                            finish()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Yes", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    private fun isBiometricAvailable(): Boolean {
        return try {
            val bm = BiometricManager.from(this)
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    private fun promptBiometricUnlock(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        LockoutManager(applicationContext).recordSuccessfulUnlock()
                        KeyHierarchy.unlockWithHardwareKeystore(applicationContext)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED
                        ) {
                            onError(errString.toString())
                        }
                    }
                }
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock LinkVault Autofill")
                .setSubtitle("Verify your identity to fill credentials")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            val cryptoObject = BiometricSecurityHelper.createCryptoObject()
            if (cryptoObject != null) {
                prompt.authenticate(promptInfo, cryptoObject)
            } else {
                prompt.authenticate(promptInfo)
            }
        } catch (e: Throwable) {
            onError(e.message ?: "Biometric authentication unavailable")
        }
    }

    private fun verifyUserPin(input: String): Boolean {
        if (input.isBlank()) {
            return false
        }
        val lockout = LockoutManager(applicationContext)
        val currentStatus = lockout.checkStatus()
        if (currentStatus is com.example.security.LockoutStatus.Locked ||
            currentStatus is com.example.security.LockoutStatus.Erased
        ) {
            return false
        }
        val prefs = SecureStorage.getEncryptedPrefs(applicationContext, "linksaver_vault_security_encrypted")
        val storedHash = prefs.getString("passcode_code", "") ?: ""
        if (storedHash.isBlank()) {
            return false
        }
        val ok = PinSecurity.verifyPasscode(input.toCharArray(), storedHash)
        if (ok) {
            lockout.recordSuccessfulUnlock()
            KeyHierarchy.unlockWithHardwareKeystore(applicationContext)
        } else {
            val newStatus = lockout.recordFailedAttempt()
            if (newStatus is com.example.security.LockoutStatus.Erased) {
                KeyHierarchy.lockVault()
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(applicationContext).linkDao().deleteAllLinks()
                }
            }
        }
        return ok
    }

    private fun completeLinkAndFillCredential(
        credentialId: Int,
        usernameNodeId: AutofillId?,
        passwordNodeId: AutofillId?,
        appPackage: String,
        appLabel: String,
        currentSigners: Set<String>,
        targetDomain: String
    ) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val metadataStore = CredentialMetadataStore(applicationContext)
                if (appPackage.isNotBlank() && currentSigners.isNotEmpty()) {
                    metadataStore.addConfirmedLinkedApp(
                        credentialId = credentialId,
                        packageName = appPackage,
                        signersSha256 = currentSigners,
                        label = appLabel.ifBlank { appPackage }
                    )
                }
                if (targetDomain.isNotBlank()) {
                    metadataStore.addConfirmedLinkedWebsite(
                        credentialId = credentialId,
                        domainOrUrl = targetDomain
                    )
                }
            }
            completeAuthenticatedFill(
                credentialId = credentialId,
                usernameNodeId = usernameNodeId,
                passwordNodeId = passwordNodeId,
                trustNewSigners = false,
                appPackage = appPackage,
                appLabel = appLabel,
                newSigners = currentSigners
            )
        }
    }

    private fun completeAuthenticatedFill(
        credentialId: Int,
        usernameNodeId: AutofillId?,
        passwordNodeId: AutofillId?,
        trustNewSigners: Boolean,
        appPackage: String,
        appLabel: String,
        newSigners: Set<String>
    ) {
        lifecycleScope.launch {
            val dataset = withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(applicationContext)
                val link = db.linkDao().getLinkById(credentialId) ?: return@withContext null
                val metadataStore = CredentialMetadataStore(applicationContext)
                if (trustNewSigners && appPackage.isNotBlank() && newSigners.isNotEmpty()) {
                    metadataStore.saveAppIdentity(
                        credentialId = credentialId,
                        appPackage = appPackage,
                        appSigners = newSigners,
                        appLabel = appLabel.ifBlank { appPackage }
                    )
                }
                val engine = AppAutofillEngine(applicationContext)
                val username = engine.extractUsername(link, isUnlocked = true)
                val password = engine.decryptPassword(link, isUnlocked = true)

                val rv = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                    setTextViewText(R.id.autofill_title, link.title.ifBlank { "LinkVault Credential" })
                    setTextViewText(R.id.autofill_subtitle, username.ifBlank { "Verified" })
                }

                val builder = Dataset.Builder()
                var hasField = false
                if (usernameNodeId != null) {
                    builder.setValue(usernameNodeId, AutofillValue.forText(username), rv)
                    hasField = true
                }
                if (passwordNodeId != null) {
                    builder.setValue(passwordNodeId, AutofillValue.forText(password), rv)
                    hasField = true
                }
                if (hasField) builder.build() else null
            }

            if (dataset != null) {
                val replyIntent = Intent().apply {
                    putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                }
                setResult(Activity.RESULT_OK, replyIntent)
            } else {
                setResult(Activity.RESULT_CANCELED)
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_MODE = "extra_autofill_confirm_mode"
        const val MODE_UNLOCK_AND_FILL = "mode_unlock_and_fill"
        const val MODE_SIGNATURE_WARNING = "mode_signature_warning"
        const val MODE_POST_SAVE_LINK_PROMPT = "mode_post_save_link_prompt"
        const val MODE_PICK_OR_SAVE_CREDENTIAL = "mode_pick_or_save_credential"

        const val EXTRA_CREDENTIAL_ID = "extra_credential_id"
        const val EXTRA_APP_PACKAGE = "extra_app_package"
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_CURRENT_SIGNERS = "extra_current_signers"
        const val EXTRA_TARGET_DOMAIN = "extra_target_domain"
        const val EXTRA_PROMPT_MESSAGE = "extra_prompt_message"
        const val EXTRA_USERNAME_NODE_ID = "extra_username_node_id"
        const val EXTRA_PASSWORD_NODE_ID = "extra_password_node_id"
    }
}
