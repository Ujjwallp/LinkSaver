package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.compose.AsyncImagePainter
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.ContentScale
import android.content.ClipboardManager
import com.example.data.LinkEntity
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*

data class CardThemeSpec(
    val cardBg: Color,
    val contentColor: Color,
    val badgeBgColor: Color,
    val tagLabel: String
)

fun formatCardDate(timestamp: Long): String {
    return com.example.util.DateTimeFormatterUtil.formatFullDateTime(timestamp)
}

fun generateRandomPassword(length: Int = 16): String {
    val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '_', '+', '=')
    return (1..length)
        .map { charPool.random() }
        .joinToString("")
}

interface BiometricHostActivity {
    fun isBiometricHardwareAvailable(): Boolean
    fun triggerBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
    fun hasWindowFocus(): Boolean
    fun setSecureScreenEnabled(enabled: Boolean)
}

fun findMainActivity(context: android.content.Context): BiometricHostActivity? {
    var cur = context
    while (cur is android.content.ContextWrapper) {
        if (cur is BiometricHostActivity) return cur
        cur = cur.baseContext
    }
    return cur as? BiometricHostActivity
}

private val clipboardScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

fun copySecureTextToClipboard(
    context: android.content.Context,
    text: String,
    isSensitive: Boolean = false,
    label: String = "Link Saver"
) {
    try {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clipData = android.content.ClipData.newPlainText(label, text)
        if (isSensitive && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val extras = android.os.PersistableBundle().apply {
                putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clipData.description.extras = extras
        }
        clipboard.setPrimaryClip(clipData)

        if (isSensitive && text.isNotEmpty()) {
            clipboardScope.launch {
                kotlinx.coroutines.delay(30_000L) // Auto-clear after 30 seconds
                try {
                    val currentClip = clipboard.primaryClip
                    if (currentClip != null && currentClip.itemCount > 0) {
                        val currentText = currentClip.getItemAt(0)?.text?.toString()
                        if (currentText == text) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                clipboard.clearPrimaryClip()
                            } else {
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                            }
                        }
                    }
                } catch (e: Throwable) {
                    // Safe fallback
                }
            }
        }
    } catch (e: Throwable) {
        // Safe fallback
    }
}

fun getCategoryThemeColor(name: String, isDark: Boolean): Color {
    val hash = name.hashCode()
    val lightColors = listOf(
        Color(0xFFE3F2FD), // Blue
        Color(0xFFE8F5E9), // Green
        Color(0xFFFFF3E0), // Orange
        Color(0xFFF3E5F5), // Purple
        Color(0xFFE0F7FA), // Cyan
        Color(0xFFFFFDE7), // Yellow
        Color(0xFFFFEBEE), // Red
        Color(0xFFF0F4C3), // Lime
        Color(0xFFE8EAF6), // Indigo
        Color(0xFFE0F2F1)  // Teal
    )
    val darkColors = listOf(
        Color(0xFF0F2C46), // Dark Blue
        Color(0xFF10361C), // Dark Green
        Color(0xFF3B2303), // Dark Orange
        Color(0xFF330E3F), // Dark Purple
        Color(0xFF063135), // Dark Cyan
        Color(0xFF3C3A0A), // Dark Yellow
        Color(0xFF450A12), // Dark Red
        Color(0xFF283606), // Dark Lime
        Color(0xFF141C47), // Dark Indigo
        Color(0xFF07332C)  // Dark Teal
    )
    val index = Math.abs(hash) % lightColors.size
    return if (isDark) darkColors[index] else lightColors[index]
}

fun getCategoryTextColor(name: String, isDark: Boolean): Color {
    val hash = name.hashCode()
    val textColorsLight = listOf(
        Color(0xFF1565C0), // Blue
        Color(0xFF2E7D32), // Green
        Color(0xFFEF6C00), // Orange
        Color(0xFF6A1B9A), // Purple
        Color(0xFF00838F), // Cyan
        Color(0xFFE65100), // Darker Orange
        Color(0xFFC62828), // Red
        Color(0xFF827717), // Lime/Olive
        Color(0xFF283593), // Indigo
        Color(0xFF00695C)  // Teal
    )
    val textColorsDark = listOf(
        Color(0xFF90CAF9), // Light Blue
        Color(0xFFA5D6A7), // Light Green
        Color(0xFFFFCC80), // Light Orange
        Color(0xFFE1BEE7), // Light Purple
        Color(0xFF80DEEA), // Light Cyan
        Color(0xFFFFF59D), // Light Yellow
        Color(0xFFEF9A9A), // Light Red
        Color(0xFFE6EE9C), // Light Lime
        Color(0xFF9FA8DA), // Light Indigo
        Color(0xFF80CBC4)  // Light Teal
    )
    val index = Math.abs(hash) % textColorsLight.size
    return if (isDark) textColorsDark[index] else textColorsLight[index]
}

fun getCategoryEmojiAndLabel(name: String): Pair<String, String> {
    if (name.isEmpty()) return Pair("", "")
    val firstChar = name.first()
    return if (firstChar.code > 127 && !firstChar.isLetterOrDigit()) {
        val parts = name.split(" ", limit = 2)
        if (parts.size == 2 && parts[0].length <= 3) {
            Pair(parts[0], parts[1])
        } else {
            Pair(firstChar.toString(), name.drop(1).trim())
        }
    } else {
        Pair("📁", name)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSaverApp(
    viewModel: LinkViewModel,
    modifier: Modifier = Modifier,
    onShareReceivedHandled: () -> Unit = {},
    isDarkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    onToggleTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val links by viewModel.links.collectAsStateWithLifecycle()
    val vaultData by viewModel.vaultData.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val allUserLinks by viewModel.allUserUnfilteredLinks.collectAsStateWithLifecycle()
    
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val showOnlyFavorites by viewModel.showOnlyFavorites.collectAsStateWithLifecycle()
    val vaultSegment by viewModel.vaultSegment.collectAsStateWithLifecycle()
    val viewDensity by viewModel.viewDensity.collectAsStateWithLifecycle()
    val privacyShieldEnabled by viewModel.privacyShieldEnabled.collectAsStateWithLifecycle()
    val vaultCounts by viewModel.vaultCounts.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                com.example.data.FaviconStorageManager.setScrolling(isScrolling)
            }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var linkToEdit by remember { mutableStateOf<LinkEntity?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showFolderDirectoryDialog by remember { mutableStateOf(false) }
    var folderDirectorySearchQuery by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }

    // Duplicate detection states
    var showDuplicateManager by remember { mutableStateOf(false) }
    var showDuplicateWarning by remember { mutableStateOf(false) }
    var duplicateMatchFound by remember { mutableStateOf<LinkEntity?>(null) }
    
    // Private Vault integration state with biometric check
    var isPrivateVaultUnlocked by remember { mutableStateOf(false) }
    var isPasswordsUnlocked by remember { mutableStateOf(false) }

    val lockedCustomCategories by viewModel.lockedCustomCategories.collectAsStateWithLifecycle()
    val unlockedCustomCategories by viewModel.unlockedCustomCategories.collectAsStateWithLifecycle()

    LaunchedEffect(isPrivateVaultUnlocked) {
        viewModel.setPrivateVaultUnlocked(isPrivateVaultUnlocked)
    }
    LaunchedEffect(isPasswordsUnlocked) {
        viewModel.setPasswordsUnlocked(isPasswordsUnlocked)
    }

    var addIsPrivate by remember { mutableStateOf(false) }
    
    // Form States
    var addUrl by remember { mutableStateOf("") }
    var addTitle by remember { mutableStateOf("") }
    var addCategory by remember { mutableStateOf("📁 Uncategorized") }
    var addNote by remember { mutableStateOf("") }
    var addIsFavorite by remember { mutableStateOf(false) }
    var attachedImagesList by remember { mutableStateOf<List<String>>(emptyList()) }
    var noteToView by remember { mutableStateOf<LinkEntity?>(null) }

    LaunchedEffect(linkToEdit) {
        if (linkToEdit != null) {
            val link = linkToEdit!!
            if (link.category == "📝 Notes") {
                attachedImagesList = LinkUiMapper.parseImagePaths(link.description)
            } else {
                attachedImagesList = emptyList()
            }
        } else {
            attachedImagesList = emptyList()
        }
    }
    
    // Secure Password Book States
    var saveAsPassword by remember { mutableStateOf(false) }
    var passwordUsername by remember { mutableStateOf("") }
    var passwordValue by remember { mutableStateOf("") }

    val onToggleFavoriteLambda = remember(viewModel) {
        { link: LinkEntity -> viewModel.toggleFavorite(link) }
    }
    
    val onDeleteLambda = remember(viewModel) {
        { link: LinkEntity -> viewModel.deleteLink(link) }
    }
    
    val onEditLambda = remember(context) {
        { link: LinkEntity ->
            linkToEdit = link
            addUrl = link.url
            addTitle = link.title
            addCategory = link.category
            val decryptedNote = com.example.security.RecordEncryption.decrypt(context, link.note, link.id, link.userEmail)
            addNote = decryptedNote
            addIsFavorite = link.isFavorite
            addIsPrivate = link.category == "🔐 Private Vault"
            saveAsPassword = link.category == "🔑 Passwords" && link.description == "SECURE_PASSWORD"
            if (saveAsPassword) {
                val parts = decryptedNote.split("|||")
                passwordUsername = parts.getOrNull(0) ?: ""
                passwordValue = parts.getOrNull(1) ?: ""
            } else {
                passwordUsername = ""
                passwordValue = ""
            }
            showAddDialog = true
        }
    }
    
    val onCopyLambda = remember(context, clipboardManager) {
        { link: LinkEntity ->
            clipboardManager.setText(buildAnnotatedString { append(link.url) })
            Toast.makeText(context, "Copied URL to Clipboard", Toast.LENGTH_SHORT).show()
        }
    }
    
    val onOpenLambda = remember(context, viewModel) {
        { link: LinkEntity ->
            viewModel.incrementOpenCount(link.id)
            if (link.category == "📝 Notes") {
                noteToView = link
            } else try {
                val webpage = Uri.parse(link.url)
                val intent = Intent(Intent.ACTION_VIEW, webpage)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open URL", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    val onShareLambda = remember(context) {
        { link: LinkEntity ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${link.title}\n\n${link.url}")
            }
            context.startActivity(Intent.createChooser(intent, "Share Link"))
        }
    }
    
    val onUpdatePasswordLambda = remember(viewModel) {
        { link: LinkEntity, newUsername: String, newPass: String ->
            val updatedNote = "${newUsername.trim()}|||${newPass}"
            viewModel.updateLink(link.copy(note = updatedNote))
        }
    }

    // Automatic Clipboard Check for quick-saving
    var clipboardLinkDetected by remember { mutableStateOf("") }
    var ignoredUrls by remember { mutableStateOf(setOf<String>()) }
    val currentLinks = rememberUpdatedState(links)
    val activity = remember(context) { findMainActivity(context) }
    val windowInfo = LocalWindowInfo.current
    
    val lifecycleOwner = LocalLifecycleOwner.current

    // Immediate Auto-Lock when navigating away from Passwords or Private Vault categories
    LaunchedEffect(selectedCategory) {
        if (selectedCategory != "🔑 Passwords") {
            isPasswordsUnlocked = false
        }
        if (selectedCategory != "🔐 Private Vault") {
            isPrivateVaultUnlocked = false
        }
        // Auto-lock custom biometric folders when leaving them in a single safe update
        viewModel.lockFolderIfDifferent(selectedCategory)
    }

    // Immediate Auto-Lock when minimizing, sleeping or exiting the app (ON_STOP)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                isPasswordsUnlocked = false
                isPrivateVaultUnlocked = false
                // Auto-lock all custom biometric folders upon exhaustion in a single safe update
                viewModel.lockAllCustomFolders()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    fun checkClipboardForLinks() {
        val hasFocus = (activity?.hasWindowFocus() == true) || windowInfo.isWindowFocused
        if (!hasFocus) return
        try {
            val clipboardService = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboardService.hasPrimaryClip()) {
                val primaryClip = clipboardService.primaryClip
                if (primaryClip != null && primaryClip.itemCount > 0) {
                    val clipText = primaryClip.getItemAt(0).text?.toString() ?: ""
                    if (clipText.isNotBlank()) {
                        val (extractedUrl, _) = viewModel.extractUrlAndTitle(clipText)
                        if (extractedUrl.isNotBlank() && !currentLinks.value.any { it.url == extractedUrl }) {
                            if (!ignoredUrls.contains(extractedUrl)) {
                                clipboardLinkDetected = extractedUrl
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // silent fallback
        }
    }

    LaunchedEffect(windowInfo.isWindowFocused) {
        if (windowInfo.isWindowFocused) {
            checkClipboardForLinks()
        }
    }

    DisposableEffect(context, windowInfo) {
        val clipboardService = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val hasFocus = (activity?.hasWindowFocus() == true) || windowInfo.isWindowFocused
            if (hasFocus) {
                checkClipboardForLinks()
            }
        }
        try {
            clipboardService.addPrimaryClipChangedListener(listener)
        } catch (e: Throwable) {
            // silent fallback
        }
        onDispose {
            try {
                clipboardService.removePrimaryClipChangedListener(listener)
            } catch (e: Throwable) {
                // silent fallback
            }
        }
    }

    val isDark = isDarkTheme
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    var showSecuritySettings by remember { mutableStateOf(false) }
    var showProfileManagement by remember { mutableStateOf(false) }

    val isSensitiveScreen = !isUnlocked || 
        selectedCategory == "🔑 Passwords" || 
        selectedCategory == "🔐 Private Vault" || 
        showSecuritySettings || 
        showProfileManagement || 
        privacyShieldEnabled

    LaunchedEffect(isSensitiveScreen) {
        activity?.setSecureScreenEnabled(isSensitiveScreen)
    }

    if (!isUnlocked || activeUser == null) {
        LockScreen(
            viewModel = viewModel,
            isDark = isDark,
            onSuccess = { viewModel.setUnlocked(true) }
        )
        return
    }

    if (showSecuritySettings) {
        SecuritySettingsDialog(
            viewModel = viewModel,
            isDark = isDark,
            onDismiss = { showSecuritySettings = false }
        )
    }

    if (showProfileManagement) {
        ProfileManagementDialog(
            viewModel = viewModel,
            isDark = isDark,
            onDismiss = { showProfileManagement = false },
            onOpenSecuritySettings = {
                showProfileManagement = false
                showSecuritySettings = true
            }
        )
    }

    // Feature 3: Live Duplicate warnings and clean up dialogs
    if (showDuplicateWarning && duplicateMatchFound != null) {
        Dialog(
            onDismissRequest = {
                showDuplicateWarning = false
                duplicateMatchFound = null
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                color = if (isDark) Color(0xFF1C1B22) else Color(0xFFF3EDF7),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Block
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Duplicate Icon",
                        tint = if (isDark) Color(0xFFFFB8D1) else Color(0xFFBA1A1A),
                        modifier = Modifier.size(40.dp)
                    )

                    Text(
                        text = "Duplicate URL Saved",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) Color.White else Color(0xFF1D192B)
                    )

                    Text(
                        text = "The link '${duplicateMatchFound!!.title}' (${duplicateMatchFound!!.url}) is already stored in your vaults.\n\nChoose an action below to proceed:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color.LightGray else Color.DarkGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                showAddDialog = false
                                showDuplicateWarning = false
                                try {
                                    val finalWebUrl = if (!duplicateMatchFound!!.url.startsWith("http://") && !duplicateMatchFound!!.url.startsWith("https://")) {
                                        "https://${duplicateMatchFound!!.url}"
                                    } else {
                                        duplicateMatchFound!!.url
                                    }
                                    val webpage = Uri.parse(finalWebUrl)
                                    val intent = Intent(Intent.ACTION_VIEW, webpage)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open URL: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                duplicateMatchFound = null
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Open Existing Link ✓", color = Color.White)
                        }

                        Button(
                            onClick = {
                                showDuplicateWarning = false
                                val finalCategory = if (saveAsPassword) {
                                    "🔑 Passwords"
                                } else if (addIsPrivate) {
                                    "🔐 Private Vault"
                                } else {
                                    addCategory
                                }
                                viewModel.addLink(
                                    url = addUrl.trim(),
                                    title = if (addTitle.isBlank()) addUrl.trim() else addTitle.trim(),
                                    category = finalCategory,
                                    note = addNote.trim(),
                                    isFavorite = addIsFavorite,
                                    description = if (saveAsPassword) "SECURE_PASSWORD" else ""
                                )
                                showAddDialog = false
                                duplicateMatchFound = null
                                Toast.makeText(context, "Saved anyway as new entry!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF332940) else Color(0xFFEADDFF),
                                contentColor = if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
                            )
                        ) {
                            Text("Save Anyway")
                        }

                        Button(
                            onClick = {
                                showDuplicateWarning = false
                                val finalCategory = if (saveAsPassword) {
                                    "🔑 Passwords"
                                } else if (addIsPrivate) {
                                    "🔐 Private Vault"
                                } else {
                                    addCategory
                                }
                                viewModel.updateLink(
                                    duplicateMatchFound!!.copy(
                                        title = if (addTitle.isBlank()) duplicateMatchFound!!.title else addTitle.trim(),
                                        category = finalCategory,
                                        note = addNote.trim(),
                                        isFavorite = addIsFavorite
                                    )
                                )
                                showAddDialog = false
                                duplicateMatchFound = null
                                Toast.makeText(context, "Updated existing link entry!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF2B2930) else Color(0xFFECE6F0),
                                contentColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF4F378B)
                            )
                        ) {
                            Text("Update Existing Entry 🔄")
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        TextButton(
                            onClick = {
                                showDuplicateWarning = false
                                duplicateMatchFound = null
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }

    if (showDuplicateManager) {
        DuplicateManagerDialog(
            viewModel = viewModel,
            isDark = isDark,
            onDismiss = { showDuplicateManager = false }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateFolderDialog = false
                newFolderName = ""
            },
            title = { Text("Create New Folder / Category") },
            text = {
                Column {
                    Text(
                        text = "Enter a name for the new folder/category. It will be added to your folders bar instantly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Enter folder name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_folder_dialog_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val trimmedName = newFolderName.trim()
                            viewModel.addManualCategory(trimmedName)
                            viewModel.onCategorySelected(trimmedName)
                            newFolderName = ""
                            showCreateFolderDialog = false
                            Toast.makeText(context, "Folder created & selected! 📂", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateFolderDialog = false
                        newFolderName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFolderDirectoryDialog) {
        val allUnfilteredLinks by viewModel.allUserUnfilteredLinks.collectAsStateWithLifecycle()
        val categoryCounts = remember(allUnfilteredLinks) {
            allUnfilteredLinks.groupBy { it.category }.mapValues { it.value.size }
        }
        val filteredDirectoryCategories = remember(categories, folderDirectorySearchQuery) {
            if (folderDirectorySearchQuery.isBlank()) {
                categories
            } else {
                categories.filter { it.contains(folderDirectorySearchQuery, ignoreCase = true) }
            }
        }
        AlertDialog(
            onDismissRequest = {
                showFolderDirectoryDialog = false
                folderDirectorySearchQuery = ""
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder Directory",
                        tint = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)
                    )
                    Text("Folder Directory")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                ) {
                    Text(
                        text = "Search and navigate all your folders, or manage custom ones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = folderDirectorySearchQuery,
                        onValueChange = { folderDirectorySearchQuery = it },
                        placeholder = { Text("Search folders") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Folders") },
                        trailingIcon = {
                            if (folderDirectorySearchQuery.isNotEmpty()) {
                                IconButton(onClick = { folderDirectorySearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("folder_directory_search"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = filteredDirectoryCategories,
                            key = { it },
                            contentType = { "folder_directory_item" }
                        ) { catName ->
                            val linkCount = categoryCounts[catName] ?: 0
                            val isDefault = catName == "🔑 Passwords" || catName == "🔐 Private Vault" || catName == "📝 Notes" || catName == "📁 Uncategorized"
                            val isSelected = selectedCategory == catName
                            
                            val themeColor = getCategoryThemeColor(catName, isDark)
                            val textColor = getCategoryTextColor(catName, isDark)
                            val (emoji, displayLabel) = getCategoryEmojiAndLabel(catName)
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (catName == "🔐 Private Vault" && !isPrivateVaultUnlocked) {
                                            if (activity?.isBiometricHardwareAvailable() == true) {
                                                activity.triggerBiometricPrompt(
                                                    onSuccess = {
                                                        isPrivateVaultUnlocked = true
                                                        viewModel.onCategorySelected("🔐 Private Vault")
                                                        showFolderDirectoryDialog = false
                                                        folderDirectorySearchQuery = ""
                                                        Toast.makeText(context, "Private Vault Unlocked 🔓", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            } else {
                                                Toast.makeText(context, "Fingerprint verification hardware check failed.", Toast.LENGTH_LONG).show()
                                            }
                                        } else if (catName == "🔑 Passwords" && !isPasswordsUnlocked) {
                                            if (activity?.isBiometricHardwareAvailable() == true) {
                                                activity.triggerBiometricPrompt(
                                                    onSuccess = {
                                                        isPasswordsUnlocked = true
                                                        viewModel.onCategorySelected("🔑 Passwords")
                                                        showFolderDirectoryDialog = false
                                                        folderDirectorySearchQuery = ""
                                                        Toast.makeText(context, "Passwords Unlocked 🔓", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            } else {
                                                Toast.makeText(context, "Fingerprint verification hardware check failed.", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            val isLocked = lockedCustomCategories.contains(catName)
                                            val isUnlocked = unlockedCustomCategories.contains(catName)
                                            if (isLocked && !isUnlocked) {
                                                if (activity?.isBiometricHardwareAvailable() == true) {
                                                    activity.triggerBiometricPrompt(
                                                        onSuccess = {
                                                            viewModel.setFolderUnlockedInSession(catName, true)
                                                            viewModel.onCategorySelected(catName)
                                                            showFolderDirectoryDialog = false
                                                            folderDirectorySearchQuery = ""
                                                            Toast.makeText(context, "$displayLabel Unlocked 🔓", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onError = { err ->
                                                            Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show()
                                                        }
                                                    )
                                                } else {
                                                    Toast.makeText(context, "Fingerprint verification hardware check failed.", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                viewModel.onCategorySelected(catName)
                                                showFolderDirectoryDialog = false
                                                folderDirectorySearchQuery = ""
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) {
                                    themeColor.copy(alpha = 0.35f)
                                } else {
                                    themeColor.copy(alpha = 0.12f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) textColor else textColor.copy(alpha = 0.25f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(textColor.copy(alpha = 0.20f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = emoji,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayLabel,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = textColor
                                        )
                                        Text(
                                            text = "$linkCount item" + if (linkCount == 1) "" else "s",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = if (isDark) textColor.copy(alpha = 0.75f) else textColor.copy(alpha = 0.85f)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Active Filter",
                                                tint = textColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        val canBeLocked = catName != "🔑 Passwords" && catName != "🔐 Private Vault" && catName != "📁 Uncategorized"
                                        if (canBeLocked) {
                                            val isFolderLocked = lockedCustomCategories.contains(catName)
                                            IconButton(
                                                onClick = {
                                                    if (isFolderLocked) {
                                                        if (activity?.isBiometricHardwareAvailable() == true) {
                                                            activity.triggerBiometricPrompt(
                                                                onSuccess = {
                                                                    viewModel.toggleFolderLock(catName)
                                                                    Toast.makeText(context, "$displayLabel lock removed.", Toast.LENGTH_SHORT).show()
                                                                },
                                                                onError = { err ->
                                                                    Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show()
                                                                }
                                                            )
                                                        } else {
                                                            Toast.makeText(context, "Fingerprint verification hardware check failed.", Toast.LENGTH_LONG).show()
                                                        }
                                                    } else {
                                                        viewModel.toggleFolderLock(catName)
                                                        Toast.makeText(context, "$displayLabel requires fingerprint unlock.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFolderLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                                    contentDescription = if (isFolderLocked) "Locked folder" else "Unlocked folder",
                                                    tint = if (isFolderLocked) Color(0xFFD32F2F) else textColor.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        if (!isDefault) {
                                            IconButton(
                                                onClick = {
                                                    categoryToDelete = catName
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Folder",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFolderDirectoryDialog = false
                        folderDirectorySearchQuery = ""
                    }
                ) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateFolderDialog = true
                    }
                ) {
                    Text("➕ Create Folder")
                }
            }
        )
    }

    if (categoryToDelete != null) {
        val catName = categoryToDelete!!
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Folder \"$catName\"?") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Choose what to do with the saved links inside this folder:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Button(
                        onClick = {
                            viewModel.deleteCategory(catName, deleteLinks = false)
                            Toast.makeText(context, "Folder deleted! Items moved to Uncategorized.", Toast.LENGTH_SHORT).show()
                            categoryToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 45.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Keep Links", fontWeight = FontWeight.SemiBold)
                    }
                    
                    Button(
                        onClick = {
                            viewModel.deleteCategory(catName, deleteLinks = true)
                            Toast.makeText(context, "Folder and items deleted permanently.", Toast.LENGTH_SHORT).show()
                            categoryToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 45.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Links", fontWeight = FontWeight.SemiBold)
                    }
                    
                    TextButton(
                        onClick = { categoryToDelete = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {}
        )
    }

    val handleSelectVaultSegment: (LinkViewModel.VaultSegment) -> Unit = { segment ->
        if (segment == LinkViewModel.VaultSegment.PASSWORDS) {
            if (isPasswordsUnlocked) {
                viewModel.setVaultSegment(LinkViewModel.VaultSegment.PASSWORDS)
                coroutineScope.launch { listState.scrollToItem(0) }
            } else {
                val activityCast = activity
                if (activityCast?.isBiometricHardwareAvailable() == true) {
                    activityCast.triggerBiometricPrompt(
                        onSuccess = {
                            isPasswordsUnlocked = true
                            viewModel.setVaultSegment(LinkViewModel.VaultSegment.PASSWORDS)
                            coroutineScope.launch { listState.scrollToItem(0) }
                            Toast.makeText(context, "Passwords Unlocked 🔓", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    isPasswordsUnlocked = true
                    viewModel.setVaultSegment(LinkViewModel.VaultSegment.PASSWORDS)
                    coroutineScope.launch { listState.scrollToItem(0) }
                }
            }
        } else {
            if (vaultSegment == LinkViewModel.VaultSegment.PASSWORDS) {
                isPasswordsUnlocked = false
            }
            if (selectedCategory == "🔑 Passwords") {
                viewModel.onCategorySelected(null)
            }
            viewModel.setVaultSegment(segment)
            coroutineScope.launch { listState.scrollToItem(0) }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {},
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) Color(0xFF14121B) else Color(0xFFFBF9FE),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 0.8.dp,
                        color = if (isDark) Color(0xFF262332) else Color(0xFFE7E1F0)
                    )
            ) {
                val navItems = listOf(
                    Triple(LinkViewModel.VaultSegment.ALL, "All", Icons.Filled.Dashboard to Icons.Outlined.Dashboard),
                    Triple(LinkViewModel.VaultSegment.LINKS, "Links", Icons.Filled.Link to Icons.Outlined.Link),
                    Triple(LinkViewModel.VaultSegment.PASSWORDS, "Logins", Icons.Filled.Key to Icons.Outlined.Key),
                    Triple(LinkViewModel.VaultSegment.PINNED, "Pinned", Icons.Filled.Star to Icons.Outlined.StarBorder)
                )
                navItems.forEach { (segment, label, icons) ->
                    val isSelected = vaultSegment == segment
                    val count = when (segment) {
                        LinkViewModel.VaultSegment.ALL -> vaultCounts.all
                        LinkViewModel.VaultSegment.LINKS -> vaultCounts.links
                        LinkViewModel.VaultSegment.PASSWORDS -> vaultCounts.passwords
                        LinkViewModel.VaultSegment.PINNED -> vaultCounts.pinned
                    }
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { handleSelectVaultSegment(segment) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (count > 0 && (segment != LinkViewModel.VaultSegment.PASSWORDS || isPasswordsUnlocked)) {
                                        Badge(
                                            containerColor = if (isSelected) {
                                                if (isDark) Color(0xFF4F378B) else Color(0xFFEADDFF)
                                            } else {
                                                if (isDark) Color(0xFF282334) else Color(0xFFECE6F5)
                                            },
                                            contentColor = if (isSelected) {
                                                if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
                                            } else {
                                                if (isDark) Color(0xFFB3ADBC) else Color(0xFF5A5265)
                                            }
                                        ) {
                                            Text(text = "$count", fontSize = 9.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) icons.first else icons.second,
                                    contentDescription = label,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D),
                            selectedTextColor = if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D),
                            indicatorColor = if (isDark) Color(0xFF2F2447) else Color(0xFFEADDFF),
                            unselectedIconColor = if (isDark) Color(0xFF938F99) else Color(0xFF605D66),
                            unselectedTextColor = if (isDark) Color(0xFF938F99) else Color(0xFF605D66)
                        ),
                        modifier = Modifier.testTag("bottom_nav_${segment.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            val isPasswordMode = vaultSegment == LinkViewModel.VaultSegment.PASSWORDS || selectedCategory == "🔑 Passwords"
            FloatingActionButton(
                onClick = {
                    addUrl = ""
                    addTitle = ""
                    addCategory = when {
                        isPasswordMode -> "🔑 Passwords"
                        selectedCategory == "🔐 Private Vault" -> "🔐 Private Vault"
                        else -> selectedCategory ?: "📁 Uncategorized"
                    }
                    addNote = ""
                    addIsFavorite = false
                    addIsPrivate = selectedCategory == "🔐 Private Vault"
                    saveAsPassword = isPasswordMode
                    passwordUsername = ""
                    passwordValue = ""
                    showAddDialog = true
                },
                containerColor = if (isPasswordMode) (if (isDark) Color(0xFF382310) else Color(0xFFFFD54F)) else (if (isDark) Color(0xFF2E214A) else Color(0xFFD0BCFF)),
                contentColor = if (isPasswordMode) (if (isDark) Color(0xFFFFD54F) else Color(0xFF3E2723)) else (if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("add_link_fab")
            ) {
                Icon(
                    imageVector = if (isPasswordMode) Icons.Filled.Key else Icons.Filled.AddLink,
                    contentDescription = if (isPasswordMode) "New Password" else "New Link"
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ------------------ UNIFIED COMPACT DOCKED SEARCH & ACTION BAR ------------------
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = if (isDark) Color(0xFF1A1723) else Color(0xFFF3EFF8),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2B2738) else Color(0xFFE2DCEB))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Left: Brand Logo Badge
                    LinkVaultLogoBadge(size = 34.dp, cornerRadius = 10.dp)

                    // Center: Integrated Search Input (preserves search_bar_input testTag)
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("search_bar_input"),
                        placeholder = {
                            Text(
                                text = when {
                                    vaultSegment == LinkViewModel.VaultSegment.PASSWORDS -> "Search logins..."
                                    vaultSegment == LinkViewModel.VaultSegment.LINKS -> "Search links..."
                                    vaultSegment == LinkViewModel.VaultSegment.PINNED -> "Search pinned..."
                                    selectedCategory != null -> "Search folder..."
                                    else -> "Search LinkVault..."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                color = if (isDark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else Color(0xFF605D66),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { viewModel.onSearchQueryChanged("") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear Search",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )

                    // Right: View Density, Theme, and Profile Avatar
                    val isDense = viewDensity == LinkViewModel.ViewDensity.DENSE
                    val densityBg = if (isDense) {
                        if (isDark) Color(0xFF2E2342) else Color(0xFFEADDFF)
                    } else {
                        if (isDark) Color(0xFF23202E) else Color(0xFFE8E2F2)
                    }
                    val densityTint = if (isDense) {
                        if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)
                    } else MaterialTheme.colorScheme.onSurface
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(densityBg)
                            .clickable {
                                viewModel.toggleViewDensity()
                                Toast.makeText(
                                    context,
                                    if (!isDense) "Dense List View 📋" else "Comfortable Cards View 🗂️",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .testTag("view_density_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDense) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.ViewAgenda,
                            contentDescription = if (isDense) "Switch to Card view" else "Switch to Dense view",
                            modifier = Modifier.size(16.dp),
                            tint = densityTint
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF23202E) else Color(0xFFE8E2F2))
                            .clickable { onToggleTheme() }
                            .testTag("theme_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDark) "Switch to Light mode" else "Switch to Dark mode",
                            modifier = Modifier.size(16.dp),
                            tint = if (isDark) Color(0xFFFFD54F) else Color(0xFF5D4037)
                        )
                    }

                    val userInitial = (activeUser?.name?.ifBlank { activeUser?.email } ?: "U").take(1).uppercase()
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF33284A) else Color(0xFFD0BCFF))
                            .clickable { showProfileManagement = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInitial,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
                        )
                    }
                }
            }

            // Clipboard Bar (Alert for newly copied links)
            AnimatedVisibility(
                visible = clipboardLinkDetected.isNotBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) Color(0xFF201B2E) else Color(0xFFE8DEF8))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Copied link detected",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
                        )
                        Text(
                            text = clipboardLinkDetected,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color(0xFFEADDFF).copy(alpha = 0.8f) else Color(0xFF21005D).copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { clipboardLinkDetected = "" }) {
                            Text("Ignore", color = if (isDark) Color(0xFFEADDFF).copy(alpha = 0.6f) else Color(0xFF21005D).copy(alpha = 0.6f))
                        }
                        Button(
                            onClick = {
                                addUrl = clipboardLinkDetected
                                addTitle = ""
                                addCategory = if (selectedCategory == "🔐 Private Vault") "🔐 Private Vault" else "📁 Uncategorized"
                                addNote = ""
                                addIsFavorite = false
                                addIsPrivate = selectedCategory == "🔐 Private Vault"
                                saveAsPassword = selectedCategory == "🔑 Passwords"
                                clipboardLinkDetected = ""
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF21005D),
                                contentColor = if (isDark) Color(0xFF21005D) else Color.White
                            )
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            // Horizontal Filter Chip Strip + Anchored Filter Menu
            VaultFilterDropdown(
                vaultSegment = vaultSegment,
                selectedCategory = selectedCategory,
                allUserLinks = allUserLinks,
                categories = categories,
                vaultCounts = vaultCounts,
                isDark = isDark,
                isPasswordsUnlocked = isPasswordsUnlocked,
                isPrivateVaultUnlocked = isPrivateVaultUnlocked,
                lockedCustomCategories = lockedCustomCategories,
                unlockedCustomCategories = unlockedCustomCategories,
                currentFilteredCount = links.size,
                onSelectSegment = { segment ->
                    if (segment == LinkViewModel.VaultSegment.PASSWORDS) {
                        if (isPasswordsUnlocked) {
                            viewModel.setVaultSegment(LinkViewModel.VaultSegment.PASSWORDS)
                            coroutineScope.launch { listState.scrollToItem(0) }
                        } else {
                            val activityCast = activity
                            if (activityCast?.isBiometricHardwareAvailable() == true) {
                                activityCast.triggerBiometricPrompt(
                                    onSuccess = {
                                        isPasswordsUnlocked = true
                                        viewModel.setVaultSegment(LinkViewModel.VaultSegment.PASSWORDS)
                                        coroutineScope.launch { listState.scrollToItem(0) }
                                        Toast.makeText(context, "Passwords Unlocked 🔓", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            } else {
                                isPasswordsUnlocked = true
                                viewModel.setVaultSegment(LinkViewModel.VaultSegment.PASSWORDS)
                                coroutineScope.launch { listState.scrollToItem(0) }
                            }
                        }
                    } else {
                        if (vaultSegment == LinkViewModel.VaultSegment.PASSWORDS) {
                            isPasswordsUnlocked = false
                        }
                        viewModel.setVaultSegment(segment)
                        coroutineScope.launch { listState.scrollToItem(0) }
                    }
                },
                onSelectCategory = { catName ->
                    if (catName == null) {
                        if (selectedCategory == "🔐 Private Vault") {
                            isPrivateVaultUnlocked = false
                        } else if (selectedCategory == "🔑 Passwords") {
                            isPasswordsUnlocked = false
                        }
                        viewModel.onCategorySelected(null)
                        if (vaultSegment == LinkViewModel.VaultSegment.PASSWORDS) {
                            viewModel.setVaultSegment(LinkViewModel.VaultSegment.ALL)
                        }
                        coroutineScope.launch { listState.scrollToItem(0) }
                    } else if (catName == "🔐 Private Vault") {
                        if (isPrivateVaultUnlocked) {
                            viewModel.onCategorySelected("🔐 Private Vault")
                            coroutineScope.launch { listState.scrollToItem(0) }
                        } else {
                            val activityCast = activity
                            if (activityCast?.isBiometricHardwareAvailable() == true) {
                                activityCast.triggerBiometricPrompt(
                                    onSuccess = {
                                        isPrivateVaultUnlocked = true
                                        viewModel.onCategorySelected("🔐 Private Vault")
                                        coroutineScope.launch { listState.scrollToItem(0) }
                                        Toast.makeText(context, "Private Vault Unlocked 🔓", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            } else {
                                Toast.makeText(context, "Fingerprint verification hardware check failed or not set up.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else if (catName == "🔑 Passwords") {
                        if (isPasswordsUnlocked) {
                            viewModel.onCategorySelected("🔑 Passwords")
                            viewModel.setVaultSegment(LinkViewModel.VaultSegment.PASSWORDS)
                            coroutineScope.launch { listState.scrollToItem(0) }
                        } else {
                            val activityCast = activity
                            if (activityCast?.isBiometricHardwareAvailable() == true) {
                                activityCast.triggerBiometricPrompt(
                                    onSuccess = {
                                        isPasswordsUnlocked = true
                                        viewModel.onCategorySelected("🔑 Passwords")
                                        viewModel.setVaultSegment(LinkViewModel.VaultSegment.PASSWORDS)
                                        coroutineScope.launch { listState.scrollToItem(0) }
                                        Toast.makeText(context, "Passwords Unlocked 🔓", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            } else {
                                Toast.makeText(context, "Fingerprint verification hardware check failed or not set up.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        if (selectedCategory == "🔐 Private Vault") {
                            isPrivateVaultUnlocked = false
                        } else if (selectedCategory == "🔑 Passwords") {
                            isPasswordsUnlocked = false
                        }
                        if (vaultSegment == LinkViewModel.VaultSegment.PASSWORDS) {
                            viewModel.setVaultSegment(LinkViewModel.VaultSegment.ALL)
                        }
                        val isLocked = lockedCustomCategories.contains(catName)
                        val isUnlocked = unlockedCustomCategories.contains(catName)
                        val activityCast = activity
                        if (isLocked && !isUnlocked) {
                            if (activityCast?.isBiometricHardwareAvailable() == true) {
                                activityCast.triggerBiometricPrompt(
                                    onSuccess = {
                                        viewModel.setFolderUnlockedInSession(catName, true)
                                        viewModel.onCategorySelected(catName)
                                        coroutineScope.launch { listState.scrollToItem(0) }
                                        Toast.makeText(context, "$catName Unlocked 🔓", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            } else {
                                Toast.makeText(context, "Fingerprint verification hardware check failed.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            viewModel.onCategorySelected(catName)
                            coroutineScope.launch { listState.scrollToItem(0) }
                        }
                    }
                },
                onResetFilters = {
                    isPrivateVaultUnlocked = false
                    isPasswordsUnlocked = false
                    viewModel.setVaultSegment(LinkViewModel.VaultSegment.ALL)
                    viewModel.onCategorySelected(null)
                    coroutineScope.launch { listState.scrollToItem(0) }
                },
                onNewFolderClick = { showCreateFolderDialog = true },
                onManageFoldersClick = { showFolderDirectoryDialog = true }
            )

            if (searchQuery.isNotEmpty()) {
                val isAnySecureUnlocked = isPrivateVaultUnlocked || isPasswordsUnlocked
                val textHint = if (selectedCategory != null) {
                    "🔍 Searching $selectedCategory folder only..."
                } else if (isAnySecureUnlocked) {
                    "🔍 Searching all unlocked vaults (including Private/Passwords)..."
                } else {
                    "🔐 Unlock Passwords / Private Vault folders to search inside them!"
                }
                val hintColor = if (selectedCategory != null || isAnySecureUnlocked) {
                    if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
                } else {
                    if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
                }
                Text(
                    text = textHint,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = hintColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }



            // Links List
            val okToRenderList = (selectedCategory != "🔑 Passwords" && vaultSegment != LinkViewModel.VaultSegment.PASSWORDS) || isPasswordsUnlocked

            if (!okToRenderList) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clickable {
                                val activityCast = activity
                                if (activityCast?.isBiometricHardwareAvailable() == true) {
                                    activityCast.triggerBiometricPrompt(
                                        onSuccess = {
                                            isPasswordsUnlocked = true
                                            Toast.makeText(context, "Passwords Unlocked 🔓", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err -> Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show() }
                                    )
                                } else {
                                    isPasswordsUnlocked = true
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2D2222) else Color(0xFFFFEBEE))
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Lock, contentDescription = "", tint = Color.Red, modifier = Modifier.size(48.dp))
                            Text("Passwords Area Locked", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.Red)
                            Text("Please authenticate using your device fingerprint to unlock your saved vaults securely.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Button(
                                onClick = {
                                    val activityCast = activity
                                    if (activityCast?.isBiometricHardwareAvailable() == true) {
                                        activityCast.triggerBiometricPrompt(
                                            onSuccess = {
                                                isPasswordsUnlocked = true
                                                Toast.makeText(context, "Passwords Unlocked 🔓", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err -> Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_LONG).show() }
                                        )
                                    } else {
                                        isPasswordsUnlocked = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("Scan Fingerprint 🔓")
                            }
                        }
                    }
                }
            } else {
                if (links.isEmpty()) {
                    EmptyStateLayout(
                        isFiltering = searchQuery.isNotEmpty() || selectedCategory != null || showOnlyFavorites || vaultSegment != LinkViewModel.VaultSegment.ALL,
                        onClearFilters = {
                            viewModel.onSearchQueryChanged("")
                            isPrivateVaultUnlocked = false
                            viewModel.onCategorySelected(null)
                            viewModel.setVaultSegment(LinkViewModel.VaultSegment.ALL)
                            if (showOnlyFavorites) viewModel.onToggleFavoritesOnly()
                        }
                    )
                } else {
                    val isDenseMode = viewDensity == LinkViewModel.ViewDensity.DENSE

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("links_list_container"),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 76.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isDenseMode) 8.dp else 12.dp)
                    ) {
                        items(
                            items = vaultData.rows,
                            key = { row -> row.rowKey },
                            contentType = { row -> row.contentTypeForDensity(isDenseMode) }
                        ) { row ->
                            when (row) {
                                is VaultListRow.HeaderRow -> {
                                    VaultDateHeaderRow(
                                        row = row,
                                        isDark = isDark
                                    )
                                }
                                is VaultListRow.ItemRow -> {
                                    val uiModel = row.uiModel
                                    if (isDenseMode) {
                                        if (uiModel.isPassword) {
                                            CheapDensePasswordRow(
                                                uiModel = uiModel,
                                                isDark = isDark,
                                                onToggleFavorite = onToggleFavoriteLambda,
                                                onDelete = onDeleteLambda,
                                                onEdit = onEditLambda,
                                                onCopy = onCopyLambda,
                                                onUpdatePassword = onUpdatePasswordLambda
                                            )
                                        } else {
                                            CheapDenseLinkRow(
                                                uiModel = uiModel,
                                                isDark = isDark,
                                                onToggleFavorite = onToggleFavoriteLambda,
                                                onDelete = onDeleteLambda,
                                                onEdit = onEditLambda,
                                                onCopy = onCopyLambda,
                                                onOpen = onOpenLambda,
                                                onShare = onShareLambda
                                            )
                                        }
                                    } else {
                                        if (uiModel.isPassword) {
                                            CheapPasswordItemCard(
                                                uiModel = uiModel,
                                                isDark = isDark,
                                                isEven = row.isEven,
                                                onToggleFavorite = onToggleFavoriteLambda,
                                                onDelete = onDeleteLambda,
                                                onEdit = onEditLambda,
                                                onOpen = onOpenLambda,
                                                onUpdatePassword = onUpdatePasswordLambda
                                            )
                                        } else {
                                            CheapLinkItemCard(
                                                uiModel = uiModel,
                                                isDark = isDark,
                                                isEven = row.isEven,
                                                onToggleFavorite = onToggleFavoriteLambda,
                                                onDelete = onDeleteLambda,
                                                onEdit = onEditLambda,
                                                onCopy = onCopyLambda,
                                                onOpen = onOpenLambda,
                                                onShare = onShareLambda
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit/Add Overlay Dialog
    if (showAddDialog || linkToEdit != null) {
        var categoryExpanded by remember { mutableStateOf(false) }
        var showCustomCategoryInput by remember { mutableStateOf(false) }
        var customCategoryName by remember { mutableStateOf("") }

        val isEditMode = linkToEdit != null
        val titleText = if (isEditMode) "Edit Link" else "Add New Link"
        val actionText = if (isEditMode) "Save Changes" else "Add Link"

        Dialog(onDismissRequest = {
            showAddDialog = false
            linkToEdit = null
        }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(8.dp)
                    .testTag("add_link_dialog"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (saveAsPassword) (if (isEditMode) "Edit Secure Password" else "Save Secure Password") else titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Toggle Save as Password option with premium design
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                saveAsPassword = !saveAsPassword
                                if (saveAsPassword) {
                                    addIsPrivate = false
                                    addCategory = "🔑 Passwords"
                                }
                            }
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = saveAsPassword,
                            onCheckedChange = {
                                saveAsPassword = it
                                if (it) {
                                    addIsPrivate = false
                                    addCategory = "🔑 Passwords"
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save as Password 🔑 (Fingerprint Protected)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (saveAsPassword) {
                        // Site Name Input Field
                        OutlinedTextField(
                            value = addTitle,
                            onValueChange = { addTitle = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("dialog_password_sitename_input"),
                            label = { Text("Site / Service Name") },
                            placeholder = { Text("Enter service name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )

                        // Username field
                        OutlinedTextField(
                            value = passwordUsername,
                            onValueChange = { passwordUsername = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("dialog_password_username_input"),
                            label = { Text("Username or Email") },
                            placeholder = { Text("Enter username") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )

                        // Password field with generating capability and eye toggle
                        var passwordVisibleInForm by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = passwordValue,
                            onValueChange = { passwordValue = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("dialog_password_value_input"),
                            label = { Text("Password Value") },
                            placeholder = { Text("Enter password") },
                            singleLine = true,
                            visualTransformation = if (passwordVisibleInForm) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { passwordVisibleInForm = !passwordVisibleInForm }) {
                                        Icon(
                                            imageVector = if (passwordVisibleInForm) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle Visibility"
                                        )
                                    }
                                    IconButton(onClick = {
                                        passwordValue = generateRandomPassword()
                                        Toast.makeText(context, "Strong secure password generated!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Autorenew,
                                            contentDescription = "Generate Password",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )

                        // Optional URL Input Field
                        OutlinedTextField(
                            value = addUrl,
                            onValueChange = { addUrl = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("dialog_password_url_input"),
                            label = { Text("Website URL (Optional)") },
                            placeholder = { Text("Enter URL") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )
                    } else {
                        val isNotesCategory = addCategory == "📝 Notes"
                        // Standard URL Input Field
                        OutlinedTextField(
                            value = addUrl,
                            onValueChange = { addUrl = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("dialog_url_input"),
                            label = { Text(if (isNotesCategory) "Associated URL Link (Optional)" else "URL Link") },
                            placeholder = { Text("Enter URL") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )

                        // Title Input Field
                        OutlinedTextField(
                            value = addTitle,
                            onValueChange = { addTitle = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("dialog_title_input"),
                            label = { Text(if (isNotesCategory) "Note Title *" else "Title (Optional)") },
                            placeholder = { Text("Enter title") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )
                    }

                    // Category Select Dropdown
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        OutlinedTextField(
                            value = if (saveAsPassword) "🔑 Passwords" else if (addIsPrivate) "🔐 Private Vault" else addCategory,
                            onValueChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_category_input"),
                            label = { Text("Category Tag") },
                            readOnly = true,
                            enabled = !addIsPrivate && !saveAsPassword,
                            trailingIcon = {
                                IconButton(
                                    onClick = { categoryExpanded = true },
                                    enabled = !addIsPrivate && !saveAsPassword
                                ) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        if (!addIsPrivate && !saveAsPassword) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { categoryExpanded = true }
                            )
                        }
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        addCategory = cat
                                        categoryExpanded = false
                                        showCustomCategoryInput = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("➕ Create Custom Category...") },
                                onClick = {
                                    showCustomCategoryInput = true
                                    categoryExpanded = false
                                }
                            )
                        }
                    }

                    // Custom Category Name Input
                    AnimatedVisibility(visible = showCustomCategoryInput) {
                        OutlinedTextField(
                            value = customCategoryName,
                            onValueChange = { customCategoryName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("dialog_custom_category_input"),
                            label = { Text("New Category Name") },
                            placeholder = { Text("Enter category name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (customCategoryName.isNotBlank()) {
                                        val trimmedName = customCategoryName.trim()
                                        addCategory = trimmedName
                                        viewModel.addManualCategory(trimmedName)
                                        customCategoryName = ""
                                        showCustomCategoryInput = false
                                    }
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Apply Category", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }

                    // Notes / Comments Input
                    val isNotesCategory = addCategory == "📝 Notes"
                    val imagePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri ->
                        if (uri != null) {
                            val savedPath = viewModel.copyUriToInternalStorage(uri.toString())
                            if (savedPath != null) {
                                attachedImagesList = attachedImagesList + savedPath
                                Toast.makeText(context, "Image attachment added! ✓", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    OutlinedTextField(
                        value = addNote,
                        onValueChange = { addNote = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("dialog_note_input"),
                        label = { Text(if (isNotesCategory) "Note Content *" else "Custom Notes or Tags") },
                        placeholder = { Text("Add notes or tags...") },
                        minLines = if (isNotesCategory) 4 else 1,
                        maxLines = 12,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )

                    if (isNotesCategory) {
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Image, contentDescription = "Add image", tint = Color.White)
                                Text("Attach Image to Note 🏞️", color = Color.White)
                            }
                        }

                        if (attachedImagesList.isNotEmpty()) {
                            Text(
                                "Attached Images (${attachedImagesList.size}):",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                items(attachedImagesList) { imgPath ->
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.DarkGray)
                                    ) {
                                        AsyncImage(
                                            model = remember(imgPath) {
                                                ImageRequest.Builder(context)
                                                    .data(java.io.File(com.example.data.AttachmentThumbnailManager.getExistingThumbnailOrOriginal(imgPath)))
                                                    .size(400, 400)
                                                    .crossfade(false)
                                                    .build()
                                            },
                                            contentDescription = "Attached Image",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = { attachedImagesList = attachedImagesList - imgPath },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(22.dp)
                                                .background(Color.Red.copy(alpha = 0.85f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove Image",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Favorite Checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addIsFavorite = !addIsFavorite }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = addIsFavorite,
                            onCheckedChange = { addIsFavorite = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add to Favorites ⭐",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    // Private Vault Overrides Checkbox with fingerprint alert
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addIsPrivate = !addIsPrivate }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = addIsPrivate,
                            onCheckedChange = { addIsPrivate = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save inside Private Vault 🔐 (Fingerprint Only)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isDark) Color(0xFFFFB8D1) else Color(0xFFBA1A1A),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Dialog Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showAddDialog = false
                                linkToEdit = null
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        }
                        Button(
                            onClick = {
                                if (saveAsPassword) {
                                    if (addTitle.isBlank()) {
                                        Toast.makeText(context, "Site / Service Name cannot be empty", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (passwordValue.isBlank()) {
                                        Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                } else if (addCategory == "📝 Notes") {
                                    if (addTitle.isBlank()) {
                                        Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                } else {
                                    if (addUrl.isBlank()) {
                                        Toast.makeText(context, "URL cannot be empty", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                }

                                val finalCategory = if (saveAsPassword) {
                                    "🔑 Passwords"
                                } else if (addIsPrivate) {
                                    "🔐 Private Vault"
                                } else {
                                    addCategory
                                }
                                val finalNote = if (saveAsPassword) {
                                    "${passwordUsername.trim()}|||${passwordValue}"
                                } else {
                                    addNote.trim()
                                }
                                val finalDescription = if (saveAsPassword) {
                                    "SECURE_PASSWORD"
                                } else if (finalCategory == "📝 Notes") {
                                    LinkUiMapper.encodeImagePaths(attachedImagesList)
                                } else {
                                    ""
                                }
                                val finalUrl = if (saveAsPassword) {
                                    if (addUrl.isNotBlank()) addUrl.trim() else "https://localhost"
                                } else {
                                    addUrl.trim()
                                }
                                val finalTitle = if (saveAsPassword) {
                                    addTitle.trim()
                                } else {
                                    if (addTitle.isBlank()) {
                                        if (finalUrl.isNotBlank()) finalUrl else "My Note"
                                    } else {
                                        addTitle.trim()
                                    }
                                }

                                if (isEditMode && linkToEdit != null) {
                                    viewModel.updateLink(
                                        linkToEdit!!.copy(
                                            url = finalUrl,
                                            title = finalTitle,
                                            category = finalCategory,
                                            description = finalDescription,
                                            note = finalNote,
                                            isFavorite = addIsFavorite
                                        )
                                    )
                                    linkToEdit = null
                                } else {
                                    val activeUserEmail = activeUser?.email ?: ""
                                    val normalizedEntered = com.example.data.DuplicateDetector.normalizeUrl(finalUrl)
                                    val duplicateResult = if (finalCategory == "📝 Notes" && finalUrl.isBlank()) {
                                         null
                                    } else if (finalCategory == "🔑 Passwords" || finalDescription == "SECURE_PASSWORD") {
                                        val partsEntered = finalNote.split("|||")
                                        val usernameEntered = partsEntered.getOrNull(0)?.trim() ?: ""
                                        links.find {
                                            it.userEmail.equals(activeUserEmail, ignoreCase = true) &&
                                            (it.category == "🔑 Passwords" || it.description == "SECURE_PASSWORD") &&
                                            com.example.data.DuplicateDetector.normalizeUrl(it.url) == normalizedEntered &&
                                            (it.note.split("|||").getOrNull(0)?.trim() ?: "") == usernameEntered
                                        }
                                    } else {
                                        links.find {
                                            it.userEmail.equals(activeUserEmail, ignoreCase = true) &&
                                            it.category != "🔑 Passwords" &&
                                            it.description != "SECURE_PASSWORD" &&
                                            com.example.data.DuplicateDetector.normalizeUrl(it.url) == normalizedEntered
                                        }
                                    }

                                    if (duplicateResult != null) {
                                        duplicateMatchFound = duplicateResult
                                        showDuplicateWarning = true
                                    } else {
                                        viewModel.addLink(
                                            url = finalUrl,
                                            title = finalTitle,
                                            category = finalCategory,
                                            note = finalNote,
                                            isFavorite = addIsFavorite,
                                            description = finalDescription
                                        )
                                        showAddDialog = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(actionText, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Interactive overlays for Feature 1 (Universal Save Overlay Panel & Clipboard Prompts)
    val incomingSharedText by viewModel.incomingSharedText.collectAsStateWithLifecycle()
    if (incomingSharedText != null) {
        UniversalSavePanel(
            viewModel = viewModel,
            isDark = isDark,
            onDismiss = { viewModel.setIncomingSharedText(null) }
        )
    }

    val incomingSharedImage by viewModel.incomingSharedImage.collectAsStateWithLifecycle()
    if (incomingSharedImage != null) {
        var selectedExistingNoteIndex by remember { mutableStateOf(-1) }
        val noteList = links.filter { it.category == "📝 Notes" }

        Dialog(onDismissRequest = { viewModel.setIncomingSharedImage(null) }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(12.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Shared Image Received 🖼️",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Image(
                        painter = rememberAsyncImagePainter(model = incomingSharedImage),
                        contentDescription = "Received Shared Image",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray)
                            .padding(bottom = 12.dp),
                        contentScale = ContentScale.Crop
                    )

                    Text(
                        text = "How would you like to save this image?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = {
                            val localPath = viewModel.copyUriToInternalStorage(incomingSharedImage!!)
                            if (localPath != null) {
                                addUrl = ""
                                addTitle = ""
                                addCategory = "📝 Notes"
                                addNote = ""
                                addIsFavorite = false
                                addIsPrivate = false
                                attachedImagesList = listOf(localPath)
                                showAddDialog = true
                            } else {
                                Toast.makeText(context, "Could not process image", Toast.LENGTH_SHORT).show()
                            }
                            viewModel.setIncomingSharedImage(null)
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save as New Note 📝")
                    }

                    if (noteList.isNotEmpty()) {
                        Text(
                            text = "Or Attach to an Existing Note:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp).align(Alignment.Start)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                .padding(4.dp)
                        ) {
                            LazyColumn {
                                itemsIndexed(
                                    items = noteList,
                                    key = { _, noteLink -> noteLink.id },
                                    contentType = { _, _ -> "existing_note_item" }
                                ) { index, noteLink ->
                                    val isSelected = selectedExistingNoteIndex == index
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedExistingNoteIndex = index }
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = noteLink.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val selectedNote = noteList.getOrNull(selectedExistingNoteIndex)
                                if (selectedNote != null) {
                                    val localPath = viewModel.copyUriToInternalStorage(incomingSharedImage!!)
                                    if (localPath != null) {
                                        val existingImages = LinkUiMapper.parseImagePaths(selectedNote.description)
                                        val updatedImages = existingImages + localPath
                                        val updatedDesc = LinkUiMapper.encodeImagePaths(updatedImages)
                                        viewModel.updateLink(selectedNote.copy(description = updatedDesc))
                                        Toast.makeText(context, "Attached to note Successfully! ✓", Toast.LENGTH_SHORT).show()
                                        viewModel.setIncomingSharedImage(null)
                                    } else {
                                        Toast.makeText(context, "Could not copy image file", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Please select an existing note first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = selectedExistingNoteIndex != -1,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Attach Image to Selected Note 📎")
                        }
                    } else {
                        Text(
                            text = "No existing notes found. Create a new note to attach this image.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    TextButton(
                        onClick = { viewModel.setIncomingSharedImage(null) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (noteToView != null) {
        val note = noteToView!!
        var lightboxUriString by remember { mutableStateOf<String?>(null) }
        var isEditingInline by remember { mutableStateOf(false) }
        
        var inlineTitleText by remember(note.id) { mutableStateOf(note.title) }
        var inlineNoteText by remember(note.id) { mutableStateOf(note.note) }
        var inlineUrlText by remember(note.id) { mutableStateOf(note.url) }
        var inlineAttachedImages by remember(note.id) {
            mutableStateOf(LinkUiMapper.parseImagePaths(note.description))
        }

        val inlineImagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val savedPath = viewModel.copyUriToInternalStorage(uri.toString())
                if (savedPath != null) {
                    inlineAttachedImages = inlineAttachedImages + savedPath
                    Toast.makeText(context, "Image added! ✓", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Merge States
        var showMergeSelection by remember { mutableStateOf(false) }
        var selectedNoteToMergeWith by remember { mutableStateOf<LinkEntity?>(null) }
        var mergedTitleText by remember { mutableStateOf("") }
        var mergedBodyText by remember { mutableStateOf("") }

        LaunchedEffect(selectedNoteToMergeWith) {
            val other = selectedNoteToMergeWith
            if (other != null) {
                mergedTitleText = "${note.title} & ${other.title}"
                mergedBodyText = "${note.note}\n\n---\nMerged Note Content:\n${other.note}"
            }
        }

        Dialog(onDismissRequest = { noteToView = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Header Area
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditingInline) "📝 Editing Note" else "📝 Note Reader",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { noteToView = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Note")
                        }
                    }

                    // Content Scroll Area Or Inline Edit fields
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isEditingInline) {
                            OutlinedTextField(
                                value = inlineTitleText,
                                onValueChange = { inlineTitleText = it },
                                label = { Text("Note Title") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = inlineNoteText,
                                onValueChange = { inlineNoteText = it },
                                label = { Text("Note Body Content") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                minLines = 6,
                                maxLines = 15,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = inlineUrlText,
                                onValueChange = { inlineUrlText = it },
                                label = { Text("Associated Link / URL (Optional)") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Text(
                                "Attached Images (${inlineAttachedImages.size}):",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            if (inlineAttachedImages.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    items(inlineAttachedImages) { imgPath ->
                                        Box(
                                            modifier = Modifier
                                                .size(90.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.DarkGray)
                                        ) {
                                            AsyncImage(
                                                model = remember(imgPath) {
                                                    ImageRequest.Builder(context)
                                                        .data(java.io.File(com.example.data.AttachmentThumbnailManager.getExistingThumbnailOrOriginal(imgPath)))
                                                        .size(400, 400)
                                                        .crossfade(false)
                                                        .build()
                                                },
                                                contentDescription = "Attached Image",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { inlineAttachedImages = inlineAttachedImages - imgPath },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(24.dp)
                                                    .background(Color.Red.copy(alpha = 0.8f), CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove Image",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { inlineImagePickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Attach Another Image 🖼️")
                            }

                        } else {
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Text(
                                text = "Saved ${com.example.util.DateTimeFormatterUtil.formatFullDateTime(note.timestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Images attachment rendering
                            val imagesToDisplay = LinkUiMapper.parseImagePaths(note.description)

                            if (imagesToDisplay.isNotEmpty()) {
                                Text(
                                    "Attached Images (${imagesToDisplay.size}):",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                ) {
                                    items(imagesToDisplay) { imgPath ->
                                        Box(
                                            modifier = Modifier
                                                .size(90.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.DarkGray)
                                                .clickable { lightboxUriString = imgPath }
                                        ) {
                                            AsyncImage(
                                                model = remember(imgPath) {
                                                    ImageRequest.Builder(context)
                                                        .data(java.io.File(com.example.data.AttachmentThumbnailManager.getExistingThumbnailOrOriginal(imgPath)))
                                                        .size(400, 400)
                                                        .crossfade(false)
                                                        .build()
                                                },
                                                contentDescription = "Attached Image",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }

                            // Note Body text
                            SelectionContainer {
                                Text(
                                    text = note.note,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                                )
                            }

                            // Associated Link Button
                            if (note.url.isNotBlank()) {
                                ElevatedCard(
                                    onClick = {
                                        try {
                                            val webpage = Uri.parse(note.url)
                                            val intent = Intent(Intent.ACTION_VIEW, webpage)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open URL", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Launch,
                                            contentDescription = "Open Link",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Open Associated Link",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                note.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Dialogue action row (Bottom)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isEditingInline) {
                            OutlinedButton(
                                onClick = {
                                    isEditingInline = false
                                    // Reset states
                                    inlineTitleText = note.title
                                    inlineNoteText = note.note
                                    inlineUrlText = note.url
                                    inlineAttachedImages = LinkUiMapper.parseImagePaths(note.description)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    if (inlineTitleText.isBlank()) {
                                        Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val finalDesc = LinkUiMapper.encodeImagePaths(inlineAttachedImages)
                                    val updatedNote = note.copy(
                                        title = inlineTitleText.trim(),
                                        note = inlineNoteText,
                                        url = inlineUrlText.trim(),
                                        description = finalDesc,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    viewModel.updateLink(updatedNote)
                                    noteToView = updatedNote
                                    isEditingInline = false
                                    Toast.makeText(context, "Note Saved ✓", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Note ✓")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    showMergeSelection = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("🔗 Merge Notes")
                            }

                            Button(
                                onClick = {
                                    isEditingInline = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Note")
                            }
                        }
                    }
                }
            }
        }

        // Merge Selection Sub-dialogue
        if (showMergeSelection) {
            val otherNotes = links.filter { it.category == "📝 Notes" && it.id != note.id }
            
            Dialog(onDismissRequest = { 
                showMergeSelection = false
                selectedNoteToMergeWith = null
            }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "🔗 Merge Notes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (selectedNoteToMergeWith == null) {
                            Text(
                                text = "Select another note to merge with '${note.title}':",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            if (otherNotes.isEmpty()) {
                                Text(
                                    text = "No other notes available to merge. Create another note first!",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 18.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                        .padding(4.dp)
                                ) {
                                    LazyColumn {
                                        items(
                                            items = otherNotes,
                                            key = { it.id },
                                            contentType = { "merge_note_item" }
                                        ) { other ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedNoteToMergeWith = other }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = other.title,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showMergeSelection = false }) {
                                    Text("Cancel")
                                }
                            }
                        } else {
                            val other = selectedNoteToMergeWith!!
                            Text(
                                text = "Configure Merged Note:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = mergedTitleText,
                                onValueChange = { mergedTitleText = it },
                                label = { Text("Merged Title") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = mergedBodyText,
                                onValueChange = { mergedBodyText = it },
                                label = { Text("Merged Content") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                minLines = 4,
                                maxLines = 10,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { selectedNoteToMergeWith = null },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Back")
                                }

                                Button(
                                    onClick = {
                                        if (mergedTitleText.isBlank()) {
                                            Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        // Merge images
                                        val note1Images = LinkUiMapper.parseImagePaths(note.description)
                                        val note2Images = LinkUiMapper.parseImagePaths(other.description)
                                        val combinedImages = (note1Images + note2Images).distinct()
                                        val finalMergedDesc = LinkUiMapper.encodeImagePaths(combinedImages)
                                        val mergedUrlValue = if (note.url.isNotBlank()) note.url else other.url

                                        val updatedMergedNote = note.copy(
                                            title = mergedTitleText.trim(),
                                            note = mergedBodyText,
                                            description = finalMergedDesc,
                                            url = mergedUrlValue,
                                            timestamp = System.currentTimeMillis()
                                        )

                                        viewModel.updateLink(updatedMergedNote)
                                        viewModel.deleteLink(other)
                                        
                                        noteToView = updatedMergedNote
                                        selectedNoteToMergeWith = null
                                        showMergeSelection = false
                                        Toast.makeText(context, "Merged Note Created Successfully! 🔗", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    Text("Confirm Merge ✓")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Image lightbox / fullscreen view
        if (lightboxUriString != null) {
            Dialog(onDismissRequest = { lightboxUriString = null }) {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    color = Color.Black.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = rememberAsyncImagePainter(model = lightboxUriString),
                            contentDescription = "Lightbox View",
                            modifier = Modifier.fillMaxSize().align(Alignment.Center),
                            contentScale = ContentScale.Fit
                        )
                        IconButton(
                            onClick = { lightboxUriString = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Lightbox", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    val neverAskClipboard by viewModel.clipboardNeverAsk.collectAsStateWithLifecycle()
    if (!neverAskClipboard && clipboardLinkDetected.isNotBlank()) {
        Dialog(onDismissRequest = {
            ignoredUrls = ignoredUrls + clipboardLinkDetected
            clipboardLinkDetected = ""
        }) {
            ClipboardNotificationPrompt(
                detectedText = clipboardLinkDetected,
                viewModel = viewModel,
                isDark = isDark,
                onDismiss = {
                    ignoredUrls = ignoredUrls + clipboardLinkDetected
                    clipboardLinkDetected = ""
                }
            )
        }
    }
}

@Composable
fun LockScreen(
    viewModel: LinkViewModel,
    isDark: Boolean,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { findMainActivity(context) }

    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val registeredUsers by viewModel.registeredUsers.collectAsStateWithLifecycle()

    val isBiometricAllowed by viewModel.isBiometricAllowed.collectAsStateWithLifecycle()
    val storedPasscodeType by viewModel.storedPasscodeType.collectAsStateWithLifecycle()
    val storedPasscode by viewModel.storedPasscode.collectAsStateWithLifecycle()
    val lockoutStatus by viewModel.lockoutStatus.collectAsStateWithLifecycle()

    var enteredCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    
    // Ticker to decrement lockout countdown every second
    LaunchedEffect(lockoutStatus) {
        if (lockoutStatus is com.example.security.LockoutStatus.Locked) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                viewModel.checkLockoutStatus()
            }
        }
    }

    // Registration form states
    var showRegisterForm by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<com.example.data.LocalUser?>(null) }
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regAllowBiometrics by remember { mutableStateOf(false) }
    var regError by remember { mutableStateOf("") }

    val hasBiometrics = activity?.isBiometricHardwareAvailable() == true

    fun launchBiometrics() {
        if (lockoutStatus is com.example.security.LockoutStatus.Locked) {
            errorMessage = "Lockout active: Please wait"
            return
        }
        if (isBiometricAllowed && hasBiometrics && activeUser != null) {
            activity?.triggerBiometricPrompt(
                onSuccess = {
                    onSuccess()
                },
                onError = { err ->
                    errorMessage = err
                }
            )
        }
    }

    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var biometricPromptAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(isUnlocked, activeUser, isBiometricAllowed) {
        if (!isUnlocked && activeUser != null && isBiometricAllowed && hasBiometrics && !biometricPromptAttempted) {
            biometricPromptAttempted = true
            launchBiometrics()
        } else if (isUnlocked) {
            biometricPromptAttempted = false
        }
    }

    LaunchedEffect(activeUser, isUnlocked, isBiometricAllowed) {
        enteredCode = ""
        errorMessage = ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF13101B) else Color(0xFFF6F3F9)),
        contentAlignment = Alignment.Center
    ) {
        val user = activeUser
        if (user == null) {
            // PROFILE SWITCHER & LOCAL SIGN-IN SELECTION PORTAL
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                LinkVaultLogoBadge(size = 76.dp, cornerRadius = 22.dp)

                Text(
                    text = "Welcome to LinkVault",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isDark) Color.White else Color(0xFF1D192B)
                )

                Text(
                    text = "Select a local profile to open secure vault storage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF49454F)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (!showRegisterForm) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        registeredUsers.forEach { profile ->
                            val initials = (profile.name.ifBlank { profile.email }).take(1).uppercase()
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF1E1C25) else Color.White
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Clickable info section taking up the left and center
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.loginUser(profile) }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(if (isDark) Color(0xFF2C2440) else Color(0xFFEADDFF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initials,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isDark) Color(0xFFD0BCFF) else Color(0xFF21005D)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isDark) Color.White else Color(0xFF1D192B)
                                            )
                                            Text(
                                                text = profile.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF49454F)
                                            )
                                        }

                                        if (profile.lockEnabled) {
                                            Icon(
                                                imageVector = Icons.Filled.Lock,
                                                contentDescription = "Secured with credentials",
                                                tint = if (isDark) Color(0xFFFFB8D1) else Color(0xFFBA1A1A),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.LockOpen,
                                                contentDescription = "Unsecured profile",
                                                tint = Color(0xFF388E3C),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Action buttons row (with trash icon for delete)
                                    Row(
                                        modifier = Modifier.padding(end = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                profileToDelete = profile
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete Profile Option",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = "Sign In Profile Arrow",
                                            tint = if (isDark) Color(0xFF938F99) else Color(0xFF79747E),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                regName = ""
                                regEmail = ""
                                regAllowBiometrics = false
                                regError = ""
                                showRegisterForm = true
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF322E3D) else Color(0xFFE0DBE6))
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "Add Local Account")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Register New Private Profile")
                        }
                    }
                } else {
                    // REGISTER FORM PANEL
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E1C25) else Color.White
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Create Local Account Profile",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isDark) Color.White else Color(0xFF1D192B)
                            )

                            Text(
                                text = "All account metadata remains strictly local on your device inside premium secure isolation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF49454F)
                            )

                            OutlinedTextField(
                                value = regName,
                                onValueChange = { regName = it; regError = "" },
                                label = { Text("Display Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = { regEmail = it; regError = "" },
                                label = { Text("Email Address") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (hasBiometrics) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Require Fingerprint Lock",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isDark) Color.White else Color(0xFF1D192B)
                                        )
                                        Text(
                                            text = "Require fingerprint check before opening this profile on app start",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF49454F)
                                        )
                                    }
                                    Switch(
                                        checked = regAllowBiometrics,
                                        onCheckedChange = { regAllowBiometrics = it }
                                    )
                                }
                            } else {
                                Text(
                                    text = "ℹ️ Fingerprint scanner hardware is not configured or available on this device. Profile will be created without app lock features.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF49454F).copy(alpha = 0.8f)
                                )
                            }

                            if (regError.isNotEmpty()) {
                                Text(
                                    text = regError,
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showRegisterForm = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel")
                                }

                                Button(
                                    onClick = {
                                        if (regName.isBlank() || regEmail.isBlank()) {
                                            regError = "Display Name and Email Address are required."
                                        } else if (false) {
                                            regError = "PIN code must be exactly 4 to 6 numeric numbers."
                                        } else {
                                            val registered = viewModel.registerNewLocalUser(
                                                name = regName,
                                                email = regEmail,
                                                pincode = "",
                                                allowBiometrics = regAllowBiometrics
                                            )
                                            if (registered) {
                                                showRegisterForm = false
                                                onSuccess()
                                            } else {
                                                regError = "This local email address has already been registered."
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF21005D),
                                        contentColor = if (isDark) Color(0xFF21005D) else Color.White
                                    )
                                ) {
                                    Text("Register Profile")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            // SECONDARY BIOMETRIC SECURE FINGERPRINT LOCK UNLOCK VIEW
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                LinkVaultLogoBadge(size = 86.dp, cornerRadius = 24.dp)

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "${user.name}'s Vault",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isDark) Color.White else Color(0xFF1D192B)
                )

                Text(
                    text = "Fingerprint verification required",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF49454F),
                    modifier = Modifier.padding(top = 8.dp)
                )

                // 1. Lockout status banner
                when (val status = lockoutStatus) {
                    is com.example.security.LockoutStatus.Locked -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "⚠️ SECURITY LOCKOUT ACTIVE",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Too many failed attempts (${status.failureCount} total).\nPlease wait ${status.remainingSeconds}s before retrying.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    is com.example.security.LockoutStatus.Warning -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0xFF3E2D12) else Color(0xFFFFF3CD),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⚠️ ${status.remainingAttemptsBeforeLockout} attempts remaining before temporary lockout",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (isDark) Color(0xFFFFD54F) else Color(0xFF856404),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    is com.example.security.LockoutStatus.Erased -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "🚨 Vault Threshold Exceeded: All local vault data was erased. Restore from cloud/file backup.",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                val isLockedOut = lockoutStatus is com.example.security.LockoutStatus.Locked || lockoutStatus is com.example.security.LockoutStatus.Erased

                // PIN entry if user has a PIN or if passcode is set
                if (storedPasscode.isNotEmpty()) {
                    OutlinedTextField(
                        value = enteredCode,
                        onValueChange = { if (it.length <= 16 && !isLockedOut) enteredCode = it },
                        label = { Text("Vault PIN / Passcode") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                if (!isLockedOut && enteredCode.isNotBlank()) {
                                    if (viewModel.verifyPasscode(enteredCode)) {
                                        onSuccess()
                                    } else {
                                        errorMessage = "Incorrect PIN code"
                                    }
                                }
                            }
                        ),
                        singleLine = true,
                        enabled = !isLockedOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (!isLockedOut && enteredCode.isNotBlank()) {
                                if (viewModel.verifyPasscode(enteredCode)) {
                                    onSuccess()
                                } else {
                                    errorMessage = "Incorrect PIN code"
                                }
                            }
                        },
                        enabled = !isLockedOut && enteredCode.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 24.dp)
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock with PIN")
                    }
                    if (hasBiometrics) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "— OR —",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color.Gray else Color.DarkGray
                        )
                    }
                }

                if (hasBiometrics) {
                    Spacer(modifier = Modifier.height(12.dp))
                    IconButton(
                        onClick = { if (!isLockedOut) launchBiometrics() },
                        enabled = !isLockedOut,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLockedOut) Color.Gray.copy(alpha = 0.3f)
                                else if (isDark) Color(0xFF2C2440) else Color(0xFFEADDFF)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = "Trigger Fingerprint Scanner",
                            modifier = Modifier.size(42.dp),
                            tint = if (isLockedOut) Color.Gray else if (isDark) Color(0xFFFFB8D1) else Color(0xFF21005D)
                        )
                    }
                    Text(
                        text = if (isLockedOut) "Biometrics disabled during lockout" else "Tap fingerprint to unlock with biometric Keystore",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF49454F).copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (storedPasscode.isEmpty()) {
                    Button(
                        onClick = {
                            viewModel.setUnlocked(true)
                            onSuccess()
                        },
                        enabled = !isLockedOut,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = "Unlock Vault")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock Vault")
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                TextButton(
                    onClick = {
                        viewModel.logout()
                    }
                ) {
                    Icon(imageVector = Icons.Filled.SwitchAccount, contentDescription = "Switch profile", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Switch Profile / Sign Out",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (profileToDelete != null) {
            val deleteTarget = profileToDelete!!
            AlertDialog(
                onDismissRequest = { profileToDelete = null },
                title = {
                    Text(
                        text = "Delete Private Profile?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Are you sure you want to permanently delete '${deleteTarget.name}''s profile?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "All associated saved links for ${deleteTarget.email} will be completely and securely erased from local device database containment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteUserProfile(deleteTarget.email)
                            profileToDelete = null
                            Toast.makeText(context, "Profile deleted successfully", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete Permanently")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { profileToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileManagementDialog(
    viewModel: LinkViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onOpenSecuritySettings: () -> Unit
) {
    val context = LocalContext.current
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val registeredUsers by viewModel.registeredUsers.collectAsStateWithLifecycle()
    val privacyShieldEnabled by viewModel.privacyShieldEnabled.collectAsStateWithLifecycle()
    val isLockEnabled by viewModel.isLockEnabled.collectAsStateWithLifecycle()

    if (activeUser == null) {
        onDismiss()
        return
    }

    val user = activeUser!!
    var showActiveUserDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = if (isDark) Color(0xFF1A1723) else Color(0xFFFAF8FF),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bottom-sheet drag handle
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF3E384D) else Color(0xFFCAC4D0))
                        )
                    }

                    // Header Row: Avatar + Profile Info + Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF33284A) else Color(0xFFEADDFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
                                )
                            }
                            Column {
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isDark) Color.White else Color(0xFF1D192B)
                                )
                                Text(
                                    text = user.email.ifBlank { "Local Encrypted Profile" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF605D66)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(18.dp),
                                tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)
                            )
                        }
                    }

                    // Security Toggles Group (soft tonal container with 100% aligned switches)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color(0xFF231F30) else Color(0xFFF2EDF9))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Privacy Shield
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (privacyShieldEnabled) Color(0xFF4CAF50).copy(alpha = 0.16f)
                                        else (if (isDark) Color(0xFF332D41) else Color(0xFFE6E0EE))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (privacyShieldEnabled) Icons.Filled.Shield else Icons.Outlined.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (privacyShieldEnabled) Color(0xFF4CAF50) else (if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F))
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Privacy Shield",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isDark) Color.White else Color(0xFF1D192B)
                                )
                                Text(
                                    text = "Hide screen in recent apps & block capture",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = if (isDark) Color.White.copy(alpha = 0.68f) else Color(0xFF49454F)
                                )
                            }
                            Switch(
                                checked = privacyShieldEnabled,
                                onCheckedChange = { viewModel.togglePrivacyShield() },
                                modifier = Modifier.testTag("privacy_shield_toggle_button")
                            )
                        }

                        HorizontalDivider(color = if (isDark) Color(0x14FFFFFF) else Color(0x12000000))

                        // 2. Vault Lock & Advanced Security Settings
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenSecuritySettings() }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isLockEnabled) Color(0xFF4CAF50).copy(alpha = 0.16f)
                                        else (if (isDark) Color(0xFF332D41) else Color(0xFFE6E0EE))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isLockEnabled) Icons.Filled.Lock else Icons.Outlined.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isLockEnabled) Color(0xFF4CAF50) else (if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F))
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Vault Lock & Security",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isDark) Color.White else Color(0xFF1D192B)
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Open Security Settings",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6200EE)
                                    )
                                }
                                Text(
                                    text = "Tap for biometrics, auto-lock, autofill & backups",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = if (isDark) Color.White.copy(alpha = 0.68f) else Color(0xFF49454F)
                                )
                            }
                            Switch(
                                checked = isLockEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val existingPass = viewModel.storedPasscode.value
                                        val existingType = viewModel.storedPasscodeType.value.ifBlank { "FINGERPRINT" }
                                        viewModel.enableLock(
                                            enabled = true,
                                            passcode = existingPass,
                                            passcodeType = if (existingPass.isNotBlank()) existingType else "FINGERPRINT",
                                            allowBiometric = true
                                        )
                                    } else {
                                        viewModel.disableLock()
                                    }
                                },
                                modifier = Modifier.testTag("vault_security_lock_toggle")
                            )
                        }
                    }

                    // Bottom Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                viewModel.logout()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (registeredUsers.size > 1) "Switch" else "Sign Out",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = onOpenSecuritySettings,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Security & Backup",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    TextButton(
                        onClick = { showActiveUserDeleteConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Delete Profile Permanently",
                            style = MaterialTheme.typography.labelSmall.copy(color = if (isDark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A))
                        )
                    }
                }
            }
        }

        if (showActiveUserDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showActiveUserDeleteConfirm = false },
                title = {
                    Text(
                        text = "Delete Private Profile?",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Are you sure you want to permanently delete your active profile '${user.name}'?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "All associated saved links in your private local vault will be completely and securely formatted. This cannot be undone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteUserProfile(user.email)
                            showActiveUserDeleteConfirm = false
                            onDismiss()
                            Toast.makeText(context, "Active profile deleted", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete Permanently")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showActiveUserDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SecuritySettingsDialog(
    viewModel: LinkViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { findMainActivity(context) }
    val coroutineScope = rememberCoroutineScope()

    val isLockEnabled by viewModel.isLockEnabled.collectAsStateWithLifecycle()
    val hasBiometrics = activity?.isBiometricHardwareAvailable() == true

    var appLockOn by remember { mutableStateOf(isLockEnabled) }

    val autoLockTimeout by viewModel.autoLockTimeoutSeconds.collectAsStateWithLifecycle()
    var autoEraseOn by remember { mutableStateOf(viewModel.lockoutManager.isAutoEraseEnabled) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    var showSecurityStatusDialog by remember { mutableStateOf(false) }
    var showMasterPasswordSetupDialog by remember { mutableStateOf(false) }
    var showChangeMasterPasswordDialog by remember { mutableStateOf(false) }
    var showEmergencyKitDialog by remember { mutableStateOf(false) }
    var showAutofillSettingsDialog by remember { mutableStateOf(false) }
    var emergencyKitSecretKey by remember { mutableStateOf(com.example.security.KeyHierarchy.getActiveSecretKey() ?: "") }

    // Collapsible sections for clean progressive disclosure
    var isMasterKeyExpanded by remember { mutableStateOf(false) }
    var isBackupSectionExpanded by remember { mutableStateOf(false) }
    var isDiagnosticsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isLockEnabled) {
        appLockOn = isLockEnabled
    }

    // Feature 1 & 2 extra states
    val driveHelper = viewModel.driveHelper
    val backupStatus by driveHelper.backupStatus.collectAsStateWithLifecycle()
    val restoreStatus by driveHelper.restoreStatus.collectAsStateWithLifecycle()

    var passphrase by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var autoBackupOn by remember { mutableStateOf(driveHelper.autoBackupEnabled) }
    var simulateNetworkFail by remember { mutableStateOf(false) }
    var backupLogs by remember { mutableStateOf(driveHelper.getBackupHistory()) }
    var availableBackups by remember { mutableStateOf<List<com.example.data.OfflineBackupItem>>(emptyList()) }
    var isListingBackups by remember { mutableStateOf(false) }

    var customDirUri by remember { mutableStateOf(driveHelper.customBackupDirUri) }
    var customDirName by remember { mutableStateOf(driveHelper.customBackupDirDisplayName) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                var folderName = "Custom Folder"
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (colIndex != -1) {
                            folderName = cursor.getString(colIndex)
                        }
                    }
                }
                
                driveHelper.setCustomBackupFolder(uri.toString(), folderName)
                customDirUri = uri.toString()
                customDirName = folderName
                backupLogs = driveHelper.getBackupHistory()
                Toast.makeText(context, "Backup folder selected: $folderName", Toast.LENGTH_SHORT).show()
                
                val enteredKey = passphrase.ifBlank { "default_secure_vault_key" }
                coroutineScope.launch {
                    availableBackups = driveHelper.listBackups(enteredKey.toCharArray())
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error selecting folder: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Sync state if user settings switched
    LaunchedEffect(driveHelper.activeUserEmail) {
        customDirUri = driveHelper.customBackupDirUri
        customDirName = driveHelper.customBackupDirDisplayName
        autoBackupOn = driveHelper.autoBackupEnabled
    }

    // Restore dialog state
    var selectedBackupForRestore by remember { mutableStateOf<com.example.data.OfflineBackupItem?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var selectedRestoreMode by remember { mutableStateOf("MERGE") } // MERGE or REPLACE

    var hasMasterKey by remember { mutableStateOf(com.example.security.KeyHierarchy.hasMasterKey(context)) }
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * 0.86f).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = if (isDark) Color(0xFF1A1723) else Color(0xFFFAF8FF),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Bottom-sheet drag handle
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF3E384D) else Color(0xFFCAC4D0))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sheet Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDark) Color(0xFF2B2240) else Color(0xFFEADDFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = "Security Vault Options",
                                    tint = if (isDark) Color(0xFFD0BCFF) else Color(0xFF21005D),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Security & Backups",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isDark) Color.White else Color(0xFF1D192B)
                                )
                                Text(
                                    text = "Hardware-backed offline vault protection",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF605D66)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close settings",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = if (isDark) Color(0xFF2B2738) else Color(0xFFE5E0EC))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Smooth LazyColumn inside full-width Bottom Sheet
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        // 1. Primary Biometric & PIN Card
                        item(key = "sec_app_lock", contentType = "sec_app_lock") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color(0xFF231F30) else Color(0xFFF2EDF9))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Biometric App Lock",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isDark) Color.White else Color(0xFF1D192B)
                                        )
                                        Text(
                                            text = "Require fingerprint or face unlock to open LinkVault",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF605D66)
                                        )
                                    }
                                    Switch(
                                        checked = appLockOn,
                                        enabled = hasBiometrics,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                viewModel.enableLock(
                                                    enabled = true,
                                                    passcode = "",
                                                    passcodeType = "FINGERPRINT",
                                                    allowBiometric = true
                                                )
                                                appLockOn = true
                                                Toast.makeText(context, "Biometric App Lock enabled", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.disableLock()
                                                appLockOn = false
                                                Toast.makeText(context, "Biometric App Lock disabled", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = if (isDark) Color(0xFF21005D) else Color.White,
                                            checkedTrackColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF21005D)
                                        )
                                    )
                                }

                                if (!hasBiometrics) {
                                    Text(
                                        text = "⚠️ Biometric sensor is not configured on this device.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                HorizontalDivider(color = if (isDark) Color(0x14FFFFFF) else Color(0x12000000))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Vault PIN / Passcode",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isDark) Color.White else Color(0xFF1D192B)
                                        )
                                        Text(
                                            text = "PBKDF2-HMAC-SHA256 (600,000 rounds)",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF605D66)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            newPinInput = ""
                                            confirmPinInput = ""
                                            pinError = ""
                                            showSetPinDialog = true
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.heightIn(min = 38.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (viewModel.storedPasscode.value.isNotEmpty()) "Change PIN" else "Set PIN",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Auto-Lock Background Timeout (Full-width 1-line segmented pills — never wraps!)
                        item(key = "sec_autolock_card", contentType = "sec_autolock_card") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color(0xFF231F30) else Color(0xFFF2EDF9))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto-Lock Timeout",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isDark) Color.White else Color(0xFF1D192B)
                                        )
                                        Text(
                                            text = "Locks vault on background or sleep",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF605D66),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                val timeoutOptions = listOf(
                                    0 to "Instant",
                                    30 to "30s",
                                    60 to "1m",
                                    120 to "2m",
                                    300 to "5m"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    timeoutOptions.forEach { (sec, label) ->
                                        val isSelected = autoLockTimeout == sec
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) {
                                                        if (isDark) Color(0xFFD0BCFF) else Color(0xFF6200EE)
                                                    } else {
                                                        if (isDark) Color(0xFF171420) else Color(0xFFE4DDF2)
                                                    }
                                                )
                                                .clickable { viewModel.setAutoLockTimeout(sec) }
                                                .padding(vertical = 6.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.5.sp
                                                ),
                                                color = if (isSelected) {
                                                    if (isDark) Color(0xFF21005D) else Color.White
                                                } else {
                                                    if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D192B)
                                                },
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. System Autofill & Credentials (Clean 1-line card with dynamic status)
                        item(key = "sec_autofill_card", contentType = "sec_autofill_card") {
                            val isAutofillActive = remember(showAutofillSettingsDialog, isDark) {
                                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) false
                                else {
                                    val afm = context.getSystemService(android.view.autofill.AutofillManager::class.java)
                                    if (afm?.hasEnabledAutofillServices() == true) true
                                    else {
                                        try {
                                            val setting = android.provider.Settings.Secure.getString(context.contentResolver, "autofill_service") ?: ""
                                            setting.contains(context.packageName, ignoreCase = true)
                                        } catch (_: Throwable) { false }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color(0xFF1E2938) else Color(0xFFE8F1FD))
                                    .clickable { showAutofillSettingsDialog = true }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhonelinkLock,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0),
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "System Autofill & Apps",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isDark) Color.White else Color(0xFF1D192B),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (isAutofillActive) "Autofill service enabled and active" else "Fill & save logins in browsers & apps offline",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = { showAutofillSettingsDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAutofillActive) (if (isDark) Color(0xFF2E7D32) else Color(0xFF1B5E20)) else MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 38.dp)
                                        .widthIn(min = 84.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (isAutofillActive) "Active ✅" else "Configure",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // 4. Auto-Erase on 10 Failed Attempts
                        item(key = "sec_autoerase_card", contentType = "sec_autoerase_card") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color(0xFF231F30) else Color(0xFFF2EDF9))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Self-Destruct After 10 Failed Attempts",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isDark) Color.White else Color(0xFF1D192B)
                                        )
                                        Text(
                                            text = "Brute-force protection wipes local vault",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF605D66)
                                        )
                                    }
                                    Switch(
                                        checked = autoEraseOn,
                                        onCheckedChange = { checked ->
                                            autoEraseOn = checked
                                            viewModel.lockoutManager.isAutoEraseEnabled = checked
                                            Toast.makeText(
                                                context,
                                                if (checked) "Auto-erase on 10 failed attempts enabled 🛡️" else "Auto-erase disabled",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFFBA1A1A)
                                        )
                                    )
                                }
                                if (autoEraseOn) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "⚠️ 10 consecutive failed attempts will permanently erase on-device vault items.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // 5. Collapsible Card: Master Key, Secret Key & Hardware Status
                        item(key = "sec_master_key_card", contentType = "sec_master_key_card") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color(0xFF231F30) else Color(0xFFF2EDF9))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isMasterKeyExpanded = !isMasterKeyExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VpnKey,
                                            contentDescription = null,
                                            tint = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6200EE),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Master Key & Hardware Status",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (isDark) Color.White else Color(0xFF1D192B)
                                            )
                                            Text(
                                                text = if (hasMasterKey) "256-bit DEK active · PBKDF2 600k+" else "Zero-knowledge key hierarchy",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF605D66)
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isMasterKeyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isMasterKeyExpanded) "Collapse" else "Expand",
                                        tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)
                                    )
                                }

                                AnimatedVisibility(visible = isMasterKeyExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!hasMasterKey) {
                                            Button(
                                                onClick = { showMasterPasswordSetupDialog = true },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 42.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = "Set Up Master Password",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val activeKey = com.example.security.KeyHierarchy.getActiveSecretKey()
                                                        if (!activeKey.isNullOrBlank()) {
                                                            emergencyKitSecretKey = activeKey
                                                            showEmergencyKitDialog = true
                                                        } else if (hasBiometrics && activity != null) {
                                                            activity.triggerBiometricPrompt(
                                                                onSuccess = {
                                                                    emergencyKitSecretKey = com.example.security.KeyHierarchy.getSavedSecretKey(context)
                                                                    showEmergencyKitDialog = true
                                                                },
                                                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                                            )
                                                        } else {
                                                            emergencyKitSecretKey = com.example.security.KeyHierarchy.getSavedSecretKey(context)
                                                            showEmergencyKitDialog = true
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 42.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                                ) {
                                                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Emergency Kit",
                                                        fontSize = 11.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                OutlinedButton(
                                                    onClick = { showChangeMasterPasswordDialog = true },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 42.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                                ) {
                                                    Icon(Icons.Filled.Password, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Change Key",
                                                        fontSize = 11.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        FilledTonalButton(
                                            onClick = { showSecurityStatusDialog = true },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight(),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    Icons.Filled.VerifiedUser,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Hardware Keystore & Security Audit",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    softWrap = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 6. Collapsible Card: Offline Backup & Restore Center
                        item(key = "sec_backup_storage", contentType = "sec_backup_storage") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color(0xFF231F30) else Color(0xFFF2EDF9))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isBackupSectionExpanded = !isBackupSectionExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SettingsBackupRestore,
                                            contentDescription = null,
                                            tint = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6200EE),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Offline Backup & Restore",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (isDark) Color.White else Color(0xFF1D192B)
                                            )
                                            Text(
                                                text = if (customDirUri.isNotEmpty()) "Folder: $customDirName" else "AES-256-GCM encrypted local archive",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF605D66),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isBackupSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isBackupSectionExpanded) "Collapse" else "Expand",
                                        tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)
                                    )
                                }

                                AnimatedVisibility(visible = isBackupSectionExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Folder picker row
                                        if (customDirUri.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { folderLauncher.launch(null) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 40.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Change Folder", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        driveHelper.clearCustomBackupFolder()
                                                        customDirUri = ""
                                                        customDirName = ""
                                                        backupLogs = driveHelper.getBackupHistory()
                                                        val enteredKey = passphrase.ifBlank { "default_secure_vault_key" }
                                                        coroutineScope.launch {
                                                            availableBackups = driveHelper.listBackups(enteredKey.toCharArray())
                                                        }
                                                        Toast.makeText(context, "Reset to internal app storage.", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 40.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Reset Sandbox", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        } else {
                                            FilledTonalButton(
                                                onClick = { folderLauncher.launch(null) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 42.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Choose Backup Folder on Phone",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        }

                                        OutlinedTextField(
                                            value = passphrase,
                                            onValueChange = { passphrase = it },
                                            label = { Text("Backup Encryption Passphrase") },
                                            placeholder = { Text("Enter passphrase") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            trailingIcon = {
                                                IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                                    Icon(
                                                        imageVector = if (passphraseVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                        contentDescription = "Toggle Passphrase Visibility"
                                                    )
                                                }
                                            },
                                            visualTransformation = if (passphraseVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = if (isDark) Color(0xFF322E3D) else Color(0xFFE0DBE6)
                                            )
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val enteredKey = passphrase.ifBlank { "default_secure_vault_key" }
                                                    viewModel.backupVault(
                                                        encryptionPassword = enteredKey.toCharArray(),
                                                        simulateRetry = false,
                                                        onSuccess = {
                                                            backupLogs = driveHelper.getBackupHistory()
                                                            val key = passphrase.ifBlank { "default_secure_vault_key" }
                                                            coroutineScope.launch {
                                                                availableBackups = driveHelper.listBackups(key.toCharArray())
                                                            }
                                                            Toast.makeText(context, "Backup finished successfully! 🔒", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onError = { err ->
                                                            backupLogs = driveHelper.getBackupHistory()
                                                            Toast.makeText(context, "Backup Error: $err", Toast.LENGTH_LONG).show()
                                                        }
                                                    )
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 42.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Backup Now", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    isListingBackups = true
                                                    coroutineScope.launch {
                                                        val enteredKey = passphrase.ifBlank { "default_secure_vault_key" }
                                                        availableBackups = driveHelper.listBackups(enteredKey.toCharArray())
                                                        isListingBackups = false
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 42.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = if (isListingBackups) "Scanning..." else "Scan Backups",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Automatic Realtime Backup",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (isDark) Color.White else Color(0xFF1D192B)
                                                )
                                                Text(
                                                    text = "Save encrypted archive on vault changes",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF605D66)
                                                )
                                            }
                                            Switch(
                                                checked = autoBackupOn,
                                                onCheckedChange = { value ->
                                                    autoBackupOn = value
                                                    driveHelper.autoBackupEnabled = value
                                                }
                                            )
                                        }

                                        if (backupStatus !is com.example.data.BackupStatus.Idle) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isDark) Color(0xFF1A1723) else Color(0xFFEADDFF).copy(alpha = 0.4f))
                                                    .padding(10.dp)
                                            ) {
                                                when (val status = backupStatus) {
                                                    is com.example.data.BackupStatus.Progress -> {
                                                        Text("Backing up vault... (${status.percent}%)", fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth())
                                                    }
                                                    is com.example.data.BackupStatus.Success -> {
                                                        Text("✔️ ${status.message}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                                    }
                                                    is com.example.data.BackupStatus.Error -> {
                                                        Text("❌ ${status.message}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }

                                        if (availableBackups.isNotEmpty()) {
                                            availableBackups.forEach { item ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(if (isDark) Color(0xFF181520) else Color(0xFFEAE5F2))
                                                        .clickable {
                                                            selectedBackupForRestore = item
                                                            showRestoreConfirm = true
                                                        }
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(item.fileName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                        Text(
                                                            "${item.payloadPreview} · ${com.example.util.DateTimeFormatterUtil.formatBackupItemTime(item.creationTime)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (item.isVerified) Color(0x334CAF50) else Color(0x33E53935))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (item.isVerified) "Restore ✓" else "Untrusted ⚠",
                                                            color = if (item.isVerified) Color(0xFF4CAF50) else Color(0xFFE53935),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (restoreStatus !is com.example.data.BackupStatus.Idle) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isDark) Color(0xFF2C2512) else Color(0xFFFFF9C4))
                                                    .padding(10.dp)
                                            ) {
                                                when (val status = restoreStatus) {
                                                    is com.example.data.BackupStatus.Progress -> {
                                                        Text("Reconstructing local vaults (${status.percent}%)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth())
                                                    }
                                                    is com.example.data.BackupStatus.Success -> {
                                                        Text("✔️ ${status.message}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                                    }
                                                    is com.example.data.BackupStatus.Error -> {
                                                        Text("❌ Restore Failed: ${status.message}", color = MaterialTheme.colorScheme.error, fontSize = 11.5.sp)
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Collapsible Card: Backup History & Diagnostics
                        item(key = "sec_history_logs", contentType = "sec_history_logs") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color(0xFF231F30) else Color(0xFFF2EDF9))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isDiagnosticsExpanded = !isDiagnosticsExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.History,
                                            contentDescription = null,
                                            tint = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6200EE),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Backup Diagnostics (${backupLogs.size})",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isDark) Color.White else Color(0xFF1D192B)
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isDiagnosticsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isDiagnosticsExpanded) "Collapse" else "Expand",
                                        tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)
                                    )
                                }

                                AnimatedVisibility(visible = isDiagnosticsExpanded) {
                                    if (backupLogs.isEmpty()) {
                                        Text(
                                            "No diagnostic events recorded yet.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = Color.Gray,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isDark) Color(0xFF14121B) else Color(0xFFE8E2F0))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            backupLogs.take(10).forEach { log ->
                                                Text(
                                                    text = "[${com.example.util.DateTimeFormatterUtil.formatLogTime(log.timestamp)}] (${log.status}) ${log.details}",
                                                    style = androidx.compose.ui.text.TextStyle(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.5.sp,
                                                        color = if (log.status == "Success") Color(0xFF4CAF50) else Color(0xFFE53935)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Restore Mode and Fingerprint Authorization Modal Prompt
    if (showRestoreConfirm && selectedBackupForRestore != null) {
        val targetItem = selectedBackupForRestore!!
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Confirm Secure Restore 🛡️") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Selected: ${targetItem.fileName}", fontWeight = FontWeight.Bold)
                    Text("Hardware biometrics or active fingerprint scanner credentials will be requested to authorize this decrypt-and-merge process.")
                    
                    HorizontalDivider()

                    Text("Choose Import Method:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRestoreMode == "MERGE",
                            onClick = { selectedRestoreMode = "MERGE" }
                        )
                        Column(modifier = Modifier.clickable { selectedRestoreMode = "MERGE" }) {
                            Text("Merge Import", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Add links without wiping current local databases.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRestoreMode == "REPLACE",
                            onClick = { selectedRestoreMode = "REPLACE" }
                        )
                        Column(modifier = Modifier.clickable { selectedRestoreMode = "REPLACE" }) {
                            Text("Overwrite Import (dangerous)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Text("Delete current local data and replace fully with backup.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        val enteredKey = passphrase.ifBlank { "default_secure_vault_key" }
                        
                        // Fingerprint verification before start (Requirement: Decrypt only during restore after auth)
                        if (hasBiometrics && activity != null) {
                            activity.triggerBiometricPrompt(
                                onSuccess = {
                                    viewModel.restoreVault(
                                        backupItem = targetItem,
                                        decryptionPassword = enteredKey.toCharArray(),
                                        mode = selectedRestoreMode,
                                        onSuccess = {
                                            Toast.makeText(context, "Database restore successful!", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, "Decryption/Integrity Error: $err", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                onError = { err ->
                                    Toast.makeText(context, "Hardware Auth Blocked: $err", Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            // Offline Fallback/PIN simulation if biometrics not available in emulator
                            viewModel.restoreVault(
                                backupItem = targetItem,
                                decryptionPassword = enteredKey.toCharArray(),
                                mode = selectedRestoreMode,
                                onSuccess = {
                                    Toast.makeText(context, "Database restore successful! (No-Hardware Auth verified)", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, "Decryption/Integrity Error: $err", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                ) {
                    Text("Authenticate & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text("Set / Change Vault PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a numeric PIN (4-12 digits). It will be hashed using PBKDF2-HMAC-SHA256 (600,000 rounds) before storing.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 12 && it.all { c -> c.isDigit() }) newPinInput = it },
                        label = { Text("New PIN") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 12 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                        label = { Text("Confirm PIN") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError.isNotEmpty()) {
                        Text(
                            text = pinError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length < 4) {
                            pinError = "PIN must be at least 4 digits"
                        } else if (newPinInput != confirmPinInput) {
                            pinError = "PINs do not match"
                        } else {
                            viewModel.enableLock(
                                enabled = true,
                                passcode = newPinInput,
                                passcodeType = "PIN",
                                allowBiometric = hasBiometrics
                            )
                            showSetPinDialog = false
                            Toast.makeText(context, "Vault PIN successfully updated and hashed! 🔒", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSecurityStatusDialog) {
        com.example.ui.SecurityStatusDialog(
            isDark = isDark,
            onShowEmergencyKit = {
                showSecurityStatusDialog = false
                emergencyKitSecretKey = com.example.security.KeyHierarchy.getSavedSecretKey(context)
                showEmergencyKitDialog = true
            },
            onChangeMasterPassword = {
                showSecurityStatusDialog = false
                showChangeMasterPasswordDialog = true
            },
            onDismiss = { showSecurityStatusDialog = false }
        )
    }

    if (showMasterPasswordSetupDialog) {
        com.example.ui.MasterPasswordSetupDialog(
            isDark = isDark,
            onSuccess = { generatedSecretKey ->
                showMasterPasswordSetupDialog = false
                hasMasterKey = com.example.security.KeyHierarchy.hasMasterKey(context)
                emergencyKitSecretKey = generatedSecretKey
                showEmergencyKitDialog = true
            },
            onDismiss = { showMasterPasswordSetupDialog = false }
        )
    }

    if (showChangeMasterPasswordDialog) {
        com.example.ui.ChangeMasterPasswordDialog(
            isDark = isDark,
            onSuccess = {
                showChangeMasterPasswordDialog = false
                hasMasterKey = com.example.security.KeyHierarchy.hasMasterKey(context)
            },
            onDismiss = { showChangeMasterPasswordDialog = false }
        )
    }

    if (showEmergencyKitDialog && emergencyKitSecretKey.isNotBlank()) {
        com.example.ui.EmergencyKitDialog(
            secretKey = emergencyKitSecretKey,
            isDark = isDark,
            onDismiss = { showEmergencyKitDialog = false }
        )
    }

    if (showAutofillSettingsDialog) {
        AutofillSettingsDialog(
            isDark = isDark,
            onDismissRequest = { showAutofillSettingsDialog = false }
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Poco F7 Display (412x915dp)",
    device = POCO_F7_DEVICE_SPEC,
    showSystemUi = true,
    showBackground = true,
    backgroundColor = 0xFF0A0A0F
)
@Composable
fun PocoF7LinkVaultScreenPreview() {
    val link1 = LinkUiMapper.toUiModel(
        com.example.data.LinkEntity(
            id = 1,
            url = "https://kotlinlang.org/docs/coroutines-guide.html",
            title = "Kotlin Coroutines & Flow Guide",
            category = "💻 Coding",
            isFavorite = true,
            note = "Structured concurrency and Dispatchers best practices"
        ),
        context = null
    )
    val link2 = LinkUiMapper.toUiModel(
        com.example.data.LinkEntity(
            id = 2,
            url = "https://news.ycombinator.com",
            title = "Hacker News",
            category = "📁 Uncategorized",
            isFavorite = false,
            note = "Daily tech news"
        ),
        context = null
    )
    val pass1 = LinkUiMapper.toUiModel(
        com.example.data.LinkEntity(
            id = 3,
            url = "https://github.com/login",
            title = "GitHub Developer Account",
            description = "SECURE_PASSWORD",
            category = "🔑 Passwords",
            isFavorite = true,
            note = "dev@linkvault.local|||SecretPass#2026"
        ),
        context = null
    )
    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0E0C14), surface = Color(0xFF1A1625))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0C14))
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinkVaultLogoBadge(size = 38.dp, cornerRadius = 11.dp)
                Column {
                    Text(
                        text = "LinkVault",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "3 items stored",
                        fontSize = 11.sp,
                        color = Color(0xFFD0BCFF)
                    )
                }
            }
            CheapLinkItemCard(
                uiModel = link1,
                isDark = true,
                isEven = true,
                onToggleFavorite = {},
                onDelete = {},
                onEdit = {},
                onCopy = {},
                onOpen = {},
                onShare = {}
            )
            CheapLinkItemCard(
                uiModel = link2,
                isDark = true,
                isEven = false,
                onToggleFavorite = {},
                onDelete = {},
                onEdit = {},
                onCopy = {},
                onOpen = {},
                onShare = {}
            )
            CheapPasswordItemCard(
                uiModel = pass1,
                isDark = true,
                isEven = true,
                onToggleFavorite = {},
                onDelete = {},
                onEdit = {},
                onOpen = {}
            )
        }
    }
}


