package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LinkEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// Password Strength & Security Helpers
// -----------------------------------------------------------------------------

data class PasswordStrength(
    val score: Int, // 1 to 5
    val label: String,
    val color: Color
)

fun calculatePasswordStrength(password: String): PasswordStrength {
    if (password.isBlank()) return PasswordStrength(0, "Empty", Color.Gray)
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 14) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++

    return when {
        password.length < 6 -> PasswordStrength(1, "Weak", Color(0xFFE53935))
        score <= 2 -> PasswordStrength(2, "Moderate", Color(0xFFFB8C00))
        score <= 3 -> PasswordStrength(3, "Good", Color(0xFFFDD835))
        score == 4 -> PasswordStrength(4, "Strong", Color(0xFF43A047))
        else -> PasswordStrength(5, "Very Strong", Color(0xFF00C853))
    }
}

fun copyWithAutoClear(
    context: Context,
    text: String,
    label: String = "Password",
    scope: CoroutineScope,
    onCleared: () -> Unit = {}
) {
    copySecureTextToClipboard(context, text, isSensitive = true, label = label)
    scope.launch {
        delay(30_000L) // 30 seconds auto-clear
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val currentClip = clipboard.primaryClip
            if (currentClip != null && currentClip.itemCount > 0) {
                val currentText = currentClip.getItemAt(0).text?.toString()
                if (currentText == text) {
                    clipboard.clearPrimaryClip()
                    onCleared()
                }
            }
        } catch (e: Throwable) {
            // Safe fallback
        }
    }
}

fun cleanDomainAndPath(rawUrl: String): Pair<String, String> {
    try {
        val clean = rawUrl.trim()
        val withProtocol = if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            "https://$clean"
        } else {
            clean
        }
        val uri = Uri.parse(withProtocol)
        val host = (uri.host ?: "").replace("www.", "")
        val path = uri.path ?: ""
        val cleanPath = if (path.length > 24) path.substring(0, 22) + "…" else path
        return Pair(host.ifBlank { clean }, cleanPath)
    } catch (e: Exception) {
        return Pair(rawUrl, "")
    }
}

// -----------------------------------------------------------------------------
// Component: Segmented Vault Filter Switcher
// -----------------------------------------------------------------------------

@Composable
fun SegmentedVaultFilterSwitcher(
    currentSegment: LinkViewModel.VaultSegment,
    vaultCounts: LinkViewModel.VaultCounts,
    isDark: Boolean,
    onSegmentSelected: (LinkViewModel.VaultSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = listOf(
        Triple(LinkViewModel.VaultSegment.ALL, "🌐 All", vaultCounts.all),
        Triple(LinkViewModel.VaultSegment.LINKS, "🔗 Links", vaultCounts.links),
        Triple(LinkViewModel.VaultSegment.PASSWORDS, "🔑 Passwords", vaultCounts.passwords),
        Triple(LinkViewModel.VaultSegment.PINNED, "⭐ Pinned", vaultCounts.pinned)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF1E1A29) else Color(0xFFF1ECF6),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2E273D) else Color(0xFFDDD5E5))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEach { (segment, label, count) ->
                val isSelected = currentSegment == segment
                val activeBg = if (isDark) Color(0xFF382A54) else Color(0xFFEADDFF)
                val activeFg = if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
                val inactiveFg = if (isDark) Color(0xFFB3ADBC) else Color(0xFF5A5265)
                val badgeBg = if (isSelected) {
                    if (isDark) Color(0xFF4F378B) else Color(0xFFD0BCFF)
                } else {
                    if (isDark) Color(0xFF292338) else Color(0xFFE4DCED)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) activeBg else Color.Transparent)
                        .clickable { onSegmentSelected(segment) }
                        .testTag("segment_${segment.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            ),
                            color = if (isSelected) activeFg else inactiveFg,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(badgeBg)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isSelected) activeFg else inactiveFg
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component: Lightweight Horizontal Filter Chip Strip + Anchored Filter Menu
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultFilterDropdown(
    vaultSegment: LinkViewModel.VaultSegment,
    selectedCategory: String?,
    allUserLinks: List<LinkEntity>,
    categories: List<String>,
    vaultCounts: LinkViewModel.VaultCounts,
    isDark: Boolean,
    isPasswordsUnlocked: Boolean,
    isPrivateVaultUnlocked: Boolean,
    lockedCustomCategories: Set<String>,
    unlockedCustomCategories: Set<String>,
    currentFilteredCount: Int,
    onSelectSegment: (LinkViewModel.VaultSegment) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onResetFilters: () -> Unit,
    onNewFolderClick: () -> Unit,
    onManageFoldersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var folderSearchQuery by remember { mutableStateOf("") }

    val folderDisplayName = remember(selectedCategory) {
        selectedCategory?.let {
            val (_, label) = getCategoryEmojiAndLabel(it)
            if (label.isNotBlank()) label else it
        }
    }

    val isFilterActive = vaultSegment != LinkViewModel.VaultSegment.ALL || selectedCategory != null

    val buttonLabel = remember(vaultSegment, selectedCategory, currentFilteredCount, vaultCounts, folderDisplayName, isPasswordsUnlocked) {
        if (selectedCategory == null) {
            when (vaultSegment) {
                LinkViewModel.VaultSegment.ALL -> "All · $currentFilteredCount"
                LinkViewModel.VaultSegment.LINKS -> "Links · $currentFilteredCount"
                LinkViewModel.VaultSegment.PASSWORDS -> "Logins · ${if (isPasswordsUnlocked) currentFilteredCount else vaultCounts.passwords}"
                LinkViewModel.VaultSegment.PINNED -> "Pinned · $currentFilteredCount"
            }
        } else {
            when (vaultSegment) {
                LinkViewModel.VaultSegment.PINNED -> "Pinned · $folderDisplayName"
                LinkViewModel.VaultSegment.LINKS -> "Links · $folderDisplayName"
                LinkViewModel.VaultSegment.PASSWORDS -> "Logins · ${if (isPasswordsUnlocked) currentFilteredCount else vaultCounts.passwords}"
                else -> "$folderDisplayName · $currentFilteredCount"
            }
        }
    }

    val talkBackDescription = "Filter: $buttonLabel, " + (if (isExpanded) "menu expanded" else "menu collapsed")

    val allFoldersForChips = remember(categories) {
        categories.filter { it != "All Vault" && it != "⚡ All Vault" }
    }

    val folderCountsForChips = remember(allUserLinks) {
        allUserLinks.groupingBy { it.category }.eachCount()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Anchored Filter Menu Pill Button (preserves vault_filter_dropdown_button testTag)
            item(key = "chip_filter_menu") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = { isExpanded = !isExpanded },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isFilterActive) {
                            if (isDark) Color(0xFF2E2246) else Color(0xFFEADDFF)
                        } else {
                            if (isDark) Color(0xFF1C1A24) else Color(0xFFF3EFF8)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isFilterActive) {
                                if (isDark) Color(0xFF6B4E99) else Color(0xFF9C87C5)
                            } else {
                                if (isDark) Color(0xFF2B2836) else Color(0xFFDDD8E6)
                            }
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("vault_filter_dropdown_button")
                            .semantics {
                                contentDescription = talkBackDescription
                                stateDescription = if (isExpanded) "menu expanded" else "menu collapsed"
                                role = Role.DropdownList
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)
                            )
                            Text(
                                text = buttonLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = if (isDark) Color(0xFFEADDFF) else Color(0xFF1D192B),
                                maxLines = 1
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isDark) Color(0xFFB3ADBC) else Color(0xFF5A5265)
                            )
                        }
                    }

                    if (isFilterActive) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF2A223A) else Color(0xFFECE4F9))
                                .clickable { onResetFilters() }
                                .testTag("filter_dropdown_clear_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear filter",
                                modifier = Modifier.size(15.dp),
                                tint = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)
                            )
                        }
                    }
                }
            }

            // 2. Quick 1-Tap "All Folders" Chip
            item(key = "chip_all_folders") {
                val isAllSelected = selectedCategory == null && vaultSegment != LinkViewModel.VaultSegment.PASSWORDS
                Surface(
                    onClick = { onSelectCategory(null) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isAllSelected) {
                        if (isDark) Color(0xFF352850) else Color(0xFFEADDFF)
                    } else {
                        if (isDark) Color(0xFF18161F) else Color(0xFFF5F2FA)
                    },
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "All",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = if (isAllSelected) {
                                if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
                            } else {
                                if (isDark) Color(0xFFB3ADBC) else Color(0xFF5A5265)
                            }
                        )
                    }
                }
            }

            // 3. 1-Tap Category / Folder Chips
            items(
                items = allFoldersForChips,
                key = { "chip_cat_$it" }
            ) { catName ->
                val isSelected = selectedCategory == catName
                val count = folderCountsForChips[catName] ?: 0
                val isPrivateVault = catName == "🔐 Private Vault"
                val isPasswords = catName == "🔑 Passwords"
                val isLockedCustom = lockedCustomCategories.contains(catName)
                val isUnlockedCustom = unlockedCustomCategories.contains(catName)
                val isCurrentlyLocked = (isPrivateVault && !isPrivateVaultUnlocked) ||
                        (isPasswords && !isPasswordsUnlocked) ||
                        (isLockedCustom && !isUnlockedCustom)

                val (emoji, displayLabel) = getCategoryEmojiAndLabel(catName)
                val cleanLabel = if (displayLabel.isNotBlank()) displayLabel else catName

                Surface(
                    onClick = { onSelectCategory(catName) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        if (isDark) Color(0xFF352850) else Color(0xFFEADDFF)
                    } else {
                        if (isDark) Color(0xFF18161F) else Color(0xFFF5F2FA)
                    },
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isPrivateVault || isPasswords || isLockedCustom) {
                            Icon(
                                imageVector = if (isCurrentlyLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (isSelected) {
                                    if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)
                                } else {
                                    if (isDark) Color(0xFFB3ADBC) else Color(0xFF5A5265)
                                }
                            )
                        } else if (emoji.isNotBlank()) {
                            Text(text = emoji, fontSize = 11.sp)
                        }
                        Text(
                            text = cleanLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = if (isSelected) {
                                if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
                            } else {
                                if (isDark) Color(0xFFB3ADBC) else Color(0xFF5A5265)
                            },
                            maxLines = 1
                        )
                        if (!isCurrentlyLocked && count > 0) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isSelected) {
                                    if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)
                                } else {
                                    if (isDark) Color(0xFF8E8899) else Color(0xFF79747E)
                                }
                            )
                        }
                    }
                }
            }

            // 4. "+ Folder" Quick Chip
            item(key = "chip_new_folder") {
                Surface(
                    onClick = onNewFolderClick,
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF18161F) else Color(0xFFF5F2FA),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Folder",
                            modifier = Modifier.size(14.dp),
                            tint = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)
                        )
                        Text(
                            text = "Folder",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            ),
                            color = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .background(if (isDark) Color(0xFF1C1926) else Color(0xFFFAF7FD))
                .border(1.dp, if (isDark) Color(0xFF332F42) else Color(0xFFE4DFEC), RoundedCornerShape(12.dp))
                .testTag("vault_filter_dropdown_menu")
        ) {
            val folderCounts = remember(allUserLinks) {
                allUserLinks.groupingBy { it.category }.eachCount()
            }

            // ---------------- Section 1: TYPE (Sticky/Pinned Header) ----------------
            Text(
                text = "TYPE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // TYPE 1: All
            val isAllTypeSelected = vaultSegment == LinkViewModel.VaultSegment.ALL
            val allTypeCount = if (selectedCategory != null) {
                folderCounts[selectedCategory] ?: 0
            } else {
                vaultCounts.all
            }
            VaultFilterMenuItem(
                key = "type_all",
                label = "All",
                icon = Icons.Default.Dashboard,
                count = allTypeCount,
                isSelected = isAllTypeSelected,
                isDark = isDark,
                testTag = "filter_type_all",
                onClick = {
                    onSelectSegment(LinkViewModel.VaultSegment.ALL)
                    isExpanded = false
                }
            )

            // TYPE 2: Links
            val isLinksTypeSelected = vaultSegment == LinkViewModel.VaultSegment.LINKS
            val linksTypeCount = if (selectedCategory != null) {
                allUserLinks.count { it.category == selectedCategory && it.category != "🔑 Passwords" && it.description != "SECURE_PASSWORD" }
            } else {
                vaultCounts.links
            }
            VaultFilterMenuItem(
                key = "type_links",
                label = "Links",
                icon = Icons.Default.Link,
                count = linksTypeCount,
                isSelected = isLinksTypeSelected,
                isDark = isDark,
                testTag = "filter_type_links",
                onClick = {
                    onSelectSegment(LinkViewModel.VaultSegment.LINKS)
                    isExpanded = false
                }
            )

            // TYPE 3: Passwords
            val isPasswordsTypeSelected = vaultSegment == LinkViewModel.VaultSegment.PASSWORDS
            val passwordsTypeCount = if (selectedCategory != null) {
                allUserLinks.count { it.category == selectedCategory && (it.category == "🔑 Passwords" || it.description == "SECURE_PASSWORD") }
            } else {
                vaultCounts.passwords
            }
            VaultFilterMenuItem(
                key = "type_passwords",
                label = "Passwords",
                icon = Icons.Default.Key,
                count = passwordsTypeCount,
                isSelected = isPasswordsTypeSelected,
                isDark = isDark,
                testTag = "filter_type_passwords",
                onClick = {
                    onSelectSegment(LinkViewModel.VaultSegment.PASSWORDS)
                    isExpanded = false
                }
            )

            // TYPE 4: Pinned
            val isPinnedTypeSelected = vaultSegment == LinkViewModel.VaultSegment.PINNED
            val pinnedTypeCount = if (selectedCategory != null) {
                allUserLinks.count { it.category == selectedCategory && it.isFavorite }
            } else {
                vaultCounts.pinned
            }
            VaultFilterMenuItem(
                key = "type_pinned",
                label = "Pinned",
                icon = Icons.Default.Star,
                count = pinnedTypeCount,
                isSelected = isPinnedTypeSelected,
                isDark = isDark,
                testTag = "filter_type_pinned",
                onClick = {
                    onSelectSegment(LinkViewModel.VaultSegment.PINNED)
                    isExpanded = false
                }
            )

            HorizontalDivider(
                color = if (isDark) Color(0xFF2C283B) else Color(0xFFECE6F2),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // ---------------- Section 2: FOLDERS ----------------
            Text(
                text = "FOLDERS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            val allFoldersSorted = remember(categories) {
                categories.filter { it != "All Vault" && it != "⚡ All Vault" }.sortedWith(String.CASE_INSENSITIVE_ORDER)
            }

            // If more than 15 folders, show search bar
            if (allFoldersSorted.size > 15) {
                OutlinedTextField(
                    value = folderSearchQuery,
                    onValueChange = { folderSearchQuery = it },
                    placeholder = { Text("Search folders...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("filter_dropdown_folder_search"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = if (folderSearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { folderSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                    } else null,
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE),
                        unfocusedBorderColor = if (isDark) Color(0xFF332F42) else Color(0xFFCAC4D0),
                        focusedContainerColor = if (isDark) Color(0xFF14121B) else Color.White,
                        unfocusedContainerColor = if (isDark) Color(0xFF14121B) else Color.White
                    )
                )
            }

            val filteredFolders = remember(allFoldersSorted, folderSearchQuery) {
                if (folderSearchQuery.isBlank()) allFoldersSorted
                else allFoldersSorted.filter { it.contains(folderSearchQuery, ignoreCase = true) }
            }

            val configuration = LocalConfiguration.current
            val maxFoldersHeight = configuration.screenHeightDp.dp * 0.40f

            Column(
                modifier = if (allFoldersSorted.size > 8) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxFoldersHeight)
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                // "All Vault" row
                val isAllVaultSelected = selectedCategory == null && vaultSegment != LinkViewModel.VaultSegment.PASSWORDS
                VaultFilterMenuItem(
                    key = "folder_all_vault",
                    label = "All Vault",
                    icon = Icons.Default.ElectricBolt,
                    count = vaultCounts.all,
                    isSelected = isAllVaultSelected,
                    isDark = isDark,
                    testTag = "filter_folder_all_vault",
                    onClick = {
                        onSelectCategory(null)
                        isExpanded = false
                    }
                )

                filteredFolders.forEach { catName ->
                    val isSelected = selectedCategory == catName
                    val count = folderCounts[catName] ?: 0
                    val isPrivateVault = catName == "🔐 Private Vault"
                    val isPasswords = catName == "🔑 Passwords"
                    val isLockedCustom = lockedCustomCategories.contains(catName)
                    val isUnlockedCustom = unlockedCustomCategories.contains(catName)
                    val isCurrentlyLocked = (isPrivateVault && !isPrivateVaultUnlocked) ||
                            (isPasswords && !isPasswordsUnlocked) ||
                            (isLockedCustom && !isUnlockedCustom)

                    val folderIcon = when {
                        isPrivateVault || isPasswords || isLockedCustom -> {
                            if (isCurrentlyLocked) Icons.Default.Lock else Icons.Default.LockOpen
                        }
                        catName == "📝 Notes" -> Icons.Default.EditNote
                        else -> Icons.Default.Folder
                    }

                    val (emoji, displayLabel) = getCategoryEmojiAndLabel(catName)
                    val cleanLabel = if (displayLabel.isNotBlank()) displayLabel else catName

                    VaultFilterMenuItem(
                        key = "folder_$catName",
                        label = cleanLabel,
                        icon = folderIcon,
                        emoji = if (!isPrivateVault && !isPasswords && !isLockedCustom && emoji != "📁") emoji else null,
                        count = count,
                        isSelected = isSelected,
                        isLocked = isCurrentlyLocked,
                        isDark = isDark,
                        testTag = "filter_folder_$catName",
                        onClick = {
                            onSelectCategory(catName)
                            isExpanded = false
                        }
                    )
                }
            }

            HorizontalDivider(
                color = if (isDark) Color(0xFF2C283B) else Color(0xFFECE6F2),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // ---------------- Section 3: ACTIONS (Sticky/Pinned Footer) ----------------
            Text(
                text = "ACTIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            VaultFilterActionItem(
                label = "+ New Folder",
                icon = Icons.Default.CreateNewFolder,
                isDark = isDark,
                testTag = "filter_action_new_folder",
                onClick = {
                    isExpanded = false
                    onNewFolderClick()
                }
            )

            VaultFilterActionItem(
                label = "Manage folders",
                icon = Icons.Default.FolderOpen,
                isDark = isDark,
                testTag = "filter_action_manage_folders",
                onClick = {
                    isExpanded = false
                    onManageFoldersClick()
                }
            )
        }
    }
}

@Composable
private fun VaultFilterMenuItem(
    key: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    isSelected: Boolean,
    isDark: Boolean,
    testTag: String,
    onClick: () -> Unit,
    emoji: String? = null,
    isLocked: Boolean = false
) {
    val activeBg = if (isDark) Color(0xFF2E2442) else Color(0xFFEADDFF)
    val activeFg = if (isDark) Color(0xFFEADDFF) else Color(0xFF21005D)
    val inactiveFg = if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F)
    val tintColor = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(if (isSelected) activeBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (emoji != null) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isLocked) {
                        if (isDark) Color(0xFFFF8A80) else Color(0xFFD32F2F)
                    } else if (isSelected) {
                        tintColor
                    } else {
                        inactiveFg
                    }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isSelected) activeFg else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) {
                            if (isDark) Color(0xFF4A386D) else Color(0xFFD0BCFF)
                        } else {
                            if (isDark) Color(0xFF262233) else Color(0xFFECE6F0)
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    color = if (isSelected) activeFg else inactiveFg
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = tintColor,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun VaultFilterActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6200EE)
        )
    }
}

// -----------------------------------------------------------------------------
// Delegating Card Adapters (using precomputed LinkUiModel & hoisted constants)
// -----------------------------------------------------------------------------

@Composable
fun LinkItemCard(
    link: LinkEntity,
    isDark: Boolean,
    isEven: Boolean = false,
    onToggleFavorite: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    onEdit: (LinkEntity) -> Unit,
    onCopy: (LinkEntity) -> Unit,
    onOpen: (LinkEntity) -> Unit,
    onShare: (LinkEntity) -> Unit
) {
    val context = LocalContext.current
    val uiModel = remember(link) { LinkUiMapper.toUiModel(link, context) }
    CheapLinkItemCard(
        uiModel = uiModel,
        isDark = isDark,
        isEven = isEven,
        onToggleFavorite = onToggleFavorite,
        onDelete = onDelete,
        onEdit = onEdit,
        onCopy = onCopy,
        onOpen = onOpen,
        onShare = onShare
    )
}

@Composable
fun PasswordItemCard(
    link: LinkEntity,
    isDark: Boolean,
    isEven: Boolean = false,
    onToggleFavorite: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    onEdit: (LinkEntity) -> Unit,
    onOpen: (LinkEntity) -> Unit = {},
    onUpdatePassword: ((LinkEntity, String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val uiModel = remember(link) { LinkUiMapper.toUiModel(link, context) }
    CheapPasswordItemCard(
        uiModel = uiModel,
        isDark = isDark,
        isEven = isEven,
        onToggleFavorite = onToggleFavorite,
        onDelete = onDelete,
        onEdit = onEdit,
        onOpen = onOpen,
        onUpdatePassword = onUpdatePassword
    )
}

@Composable
fun DenseLinkItemRow(
    link: LinkEntity,
    isDark: Boolean,
    isEven: Boolean = false,
    onToggleFavorite: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    onEdit: (LinkEntity) -> Unit,
    onCopy: (LinkEntity) -> Unit,
    onOpen: (LinkEntity) -> Unit,
    onShare: (LinkEntity) -> Unit
) {
    val context = LocalContext.current
    val uiModel = remember(link) { LinkUiMapper.toUiModel(link, context) }
    CheapDenseLinkRow(
        uiModel = uiModel,
        isDark = isDark,
        onToggleFavorite = onToggleFavorite,
        onDelete = onDelete,
        onEdit = onEdit,
        onCopy = onCopy,
        onOpen = onOpen,
        onShare = onShare
    )
}

@Composable
fun DensePasswordItemRow(
    link: LinkEntity,
    isDark: Boolean,
    isEven: Boolean = false,
    onToggleFavorite: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    onEdit: (LinkEntity) -> Unit,
    onCopy: (LinkEntity) -> Unit = {},
    onOpen: (LinkEntity) -> Unit = {},
    onUpdatePassword: ((LinkEntity, String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val uiModel = remember(link) { LinkUiMapper.toUiModel(link, context) }
    CheapDensePasswordRow(
        uiModel = uiModel,
        isDark = isDark,
        onToggleFavorite = onToggleFavorite,
        onDelete = onDelete,
        onEdit = onEdit,
        onCopy = onCopy,
        onUpdatePassword = onUpdatePassword
    )
}
