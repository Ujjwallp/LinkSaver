package com.example.autofill.debug

import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.autofill.*
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only Autofill Test Lab (Step 8G.1 & 8G.2).
 * Compiled exclusively in the `debug` source set (`app/src/debug/`).
 *
 * Includes:
 * 1. Compose login screen
 * 2. View-based login WITH proper autofillHints
 * 3. View-based login WITHOUT hints (idEntry heuristics)
 * 4. Two-step login across two steps/contexts (identifier first, then password)
 * 5. Login that navigates/finishes after submit (FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
 * 6. Sign-up screen with confirm password (matching vs mismatched)
 * 7. Change-password screen
 * 8. Screen with a search box and OTP field (must NOT be treated as credentials)
 * 9. Screen with importantForAutofill=no (must do nothing)
 * 10. Second-app simulation exercising package + SHA-256 signature matching, beta variant isolation,
 *     publisher-mismatch warning, and user-confirmed app↔website linking.
 */
object AutofillDebugLab {
    const val isAvailable: Boolean = true
}

enum class DebugFormScenario(val title: String) {
    COMPOSE_LOGIN("1. Compose Login"),
    VIEW_WITH_HINTS("2. View Login (With Hints)"),
    VIEW_ID_HEURISTICS("3. View Login (No Hints / idEntry)"),
    TWO_STEP_LOGIN("4. Two-Step Login"),
    NAVIGATE_AFTER_SUBMIT("5. Finish / Navigate on Submit"),
    SIGN_UP_CONFIRM("6. Sign-Up (Confirm Pass)"),
    CHANGE_PASSWORD("7. Change Password"),
    SEARCH_AND_OTP("8. Search & OTP (Excluded)"),
    IMPORTANT_NO("9. importantForAutofill=no"),
    SECOND_APP_SIMULATOR("10. Second-App Signature Simulator")
}

@Composable
fun AutofillDebugLabDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedScenario by remember { mutableStateOf(DebugFormScenario.COMPOSE_LOGIN) }
    var diagnosticResult by remember { mutableStateOf("Select a scenario and tap 'Run Parser & Engine Check' to inspect live autofill behavior.") }

    var inputA by remember(selectedScenario) { mutableStateOf("alice@netflix.com") }
    var inputB by remember(selectedScenario) { mutableStateOf("CorrectHorseBatteryStaple!9") }
    var inputC by remember(selectedScenario) { mutableStateOf("CorrectHorseBatteryStaple!9") }
    var twoStepStage by remember(selectedScenario) { mutableStateOf(1) }
    var simulatedSubmittedScreen by remember(selectedScenario) { mutableStateOf(false) }

    // Second-App Simulator state
    var simPackage by remember { mutableStateOf("com.netflix.mediaclient") }
    var simSignerSha256 by remember {
        mutableStateOf("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 412.dp)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF14131C)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🧪 Debug Autofill App Lab (Step 8G)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF80DEEA)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "scenario_selector", contentType = "scenario_selector") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Choose App Form Scenario:", fontSize = 12.sp, color = Color(0xFFBDB8CE))
                            for (scenario in DebugFormScenario.values()) {
                                val selected = scenario == selectedScenario
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) Color(0xFF2B2540) else Color(0xFF1E1C2A),
                                    onClick = { selectedScenario = scenario }
                                ) {
                                    Text(
                                        text = scenario.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color(0xFFD0BCFF) else Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "active_form_box", contentType = "active_form_box") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1F1D2B), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = selectedScenario.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFFFD54F)
                            )

                            when (selectedScenario) {
                                DebugFormScenario.COMPOSE_LOGIN -> {
                                    OutlinedTextField(
                                        value = inputA,
                                        onValueChange = { inputA = it },
                                        label = { Text("Compose Username / Email") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("debug_compose_username")
                                    )
                                    OutlinedTextField(
                                        value = inputB,
                                        onValueChange = { inputB = it },
                                        label = { Text("Compose Password") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("debug_compose_password")
                                    )
                                }

                                DebugFormScenario.VIEW_WITH_HINTS -> {
                                    AndroidView(
                                        modifier = Modifier.fillMaxWidth(),
                                        factory = { ctx ->
                                            LinearLayout(ctx).apply {
                                                orientation = LinearLayout.VERTICAL
                                                addView(EditText(ctx).apply {
                                                    hint = "Username (View with AUTOFILL_HINT_USERNAME)"
                                                    setAutofillHints(View.AUTOFILL_HINT_USERNAME)
                                                    setText(inputA)
                                                })
                                                addView(EditText(ctx).apply {
                                                    hint = "Password (View with AUTOFILL_HINT_PASSWORD)"
                                                    setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
                                                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                                                    setText(inputB)
                                                })
                                            }
                                        }
                                    )
                                }

                                DebugFormScenario.VIEW_ID_HEURISTICS -> {
                                    AndroidView(
                                        modifier = Modifier.fillMaxWidth(),
                                        factory = { ctx ->
                                            LinearLayout(ctx).apply {
                                                orientation = LinearLayout.VERTICAL
                                                addView(EditText(ctx).apply {
                                                    hint = "idEntry = login_user_field (No autofillHints)"
                                                    setText(inputA)
                                                })
                                                addView(EditText(ctx).apply {
                                                    hint = "idEntry = user_pwd_input (No autofillHints)"
                                                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                                                    setText(inputB)
                                                })
                                            }
                                        }
                                    )
                                }

                                DebugFormScenario.TWO_STEP_LOGIN -> {
                                    Text(
                                        text = "Step $twoStepStage of 2: ${if (twoStepStage == 1) "Continue with email/phone" else "Enter password"}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF80DEEA)
                                    )
                                    if (twoStepStage == 1) {
                                        OutlinedTextField(
                                            value = inputA,
                                            onValueChange = { inputA = it },
                                            label = { Text("Continue with email or phone") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Button(onClick = { twoStepStage = 2 }) {
                                            Text("Next (Proceed to Step 2 Activity)")
                                        }
                                    } else {
                                        OutlinedTextField(
                                            value = inputB,
                                            onValueChange = { inputB = it },
                                            label = { Text("Password for $inputA") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedButton(onClick = { twoStepStage = 1 }) {
                                            Text("Back to Step 1")
                                        }
                                    }
                                }

                                DebugFormScenario.NAVIGATE_AFTER_SUBMIT -> {
                                    if (!simulatedSubmittedScreen) {
                                        OutlinedTextField(
                                            value = inputA,
                                            onValueChange = { inputA = it },
                                            label = { Text("Username") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = inputB,
                                            onValueChange = { inputB = it },
                                            label = { Text("Password") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Button(onClick = { simulatedSubmittedScreen = true }) {
                                            Text("Submit & Finish Login Activity")
                                        }
                                    } else {
                                        Text(
                                            "Login views are now INVISIBLE (Activity transitioned). SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE triggers system save prompt!",
                                            fontSize = 12.sp,
                                            color = Color(0xFF81C784)
                                        )
                                        OutlinedButton(onClick = { simulatedSubmittedScreen = false }) {
                                            Text("Reset Login Activity")
                                        }
                                    }
                                }

                                DebugFormScenario.SIGN_UP_CONFIRM -> {
                                    OutlinedTextField(
                                        value = inputA,
                                        onValueChange = { inputA = it },
                                        label = { Text("New Account Email") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = inputB,
                                        onValueChange = { inputB = it },
                                        label = { Text("New Password") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = inputC,
                                        onValueChange = { inputC = it },
                                        label = { Text("Confirm Password") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                DebugFormScenario.CHANGE_PASSWORD -> {
                                    OutlinedTextField(
                                        value = "OldPass#111",
                                        onValueChange = {},
                                        label = { Text("Current Password") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = inputB,
                                        onValueChange = { inputB = it },
                                        label = { Text("New Password") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = inputC,
                                        onValueChange = { inputC = it },
                                        label = { Text("Confirm New Password") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                DebugFormScenario.SEARCH_AND_OTP -> {
                                    OutlinedTextField(
                                        value = "search query",
                                        onValueChange = {},
                                        label = { Text("Search catalog (search_box)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = "123456",
                                        onValueChange = {},
                                        label = { Text("6-digit SMS OTP Verification Code") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                DebugFormScenario.IMPORTANT_NO -> {
                                    Text(
                                        text = "All fields on this screen have importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO. LinkVault must do nothing (shouldOfferFill = false).",
                                        fontSize = 12.sp,
                                        color = Color(0xFFFFB74D)
                                    )
                                }

                                DebugFormScenario.SECOND_APP_SIMULATOR -> {
                                    Text(
                                        text = "Simulate a second native app package & SHA-256 signing certificate:",
                                        fontSize = 12.sp,
                                        color = Color(0xFFBDB8CE)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                simPackage = "com.netflix.mediaclient"
                                                simSignerSha256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Prod App", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                simPackage = "com.netflix.mediaclient.beta"
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Beta Variant", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                simPackage = "com.netflix.mediaclient"
                                                simSignerSha256 = "9999999999999999999999999999999999999999999999999999999999999999"
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Tampered Key", fontSize = 11.sp)
                                        }
                                    }
                                    OutlinedTextField(
                                        value = simPackage,
                                        onValueChange = { simPackage = it },
                                        label = { Text("Simulated App Package") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = simSignerSha256,
                                        onValueChange = { simSignerSha256 = it },
                                        label = { Text("Simulated Signer SHA-256") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        diagnosticResult = runScenarioVerification(
                                            context = context,
                                            scenario = selectedScenario,
                                            username = inputA,
                                            password = inputB,
                                            confirmPassword = inputC,
                                            simPackage = simPackage,
                                            simSigner = simSignerSha256
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Run Parser & Engine Check")
                            }
                        }
                    }

                    item(key = "diagnostic_output", contentType = "diagnostic_output") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0E1A16), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Verification Output:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF81C784)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = diagnosticResult,
                                fontSize = 11.sp,
                                color = Color(0xFFE0F2F1)
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun runScenarioVerification(
    context: android.content.Context,
    scenario: DebugFormScenario,
    username: String,
    password: String,
    confirmPassword: String,
    simPackage: String,
    simSigner: String
): String = withContext(Dispatchers.IO) {
    val contexts: List<List<InspectableAutofillNode>> = when (scenario) {
        DebugFormScenario.COMPOSE_LOGIN -> listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.netflix.mediaclient",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "compose_user",
                            autofillHints = listOf(View.AUTOFILL_HINT_EMAIL_ADDRESS),
                            textValue = username
                        ),
                        InspectableAutofillNode(
                            logicalId = "compose_pass",
                            autofillHints = listOf(View.AUTOFILL_HINT_PASSWORD),
                            textValue = password
                        )
                    )
                )
            )
        )
        DebugFormScenario.VIEW_WITH_HINTS -> listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.github.android",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "view_hint_user",
                            autofillHints = listOf(View.AUTOFILL_HINT_USERNAME),
                            textValue = username
                        ),
                        InspectableAutofillNode(
                            logicalId = "view_hint_pass",
                            autofillHints = listOf(View.AUTOFILL_HINT_PASSWORD),
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                            textValue = password
                        )
                    )
                )
            )
        )
        DebugFormScenario.VIEW_ID_HEURISTICS -> listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.custom.nativeapp",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "id_user",
                            idEntry = "login_user_field",
                            textValue = username
                        ),
                        InspectableAutofillNode(
                            logicalId = "id_pass",
                            idEntry = "user_pwd_input",
                            textValue = password
                        )
                    )
                )
            )
        )
        DebugFormScenario.TWO_STEP_LOGIN -> listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.twostep.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "step1_email",
                            hint = "Continue with email",
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                            textValue = username
                        )
                    )
                )
            ),
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.twostep.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "step2_password",
                            idEntry = "password_input",
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                            textValue = password
                        )
                    )
                )
            )
        )
        DebugFormScenario.NAVIGATE_AFTER_SUBMIT -> listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.FastTransition.App",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "nav_user",
                            idEntry = "username",
                            textValue = username
                        ),
                        InspectableAutofillNode(
                            logicalId = "nav_pass",
                            idEntry = "password",
                            textValue = password
                        )
                    )
                )
            )
        )
        DebugFormScenario.SIGN_UP_CONFIRM -> listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.signup.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "signup_email",
                            idEntry = "email",
                            textValue = username
                        ),
                        InspectableAutofillNode(
                            logicalId = "signup_new_pass",
                            idEntry = "new_password",
                            textValue = password
                        ),
                        InspectableAutofillNode(
                            logicalId = "signup_confirm_pass",
                            idEntry = "confirm_password",
                            textValue = confirmPassword
                        )
                    )
                )
            )
        )
        DebugFormScenario.CHANGE_PASSWORD -> listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.changepass.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "old_pass",
                            idEntry = "current_password",
                            textValue = "OldSecret#1"
                        ),
                        InspectableAutofillNode(
                            logicalId = "new_pass",
                            idEntry = "new_password",
                            textValue = password
                        ),
                        InspectableAutofillNode(
                            logicalId = "confirm_new_pass",
                            idEntry = "confirm_password",
                            textValue = confirmPassword
                        )
                    )
                )
            )
        )
        DebugFormScenario.SEARCH_AND_OTP -> listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.shop.app",
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "search_field",
                            idEntry = "search_box",
                            hint = "Search products",
                            textValue = "headphones"
                        ),
                        InspectableAutofillNode(
                            logicalId = "otp_field",
                            idEntry = "sms_otp_code",
                            hint = "6-digit verification code",
                            textValue = "123456"
                        )
                    )
                )
            )
        )
        DebugFormScenario.IMPORTANT_NO -> listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = "com.securebank.app",
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "bank_user",
                            idEntry = "username",
                            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO,
                            textValue = username
                        ),
                        InspectableAutofillNode(
                            logicalId = "bank_pass",
                            idEntry = "password",
                            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO,
                            textValue = password
                        )
                    )
                )
            )
        )
        DebugFormScenario.SECOND_APP_SIMULATOR -> listOf(
            listOf(
                InspectableAutofillNode(
                    idPackage = simPackage,
                    children = listOf(
                        InspectableAutofillNode(
                            logicalId = "sim_user",
                            autofillHints = listOf(View.AUTOFILL_HINT_USERNAME),
                            textValue = username
                        ),
                        InspectableAutofillNode(
                            logicalId = "sim_pass",
                            autofillHints = listOf(View.AUTOFILL_HINT_PASSWORD),
                            textValue = password
                        )
                    )
                )
            )
        )
    }

    val parsed = AutofillStructureParser.parseInspectableContexts(contexts)
    val db = AppDatabase.getDatabase(context)
    val engine = AppAutofillEngine(context)

    if (scenario == DebugFormScenario.SECOND_APP_SIMULATOR) {
        // Ensure a baseline prod credential exists for com.netflix.mediaclient so simulator can compare
        val baselineSigner = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        val baselineMeta = CredentialAppMetadata(
            credentialId = 999,
            appPackage = "com.netflix.mediaclient",
            appSigners = setOf(baselineSigner),
            appLabel = "Netflix"
        )
        val matchStatus = AppIdentityVerifier.verifyAppIdentity(
            requestedPackage = parsed.packageName,
            currentSigners = setOf(simSigner),
            metadata = baselineMeta
        )
        val suggestions = AppWebsiteLinkManager(context).getCatalogSuggestedDomainsForApp(parsed.packageName)
        return@withContext buildString {
            appendLine("Normalized Package: ${parsed.packageName}")
            appendLine("Signer Match Result: $matchStatus")
            when (matchStatus) {
                AppSignatureMatchResult.EXACT_MATCH ->
                    appendLine("Action: Verified package + SHA-256 signer. Ready for authenticated fill.")
                AppSignatureMatchResult.SIGNATURE_CHANGED_WARNING ->
                    appendLine("Action: BLOCKED auto-fill! Shows warning: 'This app was updated by a different publisher, tap to review'.")
                AppSignatureMatchResult.PACKAGE_MISMATCH ->
                    appendLine("Action: Package mismatch (beta/debug variant isolated by default unless linked by user).")
            }
            if (suggestions.isNotEmpty()) {
                appendLine("Catalog Suggestion (user-confirmed only): ${suggestions.joinToString { "${it.appPackage} ↔ ${it.domain}" }}")
            }
        }
    }

    val fillPlan = engine.evaluateFillRequest(
        parsed = parsed,
        allLinks = db.linkDao().getAllLinksSnapshot(),
        isVaultUnlocked = true
    )

    buildString {
        appendLine("Package: ${parsed.packageName}")
        appendLine("shouldOfferFill: ${parsed.shouldOfferFill}")
        appendLine("canSaveCredential: ${parsed.canSaveCredential}")
        appendLine("Detected Username Field: ${parsed.usernameLogicalId.ifBlank { "NONE" }}")
        appendLine("Detected Password Field: ${parsed.passwordLogicalId.ifBlank { "NONE" }}")
        if (parsed.isSignUpOrChangePasswordFlow) {
            appendLine("Confirm Password Mismatch: ${parsed.hasConfirmPasswordMismatch}")
        }
        appendLine("Fill Plan: ${fillPlan::class.simpleName}")
    }
}
