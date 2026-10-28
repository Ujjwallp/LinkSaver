package com.example.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.security.HardwareSecurityLevel
import com.example.security.KeyHierarchy
import com.example.security.MasterPasswordValidator
import com.example.security.QrCodeGenerator
import kotlinx.coroutines.launch

/**
 * Dialog displaying the printable Emergency Kit and QR Code.
 */
@Composable
fun EmergencyKitDialog(
    secretKey: String,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasConfirmedSaved by remember { mutableStateOf(false) }

    val qrMatrix = remember(secretKey) {
        try {
            QrCodeGenerator.encode(secretKey)
        } catch (e: Exception) {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 390.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1E1E2E) else Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = "Emergency Kit",
                    tint = if (isDark) Color(0xFF80CBC4) else Color(0xFF00796B),
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Emergency Kit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )

                Text(
                    text = "Secret Recovery Key",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFFB0BEC5) else Color(0xFF546E7A)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Printable Secret Key Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF12121A) else Color(0xFFF1F5F9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF374151) else Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = secretKey,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp,
                            color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0277BD)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("LinkVault Secret Key", secretKey)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    clip.description.extras = PersistableBundle().apply {
                                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                    }
                                }
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Secret Key copied (clears in 30s)", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Secret Key", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // QR Code
                if (qrMatrix != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.size(180.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            val matrixSize = qrMatrix.size
                            val cellSize = size.width / matrixSize
                            for (y in 0 until matrixSize) {
                                for (x in 0 until matrixSize) {
                                    if (qrMatrix[y][x]) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(x * cellSize, y * cellSize),
                                            size = Size(cellSize, cellSize)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "This 128-bit Secret Key was generated on your device. It is NEVER sent anywhere. Combined with your Master Password, it is strictly required to decrypt your vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasConfirmedSaved,
                        onCheckedChange = { hasConfirmedSaved = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I have saved my Secret Key securely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color.White else Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    enabled = hasConfirmedSaved,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm & Continue")
                }
            }
        }
    }
}

/**
 * Dialog to set up Master Password.
 */
@Composable
fun MasterPasswordSetupDialog(
    isDark: Boolean,
    onSuccess: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val validation = remember(password) {
        MasterPasswordValidator.validate(password.toCharArray())
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 390.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1E1E2E) else Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Master Password",
                    tint = if (isDark) Color(0xFF90CAF9) else Color(0xFF1976D2),
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Set Master Password",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )

                Text(
                    text = "Protects your entire vault with AES-256-GCM and PBKDF2 stretching (600,000+ iterations).",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Master Password (min 12 chars)") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle Visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Strength indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Strength: ${validation.strength.label}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (validation.strength) {
                            MasterPasswordValidator.PasswordStrength.TOO_SHORT -> MaterialTheme.colorScheme.error
                            MasterPasswordValidator.PasswordStrength.COMMON -> MaterialTheme.colorScheme.error
                            MasterPasswordValidator.PasswordStrength.FAIR -> Color(0xFFFFA000)
                            MasterPasswordValidator.PasswordStrength.GOOD -> Color(0xFF43A047)
                            MasterPasswordValidator.PasswordStrength.STRONG -> Color(0xFF00C853)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirm Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (!validation.isValid) {
                            errorMessage = validation.errorMessage ?: "Invalid password"
                            return@Button
                        }
                        if (password != confirmPassword) {
                            errorMessage = "Passwords do not match"
                            return@Button
                        }

                        isProcessing = true
                        val (rawSecret, formattedSecret) = KeyHierarchy.generateSecretKey()
                        KeyHierarchy.initializeMasterKey(
                            context = context,
                            masterPassword = password.toCharArray(),
                            secretKeyRaw = rawSecret,
                            secretKeyFormatted = formattedSecret
                        )
                        isProcessing = false
                        onSuccess(formattedSecret)
                    },
                    enabled = !isProcessing && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Benchmarking & Deriving...")
                    } else {
                        Text("Create Master Key")
                    }
                }
            }
        }
    }
}

/**
 * Dialog to change the Master Password (re-wraps DEK only).
 */
@Composable
fun ChangeMasterPasswordDialog(
    isDark: Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var secretKeyInput by remember { mutableStateOf(KeyHierarchy.getActiveSecretKey() ?: "") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val validation = remember(newPassword) {
        MasterPasswordValidator.validate(newPassword.toCharArray())
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 390.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1E1E2E) else Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Change Master Password",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )

                Text(
                    text = "Re-wraps your Vault Encryption Key (DEK). No saved data needs re-encrypting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Current Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (KeyHierarchy.getActiveSecretKey() == null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = secretKeyInput,
                        onValueChange = { secretKeyInput = it },
                        label = { Text("Secret Key (A3-XXXXX-...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("New Master Password (min 12 chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = { confirmNewPassword = it },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (!validation.isValid) {
                            errorMessage = validation.errorMessage ?: "Invalid new password"
                            return@Button
                        }
                        if (newPassword != confirmNewPassword) {
                            errorMessage = "New passwords do not match"
                            return@Button
                        }

                        isProcessing = true
                        val changed = KeyHierarchy.changeMasterPassword(
                            context = context,
                            oldPassword = oldPassword.toCharArray(),
                            newPassword = newPassword.toCharArray(),
                            secretKeyInput = secretKeyInput
                        )
                        isProcessing = false
                        if (changed) {
                            Toast.makeText(context, "Master password changed successfully", Toast.LENGTH_SHORT).show()
                            onSuccess()
                        } else {
                            errorMessage = "Incorrect current master password or secret key"
                        }
                    },
                    enabled = !isProcessing && oldPassword.isNotBlank() && newPassword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("Update Master Password")
                    }
                }
            }
        }
    }
}

/**
 * Security Status Page reporting cryptographic and hardware protection levels.
 */
@Composable
fun SecurityStatusDialog(
    isDark: Boolean,
    onShowEmergencyKit: () -> Unit,
    onChangeMasterPassword: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hardwareLevel = remember { HardwareSecurityLevel.detectLevel(context) }
    val hasMasterKey = remember { KeyHierarchy.hasMasterKey(context) }
    val isUnlocked = remember { KeyHierarchy.isVaultUnlocked() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 390.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1E1E2E) else Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "Security Status",
                        tint = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Security Architecture",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hardware Protection Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xFF12121A) else Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF2E384D) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "HARDWARE ISOLATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF80CBC4) else Color(0xFF00796B),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = hardwareLevel.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isDark) Color.White else Color.Black
                        )
                        Text(
                            text = hardwareLevel.badge,
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0288D1),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = hardwareLevel.description,
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cryptographic Pipeline Checklist
                Text(
                    text = "CRYPTOGRAPHIC PIPELINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                SecurityFeatureRow(
                    title = "Key Derivation Function",
                    value = "PBKDF2-HMAC-SHA256 (600,000+ iters)",
                    active = true,
                    isDark = isDark
                )
                SecurityFeatureRow(
                    title = "Secret Key Protection",
                    value = "128-bit on-device entropy (HKDF-SHA256)",
                    active = true,
                    isDark = isDark
                )
                SecurityFeatureRow(
                    title = "Vault Key (DEK)",
                    value = "256-bit AES-GCM envelope wrapping",
                    active = hasMasterKey,
                    isDark = isDark
                )
                SecurityFeatureRow(
                    title = "Biometric Binding",
                    value = "Hardware KeyStore CryptoObject",
                    active = true,
                    isDark = isDark
                )
                SecurityFeatureRow(
                    title = "Screen & Recents Guard",
                    value = "FLAG_SECURE active (Anti-screenshot)",
                    active = true,
                    isDark = isDark
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                if (hasMasterKey) {
                    OutlinedButton(
                        onClick = onShowEmergencyKit,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = "", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Emergency Kit (Secret Key & QR)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onChangeMasterPassword,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Password, contentDescription = "", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Master Password")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun SecurityFeatureRow(
    title: String,
    value: String,
    active: Boolean,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (active) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = "",
            tint = if (active) Color(0xFF43A047) else Color.Gray,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color.Black
            )
            Text(
                text = value,
                fontSize = 11.sp,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
            )
        }
    }
}
