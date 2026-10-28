package com.example.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Platform-independent representation of a single node in an autofill view/semantics tree.
 * Supports Android Views, Jetpack Compose nodes (no idEntry), and embedded WebViews.
 */
data class InspectableAutofillNode(
    val autofillId: AutofillId? = null,
    val logicalId: String = "",
    val idPackage: String? = null,
    val idEntry: String? = null,
    val className: String? = null,
    val autofillHints: List<String> = emptyList(),
    val inputType: Int = 0,
    val autofillType: Int = View.AUTOFILL_TYPE_TEXT,
    val importantForAutofill: Int = View.IMPORTANT_FOR_AUTOFILL_AUTO,
    val hint: String? = null,
    val contentDescription: String? = null,
    val textValue: String = "",
    val webDomain: String? = null,
    val webScheme: String? = null,
    val isFocused: Boolean = false,
    val visibility: Int = View.VISIBLE,
    val children: List<InspectableAutofillNode> = emptyList()
)

data class ParsedAutofillStructure(
    val domainHost: String,
    val webDomain: String = "",
    val webScheme: String = "",
    val isWebRequest: Boolean = false,
    val packageName: String,
    val usernameNodeId: AutofillId?,
    val passwordNodeId: AutofillId?,
    val newPasswordNodeId: AutofillId? = null,
    val confirmPasswordNodeId: AutofillId? = null,
    val usernameLogicalId: String = "",
    val passwordLogicalId: String = "",
    val newPasswordLogicalId: String = "",
    val confirmPasswordLogicalId: String = "",
    val usernameValue: String,
    val passwordValue: String,
    val confirmPasswordValue: String = "",
    val isSignUpOrChangePasswordFlow: Boolean = false,
    val hasConfirmPasswordMismatch: Boolean = false,
    val isTwoStepUsernameStep: Boolean = false,
    val shouldOfferFill: Boolean = false,
    val canSaveCredential: Boolean = false
)

/**
 * Advanced offline field detector for Android native apps, Jetpack Compose screens, and browsers/WebViews.
 *
 * Implements Step 8C & Step 8D rules:
 * 1. Priority order: `autofillHints` -> `inputType` -> `idEntry` -> `hint`/`contentDescription` -> position heuristics
 *    (nearest preceding text field before a password field).
 * 2. Excludes search boxes, OTP/2FA/verification codes, credit card/payment fields, and nodes marked
 *    `importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO` or `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`.
 * 3. Supports Jetpack Compose nodes (which lack `idEntry` resource names) via semantics/autofill hints,
 *    `inputType`, and text/contentDescription labels.
 * 4. Supports embedded WebViews: honors `webDomain`/`webScheme` only when verified (`http`/`https` with a valid host);
 *    otherwise treats the request as unverified and applies strict native app-package + signature rules.
 * 5. Supports two-step logins (identifier/email/phone first, password second across `FillContext`s)
 *    while refusing to fill on non-login screens (profile edit, checkout address, search) that lack a password field.
 * 6. Validates sign-up / confirm-password flows: if a confirm-password field is present, both password fields
 *    must match or `canSaveCredential` is false, and only a single password value is stored.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AutofillStructureParser {

    private val KNOWN_BROWSERS = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.vivaldi.browser",
        "com.mi.globalbrowser",
        "com.android.browser",
        "com.kiwibrowser.browser",
        "com.yandex.browser",
        "org.chromium.chrome"
    )

    private val SEARCH_TOKENS = listOf(
        "search", "search_box", "search_bar", "search_input", "search_src_text", "query", "filter_query", "find_in_page"
    )

    private val OTP_TOKENS = listOf(
        "otp", "smsotpcode", "sms_otp", "onetimecode", "one_time_code", "verification_code",
        "verify_code", "totp", "2fa", "two_factor", "mfa", "security_code", "auth_code", "pin_code", "passcode_otp"
    )

    private val CREDIT_CARD_TOKENS = listOf(
        "creditcard", "credit_card", "cardnumber", "card_number", "cc_number", "cc-number",
        "cc_csc", "cvv", "cvc", "card_expiry", "cc_exp", "expiration_date", "cardholder"
    )

    private val NON_LOGIN_SCREEN_TOKENS = listOf(
        "street", "address_line", "shipping_address", "billing_address", "postal_code",
        "zip_code", "city", "state_province", "edit_profile", "profile_bio", "first_name",
        "last_name", "company_name", "checkout", "recipient_name"
    )

    private val TWO_STEP_ACTION_TOKENS = listOf(
        "continue with email", "continue with phone", "next", "sign in", "log in",
        "login", "continue to sign in", "enter your email or phone", "identifier"
    )

    fun parseStructure(structure: AssistStructure, fallbackPackageName: String = ""): ParsedAutofillStructure {
        return parseFillContexts(listOf(structure), fallbackPackageName)
    }

    /**
     * Parses one or more [AssistStructure] snapshots across a multi-step login flow (e.g. step 1 username,
     * step 2 password), merging earlier identifier values when the user progresses to the password screen.
     */
    fun parseFillContexts(
        structures: List<AssistStructure>,
        fallbackPackageName: String = ""
    ): ParsedAutofillStructure {
        if (structures.isEmpty()) {
            return ParsedAutofillStructure(
                domainHost = fallbackPackageName.ifBlank { "unknown_domain" },
                packageName = AppIdentityVerifier.normalizePackageName(fallbackPackageName),
                usernameNodeId = null,
                passwordNodeId = null,
                usernameValue = "",
                passwordValue = ""
            )
        }

        val resolvedFallbackPkg = fallbackPackageName.ifBlank {
            structures.lastOrNull { !it.activityComponent?.packageName.isNullOrBlank() }
                ?.activityComponent?.packageName.orEmpty()
        }

        val inspectableTrees = structures.map { struct ->
            val roots = mutableListOf<InspectableAutofillNode>()
            val count = struct.windowNodeCount
            val activityPkg = struct.activityComponent?.packageName ?: resolvedFallbackPkg.ifBlank { null }
            for (i in 0 until count) {
                val win = struct.getWindowNodeAt(i)
                roots.add(toInspectableNode(win.rootViewNode, activityPkg))
            }
            roots
        }

        return parseInspectableContexts(inspectableTrees, resolvedFallbackPkg)
    }

    /**
     * Parses one or more [InspectableAutofillNode] window lists (one list per FillContext).
     * Directly callable from unit tests without needing mocked AssistStructure internals.
     */
    fun parseInspectableContexts(
        contextWindows: List<List<InspectableAutofillNode>>,
        fallbackPackageName: String = ""
    ): ParsedAutofillStructure {
        var earliestUsernameId: AutofillId? = null
        var earliestUsernameLogicalId = ""
        var earliestUsernameValue = ""
        var latestParsed: ParsedAutofillStructure? = null

        for (windowRoots in contextWindows) {
            val single = parseSingleContextNodes(windowRoots, fallbackPackageName)
            if (single.usernameNodeId != null || single.usernameLogicalId.isNotBlank()) {
                earliestUsernameId = single.usernameNodeId ?: earliestUsernameId
                if (single.usernameLogicalId.isNotBlank()) {
                    earliestUsernameLogicalId = single.usernameLogicalId
                }
            }
            if (single.usernameValue.isNotBlank()) {
                earliestUsernameValue = single.usernameValue
            }
            latestParsed = single
        }

        val base = latestParsed ?: return ParsedAutofillStructure(
            domainHost = fallbackPackageName.ifBlank { "unknown_domain" },
            packageName = AppIdentityVerifier.normalizePackageName(fallbackPackageName),
            usernameNodeId = null,
            passwordNodeId = null,
            usernameValue = "",
            passwordValue = ""
        )

        // Merge username from step 1 if step 2 only shows the password field
        val mergedUsernameId = base.usernameNodeId ?: earliestUsernameId
        val mergedUsernameLogicalId = base.usernameLogicalId.ifBlank { earliestUsernameLogicalId }
        val mergedUsernameValue = base.usernameValue.ifBlank { earliestUsernameValue }

        val effectivePasswordValue = base.passwordValue
        val canSave = effectivePasswordValue.isNotBlank() &&
            !base.hasConfirmPasswordMismatch &&
            (base.passwordNodeId != null || base.newPasswordNodeId != null ||
                base.passwordLogicalId.isNotBlank() || base.newPasswordLogicalId.isNotBlank())

        return base.copy(
            usernameNodeId = mergedUsernameId,
            usernameLogicalId = mergedUsernameLogicalId,
            usernameValue = mergedUsernameValue,
            canSaveCredential = canSave
        )
    }

    private data class CandidateField(
        val node: InspectableAutofillNode,
        val orderIndex: Int,
        val isEditableText: Boolean,
        val isExcluded: Boolean,
        val usernameRank: Int,        // 1=hint, 2=inputType, 3=idEntry, 4=label/desc, 0=none
        val passwordRank: Int,        // 1=hint, 2=inputType, 3=idEntry, 4=label/desc, 0=none
        val isNewPassword: Boolean,
        val isConfirmPassword: Boolean
    )

    private fun parseSingleContextNodes(
        roots: List<InspectableAutofillNode>,
        fallbackPackageName: String
    ): ParsedAutofillStructure {
        var rawPackageName = fallbackPackageName
        var rawWebDomain = ""
        var rawWebScheme = ""
        var hasNonLoginFieldIndicators = false
        var hasTwoStepLoginCue = false

        val candidates = mutableListOf<CandidateField>()
        var traversalOrder = 0

        fun walk(node: InspectableAutofillNode, inheritedWebDomain: String, inheritedWebScheme: String) {
            // Respect importantForAutofill = NO_EXCLUDE_DESCENDANTS on containers
            if (node.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS) {
                return
            }

            if (rawPackageName.isBlank() && !node.idPackage.isNullOrBlank() &&
                node.idPackage != "android" &&
                !node.idPackage.startsWith("androidx.") &&
                node.idPackage != "com.google.android.material" &&
                node.idPackage != "miui" &&
                !node.idPackage.startsWith("com.miui.")
            ) {
                rawPackageName = node.idPackage
            }

            val currentWebDomain = if (!node.webDomain.isNullOrBlank()) node.webDomain else inheritedWebDomain
            val currentWebScheme = if (!node.webScheme.isNullOrBlank()) node.webScheme else inheritedWebScheme

            if (rawWebDomain.isBlank() && currentWebDomain.isNotBlank()) {
                rawWebDomain = currentWebDomain
                rawWebScheme = currentWebScheme.ifBlank { "https" }
            }

            val order = traversalOrder++
            val combinedTextLower = buildString {
                append(node.idEntry.orEmpty()).append(' ')
                append(node.hint.orEmpty()).append(' ')
                append(node.contentDescription.orEmpty()).append(' ')
                append(node.textValue)
            }.lowercase(Locale.US)

            if (NON_LOGIN_SCREEN_TOKENS.any { combinedTextLower.contains(it) }) {
                hasNonLoginFieldIndicators = true
            }
            if (TWO_STEP_ACTION_TOKENS.any { combinedTextLower.contains(it) }) {
                hasTwoStepLoginCue = true
            }

            val evaluated = evaluateNode(node, order)
            if (evaluated != null) {
                candidates.add(evaluated)
            }

            for (child in node.children) {
                walk(child, currentWebDomain, currentWebScheme)
            }
        }

        for (root in roots) {
            walk(root, "", "")
        }

        val activeCandidates = candidates.filter { !it.isExcluded }

        // Select best password candidate (lowest non-zero rank, first in order)
        val passwordCandidates = activeCandidates.filter { it.passwordRank > 0 && !it.isConfirmPassword }
        val primaryPasswordCandidate = passwordCandidates
            .filter { !it.isNewPassword }
            .minWithOrNull(compareBy<CandidateField> { it.passwordRank }.thenBy { it.orderIndex })
            ?: passwordCandidates.minWithOrNull(compareBy<CandidateField> { it.passwordRank }.thenBy { it.orderIndex })

        val newPasswordCandidate = passwordCandidates
            .filter { it.isNewPassword }
            .minWithOrNull(compareBy<CandidateField> { it.passwordRank }.thenBy { it.orderIndex })
            ?: if (passwordCandidates.size >= 2 && primaryPasswordCandidate != null) {
                // In change-password screens with old + new password, the second password field is newPassword
                passwordCandidates.firstOrNull { it.orderIndex > primaryPasswordCandidate.orderIndex }
            } else null

        val confirmPasswordCandidate = activeCandidates
            .filter { it.isConfirmPassword }
            .minByOrNull { it.orderIndex }

        val effectivePasswordCandidate = newPasswordCandidate ?: primaryPasswordCandidate

        // Select best username candidate:
        // 1. Explicit rank 1..4
        var usernameCandidate = activeCandidates
            .filter {
                it.usernameRank > 0 &&
                    it != primaryPasswordCandidate &&
                    it != newPasswordCandidate &&
                    it != confirmPasswordCandidate
            }
            .minWithOrNull(compareBy<CandidateField> { it.usernameRank }.thenBy { it.orderIndex })

        // 2. Position heuristic (Step 8C.1): nearest preceding editable text field before the password field
        if (usernameCandidate == null && effectivePasswordCandidate != null) {
            usernameCandidate = activeCandidates
                .filter {
                    it.isEditableText &&
                        it.passwordRank == 0 &&
                        !it.isConfirmPassword &&
                        it.orderIndex < effectivePasswordCandidate.orderIndex
                }
                .maxByOrNull { it.orderIndex }
        }

        val normPkg = AppIdentityVerifier.normalizePackageName(rawPackageName)
        val cleanWebScheme = rawWebScheme.lowercase(Locale.US).ifBlank { "https" }
        val cleanWebDomain = if (cleanWebScheme in setOf("https", "http")) {
            AppIdentityVerifier.normalizeRegistrableDomain(rawWebDomain)
        } else {
            ""
        }
        val isVerifiedWeb = cleanWebDomain.isNotBlank() && cleanWebDomain.contains('.') &&
            cleanWebDomain != "localhost"

        val finalDomainHost = when {
            isVerifiedWeb -> cleanWebDomain
            normPkg.isNotBlank() -> normPkg
            else -> "unknown_domain"
        }

        val hasAnyPasswordField = effectivePasswordCandidate != null
        val hasUsernameField = usernameCandidate != null

        // Rule 8C.4 & 8C.5:
        // Do NOT fill on non-login screens (profile edit, search, checkout address) when there is no password field.
        // Only allow two-step identifier-only fill if there is a username field, NO non-login indicators,
        // and either an explicit two-step cue or at most 2 editable text fields on screen.
        val totalEditableCount = activeCandidates.count { it.isEditableText }
        val isTwoStepUsernameStep = !hasAnyPasswordField &&
            hasUsernameField &&
            !hasNonLoginFieldIndicators &&
            (hasTwoStepLoginCue || totalEditableCount == 1)

        val shouldOfferFill = hasAnyPasswordField || isTwoStepUsernameStep

        val chosenPasswordValue = (newPasswordCandidate?.node?.textValue?.takeIf { it.isNotBlank() }
            ?: primaryPasswordCandidate?.node?.textValue.orEmpty())
        val confirmValue = confirmPasswordCandidate?.node?.textValue.orEmpty()

        val hasConfirmMismatch = confirmPasswordCandidate != null &&
            chosenPasswordValue.isNotBlank() &&
            chosenPasswordValue != confirmValue

        val isSignUpOrChange = newPasswordCandidate != null || confirmPasswordCandidate != null

        val canSave = chosenPasswordValue.isNotBlank() &&
            !hasConfirmMismatch &&
            hasAnyPasswordField

        return ParsedAutofillStructure(
            domainHost = finalDomainHost,
            webDomain = if (isVerifiedWeb) cleanWebDomain else "",
            webScheme = if (isVerifiedWeb) cleanWebScheme else "",
            isWebRequest = isVerifiedWeb || KNOWN_BROWSERS.contains(normPkg),
            packageName = normPkg,
            usernameNodeId = usernameCandidate?.node?.autofillId,
            passwordNodeId = (effectivePasswordCandidate ?: primaryPasswordCandidate)?.node?.autofillId,
            newPasswordNodeId = newPasswordCandidate?.node?.autofillId,
            confirmPasswordNodeId = confirmPasswordCandidate?.node?.autofillId,
            usernameLogicalId = usernameCandidate?.node?.logicalId.orEmpty(),
            passwordLogicalId = (effectivePasswordCandidate ?: primaryPasswordCandidate)?.node?.logicalId.orEmpty(),
            newPasswordLogicalId = newPasswordCandidate?.node?.logicalId.orEmpty(),
            confirmPasswordLogicalId = confirmPasswordCandidate?.node?.logicalId.orEmpty(),
            usernameValue = usernameCandidate?.node?.textValue.orEmpty(),
            passwordValue = chosenPasswordValue,
            confirmPasswordValue = confirmValue,
            isSignUpOrChangePasswordFlow = isSignUpOrChange,
            hasConfirmPasswordMismatch = hasConfirmMismatch,
            isTwoStepUsernameStep = isTwoStepUsernameStep,
            shouldOfferFill = shouldOfferFill,
            canSaveCredential = canSave
        )
    }

    private fun evaluateNode(node: InspectableAutofillNode, orderIndex: Int): CandidateField? {
        if (node.visibility != View.VISIBLE) return null

        // Rule 8C.1 & 8E.1: Strictly respect importantForAutofill = NO
        if (node.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO ||
            node.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        ) {
            return CandidateField(
                node = node,
                orderIndex = orderIndex,
                isEditableText = false,
                isExcluded = true,
                usernameRank = 0,
                passwordRank = 0,
                isNewPassword = false,
                isConfirmPassword = false
            )
        }

        val hintsLower = node.autofillHints.map { it.trim().lowercase(Locale.US) }
        val idLower = node.idEntry?.trim()?.lowercase(Locale.US).orEmpty()
        val hintTextLower = node.hint?.trim()?.lowercase(Locale.US).orEmpty()
        val descLower = node.contentDescription?.trim()?.lowercase(Locale.US).orEmpty()
        val classLower = node.className?.lowercase(Locale.US).orEmpty()
        val inputType = node.inputType

        val hasIdentityOrInput = node.autofillId != null ||
            node.logicalId.isNotBlank() ||
            hintsLower.isNotEmpty() ||
            inputType != 0 ||
            idLower.isNotBlank() ||
            hintTextLower.isNotBlank() ||
            descLower.isNotBlank()
        if (!hasIdentityOrInput) return null

        val isEditableLike = node.autofillType == View.AUTOFILL_TYPE_TEXT ||
            inputType != 0 ||
            hintsLower.isNotEmpty() ||
            classLower.contains("edittext") ||
            classLower.contains("textfield") ||
            idLower.isNotBlank() ||
            hintTextLower.isNotBlank()
        if (!isEditableLike) return null

        val allTokens = buildString {
            append(hintsLower.joinToString(" ")).append(' ')
            append(idLower).append(' ')
            append(hintTextLower).append(' ')
            append(descLower)
        }

        // Check exclusions: Search, OTP/2FA, Credit Card
        val isSearch = SEARCH_TOKENS.any { token ->
            hintsLower.contains(token) || idLower.contains(token) ||
                hintTextLower.contains(token) || descLower.contains(token)
        }
        val isOtp = OTP_TOKENS.any { token ->
            hintsLower.any { it.contains(token) } || idLower.contains(token) ||
                hintTextLower.contains(token) || descLower.contains(token)
        }
        val isCreditCard = CREDIT_CARD_TOKENS.any { token ->
            hintsLower.any { it.contains(token) } || idLower.contains(token) ||
                hintTextLower.contains(token) || descLower.contains(token)
        }

        if (isSearch || isOtp || isCreditCard) {
            return CandidateField(
                node = node,
                orderIndex = orderIndex,
                isEditableText = false,
                isExcluded = true,
                usernameRank = 0,
                passwordRank = 0,
                isNewPassword = false,
                isConfirmPassword = false
            )
        }

        // Detect Confirm Password vs New Password
        val isConfirmPassword = idLower.contains("confirm_pass") ||
            idLower.contains("pass_confirm") ||
            idLower.contains("confirmpass") ||
            idLower.contains("repeat_pass") ||
            idLower.contains("retype_pass") ||
            idLower.contains("verify_pass") ||
            hintTextLower.contains("confirm password") ||
            hintTextLower.contains("re-enter password") ||
            hintTextLower.contains("repeat password") ||
            descLower.contains("confirm password")

        val isNewPassword = !isConfirmPassword && (
            hintsLower.any { it == "newpassword" || it == "new_password" || it.contains("newpassword") } ||
                idLower.contains("new_pass") ||
                idLower.contains("newpass") ||
                idLower.contains("create_pass") ||
                hintTextLower.contains("new password") ||
                hintTextLower.contains("create password") ||
                descLower.contains("new password")
            )

        // Password rank (1 = autofillHints, 2 = inputType, 3 = idEntry, 4 = hint/contentDescription)
        val hasPasswordHint = hintsLower.any {
            it == View.AUTOFILL_HINT_PASSWORD.lowercase(Locale.US) ||
                it == "newpassword" ||
                it == "new_password" ||
                it.contains("password")
        }
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val hasPasswordInputType = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        val hasPasswordId = idLower.contains("password") ||
            idLower == "pass" ||
            idLower.startsWith("pass_") ||
            idLower.endsWith("_pass") ||
            idLower.contains("pwd")
        val hasPasswordLabel = hintTextLower.contains("password") ||
            descLower.contains("password") ||
            hintTextLower == "passcode"

        val passwordRank = when {
            hasPasswordHint -> 1
            hasPasswordInputType -> 2
            hasPasswordId -> 3
            hasPasswordLabel -> 4
            else -> 0
        }

        // Username rank (1 = autofillHints, 2 = inputType, 3 = idEntry, 4 = hint/contentDescription)
        val hasUsernameHint = hintsLower.any {
            it == View.AUTOFILL_HINT_USERNAME.lowercase(Locale.US) ||
                it == View.AUTOFILL_HINT_EMAIL_ADDRESS.lowercase(Locale.US) ||
                it == View.AUTOFILL_HINT_PHONE.lowercase(Locale.US) ||
                it == "phonenumber" ||
                it == "phonenational" ||
                it == "newusername" ||
                it.contains("username") ||
                it.contains("email")
        }
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val hasUsernameInputType = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            inputClass == InputType.TYPE_CLASS_PHONE
        val hasUsernameId = idLower.contains("username") ||
            idLower.contains("user_name") ||
            idLower == "user" ||
            idLower.startsWith("user_") ||
            idLower.endsWith("_user") ||
            idLower.contains("email") ||
            idLower.contains("login") ||
            idLower.contains("identifier") ||
            idLower.contains("phone") ||
            idLower.contains("mobile")
        val hasUsernameLabel = hintTextLower.contains("username") ||
            hintTextLower.contains("email") ||
            hintTextLower.contains("phone") ||
            hintTextLower.contains("mobile number") ||
            hintTextLower.contains("user id") ||
            descLower.contains("username") ||
            descLower.contains("email") ||
            descLower.contains("phone")

        val usernameRank = if (passwordRank > 0 || isConfirmPassword) {
            0
        } else {
            when {
                hasUsernameHint -> 1
                hasUsernameInputType -> 2
                hasUsernameId -> 3
                hasUsernameLabel -> 4
                else -> 0
            }
        }

        val isPlainEditableText = passwordRank == 0 && !isConfirmPassword && (
            inputClass == InputType.TYPE_CLASS_TEXT ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                classLower.contains("edittext") ||
                classLower.contains("textfield") ||
                usernameRank > 0
            )

        if (passwordRank == 0 && usernameRank == 0 && !isConfirmPassword && !isPlainEditableText) {
            return null
        }

        return CandidateField(
            node = node,
            orderIndex = orderIndex,
            isEditableText = isPlainEditableText,
            isExcluded = false,
            usernameRank = usernameRank,
            passwordRank = passwordRank,
            isNewPassword = isNewPassword,
            isConfirmPassword = isConfirmPassword
        )
    }

    private fun toInspectableNode(
        node: AssistStructure.ViewNode,
        fallbackActivityPkg: String?
    ): InspectableAutofillNode {
        val childCount = node.childCount
        val children = ArrayList<InspectableAutofillNode>(childCount)
        for (i in 0 until childCount) {
            children.add(toInspectableNode(node.getChildAt(i), fallbackActivityPkg))
        }
        val textVal = node.autofillValue?.let {
            if (it.isText) it.textValue?.toString() else null
        } ?: node.text?.toString() ?: ""

        val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                node.webScheme
            } catch (_: Throwable) {
                null
            }
        } else null

        val important = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                node.importantForAutofill
            } catch (_: Throwable) {
                View.IMPORTANT_FOR_AUTOFILL_AUTO
            }
        } else View.IMPORTANT_FOR_AUTOFILL_AUTO

        // Extract HTML <input> attributes for Chrome, Mi Browser, and embedded WebViews
        val htmlInfo = try {
            node.htmlInfo
        } catch (_: Throwable) {
            null
        }
        val htmlTag = htmlInfo?.tag?.lowercase(Locale.US).orEmpty()
        val htmlAttrs = mutableMapOf<String, String>()
        if (htmlInfo != null) {
            try {
                htmlInfo.attributes?.forEach { pair ->
                    val k = pair?.first?.lowercase(Locale.US)?.trim().orEmpty()
                    val v = pair?.second?.trim().orEmpty()
                    if (k.isNotEmpty() && v.isNotEmpty()) {
                        htmlAttrs[k] = v
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val htmlType = htmlAttrs["type"]?.lowercase(Locale.US).orEmpty()
        val htmlAutocomplete = htmlAttrs["autocomplete"]?.lowercase(Locale.US).orEmpty()
        val htmlName = htmlAttrs["name"].orEmpty()
        val htmlId = htmlAttrs["id"].orEmpty()
        val htmlPlaceholder = htmlAttrs["placeholder"].orEmpty()
        val htmlLabel = htmlAttrs["label"].orEmpty()

        val mergedHints = mutableListOf<String>()
        node.autofillHints?.let { mergedHints.addAll(it) }
        if (htmlAutocomplete.isNotBlank()) {
            mergedHints.addAll(htmlAutocomplete.split(' ').map { it.trim() }.filter { it.isNotEmpty() })
        }
        if (htmlTag == "input") {
            when (htmlType) {
                "password" -> if (mergedHints.none { it.contains("password", ignoreCase = true) }) {
                    mergedHints.add(View.AUTOFILL_HINT_PASSWORD)
                }
                "email" -> if (mergedHints.none { it.contains("email", ignoreCase = true) }) {
                    mergedHints.add(View.AUTOFILL_HINT_EMAIL_ADDRESS)
                }
                "tel" -> if (mergedHints.none { it.contains("phone", ignoreCase = true) }) {
                    mergedHints.add(View.AUTOFILL_HINT_PHONE)
                }
            }
        }

        val effectiveInputType = if (node.inputType != 0) {
            node.inputType
        } else if (htmlTag == "input" && htmlType !in listOf("hidden", "submit", "button", "checkbox", "radio", "reset", "file", "image")) {
            when (htmlType) {
                "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                "tel" -> InputType.TYPE_CLASS_PHONE
                else -> InputType.TYPE_CLASS_TEXT
            }
        } else {
            0
        }

        val effectiveIdEntry = node.idEntry?.takeIf { it.isNotBlank() }
            ?: htmlName.takeIf { it.isNotBlank() }
            ?: htmlId.takeIf { it.isNotBlank() }

        val effectiveHint = node.hint?.takeIf { it.isNotBlank() }
            ?: htmlPlaceholder.takeIf { it.isNotBlank() }
            ?: htmlLabel.takeIf { it.isNotBlank() }

        return InspectableAutofillNode(
            autofillId = node.autofillId,
            logicalId = effectiveIdEntry.orEmpty(),
            idPackage = fallbackActivityPkg ?: node.idPackage,
            idEntry = effectiveIdEntry,
            className = node.className,
            autofillHints = mergedHints,
            inputType = effectiveInputType,
            autofillType = node.autofillType,
            importantForAutofill = important,
            hint = effectiveHint,
            contentDescription = node.contentDescription?.toString(),
            textValue = textVal,
            webDomain = node.webDomain,
            webScheme = scheme,
            isFocused = node.isFocused,
            visibility = node.visibility,
            children = children
        )
    }
}
