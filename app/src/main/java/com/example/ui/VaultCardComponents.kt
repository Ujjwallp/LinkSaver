package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.LinkEntity
import com.example.security.RecordEncryption
import kotlinx.coroutines.delay

// -----------------------------------------------------------------------------
// Hoisted Top-Level Shapes, Borders, Colors & TextStyles (Zero Per-Card Allocations)
// -----------------------------------------------------------------------------

val CardShape16 = RoundedCornerShape(16.dp)
val CardShape24 = RoundedCornerShape(22.dp)
val CardShape28 = RoundedCornerShape(26.dp)
val BottomSheetShape28 = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val DenseRowShape14 = RoundedCornerShape(13.dp)
val PillShape12 = RoundedCornerShape(10.dp)
val IconBoxShape10 = RoundedCornerShape(11.dp)
val IconBoxShape8 = RoundedCornerShape(9.dp)
val FaviconClipShape4 = RoundedCornerShape(4.dp)
val NoteBoxShape10 = RoundedCornerShape(10.dp)
val ButtonShape8 = RoundedCornerShape(9.dp)

private val DarkCardEvenBg = Color(0xFF181522)
private val DarkCardOddBg = Color(0xFF151822)
private val LightCardEvenBg = Color(0xFFFFFFFF)
private val LightCardOddBg = Color(0xFFFAF8FF)

private val DarkPrimaryText = Color(0xFFF2ECFF)
private val LightPrimaryText = Color(0xFF1A1626)
private val DarkSecondaryText = Color(0xFFA9A2B8)
private val LightSecondaryText = Color(0xFF5E566B)

private val DarkCardBorderStroke = BorderStroke(1.dp, Color(0xFF262134))
private val LightCardBorderStroke = BorderStroke(1.dp, Color(0xFFE8E2F2))

private val DarkPassCardBg = Color(0xFF1C1612)
private val LightPassCardBg = Color(0xFFFFFBF7)
private val DarkPassBorderStroke = BorderStroke(1.dp, Color(0xFF47321B))
private val LightPassBorderStroke = BorderStroke(1.dp, Color(0xFFFFE0B2))
private val DarkDensePassBorderStroke = BorderStroke(1.dp, Color(0xFF47321B))
private val LightDensePassBorderStroke = BorderStroke(1.dp, Color(0xFFFFE0B2))

private val AvatarInitialTextStyle = TextStyle(
    fontWeight = FontWeight.ExtraBold,
    fontSize = 14.sp
)
private val CardTitleTextStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.5.sp,
    lineHeight = 19.sp
)
private val CardSubtitleMetaTextStyle = TextStyle(
    fontSize = 11.5.sp,
    fontWeight = FontWeight.Medium
)
private val CardNoteTextStyle = TextStyle(
    fontSize = 11.5.sp,
    lineHeight = 15.sp
)
private val CardOpenButtonTextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 11.5.sp
)
private val DenseTitleTextStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.5.sp
)
private val DenseUrlTextStyle = TextStyle(
    fontSize = 11.sp
)
private val PassTitleTextStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.5.sp,
    lineHeight = 19.sp
)
private val PassSectionLabelTextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.4.sp,
    fontSize = 9.sp
)
private val PassValueTextStyle = TextStyle(
    fontWeight = FontWeight.Medium,
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp
)
private val PassMaskedValueTextStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    letterSpacing = 2.sp
)
private val PassSmallBtnTextStyle = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold
)
private val HeaderDateTitleTextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 12.5.sp,
    letterSpacing = 0.3.sp
)
private val HeaderDateCountTextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp
)
private val HeaderCountShape10 = RoundedCornerShape(10.dp)

// -----------------------------------------------------------------------------
// New LinkVault Brand Logo Badge (Used in App Header, Lock Screen & Previews)
// -----------------------------------------------------------------------------

@Composable
fun LinkVaultLogoBadge(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 11.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF0F0C1B))
            .border(BorderStroke(1.dp, Color(0xFF6542C9)), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize(0.82f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            // Hexagonal Shield Backdrop
            val shieldPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, h * 0.06f)
                lineTo(w * 0.90f, h * 0.25f)
                lineTo(w * 0.90f, h * 0.62f)
                quadraticTo(w * 0.90f, h * 0.84f, cx, h * 0.95f)
                quadraticTo(w * 0.10f, h * 0.84f, w * 0.10f, h * 0.62f)
                lineTo(w * 0.10f, h * 0.25f)
                close()
            }
            drawPath(
                path = shieldPath,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(Color(0xFF7C4DFF), Color(0xFF311B92))
                )
            )

            // Inner Dark Vault Core
            val innerPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, h * 0.14f)
                lineTo(w * 0.82f, h * 0.30f)
                lineTo(w * 0.82f, h * 0.60f)
                quadraticTo(w * 0.82f, h * 0.78f, cx, h * 0.87f)
                quadraticTo(w * 0.18f, h * 0.78f, w * 0.18f, h * 0.60f)
                lineTo(w * 0.18f, h * 0.30f)
                close()
            }
            drawPath(
                path = innerPath,
                color = Color(0xFF141026)
            )

            // Interlocking Cyber Chain Links (Electric Cyan & Glowing Violet)
            val strokeW = w * 0.095f
            drawRoundRect(
                color = Color(0xFF00E5FF),
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.27f, h * 0.32f),
                size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.22f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.11f, w * 0.11f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
            )
            drawRoundRect(
                color = Color(0xFFD0BCFF),
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.41f, h * 0.44f),
                size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.22f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.11f, w * 0.11f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
            )

            // Golden Keyhole Core Dot
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = w * 0.075f,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Precomputed Date Header Row for LazyColumn (contentType = "header")
// -----------------------------------------------------------------------------

@Composable
fun VaultDateHeaderRow(
    row: VaultListRow.HeaderRow,
    isDark: Boolean
) {
    val accentColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF5E35B1)
    val badgeBg = if (isDark) Color(0xFF241F30) else Color(0xFFEDE7F6)
    val badgeTextColor = if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 2.dp, end = 2.dp)
    ) {
        Text(
            text = row.dateHeader,
            style = HeaderDateTitleTextStyle,
            color = accentColor
        )
        Text(
            text = row.countText,
            style = HeaderDateCountTextStyle,
            color = badgeTextColor,
            modifier = Modifier
                .background(badgeBg, HeaderCountShape10)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// Sleek 2-Row Progressive Disclosure Link Card (Zero Clutter, High Density)
// -----------------------------------------------------------------------------

@Composable
fun CheapLinkItemCard(
    uiModel: LinkUiModel,
    isDark: Boolean,
    isEven: Boolean = false,
    onToggleFavorite: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    onEdit: (LinkEntity) -> Unit,
    onCopy: (LinkEntity) -> Unit,
    onOpen: (LinkEntity) -> Unit,
    onShare: (LinkEntity) -> Unit
) {
    val cardBg = if (isDark) {
        if (isEven) DarkCardEvenBg else DarkCardOddBg
    } else {
        if (isEven) LightCardEvenBg else LightCardOddBg
    }
    val primaryTextColor = if (isDark) DarkPrimaryText else LightPrimaryText
    val secondaryTextColor = if (isDark) DarkSecondaryText else LightSecondaryText
    val borderStroke = if (isDark) DarkCardBorderStroke else LightCardBorderStroke

    var showActions by remember(uiModel.id) { mutableStateOf(false) }

    val onOpenClick = remember(uiModel.id, uiModel.entity, onOpen) { { onOpen(uiModel.entity) } }
    val onFavoriteClick = remember(uiModel.id, uiModel.entity, onToggleFavorite) { { onToggleFavorite(uiModel.entity) } }
    val onCopyClick = remember(uiModel.id, uiModel.entity, onCopy) { { onCopy(uiModel.entity) } }
    val onEditClick = remember(uiModel.id, uiModel.entity, onEdit) { { onEdit(uiModel.entity) } }
    val onDeleteClick = remember(uiModel.id, uiModel.entity, onDelete) { { onDelete(uiModel.entity) } }
    val onShareClick = remember(uiModel.id, uiModel.entity, onShare) { { onShare(uiModel.entity) } }

    val domainColor = Color(uiModel.domainColorArgb)
    val cleanCategory = remember(uiModel.categoryLabel) {
        uiModel.categoryLabel.substringAfter(" ").ifBlank { uiModel.categoryLabel }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("link_card_${uiModel.id}")
            .clip(CardShape16)
            .background(cardBg)
            .border(borderStroke, CardShape16)
            .clickable { showActions = !showActions }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Row 1 & 2: Monogram Avatar + [Title / Domain · Category · ShortDate] + [Pin + Open ↗]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Modern Monogram Avatar (Soft tinted squircle, no extra inner border)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(IconBoxShape10)
                    .background(domainColor.copy(alpha = if (isDark) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiModel.domainInitial,
                    style = AvatarInitialTextStyle,
                    color = if (isDark) Color.White else domainColor
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Center 2-Line Stack: Title + Unified Metadata Line
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiModel.displayTitle,
                    style = CardTitleTextStyle,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = uiModel.domainBadgeText,
                        style = CardSubtitleMetaTextStyle,
                        color = if (isDark) Color(0xFFD0BCFF) else Color(0xFF5E35B1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "·",
                        style = CardSubtitleMetaTextStyle,
                        color = secondaryTextColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = cleanCategory,
                        style = CardSubtitleMetaTextStyle,
                        color = secondaryTextColor,
                        maxLines = 1
                    )
                    Text(
                        text = "·",
                        style = CardSubtitleMetaTextStyle,
                        color = secondaryTextColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = uiModel.shortDateText,
                        style = CardSubtitleMetaTextStyle,
                        color = secondaryTextColor.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right Quick Actions: Pin Star + Primary Open Pill + Expand Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(IconBoxShape8)
                        .clickable(onClick = onFavoriteClick)
                        .testTag("favorite_toggle_${uiModel.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiModel.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Toggle Favorite",
                        modifier = Modifier.size(17.dp),
                        tint = if (uiModel.isFavorite) {
                            if (isDark) Color(0xFFFFD54F) else Color(0xFFF57C00)
                        } else {
                            secondaryTextColor.copy(alpha = 0.55f)
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(PillShape12)
                        .background(if (isDark) Color(0xFF33264D) else Color(0xFFEDE7F6))
                        .clickable(onClick = onOpenClick)
                        .padding(horizontal = 10.dp)
                        .testTag("open_button_${uiModel.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open ↗",
                        style = CardOpenButtonTextStyle,
                        color = if (isDark) Color(0xFFEADDFF) else Color(0xFF4A148C)
                    )
                }
            }
        }

        // Soft Tonal Note Preview (only when present, no inner border clutter)
        if (uiModel.hasNotePreview) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NoteBoxShape10)
                    .background(if (isDark) Color(0xFF211D2E) else Color(0xFFF3EFFA))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Notes,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = secondaryTextColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = uiModel.notePreview,
                    style = CardNoteTextStyle,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Progressive Disclosure Action Bar (Revealed on Card Tap)
        AnimatedVisibility(
            visible = showActions,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = if (isDark) Color(0x16FFFFFF) else Color(0x10000000))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiModel.displayUrl,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp),
                        color = secondaryTextColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionChipButton(
                            icon = Icons.Default.ContentCopy,
                            label = "Copy",
                            isDark = isDark,
                            testTag = "copy_button_${uiModel.id}",
                            onClick = onCopyClick
                        )
                        ActionChipButton(
                            icon = Icons.Default.Share,
                            label = "Share",
                            isDark = isDark,
                            testTag = "share_button_${uiModel.id}",
                            onClick = onShareClick
                        )
                        ActionChipButton(
                            icon = Icons.Outlined.Edit,
                            label = "Edit",
                            isDark = isDark,
                            testTag = "edit_button_${uiModel.id}",
                            onClick = onEditClick
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(IconBoxShape8)
                                .background(if (isDark) Color(0xFF311B1E) else Color(0xFFFFEBEE))
                                .clickable(onClick = onDeleteClick)
                                .testTag("delete_button_${uiModel.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete link",
                                modifier = Modifier.size(14.dp),
                                tint = if (isDark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDark: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(IconBoxShape8)
            .background(if (isDark) Color(0xFF252132) else Color(0xFFEEE9F7))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isDark) DarkPrimaryText else LightPrimaryText
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) DarkPrimaryText else LightPrimaryText
        )
    }
}

// -----------------------------------------------------------------------------
// Sleek 2-Row Progressive Disclosure Password Card (On-Demand Secret Decryption)
// -----------------------------------------------------------------------------

@Composable
fun CheapPasswordItemCard(
    uiModel: LinkUiModel,
    isDark: Boolean,
    isEven: Boolean = false,
    onToggleFavorite: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    onEdit: (LinkEntity) -> Unit,
    onOpen: (LinkEntity) -> Unit,
    onUpdatePassword: ((LinkEntity, String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { findMainActivity(context) }
    val coroutineScope = rememberCoroutineScope()
    val link = uiModel.entity
    val username = uiModel.usernamePreview

    val onFavoriteClick = remember(uiModel.id, link, onToggleFavorite) { { onToggleFavorite(link) } }
    val onOpenClick = remember(uiModel.id, link, onOpen) { { onOpen(link) } }
    val onEditClick = remember(uiModel.id, link, onEdit) { { onEdit(link) } }
    val onDeleteClick = remember(uiModel.id, link, onDelete) { { onDelete(link) } }

    // Sensitive secret is NEVER held in LinkUiModel; decrypted on demand only when revealed/updated
    var revealedPassword by remember(link.id, link.note) { mutableStateOf<String?>(null) }
    var autoMaskCountdown by remember { mutableStateOf(0) }
    var showQuickUpdateDialog by remember { mutableStateOf(false) }
    var isExpanded by remember(uiModel.id) { mutableStateOf(false) }

    fun decryptSecretOnDemand(): String {
        val full = RecordEncryption.decrypt(context, link.note, link.id, link.userEmail)
        return full.substringAfter("|||", "")
    }

    fun triggerCopyPassword() {
        if (activity?.isBiometricHardwareAvailable() == true) {
            activity.triggerBiometricPrompt(
                onSuccess = {
                    val secret = decryptSecretOnDemand()
                    copyWithAutoClear(
                        context = context,
                        text = secret,
                        label = "Password",
                        scope = coroutineScope,
                        onCleared = {
                            Toast.makeText(context, "Clipboard auto-cleared ⏱️", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Toast.makeText(context, "Password copied! Auto-clears in 30s ⏱️", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    Toast.makeText(context, "Biometric failed: $err", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            val secret = decryptSecretOnDemand()
            copyWithAutoClear(
                context = context,
                text = secret,
                label = "Password",
                scope = coroutineScope,
                onCleared = {
                    Toast.makeText(context, "Clipboard auto-cleared ⏱️", Toast.LENGTH_SHORT).show()
                }
            )
            Toast.makeText(context, "Password copied! Auto-clears in 30s ⏱️", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(revealedPassword) {
        if (revealedPassword != null) {
            autoMaskCountdown = 15
            while (autoMaskCountdown > 0) {
                delay(1000L)
                autoMaskCountdown--
            }
            revealedPassword = null
        }
    }

    val cardBg = if (isDark) DarkPassCardBg else LightPassCardBg
    val securityBorder = if (isDark) DarkPassBorderStroke else LightPassBorderStroke

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("password_card_${uiModel.id}")
            .clip(CardShape16)
            .background(cardBg)
            .border(securityBorder, CardShape16)
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Compact 2-Row Header: Key Avatar + [Title / Username · Domain] + [Copy User | Copy Pass | Details]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(IconBoxShape10)
                    .background(if (isDark) Color(0xFF332211) else Color(0xFFFFECB3)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = "Password Credential",
                    modifier = Modifier.size(18.dp),
                    tint = if (isDark) Color(0xFFFFD54F) else Color(0xFFE65100)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uiModel.title,
                        style = PassTitleTextStyle,
                        color = if (isDark) Color(0xFFFFF3E0) else Color(0xFF2D1B12),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (uiModel.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(13.dp),
                            tint = Color(0xFFFFB74D)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = uiModel.displayUsername,
                        style = CardSubtitleMetaTextStyle,
                        color = if (isDark) Color(0xFFFFCC80) else Color(0xFF6D4C41),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (uiModel.hasAssociatedDomain) {
                        Text(
                            text = "·",
                            style = CardSubtitleMetaTextStyle,
                            color = if (isDark) Color(0x88FFCC80) else Color(0x886D4C41)
                        )
                        Text(
                            text = uiModel.domainHost,
                            style = CardSubtitleMetaTextStyle,
                            color = if (isDark) Color(0xFFA1887F) else Color(0xFF8D6E63),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 1-Tap Quick Copy Buttons (Username + Password) + Details Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1-Tap Copy Username icon button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(ButtonShape8)
                        .background(if (isDark) Color(0xFF2D2016) else Color(0xFFFFF3E0))
                        .clickable {
                            copySecureTextToClipboard(context, username, isSensitive = false, label = "Username")
                            Toast.makeText(context, "Username copied! 👤", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Copy Username",
                        modifier = Modifier.size(15.dp),
                        tint = if (isDark) Color(0xFFFFCC80) else Color(0xFF5D4037)
                    )
                }

                // 1-Tap Copy Password pill button
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(PillShape12)
                        .background(if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100))
                        .clickable { triggerCopyPassword() }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (isDark) Color(0xFF2D1B12) else Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copy",
                            style = PassSmallBtnTextStyle,
                            color = if (isDark) Color(0xFF2D1B12) else Color.White
                        )
                    }
                }
            }
        }

        // Progressive Disclosure Details Drawer (Tap card to reveal secret, linked apps & edit/delete)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val passwordVisible = revealedPassword != null
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else Color(0x14000000))
                Spacer(modifier = Modifier.height(8.dp))

                // Inline Password Reveal Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NoteBoxShape10)
                        .background(if (isDark) Color(0xFF281E17) else Color(0xFFFFF3E0))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PASSWORD",
                                style = PassSectionLabelTextStyle,
                                color = if (isDark) Color(0xFFFFB74D) else Color(0xFF8D6E63)
                            )
                            if (passwordVisible) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${autoMaskCountdown}s",
                                    style = PassSectionLabelTextStyle,
                                    color = Color(0xFFE53935)
                                )
                            }
                        }
                        Text(
                            text = revealedPassword ?: "••••••••••••",
                            style = if (passwordVisible) PassValueTextStyle else PassMaskedValueTextStyle,
                            color = if (isDark) Color(0xFFFFE0B2) else Color(0xFF212121),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            if (passwordVisible) {
                                revealedPassword = null
                            } else {
                                if (activity?.isBiometricHardwareAvailable() == true) {
                                    activity.triggerBiometricPrompt(
                                        onSuccess = {
                                            revealedPassword = decryptSecretOnDemand()
                                            Toast.makeText(context, "Password revealed (masks in 15s) 🔓", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, "Biometric failed: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    revealedPassword = decryptSecretOnDemand()
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle Password Visibility",
                            modifier = Modifier.size(16.dp),
                            tint = if (isDark) Color(0xFFFFB74D) else Color(0xFF8D6E63)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Secondary Actions Row: Pin, Website Launch, Linked Apps / Update, Edit, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(IconBoxShape8)
                                .background(if (isDark) Color(0xFF2D2016) else Color(0xFFFFF3E0))
                                .clickable(onClick = onFavoriteClick)
                                .testTag("favorite_toggle_${uiModel.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (uiModel.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Toggle Favorite",
                                modifier = Modifier.size(15.dp),
                                tint = if (uiModel.isFavorite) Color(0xFFFFB74D) else Color.Gray
                            )
                        }

                        if (uiModel.hasAssociatedDomain) {
                            ActionChipButton(
                                icon = Icons.AutoMirrored.Filled.OpenInNew,
                                label = uiModel.domainHost,
                                isDark = isDark,
                                testTag = "open_domain_${uiModel.id}",
                                onClick = onOpenClick
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Linked Apps & Update Dialog Trigger
                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(PillShape12)
                                .background(if (isDark) Color(0xFF382310) else Color(0xFFFFE082))
                                .clickable {
                                    if (activity?.isBiometricHardwareAvailable() == true) {
                                        activity.triggerBiometricPrompt(
                                            onSuccess = { showQuickUpdateDialog = true },
                                            onError = { err ->
                                                Toast.makeText(context, "Authentication failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        showQuickUpdateDialog = true
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (isDark) Color(0xFFFFD54F) else Color(0xFF5D4037)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Links & History",
                                style = CardOpenButtonTextStyle,
                                color = if (isDark) Color(0xFFFFD54F) else Color(0xFF5D4037)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(IconBoxShape8)
                                .background(if (isDark) Color(0xFF2D2016) else Color(0xFFFFF3E0))
                                .clickable(onClick = onEditClick)
                                .testTag("edit_button_${uiModel.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit Credentials",
                                modifier = Modifier.size(14.dp),
                                tint = if (isDark) Color(0xFFFFB74D) else Color(0xFF8D6E63)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(IconBoxShape8)
                                .background(if (isDark) Color(0xFF311B1E) else Color(0xFFFFEBEE))
                                .clickable(onClick = onDeleteClick)
                                .testTag("delete_button_${uiModel.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete Password",
                                modifier = Modifier.size(14.dp),
                                tint = if (isDark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQuickUpdateDialog) {
        val metadataStore = remember(context) { com.example.autofill.CredentialMetadataStore(context) }
        val linkManager = remember(context) { com.example.autofill.AppWebsiteLinkManager(context, metadataStore) }
        var credMeta by remember(link.id) { mutableStateOf(metadataStore.getMetadata(link.id)) }
        val initialSecret = remember(link.id) { decryptSecretOnDemand() }
        var updateUsernameText by remember { mutableStateOf(username) }
        var updatePasswordText by remember { mutableStateOf(initialSecret) }
        var updatePassVisible by remember { mutableStateOf(false) }
        var newLinkedAppInput by remember { mutableStateOf("") }
        var newLinkedWebsiteInput by remember { mutableStateOf("") }

        val effectivePkg = credMeta.appPackage.ifBlank {
            if (link.url.startsWith("app://", ignoreCase = true)) {
                com.example.autofill.AppIdentityVerifier.normalizePackageName(link.url)
            } else ""
        }
        val effectiveDomain = if (!link.url.startsWith("app://", ignoreCase = true)) {
            com.example.autofill.AppIdentityVerifier.normalizeRegistrableDomain(link.url)
        } else ""

        val catalogSuggestions = remember(credMeta, effectivePkg, effectiveDomain) {
            if (effectivePkg.isNotBlank()) {
                linkManager.getCatalogSuggestedDomainsForApp(effectivePkg)
                    .filterNot { pair -> credMeta.linkedWebsites.any { it.equals(pair.domain, ignoreCase = true) } }
            } else if (effectiveDomain.isNotBlank()) {
                linkManager.getCatalogSuggestedAppsForDomain(effectiveDomain)
                    .filterNot { pair -> credMeta.linkedApps.any { com.example.autofill.AppIdentityVerifier.isSamePackage(it.packageName, pair.appPackage) } }
            } else {
                emptyList()
            }
        }

        // Full-Width Bottom-Sheet Style Modal for Credential Details & Linked Apps
        Dialog(
            onDismissRequest = { showQuickUpdateDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = { showQuickUpdateDialog = false }),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 680.dp)
                        .clickable(enabled = false) {},
                    shape = BottomSheetShape28,
                    color = if (isDark) Color(0xFF1A1722) else Color(0xFFFAF8FF),
                    tonalElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Bottom Sheet Drag Handle
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0x44FFFFFF) else Color(0x33000000))
                        )

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
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFFFFD54F) else Color(0xFFE65100),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Credential & Linked Apps",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isDark) Color.White else Color(0xFF1D192B)
                                )
                            }
                            IconButton(
                                onClick = { showQuickUpdateDialog = false },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                            }
                        }

                        if (effectivePkg.isNotBlank()) {
                            Text(
                                text = "App Package: $effectivePkg • Signers: ${credMeta.appSigners.size}",
                                fontSize = 11.sp,
                                color = if (isDark) Color(0xFFFFCC80) else Color(0xFFE65100),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = updateUsernameText,
                            onValueChange = { updateUsernameText = it },
                            label = { Text("Username or Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = PillShape12
                        )

                        OutlinedTextField(
                            value = updatePasswordText,
                            onValueChange = { updatePasswordText = it },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = PillShape12,
                            visualTransformation = if (updatePassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { updatePassVisible = !updatePassVisible }) {
                                        Icon(
                                            imageVector = if (updatePassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle Visibility"
                                        )
                                    }
                                    IconButton(onClick = { updatePasswordText = generateRandomPassword() }) {
                                        Icon(
                                            imageVector = Icons.Filled.Autorenew,
                                            contentDescription = "Generate Strong Password",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        )

                        // Encrypted Password History & 1-Tap Undo (Step 8D.2)
                        if (credMeta.passwordHistory.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NoteBoxShape10)
                                    .background(if (isDark) Color(0xFF2B2218) else Color(0xFFFFF3E0))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Encrypted History: ${credMeta.passwordHistory.size} saved",
                                    fontSize = 11.5.sp,
                                    color = if (isDark) Color(0xFFFFCC80) else Color(0xFF5D4037)
                                )
                                TextButton(
                                    onClick = {
                                        val prev = metadataStore.popMostRecentHistoricalPassword(
                                            credentialId = link.id,
                                            userEmail = link.userEmail,
                                            isVaultUnlocked = true
                                        )
                                        if (prev != null) {
                                            updatePasswordText = prev
                                            credMeta = metadataStore.getMetadata(link.id)
                                            onUpdatePassword?.invoke(link, updateUsernameText.trim(), prev)
                                            Toast.makeText(context, "Restored previous password! ↩️", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Undo Update", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Bundled Catalog Suggestions (User-Confirmed Only, Step 8B.2)
                        if (catalogSuggestions.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NoteBoxShape10)
                                    .background(if (isDark) Color(0xFF1A2830) else Color(0xFFE0F7FA))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Suggested App ↔ Website Links (Offline Catalog)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isDark) Color(0xFF80DEEA) else Color(0xFF006064)
                                )
                                for (pair in catalogSuggestions) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (effectivePkg.isNotBlank()) "Also link ${pair.domain}?" else "Also link ${pair.appLabel} (${pair.appPackage})?",
                                            fontSize = 11.sp,
                                            color = if (isDark) Color.White else Color.Black,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = {
                                                    if (effectivePkg.isNotBlank()) {
                                                        credMeta = metadataStore.addConfirmedLinkedWebsite(link.id, pair.domain)
                                                    } else {
                                                        val signers = com.example.autofill.AppIdentityVerifier.getAppSignersSha256(context, pair.appPackage)
                                                        credMeta = metadataStore.addConfirmedLinkedApp(
                                                            credentialId = link.id,
                                                            packageName = pair.appPackage,
                                                            signersSha256 = signers,
                                                            label = pair.appLabel
                                                        )
                                                    }
                                                }
                                            ) {
                                                Text("Link", fontSize = 11.sp)
                                            }
                                            TextButton(
                                                onClick = {
                                                    linkManager.ignoreSuggestion(pair.appPackage, pair.domain)
                                                    credMeta = metadataStore.getMetadata(link.id)
                                                }
                                            ) {
                                                Text("Ignore", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Linked Apps & Websites Editor (Step 8B.3)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(NoteBoxShape10)
                                .background(if (isDark) Color(0xFF23202E) else Color(0xFFEDE7F6))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Linked Android Apps (${credMeta.linkedApps.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isDark) Color(0xFFD0BCFF) else Color(0xFF4A148C)
                            )
                            for (app in credMeta.linkedApps) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${app.label} (${app.packageName})",
                                        fontSize = 11.sp,
                                        color = if (isDark) Color.White else Color.Black,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { credMeta = metadataStore.removeLinkedApp(link.id, app.packageName) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Unlink App", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = newLinkedAppInput,
                                    onValueChange = { newLinkedAppInput = it },
                                    placeholder = { Text("Enter app package name", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val normPkg = com.example.autofill.AppIdentityVerifier.normalizePackageName(newLinkedAppInput)
                                        if (normPkg.isNotBlank()) {
                                            val info = com.example.autofill.AppIdentityVerifier.resolveAppDisplayInfo(context, normPkg)
                                            val signers = com.example.autofill.AppIdentityVerifier.getAppSignersSha256(context, normPkg)
                                            credMeta = metadataStore.addConfirmedLinkedApp(
                                                credentialId = link.id,
                                                packageName = normPkg,
                                                signersSha256 = signers,
                                                label = info.displayLabel
                                            )
                                            newLinkedAppInput = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Link App")
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Linked Websites (${credMeta.linkedWebsites.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isDark) Color(0xFFD0BCFF) else Color(0xFF4A148C)
                            )
                            for (site in credMeta.linkedWebsites) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = site,
                                        fontSize = 11.sp,
                                        color = if (isDark) Color.White else Color.Black,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { credMeta = metadataStore.removeLinkedWebsite(link.id, site) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Unlink Website", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = newLinkedWebsiteInput,
                                    onValueChange = { newLinkedWebsiteInput = it },
                                    placeholder = { Text("Enter website domain", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val normSite = com.example.autofill.AppIdentityVerifier.normalizeRegistrableDomain(newLinkedWebsiteInput)
                                        if (normSite.isNotBlank()) {
                                            credMeta = metadataStore.addConfirmedLinkedWebsite(link.id, normSite)
                                            newLinkedWebsiteInput = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Link Website")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showQuickUpdateDialog = false },
                                modifier = Modifier.weight(1f),
                                shape = PillShape12
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    if (updatePasswordText.isBlank()) {
                                        Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (initialSecret.isNotBlank() && initialSecret != updatePasswordText) {
                                        metadataStore.recordPasswordHistory(
                                            credentialId = link.id,
                                            previousPlainPassword = initialSecret,
                                            userEmail = link.userEmail
                                        )
                                    }
                                    onUpdatePassword?.invoke(link, updateUsernameText.trim(), updatePasswordText)
                                    Toast.makeText(context, "Password updated successfully! 🔒", Toast.LENGTH_SHORT).show()
                                    showQuickUpdateDialog = false
                                },
                                modifier = Modifier.weight(1.2f),
                                shape = PillShape12,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Save Changes", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Cheap Precomputed Dense Link Row (Flat Zero-Jank Layout)
// -----------------------------------------------------------------------------

@Composable
fun CheapDenseLinkRow(
    uiModel: LinkUiModel,
    isDark: Boolean,
    onToggleFavorite: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    onEdit: (LinkEntity) -> Unit,
    onCopy: (LinkEntity) -> Unit,
    onOpen: (LinkEntity) -> Unit,
    onShare: (LinkEntity) -> Unit
) {
    val borderStroke = if (isDark) DarkCardBorderStroke else LightCardBorderStroke
    val onOpenClick = remember(uiModel.id, uiModel.entity, onOpen) { { onOpen(uiModel.entity) } }
    val onFavoriteClick = remember(uiModel.id, uiModel.entity, onToggleFavorite) { { onToggleFavorite(uiModel.entity) } }
    val onCopyClick = remember(uiModel.id, uiModel.entity, onCopy) { { onCopy(uiModel.entity) } }
    val domainColor = Color(uiModel.domainColorArgb)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dense_link_row_${uiModel.id}")
            .clip(DenseRowShape14)
            .background(if (isDark) DarkCardEvenBg else Color.White)
            .border(borderStroke, DenseRowShape14)
            .clickable(onClick = onOpenClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(IconBoxShape8)
                .background(domainColor.copy(alpha = if (isDark) 0.22f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = uiModel.domainInitial,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                color = if (isDark) Color.White else domainColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiModel.displayTitle,
                style = DenseTitleTextStyle,
                color = if (isDark) DarkPrimaryText else LightPrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = uiModel.denseSubtitleText,
                style = DenseUrlTextStyle,
                color = if (isDark) DarkSecondaryText else LightSecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(IconBoxShape8)
                    .clickable(onClick = onFavoriteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (uiModel.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite",
                    modifier = Modifier.size(15.dp),
                    tint = if (uiModel.isFavorite) {
                        if (isDark) Color(0xFFFFD54F) else Color(0xFFF57C00)
                    } else Color.Gray
                )
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(IconBoxShape8)
                    .clickable(onClick = onCopyClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(14.dp),
                    tint = if (isDark) DarkPrimaryText else LightPrimaryText
                )
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(IconBoxShape8)
                    .clickable(onClick = onOpenClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open",
                    modifier = Modifier.size(15.dp),
                    tint = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6200EE)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Cheap Precomputed Dense Password Row (Flat Zero-Jank Layout)
// -----------------------------------------------------------------------------

@Composable
fun CheapDensePasswordRow(
    uiModel: LinkUiModel,
    isDark: Boolean,
    onToggleFavorite: (LinkEntity) -> Unit,
    onDelete: (LinkEntity) -> Unit,
    onEdit: (LinkEntity) -> Unit,
    onCopy: (LinkEntity) -> Unit,
    onUpdatePassword: ((LinkEntity, String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { findMainActivity(context) }
    val coroutineScope = rememberCoroutineScope()
    val link = uiModel.entity
    val username = uiModel.usernamePreview
    val borderStroke = if (isDark) DarkDensePassBorderStroke else LightDensePassBorderStroke

    val onEditClick = remember(uiModel.id, link, onEdit) { { onEdit(link) } }
    val onFavoriteClick = remember(uiModel.id, link, onToggleFavorite) { { onToggleFavorite(link) } }

    fun decryptSecretOnDemand(): String {
        val full = RecordEncryption.decrypt(context, link.note, link.id, link.userEmail)
        return full.substringAfter("|||", "")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dense_password_row_${uiModel.id}")
            .clip(DenseRowShape14)
            .background(if (isDark) DarkPassCardBg else LightPassCardBg)
            .border(borderStroke, DenseRowShape14)
            .clickable(onClick = onEditClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(IconBoxShape8)
                .background(if (isDark) Color(0xFF382310) else Color(0xFFFFE082)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (isDark) Color(0xFFFFD54F) else Color(0xFFB78103)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiModel.title,
                style = DenseTitleTextStyle,
                color = if (isDark) Color(0xFFFFECB3) else Color(0xFF3E2723),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = uiModel.denseSubtitleText,
                style = DenseUrlTextStyle,
                color = if (isDark) Color(0xFFFFB74D).copy(alpha = 0.8f) else Color(0xFF8D6E63),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(IconBoxShape8)
                    .clickable {
                        copySecureTextToClipboard(context, username, isSensitive = false, label = "Username")
                        Toast.makeText(context, "Username copied! 👤", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Copy User",
                    modifier = Modifier.size(14.dp),
                    tint = if (isDark) Color(0xFFFFB74D) else Color(0xFF8D6E63)
                )
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(IconBoxShape8)
                    .clickable {
                        if (activity?.isBiometricHardwareAvailable() == true) {
                            activity.triggerBiometricPrompt(
                                onSuccess = {
                                    val secret = decryptSecretOnDemand()
                                    copyWithAutoClear(
                                        context = context,
                                        text = secret,
                                        label = "Password",
                                        scope = coroutineScope,
                                        onCleared = {
                                            Toast.makeText(context, "Clipboard auto-cleared ⏱️", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    Toast.makeText(context, "Password copied! Clipboard auto-clears in 30s ⏱️", Toast.LENGTH_LONG).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, "Biometric failed: $err", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            val secret = decryptSecretOnDemand()
                            copyWithAutoClear(
                                context = context,
                                text = secret,
                                label = "Password",
                                scope = coroutineScope,
                                onCleared = {
                                    Toast.makeText(context, "Clipboard auto-cleared ⏱️", Toast.LENGTH_SHORT).show()
                                }
                            )
                            Toast.makeText(context, "Password copied! Clipboard auto-clears in 30s ⏱️", Toast.LENGTH_LONG).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Copy Pass",
                    modifier = Modifier.size(14.dp),
                    tint = if (isDark) Color(0xFFFFD54F) else Color(0xFFE65100)
                )
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(IconBoxShape8)
                    .clickable(onClick = onFavoriteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (uiModel.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite",
                    modifier = Modifier.size(15.dp),
                    tint = if (uiModel.isFavorite) Color(0xFFFFB74D) else Color.Gray
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// @Preview Functions Configured for Poco F7 Display Dimension (412dp x 915dp, 440dpi)
// -----------------------------------------------------------------------------

const val POCO_F7_DEVICE_SPEC = "spec:width=412dp,height=915dp,dpi=440"

@Preview(
    name = "Poco F7 - Link Card",
    device = POCO_F7_DEVICE_SPEC,
    showBackground = true,
    backgroundColor = 0xFF0E0C14
)
@Composable
private fun PreviewCheapLinkItemCard() {
    val sampleEntity = LinkEntity(
        id = 1,
        url = "https://developer.android.com/jetpack/compose",
        title = "Jetpack Compose Performance Guide",
        category = "💻 Coding",
        isFavorite = true,
        note = "Precomputed UI models and Baseline Profiles keep frame times under 8ms."
    )
    val model = LinkUiMapper.toUiModel(sampleEntity, context = null)
    Box(modifier = Modifier.padding(16.dp)) {
        CheapLinkItemCard(
            uiModel = model,
            isDark = true,
            isEven = true,
            onToggleFavorite = {},
            onDelete = {},
            onEdit = {},
            onCopy = {},
            onOpen = {},
            onShare = {}
        )
    }
}

@Preview(
    name = "Poco F7 - Password Card",
    device = POCO_F7_DEVICE_SPEC,
    showBackground = true,
    backgroundColor = 0xFF0E0C14
)
@Composable
private fun PreviewCheapPasswordItemCard() {
    val sampleEntity = LinkEntity(
        id = 2,
        url = "https://github.com",
        title = "GitHub Enterprise",
        description = "SECURE_PASSWORD",
        category = "🔑 Passwords",
        isFavorite = true,
        note = "security_lead|||SecretPass#2026"
    )
    val model = LinkUiMapper.toUiModel(sampleEntity, context = null)
    Box(modifier = Modifier.padding(16.dp)) {
        CheapPasswordItemCard(
            uiModel = model,
            isDark = true,
            isEven = false,
            onToggleFavorite = {},
            onDelete = {},
            onEdit = {},
            onOpen = {}
        )
    }
}
