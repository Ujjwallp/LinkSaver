package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.FaviconStorageManager
import com.example.data.LinkEntity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LinkCard(
    link: LinkEntity,
    isDark: Boolean,
    isEven: Boolean = false,
    onToggleFavorite: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    onEdit: (LinkEntity) -> Unit,
    onCopy: (LinkEntity) -> Unit,
    onOpen: (LinkEntity) -> Unit,
    onShare: (LinkEntity) -> Unit,
    onUpdatePassword: ((LinkEntity, String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val model = remember(
        link.id,
        link.url,
        link.title,
        link.category,
        link.description,
        link.note,
        link.timestamp,
        link.isFavorite,
        link.openCount
    ) {
        LinkUiMapper.toUiModel(link, context)
    }
    if (model.isPassword) {
        CheapPasswordItemCard(
            uiModel = model,
            isDark = isDark,
            isEven = isEven,
            onToggleFavorite = onToggleFavorite,
            onDelete = onDelete,
            onEdit = onEdit,
            onOpen = onOpen,
            onUpdatePassword = onUpdatePassword ?: { _, _, _ -> }
        )
    } else {
        CheapLinkItemCard(
            uiModel = model,
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
}

@Composable
fun EmptyStateLayout(
    isFiltering: Boolean,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFiltering) Icons.Outlined.FindInPage else Icons.Outlined.FolderZip,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isFiltering) "No Matching Links Found" else "Your Link Vault is Empty",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isFiltering) {
                    "Try refining your search keyword or clearing filters to locate your vault links."
                } else {
                    "Save text, website links, videos from standard browser share sheets or click the '+' floating button."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isFiltering) {
                Button(
                    onClick = onClearFilters,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.FilterListOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Filters", color = Color.White)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                append("Tip: ")
                            }
                            append("Share links directly from Chrome, Twitter, YouTube or other apps straight to ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Link Saver")
                            }
                            append(" for instant capture!")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DuplicateManagerDialog(
    viewModel: LinkViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scanType by remember { mutableStateOf(com.example.data.DuplicateDetector.DuplicateType.EXACT_URL) }
    var duplicateGroups by remember { mutableStateOf<List<com.example.data.DuplicateDetector.DuplicateGroup>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scanType) {
        duplicateGroups = viewModel.getDuplicateGroups(scanType)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(4.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) Color(0xFF1C1B22) else Color(0xFFF3EDF7),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Duplicate Manager Vault Icon",
                            tint = if (isDark) Color(0xFF80DEEA) else Color(0xFF00ACC1),
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Duplicate Cleanup",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isDark) Color.White else Color(0xFF1D192B)
                            )
                            Text(
                                text = "Scan and merge redundant entries",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close duplicate manager",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0xFF322E3D) else Color(0xFFE0DBE6))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    val types = listOf(
                        com.example.data.DuplicateDetector.DuplicateType.EXACT_URL to "🔗 Exact Link",
                        com.example.data.DuplicateDetector.DuplicateType.NORMAL_URL to "📂 Normalized URL",
                        com.example.data.DuplicateDetector.DuplicateType.TITLE to "🔤 Title Match",
                        com.example.data.DuplicateDetector.DuplicateType.DOMAIN to "🌐 Same Domain"
                    )
                    items(types) { (type, label) ->
                        val isSelected = scanType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { scanType = type },
                            label = { Text(label, fontSize = 12.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (isDark) Color(0xFF004E3E) else Color(0xFFD1F2EB),
                                selectedLabelColor = if (isDark) Color(0xFFE6F9F5) else Color(0xFF005A43),
                                containerColor = if (isDark) Color(0xFF2E2C3A) else Color(0xFFF0ECF4),
                                labelColor = if (isDark) Color.LightGray else Color.DarkGray
                            )
                        )
                    }
                }

                if (duplicateGroups.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDark) Color(0xFF2C2434) else Color(0xFFF3E5F5), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        val totalHits = duplicateGroups.sumOf { it.items.size }
                        Column {
                            Text(
                                text = "💡 Cleanup Suggestion (${duplicateGroups.size} groups, $totalHits items)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6750A4)
                            )
                            Text(
                                text = "Merge duplicate accounts or use 'Most Opened' / 'Keep Favorite' to automatically target valid records offline.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                if (duplicateGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "No duplicates",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Your vault is perfectly clean!",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isDark) Color.White else Color(0xFF1D192B)
                            )
                            Text(
                                "No duplicates found for current profile.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = duplicateGroups,
                            key = { it.key },
                            contentType = { "duplicate_group" }
                        ) { group ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF23222A) else Color(0xFFF1EFF4)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = group.key,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                            color = if (isDark) Color(0xFF80DEEA) else Color(0xFF00838F)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isDark) Color(0xFF37474F) else Color(0xFFECEFF1))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${group.items.size} hits",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        group.items.forEach { link ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isDark) Color(0xFF1C1A20) else Color(0xFFE8E5EA))
                                                    .padding(10.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    val categoryIconVec = when (link.category) {
                                                        "🔐 Private Vault" -> Icons.Filled.Lock
                                                        "🔑 Passwords" -> Icons.Filled.Key
                                                        "⭐ Favorites" -> Icons.Filled.Star
                                                        else -> Icons.Filled.Folder
                                                    }

                                                    val domainHost = remember(link.url) {
                                                        FaviconStorageManager.normalizeDomain(link.url)
                                                    }
                                                    val localFaviconPath = remember(domainHost) {
                                                        FaviconStorageManager.getLocalFaviconPathIfPresent(context, domainHost)
                                                    }

                                                    val fallbackPainterVec = rememberVectorPainter(categoryIconVec)
                                                    if (!localFaviconPath.isNullOrEmpty()) {
                                                        val req = remember(localFaviconPath) {
                                                            ImageRequest.Builder(context)
                                                                .data(File(localFaviconPath))
                                                                .size(64, 64)
                                                                .crossfade(false)
                                                                .build()
                                                        }
                                                        AsyncImage(
                                                            model = req,
                                                            contentDescription = "Logo",
                                                            modifier = Modifier
                                                                .size(22.dp)
                                                                .clip(RoundedCornerShape(4.dp)),
                                                            placeholder = fallbackPainterVec,
                                                            error = fallbackPainterVec,
                                                            fallback = fallbackPainterVec
                                                        )
                                                    } else {
                                                        Icon(categoryIconVec, contentDescription = "", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                                    }

                                                    Text(
                                                        text = link.title.ifBlank { "Untitled link" },
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isDark) Color.White else Color.Black,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                Text(
                                                    text = link.url,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                                if (link.note.isNotBlank()) {
                                                    val displayNote = if (link.description == "SECURE_PASSWORD") {
                                                        val parts = link.note.split("|||")
                                                        "User: ${parts.getOrNull(0) ?: ""}"
                                                    } else {
                                                        link.note
                                                    }
                                                    Text(
                                                        text = "Info: $displayNote",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isDark) Color(0xFFB0BEC5) else Color(0xFF455A64),
                                                        modifier = Modifier.padding(top = 4.dp),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "Filter & Keep Record By:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isDark) Color(0xFF80DEEA) else Color(0xFF006064),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    viewModel.resolveDuplicatesInGroup(group, "KEEP_OLDEST")
                                                    Toast.makeText(context, "Retained oldest entry", Toast.LENGTH_SHORT).show()
                                                    coroutineScope.launch { duplicateGroups = viewModel.getDuplicateGroups(scanType) }
                                                },
                                                label = { Text("Oldest 📅", fontSize = 11.sp) }
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    viewModel.resolveDuplicatesInGroup(group, "KEEP_NEWEST")
                                                    Toast.makeText(context, "Retained newest entry", Toast.LENGTH_SHORT).show()
                                                    coroutineScope.launch { duplicateGroups = viewModel.getDuplicateGroups(scanType) }
                                                },
                                                label = { Text("Newest 🕐", fontSize = 11.sp) }
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    viewModel.resolveDuplicatesInGroup(group, "KEEP_FAVORITE")
                                                    Toast.makeText(context, "Retained favorite match", Toast.LENGTH_SHORT).show()
                                                    coroutineScope.launch { duplicateGroups = viewModel.getDuplicateGroups(scanType) }
                                                },
                                                label = { Text("Favorite ⭐", fontSize = 11.sp) }
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    viewModel.resolveDuplicatesInGroup(group, "KEEP_MOST_OPENED")
                                                    Toast.makeText(context, "Retained most opened item", Toast.LENGTH_SHORT).show()
                                                    coroutineScope.launch { duplicateGroups = viewModel.getDuplicateGroups(scanType) }
                                                },
                                                label = { Text("Most Opened 🏆", fontSize = 11.sp) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.resolveDuplicatesInGroup(group, "DELETE_DUPLICATES")
                                                Toast.makeText(context, "Cleaned duplicates completely", Toast.LENGTH_SHORT).show()
                                                coroutineScope.launch { duplicateGroups = viewModel.getDuplicateGroups(scanType) }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            contentPadding = PaddingValues(10.dp)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete All", fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.resolveDuplicatesInGroup(group, "MERGE")
                                                Toast.makeText(context, "Merged notes & contents", Toast.LENGTH_SHORT).show()
                                                coroutineScope.launch { duplicateGroups = viewModel.getDuplicateGroups(scanType) }
                                            },
                                            modifier = Modifier.weight(1.2f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isDark) Color(0xFF00ACC1) else Color(0xFF00838F),
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(10.dp)
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = "", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Merge", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

@Composable
fun UniversalSavePanel(
    viewModel: LinkViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val incomingText by viewModel.incomingSharedText.collectAsStateWithLifecycle()
    if (incomingText.isNullOrBlank()) return

    val text = incomingText!!
    val (extractedUrl, extractedTitle) = remember(text) { viewModel.extractUrlAndTitle(text) }

    val detectedCategory = remember(text, extractedUrl) {
        val urlLower = extractedUrl.lowercase()
        val textLower = text.lowercase()
        when {
            urlLower.contains("youtube") || urlLower.contains("youtu.be") -> "📺 Videos"
            urlLower.contains("github") || urlLower.contains("gitlab") -> "💻 Coding"
            urlLower.contains("amazon") || urlLower.contains("ebay") || urlLower.contains("shopify") -> "🛍️ Shopping"
            textLower.contains("password") || textLower.contains("login") || textLower.contains("credential") || textLower.contains("passphrase") -> "🔑 Passwords"
            extractedUrl.isBlank() -> "📝 Notes"
            else -> "📁 Uncategorized"
        }
    }

    val isProbablyPassword = detectedCategory == "🔑 Passwords"
    val isProbablyNote = detectedCategory == "📝 Notes"

    var title by remember { mutableStateOf(if (isProbablyNote) "" else (if (extractedTitle.isNotBlank()) extractedTitle else extractedUrl)) }
    var url by remember { mutableStateOf(extractedUrl) }
    var category by remember { mutableStateOf(detectedCategory) }
    var noteText by remember { mutableStateOf(if (isProbablyNote) text else "") }

    LaunchedEffect(category) {
        if (category == "📝 Notes") {
            if (title == extractedTitle || title == extractedUrl || title == "My Text Snippet") {
                title = ""
            }
            if (noteText.isBlank()) {
                noteText = text
            }
        }
    }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf(if (isProbablyPassword && extractedUrl.isBlank()) text else "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1E1B24) else Color(0xFFF9F6FC)
            ),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = "Universal Save Panel Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Universal Save Panel ⚡",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isDark) Color.White else Color(0xFF1D192B)
                        )
                        Text(
                            text = "Smart content detection active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0xFF2E2C38) else Color(0xFFECE6F0))

                Box(
                    modifier = Modifier
                        .background(if (isDark) Color(0xFF322749) else Color(0xFFF3E8FF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Source:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            text = if (extractedUrl.isNotBlank()) "Webpage / URL" else "Text / Notes Snippet",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4)
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (!isProbablyNote && url.isNotBlank()) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL Link") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (category == "🔑 Passwords") {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username / Email") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password Value") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(if (isProbablyNote) "Note Content" else "Append Note / Tags") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (isProbablyNote) 3 else 1,
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Select Category:", style = MaterialTheme.typography.labelMedium)
                val folders = listOf("📁 Uncategorized", "🔑 Passwords", "📝 Notes", "🔐 Private Vault")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(folders) { folder ->
                        val isSel = category == folder
                        FilterChip(
                            selected = isSel,
                            onClick = { category = folder },
                            label = { Text(folder, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val finalUrl = if (category == "🔑 Passwords") {
                                if (url.isNotBlank()) url else "https://localhost"
                            } else {
                                url
                            }
                            val finalNote = if (category == "🔑 Passwords") {
                                "${username.trim()}|||${password}"
                            } else {
                                noteText
                            }
                            val finalDesc = if (category == "🔑 Passwords") "SECURE_PASSWORD" else ""

                            viewModel.addLink(
                                url = finalUrl,
                                title = title,
                                category = category,
                                note = finalNote,
                                description = finalDesc
                            ) {
                                Toast.makeText(context, "Saved Successfully!", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Instantly ✓")
                    }
                }
            }
        }
    }
}

@Composable
fun ClipboardNotificationPrompt(
    detectedText: String,
    viewModel: LinkViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF282531) else Color(0xFFF3EAFC)
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Clipboard Logo", tint = MaterialTheme.colorScheme.primary)
                Text("Copied Content Detected 📋", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Text("Would you like to save this copied text from your clipboard to your Link Saver vault?", style = MaterialTheme.typography.bodySmall)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(detectedText, color = Color.LightGray, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    viewModel.setClipboardNeverAsk(true)
                    onDismiss()
                }) {
                    Text("Never Ask", color = Color.Gray, fontSize = 11.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("Ignore", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = {
                        viewModel.setIncomingSharedText(detectedText)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Save?", fontSize = 11.sp)
                }
            }
        }
    }
}

fun groupItemsByDate(items: List<LinkEntity>): Map<String, List<LinkEntity>> {
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val startOfYesterday = startOfToday - 24 * 60 * 60 * 1000L
    val startOfThisWeek = startOfToday - 7 * 24 * 60 * 60 * 1000L
    val startOfLastWeek = startOfToday - 14 * 24 * 60 * 60 * 1000L
    val startOfThisMonth = startOfToday - 30 * 24 * 60 * 60 * 1000L

    val groups = LinkedHashMap<String, MutableList<LinkEntity>>()
    groups["Today"] = mutableListOf()
    groups["Yesterday"] = mutableListOf()
    groups["This Week"] = mutableListOf()
    groups["Last Week"] = mutableListOf()
    groups["This Month"] = mutableListOf()
    groups["Older"] = mutableListOf()

    items.forEach { item ->
        val t = item.timestamp
        when {
            t >= startOfToday -> groups["Today"]?.add(item)
            t >= startOfYesterday -> groups["Yesterday"]?.add(item)
            t >= startOfThisWeek -> groups["This Week"]?.add(item)
            t >= startOfLastWeek -> groups["Last Week"]?.add(item)
            t >= startOfThisMonth -> groups["This Month"]?.add(item)
            else -> groups["Older"]?.add(item)
        }
    }

    return groups.filter { it.value.isNotEmpty() }
}

@Composable
fun DailyShelfDashboardView(
    viewModel: LinkViewModel,
    isDark: Boolean,
    isPasswordsUnlocked: Boolean,
    isPrivateVaultUnlocked: Boolean,
    onUnlockPasswords: () -> Unit,
    onUnlockVault: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val allLinks by viewModel.links.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var sortNewestFirst by remember { mutableStateOf(true) }

    val processedItems = remember(allLinks, searchQuery, sortNewestFirst) {
        var list = allLinks
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.url.contains(searchQuery, ignoreCase = true) ||
                it.note.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }
        }
        if (sortNewestFirst) {
            list.sortedByDescending { it.timestamp }
        } else {
            list.sortedBy { it.timestamp }
        }
    }

    val continueRecentItem = remember(allLinks) {
        allLinks.maxByOrNull { it.timestamp }
    }

    val groups = remember(processedItems, isPasswordsUnlocked, isPrivateVaultUnlocked) {
        groupItemsByDate(processedItems).mapValues { (_, entities) ->
            entities.map { entity ->
                LinkUiMapper.toUiModel(
                    entity = entity,
                    context = context,
                    isVaultUnlocked = isPasswordsUnlocked || isPrivateVaultUnlocked
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("daily_shelf_container"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (continueRecentItem != null) {
            item(key = "shelf_continue_recent", contentType = "shelf_banner") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF2C233D) else Color(0xFFF3E5F5)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isDark) Color(0xFFE040FB) else Color(0xFFBA68C8))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.HourglassEmpty, contentDescription = "", tint = if (isDark) Color(0xFFEA80FC) else Color(0xFF8E24AA), modifier = Modifier.size(14.dp))
                                Text("Continue Recent Activity 🔥", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFEA80FC) else Color(0xFF8E24AA))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = continueRecentItem.title.ifBlank { continueRecentItem.url },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val formattedTime = remember(continueRecentItem.timestamp) {
                                SimpleDateFormat("hh:mm a • MMM d", Locale.getDefault()).format(Date(continueRecentItem.timestamp))
                            }
                            Text(formattedTime, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                try {
                                    viewModel.incrementOpenCount(continueRecentItem.id)
                                    val finalWebUrl = if (!continueRecentItem.url.startsWith("http://") && !continueRecentItem.url.startsWith("https://")) {
                                        "https://${continueRecentItem.url}"
                                    } else {
                                        continueRecentItem.url
                                    }
                                    val webpage = Uri.parse(finalWebUrl)
                                    val intent = Intent(Intent.ACTION_VIEW, webpage)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Text("Resume", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item(key = "shelf_controls", contentType = "shelf_controls") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Shelf Items (${processedItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF1D192B)
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0xFF2C2A34) else Color(0xFFE8E4EB))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clickable { sortNewestFirst = !sortNewestFirst },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (sortNewestFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = "Toggle Sort",
                        modifier = Modifier.size(14.dp)
                    )
                    Text(if (sortNewestFirst) "Newest First" else "Oldest First", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (groups.isEmpty()) {
            item(key = "shelf_empty", contentType = "shelf_empty") {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No items found on shelf for that search query.", color = Color.Gray)
                }
            }
        } else {
            groups.forEach { (heading, list) ->
                item(key = "shelf_heading_$heading", contentType = "shelf_header") {
                    Text(
                        heading,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFFFB8D1) else Color(0xFF6750A4),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(
                    items = list,
                    key = { it.id },
                    contentType = { uiModel ->
                        val isLockedPassword = uiModel.categoryLabel == "🔑 Passwords" && !isPasswordsUnlocked
                        val isLockedVault = uiModel.categoryLabel == "🔐 Private Vault" && !isPrivateVaultUnlocked
                        when {
                            isLockedPassword || isLockedVault -> "shelf_locked_item"
                            uiModel.isPassword -> "shelf_password_item"
                            uiModel.categoryLabel == "📝 Notes" -> "shelf_note_item"
                            else -> "shelf_link_item"
                        }
                    }
                ) { uiModel ->
                    val item = uiModel.entity
                    val isLockedPassword = uiModel.categoryLabel == "🔑 Passwords" && !isPasswordsUnlocked
                    val isLockedVault = uiModel.categoryLabel == "🔐 Private Vault" && !isPrivateVaultUnlocked
                    val isProtected = isLockedPassword || isLockedVault

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E1C24) else Color(0xFFFFFFFF)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val icon = when (uiModel.categoryLabel) {
                                        "🔑 Passwords" -> "🔑"
                                        "🔐 Private Vault" -> "🔐"
                                        "📝 Notes" -> "📝"
                                        else -> "📁"
                                    }
                                    Text(icon)
                                    Text(
                                        text = if (isProtected) "•••••••••••• (Fingerprint Secure)" else uiModel.displayTitle,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (isDark) Color(0xFF2E2A34) else Color(0xFFECE6F0), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(uiModel.categoryLabel, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (isProtected) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isLockedPassword) onUnlockPasswords() else onUnlockVault()
                                        }
                                        .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = "", tint = Color.Red, modifier = Modifier.size(12.dp))
                                    Text("Fingerprint scan required. Click to decrypt securely 🔒", style = MaterialTheme.typography.labelSmall, color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                if (uiModel.categoryLabel == "📝 Notes") {
                                    Text(
                                        text = uiModel.notePreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) Color.LightGray else Color(0xFF49454F),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else if (uiModel.isPassword) {
                                    Text("Username: ${uiModel.displayUsername}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                } else {
                                    Text(uiModel.displayUrl, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (!isProtected) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val copyText = if (uiModel.categoryLabel == "📝 Notes") uiModel.notePreview else item.url
                                            clipboardManager.setText(buildAnnotatedString { append(copyText) })
                                            Toast.makeText(context, "Copied content ✓", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.CopyAll, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    }
                                    if (uiModel.categoryLabel != "📝 Notes") {
                                        IconButton(
                                            onClick = {
                                                try {
                                                    viewModel.incrementOpenCount(uiModel.id)
                                                    val webpage = Uri.parse(item.url)
                                                    val intent = Intent(Intent.ACTION_VIEW, webpage)
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = "Open", modifier = Modifier.size(16.dp))
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
