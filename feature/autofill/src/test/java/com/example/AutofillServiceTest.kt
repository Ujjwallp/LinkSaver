package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.InputType
import android.view.View
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.autofill.*
import com.example.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AutofillServiceTest {

    private lateinit var context: Context
    private lateinit var neverSaveManager: NeverSaveManager
    private lateinit var metadataStore: CredentialMetadataStore
    private lateinit var linkManager: AppWebsiteLinkManager
    private lateinit var engine: AppAutofillEngine
    private lateinit var db: AppDatabase

    private val signerProd = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val signerTampered = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        neverSaveManager = NeverSaveManager(context)
        neverSaveManager.clearAllNeverSaveDomains()

        metadataStore = CredentialMetadataStore(context)
        metadataStore.clearAll()

        linkManager = AppWebsiteLinkManager(context, metadataStore)
        linkManager.clearAllIgnoredSuggestions()

        engine = AppAutofillEngine(context, neverSaveManager, metadataStore, linkManager)

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testNeverSaveManagerDomainsAndPackages() {
        assertFalse(neverSaveManager.isNeverSaveDomain("example.com"))
        assertFalse(neverSaveManager.isNeverSavePackage("com.example.bank"))

        neverSaveManager.addNeverSaveDomain("https://www.example.com")
        assertTrue(neverSaveManager.isNeverSaveDomain("example.com"))
        assertTrue(neverSaveManager.isNeverSaveDomain("www.example.com"))

        neverSaveManager.addNeverSavePackage("  COM.Example.Bank ")
        assertTrue(neverSaveManager.isNeverSavePackage("com.example.bank"))
        assertTrue(neverSaveManager.isNeverSaveTarget("", "com.example.bank"))

        neverSaveManager.removeNeverSaveDomain("example.com")
        assertFalse(neverSaveManager.isNeverSaveDomain("example.com"))
    }

    @Test
    fun testAutofillServicesInstantiable() {
        val autofillServiceClass = VaultAutofillService::class.java
        assertNotNull(autofillServiceClass)
        assertTrue(android.service.autofill.AutofillService::class.java.isAssignableFrom(autofillServiceClass))

        val credentialProviderClass = VaultCredentialProviderService::class.java
        assertNotNull(credentialProviderClass)
    }

    @Test
    fun testPackageAndSignatureMatchingAndBetaVariantIsolation() {
        val meta = CredentialAppMetadata(
            credentialId = 1,
            appPackage = "com.netflix.mediaclient",
            appSigners = setOf(signerProd),
            appLabel = "Netflix"
        )

        // 1. Exact package + exact SHA-256 signer matches (including different casing/whitespace)
        assertEquals(
            AppSignatureMatchResult.EXACT_MATCH,
            AppIdentityVerifier.verifyAppIdentity("com.netflix.mediaclient", setOf(signerProd), meta)
        )
        assertEquals(
            AppSignatureMatchResult.EXACT_MATCH,
            AppIdentityVerifier.verifyAppIdentity("  COM.Netflix.MediaClient ", setOf(signerProd.uppercase()), meta)
        )

        // 2. Same package but different signing certificate -> WARNING_SIGNATURE_CHANGED (never auto-fill!)
        assertEquals(
            AppSignatureMatchResult.SIGNATURE_CHANGED_WARNING,
            AppIdentityVerifier.verifyAppIdentity("com.netflix.mediaclient", setOf(signerTampered), meta)
        )
        assertEquals(
            AppSignatureMatchResult.SIGNATURE_CHANGED_WARNING,
            AppIdentityVerifier.verifyAppIdentity("com.netflix.mediaclient", emptySet(), meta)
        )

        // 3. Beta/debug variants are NOT matched by default
        assertEquals(
            AppSignatureMatchResult.PACKAGE_MISMATCH,
            AppIdentityVerifier.verifyAppIdentity("com.netflix.mediaclient.beta", setOf(signerProd), meta)
        )
        assertEquals(
            AppSignatureMatchResult.PACKAGE_MISMATCH,
            AppIdentityVerifier.verifyAppIdentity("com.netflix.mediaclient.debug", setOf(signerProd), meta)
        )

        // 4. After user explicitly links the beta variant with its signer, it matches
        val metaWithLinkedBeta = meta.copy(
            linkedApps = listOf(
                LinkedAppIdentity(
                    packageName = "com.netflix.mediaclient.beta",
                    signersSha256 = setOf(signerProd),
                    label = "Netflix Beta"
                )
            )
        )
        assertEquals(
            AppSignatureMatchResult.EXACT_MATCH,
            AppIdentityVerifier.verifyAppIdentity("com.netflix.mediaclient.beta", setOf(signerProd), metaWithLinkedBeta)
        )
    }

    @Test
    fun testBundledSuggestionMapUsedOnlyForSuggestionsNeverAutomaticFill() = runBlocking {
        // Save a website login for netflix.com
        val webParse = ParsedAutofillStructure(
            domainHost = "netflix.com",
            webDomain = "netflix.com",
            webScheme = "https",
            isWebRequest = true,
            packageName = "com.android.chrome",
            usernameNodeId = null,
            passwordNodeId = null,
            usernameLogicalId = "u",
            passwordLogicalId = "p",
            usernameValue = "viewer@netflix.com",
            passwordValue = "StreamPass#42",
            shouldOfferFill = true,
            canSaveCredential = true
        )
        val saveRes = engine.processSaveRequest(webParse, db.linkDao(), "vault@linkvault.local")
        assertTrue(saveRes is AutofillSaveOutcome.SavedNew)
        val webCredId = (saveRes as AutofillSaveOutcome.SavedNew).credentialId

        // Bundled catalog has com.netflix.mediaclient <-> netflix.com
        val catalogSuggestions = linkManager.getCatalogSuggestedAppsForDomain("netflix.com")
        assertTrue(catalogSuggestions.any { it.appPackage == "com.netflix.mediaclient" })

        // Request autofill from the native Netflix app BEFORE user confirms a link:
        // Must NOT match or fill automatically!
        val appFillRequest = ParsedAutofillStructure(
            domainHost = "com.netflix.mediaclient",
            isWebRequest = false,
            packageName = "com.netflix.mediaclient",
            usernameNodeId = null,
            passwordNodeId = null,
            usernameLogicalId = "u",
            passwordLogicalId = "p",
            usernameValue = "",
            passwordValue = "",
            shouldOfferFill = true
        )
        val planBeforeLink = engine.evaluateFillRequest(
            parsed = appFillRequest,
            allLinks = db.linkDao().getAllLinksSnapshot(),
            currentAppSignersOverride = setOf(signerProd)
        )
        assertEquals(
            "Bundled catalog must NEVER auto-fill an unconfirmed app",
            AutofillFillPlan.NoMatchingCredentials,
            planBeforeLink
        )

        // Now user saves or links com.netflix.mediaclient -> cross-link suggestion is offered
        val suggestions = linkManager.findCrossLinkSuggestionsOnSave(
            allCredentials = db.linkDao().getAllLinksSnapshot(),
            savingUsername = "viewer@netflix.com",
            savingAppPackage = "com.netflix.mediaclient",
            savingAppLabel = "Netflix",
            savingAppSigners = setOf(signerProd),
            savingWebsiteDomain = ""
        )
        assertEquals(1, suggestions.size)
        assertEquals("Also use this login for netflix.com?", suggestions.first().promptMessage)

        // "Always ask" leaves it unlinked
        linkManager.applyUserLinkDecision(suggestions.first(), LinkConfirmationChoice.ALWAYS_ASK)
        val planAfterAlwaysAsk = engine.evaluateFillRequest(
            parsed = appFillRequest,
            allLinks = db.linkDao().getAllLinksSnapshot(),
            currentAppSignersOverride = setOf(signerProd)
        )
        assertEquals(AutofillFillPlan.NoMatchingCredentials, planAfterAlwaysAsk)

        // User explicitly confirms "Yes"
        linkManager.applyUserLinkDecision(suggestions.first(), LinkConfirmationChoice.YES_LINK_NOW)
        val planAfterYes = engine.evaluateFillRequest(
            parsed = appFillRequest,
            allLinks = db.linkDao().getAllLinksSnapshot(),
            currentAppSignersOverride = setOf(signerProd)
        )
        assertTrue(planAfterYes is AutofillFillPlan.Ready)
        val ready = planAfterYes as AutofillFillPlan.Ready
        assertEquals(1, ready.verifiedMatches.size)
        assertEquals(webCredId, ready.verifiedMatches.first().entity.id)

        // If the linked app's signature changes, it must emit a warning match and NOT a verified match
        val planTampered = engine.evaluateFillRequest(
            parsed = appFillRequest,
            allLinks = db.linkDao().getAllLinksSnapshot(),
            currentAppSignersOverride = setOf(signerTampered)
        )
        assertTrue(planTampered is AutofillFillPlan.Ready)
        val readyTampered = planTampered as AutofillFillPlan.Ready
        assertTrue("Verified matches must be empty when signature changed", readyTampered.verifiedMatches.isEmpty())
        assertEquals(1, readyTampered.signatureWarningMatches.size)
    }

    @Test
    fun testFieldParserForAppsHeuristicsAndExclusions() {
        // 1. Compose-style nodes (no idEntry, using autofillHints & inputType)
        val composeTree = listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.compose.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "compose_phone_user",
                            inputType = InputType.TYPE_CLASS_PHONE,
                            hint = "Phone number",
                            textValue = "+15550199"
                        ),
                        InspectableAutofillNode(
                            logicalId = "compose_pass",
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                            textValue = "ComposeSecret!1"
                        )
                    )
                )
            )
        )
        val parsedCompose = AutofillStructureParser.parseInspectableContexts(composeTree)
        assertTrue(parsedCompose.shouldOfferFill)
        assertEquals("compose_phone_user", parsedCompose.usernameLogicalId)
        assertEquals("compose_pass", parsedCompose.passwordLogicalId)
        assertEquals("+15550199", parsedCompose.usernameValue)

        // 2. Position heuristic: password field preceded by a plain text field without hints/ids
        val positionTree = listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.legacy.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "search_bar",
                            idEntry = "search_input",
                            inputType = InputType.TYPE_CLASS_TEXT,
                            textValue = "ignored search"
                        ),
                        InspectableAutofillNode(
                            logicalId = "preceding_handle_input",
                            className = "android.widget.EditText",
                            inputType = InputType.TYPE_CLASS_TEXT,
                            textValue = "my_custom_handle"
                        ),
                        InspectableAutofillNode(
                            logicalId = "legacy_pwd",
                            idEntry = "pwd_field",
                            textValue = "LegacyPass!2"
                        )
                    )
                )
            )
        )
        val parsedPos = AutofillStructureParser.parseInspectableContexts(positionTree)
        assertTrue(parsedPos.shouldOfferFill)
        assertEquals("preceding_handle_input", parsedPos.usernameLogicalId)
        assertEquals("legacy_pwd", parsedPos.passwordLogicalId)

        // 3. Excluded fields: Search box, OTP verification code, Credit Card, and importantForAutofill=no
        val excludedTree = listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.shopping.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "search_field",
                            idEntry = "search_box",
                            textValue = "shoes"
                        ),
                        InspectableAutofillNode(
                            logicalId = "otp_field",
                            idEntry = "sms_otp_code",
                            textValue = "948201"
                        ),
                        InspectableAutofillNode(
                            logicalId = "cc_field",
                            idEntry = "card_number",
                            textValue = "4111222233334444"
                        ),
                        InspectableAutofillNode(
                            logicalId = "opted_out_pass",
                            idEntry = "password",
                            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO,
                            textValue = "Hidden"
                        )
                    )
                )
            )
        )
        val parsedExcluded = AutofillStructureParser.parseInspectableContexts(excludedTree)
        assertFalse("Must NOT offer fill on search/OTP/credit-card/opted-out fields", parsedExcluded.shouldOfferFill)
        assertEquals("", parsedExcluded.usernameLogicalId)
        assertEquals("", parsedExcluded.passwordLogicalId)

        // 4. Non-login screen (Profile edit / checkout address with "name" and "email" but NO password field)
        val checkoutAddressTree = listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.store.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "first_name",
                            idEntry = "first_name",
                            textValue = "Alice"
                        ),
                        InspectableAutofillNode(
                            logicalId = "email_field",
                            idEntry = "email",
                            textValue = "alice@example.com"
                        ),
                        InspectableAutofillNode(
                            logicalId = "street_addr",
                            idEntry = "shipping_address",
                            textValue = "123 Main St"
                        )
                    )
                )
            )
        )
        val parsedCheckout = AutofillStructureParser.parseInspectableContexts(checkoutAddressTree)
        assertFalse("Must NOT fill on checkout/profile-edit screens without a password field", parsedCheckout.shouldOfferFill)

        // 5. Two-step login across two FillContexts (Identifier first, then password)
        val step1Nodes = listOf(
            InspectableAutofillNode(
                idPackage = "com.twostep.login",
                children = listOf(
                    InspectableAutofillNode(
                        logicalId = "step1_identifier",
                        hint = "Continue with email",
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                        textValue = "step1user@domain.com"
                    )
                )
            )
        )
        val step2Nodes = listOf(
            InspectableAutofillNode(
                idPackage = "com.twostep.login",
                children = listOf(
                    InspectableAutofillNode(
                        logicalId = "step2_pass",
                        idEntry = "password",
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                        textValue = "Step2Pass!99"
                    )
                )
            )
        )
        val parsedStep1Only = AutofillStructureParser.parseInspectableContexts(listOf(step1Nodes))
        assertTrue("Two-step identifier screen should offer username fill", parsedStep1Only.shouldOfferFill)
        assertTrue(parsedStep1Only.isTwoStepUsernameStep)

        val parsedTwoStepMerged = AutofillStructureParser.parseInspectableContexts(listOf(step1Nodes, step2Nodes))
        assertTrue(parsedTwoStepMerged.shouldOfferFill)
        assertTrue(parsedTwoStepMerged.canSaveCredential)
        assertEquals("step1user@domain.com", parsedTwoStepMerged.usernameValue)
        assertEquals("Step2Pass!99", parsedTwoStepMerged.passwordValue)
    }

    @Test
    fun testAppSaveNewUpdateWithHistoryUndoAndConfirmPasswordValidation() = runBlocking {
        // 1. Mismatched confirm-password on sign-up must be REJECTED
        val signUpMismatchTree = listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.acme.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "u",
                            idEntry = "email",
                            textValue = "bob@acme.com"
                        ),
                        InspectableAutofillNode(
                            logicalId = "np",
                            idEntry = "new_password",
                            textValue = "FirstPass#1"
                        ),
                        InspectableAutofillNode(
                            logicalId = "cp",
                            idEntry = "confirm_password",
                            textValue = "DifferentPass#2"
                        )
                    )
                )
            )
        )
        val parsedMismatch = AutofillStructureParser.parseInspectableContexts(signUpMismatchTree)
        assertTrue(parsedMismatch.hasConfirmPasswordMismatch)
        assertFalse(parsedMismatch.canSaveCredential)

        val mismatchOutcome = engine.processSaveRequest(
            parsed = parsedMismatch,
            dao = db.linkDao(),
            currentAppSignersOverride = setOf(signerProd),
            appLabelOverride = "Acme App"
        )
        assertEquals(AutofillSaveOutcome.RejectedConfirmPasswordMismatch, mismatchOutcome)
        assertEquals(0, db.linkDao().getAllLinksSnapshot().size)

        // 2. Matching confirm-password saves new credential with a single password value
        val signUpMatchTree = listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.acme.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "u",
                            idEntry = "email",
                            textValue = "bob@acme.com"
                        ),
                        InspectableAutofillNode(
                            logicalId = "np",
                            idEntry = "new_password",
                            textValue = "InitialPass#100"
                        ),
                        InspectableAutofillNode(
                            logicalId = "cp",
                            idEntry = "confirm_password",
                            textValue = "InitialPass#100"
                        )
                    )
                )
            )
        )
        val parsedMatch = AutofillStructureParser.parseInspectableContexts(signUpMatchTree)
        assertFalse(parsedMatch.hasConfirmPasswordMismatch)
        assertTrue(parsedMatch.canSaveCredential)

        val savedOutcome = engine.processSaveRequest(
            parsed = parsedMatch,
            dao = db.linkDao(),
            currentAppSignersOverride = setOf(signerProd),
            appLabelOverride = "Acme App"
        )
        assertTrue(savedOutcome is AutofillSaveOutcome.SavedNew)
        val credId = (savedOutcome as AutofillSaveOutcome.SavedNew).credentialId
        assertEquals("Acme App", savedOutcome.displayTitle)

        val storedMeta = metadataStore.getMetadata(credId)
        assertEquals("com.acme.app", storedMeta.appPackage)
        assertEquals(setOf(signerProd), storedMeta.appSigners)
        assertEquals("Acme App", storedMeta.appLabel)

        // 3. Updating password for the same package + username records encrypted history & supports Undo
        val updateParsed = parsedMatch.copy(
            passwordValue = "UpdatedPass#200",
            confirmPasswordValue = "UpdatedPass#200"
        )
        val updateOutcome = engine.processSaveRequest(
            parsed = updateParsed,
            dao = db.linkDao(),
            currentAppSignersOverride = setOf(signerProd),
            appLabelOverride = "Acme App"
        )
        assertTrue(updateOutcome is AutofillSaveOutcome.UpdatedExisting)
        assertTrue((updateOutcome as AutofillSaveOutcome.UpdatedExisting).canUndo)

        val updatedEntity = db.linkDao().getLinkById(credId)!!
        assertEquals("UpdatedPass#200", engine.decryptPassword(updatedEntity))
        assertEquals(1, metadataStore.getMetadata(credId).passwordHistory.size)

        // Undo restores InitialPass#100
        val undone = engine.undoLastPasswordUpdate(credId, db.linkDao())
        assertTrue(undone)
        val restoredEntity = db.linkDao().getLinkById(credId)!!
        assertEquals("InitialPass#100", engine.decryptPassword(restoredEntity))

        // 4. Never-save by package blocks saving
        neverSaveManager.addNeverSavePackage("com.acme.app")
        val blockedOutcome = engine.processSaveRequest(
            parsed = updateParsed,
            dao = db.linkDao(),
            currentAppSignersOverride = setOf(signerProd)
        )
        assertEquals(AutofillSaveOutcome.SkippedNeverSave, blockedOutcome)
    }

    @Test
    fun testAppLabelFallbackWhenPackageVisibilityBlocksLookup() {
        // When PackageManager cannot see an unlisted package on Android 11+, resolveAppDisplayInfo
        // catches NameNotFoundException / SecurityException and falls back cleanly to the normalized package name.
        val resolved = AppIdentityVerifier.resolveAppDisplayInfo(
            context = context,
            rawPackageName = "  COM.Invisible.PrivateApp  "
        )
        assertEquals("com.invisible.privateapp", resolved.packageName)
        assertEquals("com.invisible.privateapp", resolved.displayLabel)
        assertFalse(resolved.resolvedFromPackageManager)
    }
}
