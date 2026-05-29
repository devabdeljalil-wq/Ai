package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.ClipboardHelper
import com.example.ui.locale.Localizer
import com.example.ui.viewmodel.VaultViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsScreen(
    viewModel: VaultViewModel,
    currentLanguage: String
) {
    val t = { key: String -> Localizer.t(key, currentLanguage) }
    val context = LocalContext.current

    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()
    val selfDestructEnabled by viewModel.selfDestructEnabled.collectAsState()
    val clipboardDelay by viewModel.clipboardDelay.collectAsState()
    val autoLockTimeout by viewModel.autoLockTimeout.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var isOperating by rememberSaveable { mutableStateOf(false) }
    var operationMode by rememberSaveable { mutableStateOf("") } // "export" or "import"
    var operationState by rememberSaveable { mutableStateOf("loading") } // "loading", "success", "error"

    var showExportPayloadDialog by rememberSaveable { mutableStateOf(false) }
    var latestEncryptedBackup by rememberSaveable { mutableStateOf("") }
    val exportPayloadText by viewModel.exportPayloadText.collectAsState()

    var showImportPayloadDialog by rememberSaveable { mutableStateOf(false) }
    val importPayloadText by viewModel.importPayloadText.collectAsState()

    var feedbackMessage by rememberSaveable { mutableStateOf("") }

    // Multi-Language Strings for Secure CSV backups
    val locText = remember(currentLanguage) {
        when (currentLanguage) {
            "ar" -> mapOf(
                "title_secure_export" to "تصدير النسخة المشفرة الآمنة",
                "desc_secure_export" to "سيتم تشفير كافة كلمات المرور والملاحظات في الخزنة باستخدام خوارزمية AES-GCM 256 مع رمز مرور مخصص تختاره لحمايتها.",
                "enter_passphrase" to "رمز مرور تشفير النسخة الاحتياطية",
                "confirm_passphrase" to "تأكيد رمز المرور",
                "pass_mismatch" to "الرموز غير متطابقة أو قصيرة جداً (أقل من 4 خانات)!",
                "btn_generate" to "إنشاء وتشفير النسخة",
                "save_local_file" to "حفظ كملف على الجهاز (.txt)",
                "import_options_title" to "استيراد واستعادة الخزنة",
                "import_options_desc" to "اختر الطريقة المفضلة لتحميل واستعادة نسخة بياناتك الاحتياطية المشفرة.",
                "import_file_opt" to "استيراد من ملف نسخة احتياطية",
                "import_text_opt" to "لصق نص النسخة المشفر",
                "enter_decrypt_pass" to "رمز مرور فك تشفير النسخة الاحتياطية",
                "desc_decrypt" to "أدخل نفس كلمة المرور التي اخترتها أثناء عملية التصدير لفتح وقراءة البيانات بأمان.",
                "btn_decrypt" to "فك تشفير واسترجاع الخزنة",
                "export_row_title" to "تصدير الخزنة مشفرة (TXT)",
                "export_row_desc" to "تشفير وحماية كلمات المرور والملاحظات في ملف واحد",
                "import_row_title" to "استيراد الخزنة مشفرة (TXT)",
                "import_row_desc" to "استرجاع كافة كلمات المرور والملاحظات بأمان",
                "copied_toast" to "تم النسخ بنجاح للحافظة"
            )
            "fr" -> mapOf(
                "title_secure_export" to "Sauvegarde Chiffrée Sécurisée",
                "desc_secure_export" to "Tous vos mots de passe et notes de coffre seront entièrement cryptés avec AES-GCM 256 à l'aide d'une phrase de passe personnalisée de votre choix.",
                "enter_passphrase" to "Phrase de passe de chiffrement",
                "confirm_passphrase" to "Confirmer la phrase de passe",
                "pass_mismatch" to "Les phrases de passe ne correspondent pas ou sont trop courtes (min 4 caractères) !",
                "btn_generate" to "Générer la sauvegarde cryptée",
                "save_local_file" to "Enregistrer le fichier (.txt)",
                "import_options_title" to "Restauration du Coffre",
                "import_options_desc" to "Choisissez la méthode pour charger et importer votre fichier de sauvegarde crypté.",
                "import_file_opt" to "Importer un fichier de sauvegarde",
                "import_text_opt" to "Coller le texte de sauvegarde chiffré",
                "enter_decrypt_pass" to "Phrase de passe de décryptage",
                "desc_decrypt" to "Saisissez le mot de passe défini lors de l'exportation pour déverrouiller et restaurer vos données.",
                "btn_decrypt" to "Décrypter et restaurer le coffre",
                "export_row_title" to "Exporter le coffre chiffré (TXT)",
                "export_row_desc" to "Crypter et packagez vos logins et notes",
                "import_row_title" to "Importer le coffre chiffré (TXT)",
                "import_row_desc" to "Décrypter et restaurer tous les éléments",
                "copied_toast" to "Copié dans le presse-papiers !"
            )
            else -> mapOf( // "en"
                "title_secure_export" to "Secure Encrypted Export",
                "desc_secure_export" to "All your vault passwords and secure notes will be packaged together and fully encrypted using 256-bit AES-GCM with a custom passphrase of your choice.",
                "enter_passphrase" to "Backup encryption passphrase",
                "confirm_passphrase" to "Confirm backup passphrase",
                "pass_mismatch" to "Passphrases mismatch or too short (min 4 chars)!",
                "btn_generate" to "Generate Encrypted Backup",
                "save_local_file" to "Save Backup to Device (File)",
                "import_options_title" to "Restore Encrypted Backup",
                "import_options_desc" to "Choose your preferred option to load and restore your secure backup database package.",
                "import_file_opt" to "Select Backup File From Device",
                "import_text_opt" to "Paste Encrypted Backup Text",
                "enter_decrypt_pass" to "Backup Decryption Passphrase",
                "desc_decrypt" to "The custom passphrase you selected during export is required to decrypt and match safety keys to restore your records.",
                "btn_decrypt" to "Decrypt & Restore Database",
                "export_row_title" to "Export Encrypted Vault (TXT)",
                "export_row_desc" to "Encrypt and package passwords and notes securely",
                "import_row_title" to "Import Encrypted Vault (TXT)",
                "import_row_desc" to "Decrypt and restore all vault components",
                "copied_toast" to "Copied to clipboard!"
            )
        }
    }

    // New dialog controls
    var showExportConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showExportPasswordPrompt by rememberSaveable { mutableStateOf(false) }
    var exportPasswordText by rememberSaveable { mutableStateOf("") }
    var exportPasswordConfirmText by rememberSaveable { mutableStateOf("") }
    var exportPasswordError by rememberSaveable { mutableStateOf("") }
    var exportPasswordVisible by rememberSaveable { mutableStateOf(false) }

    var showImportOptionsDialog by rememberSaveable { mutableStateOf(false) }

    var showImportPasswordPrompt by rememberSaveable { mutableStateOf(false) }
    var importPasswordText by rememberSaveable { mutableStateOf("") }
    var importPasswordError by rememberSaveable { mutableStateOf("") }
    var importPasswordVisible by rememberSaveable { mutableStateOf(false) }

    var importedLoginsCount by rememberSaveable { mutableStateOf(0) }
    var importedNotesCount by rememberSaveable { mutableStateOf(0) }

    val contentResolver = context.contentResolver

    // Android Storage Access Framework System Launchers
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    try {
                        val payload = latestEncryptedBackup
                        if (payload.isNotBlank()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                contentResolver.openOutputStream(uri)?.use { os ->
                                    os.write(payload.toByteArray(Charsets.UTF_8))
                                    os.flush()
                                }
                            }
                            feedbackMessage = if (currentLanguage == "ar") "تم حفظ النسخة الاحتياطية بنجاح!" else "Backup saved to file successfully!"
                        } else {
                            feedbackMessage = if (currentLanguage == "ar") "فشل الحفظ: لا توجد بيانات للنسخة الاحتياطية" else "Save failed: No backup data found"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        feedbackMessage = if (currentLanguage == "ar") "خطأ أثناء الحفظ: ${e.message}" else "Error writing backup: ${e.message}"
                    }
                }
            }
        }
    )

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    try {
                        val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            contentResolver.openInputStream(uri)?.use { ins ->
                                ins.bufferedReader().use { it.readText() }
                            } ?: ""
                        }
                        viewModel.importPayloadText.value = text
                        if (text.isNotBlank()) {
                            importPasswordText = ""
                            importPasswordError = ""
                            showImportPasswordPrompt = true
                        } else {
                            feedbackMessage = if (currentLanguage == "ar") "الملف فارغ!" else "Backup file is empty!"
                        }
                    } catch (e: Exception) {
                        feedbackMessage = if (currentLanguage == "ar") "خطأ أثناء القراءة: ${e.message}" else "Error reading backup: ${e.message}"
                    }
                }
            }
        }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = t("settings_tab"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Configure SafeVault system policies",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // SYSTEM ACCESS CONTROL
            item {
                Text(
                    text = "System Access Control",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Bio Control Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Fingerprint, contentDescription = "", tint = Color(0xFF818CF8))
                                Column {
                                    Text(text = t("biometrics_toggle"), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = t("try_biometrics"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Switch(
                                checked = biometricsEnabled,
                                onCheckedChange = { viewModel.toggleBiometrics(context) },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        // Self-Destruct Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = "", tint = Color(0xFFFB7185))
                                Column {
                                    Text(text = t("self_destruct_toggle"), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = t("self_destruct_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Switch(
                                checked = selfDestructEnabled,
                                onCheckedChange = { viewModel.toggleSelfDestruct() },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        // Clipboard Timer Cycle Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(text = t("clear_clipboard"), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = t("clear_clipboard_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Button(
                                onClick = { viewModel.cycleClipboardDelay() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = "$clipboardDelay Seconds", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        // Auto-Lock Timer Cycle Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(text = t("auto_lock"), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = t("auto_lock_desc"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            val autoLockLabel = when (autoLockTimeout) {
                                0 -> t("never")
                                1 -> t("minutes_1")
                                5 -> t("minutes_5")
                                10 -> t("minutes_10")
                                else -> "$autoLockTimeout Minutes"
                            }
                            Button(
                                onClick = { viewModel.cycleAutoLockTimeout() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = autoLockLabel, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // APPEARANCE & LOCALIZATION
            item {
                Text(
                    text = "System Environment Preferences",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Dark mode toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.DarkMode, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(text = t("dark_mode"), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = "AMOLED true black optimization", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { viewModel.toggleDarkMode() },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        // Language selector
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Language, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                                Text(text = t("language"), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val langs = listOf(
                                    Localizer.AppLanguage.ENGLISH,
                                    Localizer.AppLanguage.ARABIC,
                                    Localizer.AppLanguage.FRENCH
                                )
                                langs.forEach { lang ->
                                    val isSel = currentLanguage == lang.code
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer)
                                            .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                            .clickable { viewModel.setAppLanguage(lang.code) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lang.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // DATA PORTABILITY (EXPORT / IMPORT)
            item {
                Text(
                    text = if (currentLanguage == "ar") "أدوات النسخ الاحتياطي والنقل" else "Backup & Portability Tools",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Export Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExportConfirmDialog = true
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(text = locText["export_row_title"] ?: "Export Encrypted Vault", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = locText["export_row_desc"] ?: "Protect logins & notes with strong AES-GCM encryption", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Icon(imageVector = Icons.Default.ArrowForwardIos, contentDescription = "", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        // Import Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showImportOptionsDialog = true
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.FileUpload, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(text = locText["import_row_title"] ?: "Import Encrypted Vault", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = locText["import_row_desc"] ?: "Decrypt and restore passwords & notes seamlessly", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Icon(imageVector = Icons.Default.ArrowForwardIos, contentDescription = "", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Feedback Toast Messages inside the layout
            item {
                AnimatedVisibility(visible = feedbackMessage.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = feedbackMessage, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    LaunchedEffect(feedbackMessage) {
                        kotlinx.coroutines.delay(4000)
                        feedbackMessage = ""
                    }
                }
            }

            // Padding spacing at bottom of scrollable settings list
            item {
                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }

    // 0. EXPORT CONFIRMATION DIALOG
    if (showExportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExportConfirmDialog = false },
            title = {
                Text(
                    text = locText["export_confirm_title"] ?: "Secure Export Confirmation",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = locText["export_confirm_message"] ?: "Before initiating the export process, please confirm that you have your master passphrase handy.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportConfirmDialog = false
                        exportPasswordText = ""
                        exportPasswordConfirmText = ""
                        exportPasswordError = ""
                        showExportPasswordPrompt = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("export_confirm_proceed_button")
                ) {
                    Text(text = locText["export_confirm_ok"] ?: "Proceed", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportConfirmDialog = false },
                    modifier = Modifier.testTag("export_confirm_cancel_button")
                ) {
                    Text(text = t("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 1. EXPORT PASSPHRASE PROMPT
    if (showExportPasswordPrompt) {
        AlertDialog(
            onDismissRequest = { showExportPasswordPrompt = false },
            title = { Text(text = locText["title_secure_export"] ?: "Secure Export", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = locText["desc_secure_export"] ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = exportPasswordText,
                        onValueChange = { exportPasswordText = it; exportPasswordError = "" },
                        label = { Text(text = locText["enter_passphrase"] ?: "") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (exportPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { exportPasswordVisible = !exportPasswordVisible }) {
                                Icon(
                                    imageVector = if (exportPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    OutlinedTextField(
                        value = exportPasswordConfirmText,
                        onValueChange = { exportPasswordConfirmText = it; exportPasswordError = "" },
                        label = { Text(text = locText["confirm_passphrase"] ?: "") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (exportPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    if (exportPasswordError.isNotBlank()) {
                        Text(text = exportPasswordError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (exportPasswordText.length < 4) {
                            exportPasswordError = locText["pass_mismatch"] ?: "Too short (min 4 chars)"
                            return@Button
                        }
                        if (exportPasswordText != exportPasswordConfirmText) {
                            exportPasswordError = locText["pass_mismatch"] ?: "Mismatched passphrases"
                            return@Button
                        }
                        showExportPasswordPrompt = false
                        operationMode = "export"
                        operationState = "loading"
                        isOperating = true
                        
                        viewModel.exportVaultSecurely(exportPasswordText) { encrypted ->
                            latestEncryptedBackup = encrypted
                            viewModel.exportPayloadText.value = encrypted
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(1800)
                                if (encrypted.isNotBlank()) {
                                    operationState = "success"
                                } else {
                                    operationState = "error"
                                    isOperating = false
                                    feedbackMessage = if (currentLanguage == "ar") "فشل تصدير البيانات" else "Failed to package secure export"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(text = locText["btn_generate"] ?: "Export", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPasswordPrompt = false }) {
                    Text(text = t("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 2. EXPORT READY PAYLOAD SHOW
    if (showExportPayloadDialog) {
        AlertDialog(
            onDismissRequest = { showExportPayloadDialog = false },
            title = { Text(text = if (currentLanguage == "ar") "ملف النسخة الاحتياطية جاهز!" else "Backup Package Ready", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = if (currentLanguage == "ar") "يمكنك الآن نسخ نص التشفير أدناه مباشرة أو حفظه في ملف آمن على جهازك." else "Copy the encrypted database content below or write it safely into a physical device file.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = latestEncryptedBackup,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        readOnly = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Button(
                        onClick = {
                            try {
                                val sdf = java.text.SimpleDateFormat("dd-MM-yyyy HH.mm", java.util.Locale.US)
                                val formattedDate = sdf.format(java.util.Date())
                                exportFileLauncher.launch("vault_backup_${formattedDate}.txt")
                                showExportPayloadDialog = false
                            } catch (e: Exception) {
                                feedbackMessage = if (currentLanguage == "ar") "مدير الملفات غير مدعوم على جهازك. يرجى استخدام خيار النسخ." else "System File Pick/Save is not supported on this device. Please use Clipboard copy/paste instead."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = locText["save_local_file"] ?: "Save Backup to Device (File)")
                    }

                    OutlinedButton(
                        onClick = {
                            ClipboardHelper.copyToClipboard(context, latestEncryptedBackup, "SafeVault_Backup")
                            showExportPayloadDialog = false
                            feedbackMessage = locText["copied_toast"] ?: "Copied!"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = t("copied_clipboard"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportPayloadDialog = false }) {
                    Text(text = "Close", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 3. IMPORT METHOD CHOICE DIALOG
    if (showImportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showImportOptionsDialog = false },
            title = { Text(text = locText["import_options_title"] ?: "Import", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = locText["import_options_desc"] ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    // Option 1: Pick File
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    showImportOptionsDialog = false
                                    importFileLauncher.launch(arrayOf("*/*"))
                                } catch (e: Exception) {
                                    feedbackMessage = if (currentLanguage == "ar") "مدير الملفات غير مدعوم على جهازك. يرجى لصق النص مباشرة." else "System File picking is not supported on this device. Please use direct Clipboard Paste instead."
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.InsertDriveFile, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                            Text(text = locText["import_file_opt"] ?: "", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Option 2: Paste Text
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showImportOptionsDialog = false
                                viewModel.importPayloadText.value = ""
                                showImportPayloadDialog = true
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                            Text(text = locText["import_text_opt"] ?: "", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportOptionsDialog = false }) {
                    Text(text = t("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 4. PASTE PAYLOAD TEXT DIALOG
    if (showImportPayloadDialog) {
        AlertDialog(
            onDismissRequest = { showImportPayloadDialog = false },
            title = { Text(text = locText["import_text_opt"] ?: "Paste Backup text", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (currentLanguage == "ar") "ألصق نص الرموز المشفر الذي قمت بنسخه مسبقاً أثناء عملية التصدير." else "Paste the compiled base64 encrypted text block you exported earlier.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = importPayloadText,
                        onValueChange = { viewModel.importPayloadText.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        placeholder = { Text(text = t("enter_imported_csv"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importPayloadText.isBlank()) return@Button
                        showImportPayloadDialog = false
                        importPasswordText = ""
                        importPasswordError = ""
                        showImportPasswordPrompt = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(text = "Continue", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportPayloadDialog = false }) {
                    Text(text = t("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 5. DECRYPT / RESTORE SECURE PASSPHRASE PROMPT
    if (showImportPasswordPrompt) {
        AlertDialog(
            onDismissRequest = { showImportPasswordPrompt = false },
            title = { Text(text = locText["enter_decrypt_pass"] ?: "Decrypt Backup", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = locText["desc_decrypt"] ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = importPasswordText,
                        onValueChange = { importPasswordText = it; importPasswordError = "" },
                        label = { Text(text = if (currentLanguage == "ar") "رمز فك التشفير للنسخة" else "Decryption Passphrase") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (importPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { importPasswordVisible = !importPasswordVisible }) {
                                Icon(
                                    imageVector = if (importPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    if (importPasswordError.isNotBlank()) {
                        Text(text = importPasswordError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importPasswordText.isBlank()) return@Button
                        showImportPasswordPrompt = false
                        operationMode = "import"
                        operationState = "loading"
                        isOperating = true
                        
                        viewModel.importVaultSecurely(
                            encryptedPayload = importPayloadText.trim(),
                            passwordPhrase = importPasswordText,
                            successCallback = { numPasswords, numNotes ->
                                importedLoginsCount = numPasswords
                                importedNotesCount = numNotes
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(1800)
                                    operationState = "success"
                                }
                            },
                            errorCallback = { errorMsg ->
                                isOperating = false
                                importPasswordError = errorMsg
                                showImportPasswordPrompt = true // reopen and show error feedback
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(text = locText["btn_decrypt"] ?: "Decrypt", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportPasswordPrompt = false }) {
                    Text(text = t("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 6. OPERATION PROGRESS DIALOG WITH PROGRESS INDICATOR & LOTTIE
    if (isOperating) {
        Dialog(onDismissRequest = {}) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    val titleText = if (operationMode == "export") {
                        if (operationState == "loading") {
                            if (currentLanguage == "ar") "تشفير وحزم بيانات الخزنة..." else "Generating Backup Package..."
                        } else {
                            if (currentLanguage == "ar") "النسخة الاحتياطية جاهزة!" else "Backup Package Ready!"
                        }
                    } else {
                        if (operationState == "loading") {
                            if (currentLanguage == "ar") "فك تشفير واستيراد البيانات..." else "Securing and Decrypting Imports..."
                        } else {
                            if (currentLanguage == "ar") "تم الاستيراد بنجاح!" else "Import Successful!"
                        }
                    }

                    Text(
                        text = titleText,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (operationState == "loading") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(140.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 6.dp,
                                trackColor = MaterialTheme.colorScheme.outline
                            )
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Processing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        } else if (operationState == "success") {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(100.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Failure",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(100.dp)
                            )
                        }
                    }

                    Text(
                        text = if (operationState == "loading") {
                            if (currentLanguage == "ar") "نطبق بروتوكولات تشفير لا معرفية لحماية كافة سجلات قاعدة بيانات خزنتك." else "Applying zero-knowledge cryptography protocols to package database records safely."
                        } else {
                            if (operationMode == "export") {
                                if (currentLanguage == "ar") "تم تشفير بياناتك وحفظ المعايرة بنجاح." else "Database records successfully encrypted under solid AES security."
                            } else {
                                if (currentLanguage == "ar") "تمت استعادة $importedLoginsCount حساب و $importedNotesCount ملاحظة آمنة بنجاح!" else "Successfully decrypted and restored $importedLoginsCount logins and $importedNotesCount secure notes!"
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // If finished, show action to continue
                    if (operationState != "loading") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                isOperating = false
                                if (operationMode == "export") {
                                    showExportPayloadDialog = true
                                } else {
                                    feedbackMessage = if (currentLanguage == "ar") "تمت الاستعادة وتحديث الخزنة بنجاح!" else "Database successfully restored and updated!"
                                    try {
                                        val intent = context.packageManager?.getLaunchIntentForPackage(context.packageName)
                                        if (intent != null) {
                                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                            context.startActivity(intent)
                                            if (context is Activity) {
                                                context.finish()
                                            }
                                        }
                                    } catch (ex: Exception) {
                                        ex.printStackTrace()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (currentLanguage == "ar") "متابعة" else "Proceed", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
