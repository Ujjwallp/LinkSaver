package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.autofill.AppAutofillEngine
import com.example.autofill.NeverSaveManager
import com.example.data.AppDatabase
import com.example.security.KeyHierarchy
import com.example.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun checkAutofillServiceActive(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val afm = context.getSystemService(AutofillManager::class.java)
    if (afm?.hasEnabledAutofillServices() == true) {
        return true
    }
    return try {
        val secureSetting = Settings.Secure.getString(context.contentResolver, "autofill_service") ?: ""
        secureSetting.contains(context.packageName, ignoreCase = true)
    } catch (_: Throwable) {
        false
    }
}

@Composable
fun AutofillSettingsDialog(
    isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val neverSaveManager = remember { NeverSaveManager(context) }
    val neverSaveDomains by neverSaveManager.neverSaveDomains.collectAsState()
    var newBlockDomain by remember { mutableStateOf("") }

    var showTroubleshootingHelp by remember { mutableStateOf(false) }
    var showManualAppEntry by remember { mutableStateOf(false) }
    var manualAppPackage by remember { mutableStateOf("") }
    var manualAppLabel by remember { mutableStateOf("") }
    var manualUsername by remember { mutableStateOf("") }
    var manualPassword by remember { mutableStateOf("") }
    var manualStatusMessage by remember { mutableStateOf("") }

    val isSupported = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val afm = context.getSystemService(AutofillManager::class.java)
            afm?.isAutofillSupported == true
        } else {
            false
        }
    }

    var isEnabled by remember {
        mutableStateOf(checkAutofillServiceActive(context))
    }

    val autofillSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isEnabled = checkAutofillServiceActive(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = checkAutofillServiceActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Explicit high-contrast palette for both Light and Dark themes
    val sheetBg = if (isDark) Color(0xFF171422) else Color(0xFFFAF8FF)
    val cardBg = if (isDark) Color(0xFF231F30) else Color(0xFFF2EDF9)
    val subCardBg = if (isDark) Color(0xFF1B1826) else Color(0xFFFFFFFF)
    val primaryText = if (isDark) Color(0xFFF5F2FA) else Color(0xFF1D192B)
    val secondaryText = if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F)
    val accentColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6200EE)
    val dividerColor = if (isDark) Color(0x22FFFFFF) else Color(0x18000000)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = primaryText,
        unfocusedTextColor = primaryText,
        focusedBorderColor = accentColor,
        unfocusedBorderColor = if (isDark) Color(0xFF49454F) else Color(0xFF938F99),
        focusedLabelColor = accentColor,
        unfocusedLabelColor = secondaryText,
        cursorColor = accentColor
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .fillMaxHeight(0.90f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = sheetBg,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f))
                        )
                    }

                    // Top Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Autofill & App Credentials",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "100% offline • Package & SHA-256 verified",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = secondaryText
                            )
                        }
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = secondaryText
                            )
                        }
                    }

                    HorizontalDivider(color = dividerColor)

                    // Scrollable Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Autofill Status Card
                        val statusBg = if (isEnabled) {
                            if (isDark) Color(0xFF142E22) else Color(0xFFE6F4EA)
                        } else {
                            if (isDark) Color(0xFF332018) else Color(0xFFFCE8E6)
                        }
                        val statusBorder = if (isEnabled) {
                            if (isDark) Color(0xFF2E7D32) else Color(0xFF81C995)
                        } else {
                            if (isDark) Color(0xFFE65100) else Color(0xFFF28B82)
                        }
                        val statusTitleColor = if (isEnabled) {
                            if (isDark) Color(0xFF81C784) else Color(0xFF137333)
                        } else {
                            if (isDark) Color(0xFFFFB74D) else Color(0xFFC5221F)
                        }
                        val statusBodyColor = if (isDark) Color(0xFFEAE6F2) else Color(0xFF2D2936)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = statusBg,
                            border = BorderStroke(1.dp, statusBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = statusTitleColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isEnabled) "LinkVault Autofill Active" else "System Autofill Not Selected",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = statusTitleColor
                                        )
                                        Text(
                                            text = if (!isSupported) {
                                                "Android Autofill Framework is not supported on this device."
                                            } else if (isEnabled) {
                                                "LinkVault is active for native apps and browsers."
                                            } else {
                                                "Select LinkVault (Link Saver) in Android Autofill Settings to fill and save passwords."
                                            },
                                            fontSize = 12.sp,
                                            color = statusBodyColor
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                val componentUri = Uri.parse("package:${context.packageName}")
                                                val requestIntent = Intent(
                                                    Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                                                    componentUri
                                                )
                                                try {
                                                    autofillSettingsLauncher.launch(requestIntent)
                                                } catch (_: Exception) {
                                                    try {
                                                        autofillSettingsLauncher.launch(Intent(Settings.ACTION_SETTINGS))
                                                    } catch (_: Exception) {
                                                        Toast.makeText(
                                                            context,
                                                            "Open Android Settings > Passwords & accounts > Autofill service",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("configure_system_autofill_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isEnabled) Color(0xFF2E7D32) else Color(0xFF6200EE),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isEnabled) "Change Service" else "Enable in Settings",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            isEnabled = checkAutofillServiceActive(context)
                                            val msg = if (isEnabled) "LinkVault is enabled as your Autofill service." else "LinkVault is not selected yet in System Settings."
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, statusTitleColor.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "Refresh",
                                            fontSize = 12.sp,
                                            color = statusTitleColor,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Manual App Password Entry & Troubleshooting Action Row
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "App Credential Tools",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = primaryText
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showManualAppEntry = !showManualAppEntry },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("toggle_manual_app_entry_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = accentColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Save App Login",
                                        fontSize = 12.sp,
                                        color = primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showTroubleshootingHelp = !showTroubleshootingHelp },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("toggle_autofill_help_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HelpOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = accentColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Why It's Blocked",
                                        fontSize = 12.sp,
                                        color = primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (com.example.BuildConfig.DEBUG) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val clazz = Class.forName("com.example.autofill.debug.AutofillDebugLabActivity")
                                            context.startActivity(Intent(context, clazz))
                                        } catch (_: Exception) {
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("open_autofill_debug_lab_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Science,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = accentColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Open Debug Autofill Test Forms (Step 8G)",
                                        fontSize = 12.sp,
                                        color = primaryText
                                    )
                                }
                            }
                        }

                        // 3. Expandable Manual App Password Entry Card (Step 8E.1)
                        if (showManualAppEntry) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = subCardBg,
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Manual App Password Entry",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = accentColor
                                    )
                                    Text(
                                        text = "For banking or custom-keyboard apps that opt out of system autofill, save credentials here and use Copy Username / Copy Password (auto-clears after 30 s).",
                                        fontSize = 12.sp,
                                        color = secondaryText
                                    )
                                    OutlinedTextField(
                                        value = manualAppLabel,
                                        onValueChange = { manualAppLabel = it },
                                        label = { Text("App Name", fontSize = 12.sp) },
                                        placeholder = { Text("Enter readable app name", fontSize = 12.sp) },
                                        singleLine = true,
                                        colors = textFieldColors,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("manual_app_label_input")
                                    )
                                    OutlinedTextField(
                                        value = manualAppPackage,
                                        onValueChange = { manualAppPackage = it },
                                        label = { Text("App Package Name", fontSize = 12.sp) },
                                        placeholder = { Text("Enter application package ID", fontSize = 12.sp) },
                                        singleLine = true,
                                        colors = textFieldColors,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("manual_app_package_input")
                                    )
                                    OutlinedTextField(
                                        value = manualUsername,
                                        onValueChange = { manualUsername = it },
                                        label = { Text("Username / Email / Phone", fontSize = 12.sp) },
                                        singleLine = true,
                                        colors = textFieldColors,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("manual_app_username_input")
                                    )
                                    OutlinedTextField(
                                        value = manualPassword,
                                        onValueChange = { manualPassword = it },
                                        label = { Text("Password", fontSize = 12.sp) },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        colors = textFieldColors,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("manual_app_password_input")
                                    )
                                    if (manualStatusMessage.isNotBlank()) {
                                        Text(
                                            text = manualStatusMessage,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color(0xFF81C784) else Color(0xFF1B5E20)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = {
                                                val pkg = manualAppPackage.trim()
                                                val pass = manualPassword
                                                if (pkg.isNotBlank() && pass.isNotBlank()) {
                                                    scope.launch(Dispatchers.IO) {
                                                        if (!KeyHierarchy.isVaultUnlocked()) {
                                                            KeyHierarchy.unlockWithHardwareKeystore(context)
                                                        }
                                                        val db = AppDatabase.getDatabase(context)
                                                        val prefs = SecureStorage.getEncryptedPrefs(
                                                            context,
                                                            "linksaver_vault_security_encrypted"
                                                        )
                                                        val activeEmail = (prefs.getString("active_user_email", null)
                                                            ?.takeIf { it.isNotBlank() }
                                                            ?: prefs.getString("last_active_user_email", "")
                                                            ?: "")
                                                        val engine = AppAutofillEngine(context, neverSaveManager)
                                                        val outcome = engine.processSaveRequest(
                                                            parsed = com.example.autofill.ParsedAutofillStructure(
                                                                domainHost = pkg,
                                                                packageName = pkg,
                                                                usernameNodeId = null,
                                                                passwordNodeId = null,
                                                                usernameValue = manualUsername.trim(),
                                                                passwordValue = pass,
                                                                canSaveCredential = true
                                                            ),
                                                            dao = db.linkDao(),
                                                            userEmail = activeEmail,
                                                            appLabelOverride = manualAppLabel.trim().ifBlank { null },
                                                            isVaultUnlocked = true
                                                        )
                                                        withContext(Dispatchers.Main) {
                                                            if (outcome is com.example.autofill.AutofillSaveOutcome.SavedNew ||
                                                                outcome is com.example.autofill.AutofillSaveOutcome.UpdatedExisting ||
                                                                outcome is com.example.autofill.AutofillSaveOutcome.AlreadyUpToDate
                                                            ) {
                                                                manualStatusMessage = "Saved encrypted credential for ${manualAppLabel.ifBlank { pkg }}."
                                                                manualAppPackage = ""
                                                                manualAppLabel = ""
                                                                manualUsername = ""
                                                                manualPassword = ""
                                                            } else {
                                                                manualStatusMessage = "Could not save credential."
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    manualStatusMessage = "Package name and password are required."
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accentColor,
                                                contentColor = if (isDark) Color(0xFF1D192B) else Color.White
                                            ),
                                            modifier = Modifier.testTag("save_manual_app_credential_button")
                                        ) {
                                            Text("Encrypt & Save", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Expandable "Why isn't autofill working in this app?" Help Screen (Step 8E.2)
                        if (showTroubleshootingHelp) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("autofill_troubleshooting_help_card"),
                                shape = RoundedCornerShape(16.dp),
                                color = subCardBg,
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Why isn't autofill working in this app?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = accentColor
                                    )
                                    TroubleshootingReasonItem(
                                        title = "1. The service isn't selected in Android Settings",
                                        fix = "Fix: Tap 'Enable in Settings' above and choose Link Saver as your default Autofill service. On Xiaomi HyperOS / Poco devices, also ensure Android Settings > Passwords & accounts > Autofill service is set to Link Saver.",
                                        isDark = isDark
                                    )
                                    TroubleshootingReasonItem(
                                        title = "2. The app blocks autofill (importantForAutofill=no or FLAG_SECURE)",
                                        fix = "Fix: Banking apps and custom PIN pads intentionally block autofill. Open LinkVault > Passwords, search the app, and use Copy Username / Copy Password (auto-clears after 30 s).",
                                        isDark = isDark
                                    )
                                    TroubleshootingReasonItem(
                                        title = "3. The vault is locked",
                                        fix = "Fix: Tap the 'Unlock LinkVault to autofill' prompt above your keyboard and verify your fingerprint or PIN.",
                                        isDark = isDark
                                    )
                                    TroubleshootingReasonItem(
                                        title = "4. The app's signing certificate changed",
                                        fix = "Fix: LinkVault checks the SHA-256 certificate of every app. If an app was re-signed by a different publisher, tap the warning prompt, authenticate, and confirm only if you trust the update.",
                                        isDark = isDark
                                    )
                                    TroubleshootingReasonItem(
                                        title = "5. Website login vs. Native App package not linked",
                                        fix = "Fix: LinkVault never links websites and apps without your consent. Open the credential in Passwords > Edit > Linked Apps & Websites to link the app package.",
                                        isDark = isDark
                                    )
                                }
                            }
                        }

                        // 5. Never-Save Blocklist Section (Domains & App Packages - Step 8D.1)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Never-Save List (Domains & App Packages)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = primaryText
                            )
                            Text(
                                text = "LinkVault will never offer to save or autofill credentials for domains or app packages in this list.",
                                fontSize = 12.sp,
                                color = secondaryText
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newBlockDomain,
                                    onValueChange = { newBlockDomain = it },
                                    placeholder = { Text("Domain or package name", fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = textFieldColors,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("never_save_domain_input")
                                )
                                Button(
                                    onClick = {
                                        if (newBlockDomain.isNotBlank()) {
                                            neverSaveManager.addNeverSaveDomain(newBlockDomain)
                                            newBlockDomain = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = if (isDark) Color(0xFF1D192B) else Color.White
                                    ),
                                    modifier = Modifier.testTag("add_never_save_domain_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Block Target", modifier = Modifier.size(18.dp))
                                }
                            }

                            if (neverSaveDomains.isEmpty()) {
                                Text(
                                    text = "No blocked domains or app packages.",
                                    fontSize = 12.sp,
                                    color = secondaryText.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (domain in neverSaveDomains.toList()) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = subCardBg,
                                            border = BorderStroke(1.dp, dividerColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = domain,
                                                    fontSize = 13.sp,
                                                    color = primaryText,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { neverSaveManager.removeNeverSaveDomain(domain) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Remove $domain",
                                                        tint = if (isDark) Color(0xFFFF8A80) else Color(0xFFD32F2F),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 6. Privacy & Play Data Safety Disclosure (Step 8F.2)
                        Text(
                            text = "Privacy & Security Disclosure: LinkVault processes login fields 100% locally on your device, verifies app package names and SHA-256 signing certificates before filling, never transmits data over the network (no INTERNET permission), and stores all credentials encrypted with Android Keystore AES-256-GCM.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = secondaryText
                        )
                    }

                    HorizontalDivider(color = dividerColor)

                    // Bottom Close Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("close_autofill_settings_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = if (isDark) Color(0xFF1D192B) else Color.White
                            )
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TroubleshootingReasonItem(
    title: String,
    fix: String,
    isDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = if (isDark) Color(0xFFFFD54F) else Color(0xFFB26A00)
        )
        Text(
            text = fix,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F)
        )
    }
}
