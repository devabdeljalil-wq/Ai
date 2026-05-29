package com.example.ui.screens

import android.widget.Space
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.PasswordEntry
import com.example.security.ClipboardHelper
import com.example.ui.locale.Localizer
import com.example.ui.viewmodel.VaultViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    currentLanguage: String
) {
    val passwords by viewModel.passwords.collectAsState()
    val searchQuery by viewModel.passwordSearchQuery.collectAsState()
    val categoryFilter by viewModel.passwordCategoryFilter.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedEntryForDetail by remember { mutableStateOf<PasswordEntry?>(null) }

    var selectedPasswordIds by remember { mutableStateOf(emptySet<Int>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchCategorizeDialog by remember { mutableStateOf(false) }

    val t = { key: String -> Localizer.t(key, currentLanguage) }
    val context = LocalContext.current

    // Calculated fields based on actual secure records in vault
    val weakCount = passwords.count { viewModel.calculatePasswordStrength(viewModel.decryptPassword(it.encryptedPassword)) == "pw_strength_weak" }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add password entry", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header
            if (selectedPasswordIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(onClick = { selectedPasswordIds = emptySet() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel selection", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = String.format(t("selected_count"), selectedPasswordIds.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Select All / Deselect All Toggle Icon
                        val allSel = passwords.isNotEmpty() && passwords.all { it.id in selectedPasswordIds }
                        IconButton(onClick = {
                            if (allSel) {
                                selectedPasswordIds = selectedPasswordIds - passwords.map { it.id }.toSet()
                            } else {
                                selectedPasswordIds = selectedPasswordIds + passwords.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = if (allSel) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSel) t("deselect_all") else t("select_all"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Categorize Button
                        IconButton(onClick = { showBatchCategorizeDialog = true }) {
                            Icon(imageVector = Icons.Default.Category, contentDescription = t("batch_categorize"), tint = MaterialTheme.colorScheme.primary)
                        }

                        // Delete Button
                        IconButton(onClick = { showBatchDeleteConfirm = true }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = t("batch_delete"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = t("app_name"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Text(
                                text = t("biometric_active"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield Active icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Quick Stats Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = t("vault_protection"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format(t("items_count"), passwords.size),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "SQLCipher AES-256",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Weak Passwords", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "$weakCount",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (weakCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "SECURE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar & Filters
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setPasswordSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(t("search_hint"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Logins", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Category Horizontal Tabs
            val dynamicCategories by viewModel.passwordCategories.collectAsState()
            val categories = listOf("") + dynamicCategories
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = categoryFilter == cat
                    val tabText = if (cat.isBlank()) t("see_all") else {
                        when (cat.lowercase()) {
                            "personal" -> t("category_personal")
                            "work" -> t("category_work")
                            "finance" -> t("category_finance")
                            "other" -> t("category_other")
                            else -> cat
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable { viewModel.setPasswordCategoryFilter(cat) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tabText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Passwords List
            if (passwords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val composition by com.airbnb.lottie.compose.rememberLottieComposition(com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(com.example.R.raw.empty_animation))
                        com.airbnb.lottie.compose.LottieAnimation(
                            composition = composition,
                            iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                            modifier = Modifier.size(150.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = t("empty_passwords"),
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(passwords) { entry ->
                        val isSelected = selectedPasswordIds.contains(entry.id)
                        PasswordItemRow(
                            entry = entry,
                            currentLanguage = currentLanguage,
                            isSelectionMode = selectedPasswordIds.isNotEmpty(),
                            isSelected = isSelected,
                            onItemClicked = {
                                if (selectedPasswordIds.isNotEmpty()) {
                                    selectedPasswordIds = if (isSelected) {
                                        selectedPasswordIds - entry.id
                                    } else {
                                        selectedPasswordIds + entry.id
                                    }
                                } else {
                                    selectedEntryForDetail = entry
                                }
                            },
                            onLongClick = {
                                selectedPasswordIds = if (isSelected) {
                                    selectedPasswordIds - entry.id
                                } else {
                                    selectedPasswordIds + entry.id
                                }
                            },
                            onCopyPassword = {
                                val decrypted = viewModel.decryptPassword(entry.encryptedPassword)
                                ClipboardHelper.copyToClipboard(context, decrypted)
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog / Edit / View
    selectedEntryForDetail?.let { entry ->
        PasswordDetailDialog(
            entry = entry,
            currentLanguage = currentLanguage,
            viewModel = viewModel,
            onDismiss = { selectedEntryForDetail = null },
            onDelete = {
                viewModel.deletePassword(entry)
                selectedEntryForDetail = null
            }
        )
    }

    // Add Password Dialog Screen Modal
    if (showAddDialog) {
        AddPasswordDialog(
            currentLanguage = currentLanguage,
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onSave = { platform, user, pass, url, category, desc, catLabel, catColor ->
                viewModel.addPassword(
                    PasswordEntry(
                        platformName = platform,
                        username = user,
                        encryptedPassword = pass,
                        websiteUrl = url,
                        category = category,
                        description = desc,
                        categoryLabel = catLabel,
                        categoryColor = catColor
                    )
                )
                showAddDialog = false
            }
        )
    }

    // Batch Categorize Dialog Modal
    if (showBatchCategorizeDialog) {
        PasswordBatchCategorizeDialog(
            currentLanguage = currentLanguage,
            viewModel = viewModel,
            onDismiss = { showBatchCategorizeDialog = false },
            onSave = { finalCategory: String, finalLabel: String, finalColor: String ->
                viewModel.categorizePasswordsBatch(
                    passwords.filter { it.id in selectedPasswordIds },
                    finalCategory,
                    finalLabel,
                    finalColor
                )
                selectedPasswordIds = emptySet()
                showBatchCategorizeDialog = false
            }
        )
    }

    // Batch Delete Confirm Dialog Modal
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Text(text = t("batch_delete"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = String.format(t("batch_delete_confirm"), selectedPasswordIds.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePasswordsBatch(passwords.filter { it.id in selectedPasswordIds })
                        selectedPasswordIds = emptySet()
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) {
                    Text(t("delete"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(t("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PasswordItemRow(
    entry: PasswordEntry,
    currentLanguage: String,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onItemClicked: () -> Unit,
    onLongClick: () -> Unit,
    onCopyPassword: () -> Unit
) {
    val t = { key: String -> Localizer.t(key, currentLanguage) }

    // Animated colors during batch selection
    val checkboxBgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "checkbox_bg"
    )
    val checkboxBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "checkbox_border"
    )

    // Animated scale and alpha during batch selection
    val checkboxScale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkbox_scale"
    )
    val checkmarkScale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkmark_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onLongClick = { onLongClick() },
                onClick = { onItemClicked() }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .graphicsLayer {
                            scaleX = checkboxScale
                            scaleY = checkboxScale
                        }
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(checkboxBgColor)
                        .border(1.5.dp, checkboxBorderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = checkmarkScale
                                scaleY = checkmarkScale
                                alpha = checkmarkScale
                            }
                            .size(16.dp)
                    )
                }
            }

            // Icon Rounded
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.platformName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.platformName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = entry.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Category colored tag
                val catColor = if (entry.categoryColor.isNotBlank()) {
                    try {
                        Color(android.graphics.Color.parseColor(entry.categoryColor))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                } else {
                    when (entry.category.lowercase()) {
                        "personal" -> Color(0xFF818CF8) // Indigo-400
                        "work" -> Color(0xFF34D399) // Emerald
                        "finance" -> Color(0xFFFBBF24) // Yellow
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }
                val badgeText = if (entry.categoryLabel.isNotBlank()) entry.categoryLabel else entry.category
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(catColor.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = badgeText, style = MaterialTheme.typography.labelSmall, color = catColor, fontSize = 9.sp)
                }

                // Copy Action icon
                IconButton(onClick = onCopyPassword) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy password action", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// Dialog to Add a Password with real-time strength assessment
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPasswordDialog(
    currentLanguage: String,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String) -> Unit
) {
    val t = { key: String -> Localizer.t(key, currentLanguage) }

    var platform by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var websiteUrl by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Personal") }
    var description by remember { mutableStateOf("") }

    var isCustomCategory by remember { mutableStateOf(false) }
    var customCategoryLabel by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#818CF8") }

    var passwordVisible by remember { mutableStateOf(false) }

    val strengthKey = viewModel.calculatePasswordStrength(password)
    val strengthText = t(strengthKey)
    val strengthColor = when (strengthKey) {
        "pw_strength_strong" -> Color(0xFF10B981) // emerald
        "pw_strength_medium" -> Color(0xFFFBBF24) // yellow
        else -> Color(0xFFFB7185) // rose-400
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = t("add_password"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = platform,
                        onValueChange = { platform = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(t("platform"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(t("username"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(t("password"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(image, contentDescription = "", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    // Real-time Strength feedback
                    if (password.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = strengthText, color = strengthColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            // Small progress indicators
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val steps = when (strengthKey) {
                                    "pw_strength_strong" -> 3
                                    "pw_strength_medium" -> 2
                                    else -> 1
                                }
                                for (i in 1..3) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 24.dp, height = 4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (i <= steps) strengthColor else MaterialTheme.colorScheme.outlineVariant)
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = websiteUrl,
                        onValueChange = { websiteUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(t("website_url"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                item {
                    Text(text = t("category"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Personal", "Work", "Finance", "Other", "Custom").forEach { cat ->
                            val isSelected = if (cat == "Custom") isCustomCategory else (!isCustomCategory && category == cat)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        if (cat == "Custom") {
                                            isCustomCategory = true
                                        } else {
                                            isCustomCategory = false
                                            category = cat
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = cat, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (isCustomCategory) {
                    item {
                        OutlinedTextField(
                            value = customCategoryLabel,
                            onValueChange = { customCategoryLabel = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            label = { Text("Custom Category Name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Category Color", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val presetColors = listOf("#818CF8", "#34D399", "#FBBF24", "#F43F5E", "#06B6D4", "#8B5CF6", "#EC4899")
                            presetColors.forEach { hex ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                val isColorSelected = selectedColorHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isColorSelected) 2.dp else 1.dp,
                                            color = if (isColorSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorHex = hex }
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(t("description"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (platform.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                        val finalCategory = if (isCustomCategory) {
                            if (customCategoryLabel.trim().isNotBlank()) customCategoryLabel.trim() else "Custom"
                        } else {
                            category
                        }
                        val finalLabel = finalCategory
                        val finalColor = if (isCustomCategory) {
                            selectedColorHex
                        } else {
                            when (category.lowercase()) {
                                "personal" -> "#818CF8"
                                "work" -> "#34D399"
                                "finance" -> "#FBBF24"
                                else -> "#94A3B8"
                            }
                        }
                        onSave(platform, username, password, websiteUrl, finalCategory, description, finalLabel, finalColor)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text(t("save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// Detail and view dialog
@Composable
fun PasswordDetailDialog(
    entry: PasswordEntry,
    currentLanguage: String,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val t = { key: String -> Localizer.t(key, currentLanguage) }
    val context = LocalContext.current

    val rawPassword = viewModel.decryptPassword(entry.encryptedPassword)
    val rawDesc = viewModel.decryptDesc(entry.description)
    var isPassVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = entry.platformName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(text = t("username"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = entry.username, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { ClipboardHelper.copyToClipboard(context, entry.username) }) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Username", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Column {
                    Text(text = t("password"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPassVisible) rawPassword else "••••••••••••",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                        Row {
                            IconButton(onClick = { isPassVisible = !isPassVisible }) {
                                Icon(
                                    imageVector = if (isPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Pass Visibility",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(onClick = { ClipboardHelper.copyToClipboard(context, rawPassword) }) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Password", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (entry.websiteUrl.isNotBlank()) {
                    Column {
                        Text(text = t("website_url"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = entry.websiteUrl, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { ClipboardHelper.copyToClipboard(context, entry.websiteUrl) }) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (rawDesc.isNotBlank()) {
                    Column {
                        Text(text = t("description"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = rawDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                val badgeColor = if (entry.categoryColor.isNotBlank()) {
                    try {
                        Color(android.graphics.Color.parseColor(entry.categoryColor))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }
                } else {
                    when (entry.category.lowercase()) {
                        "personal" -> Color(0xFF818CF8) // Indigo-400
                        "work" -> Color(0xFF34D399) // Emerald
                        "finance" -> Color(0xFFFBBF24) // Yellow
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }
                val badgeLabel = if (entry.categoryLabel.isNotBlank()) entry.categoryLabel else entry.category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Category:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = badgeLabel, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(t("cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordBatchCategorizeDialog(
    currentLanguage: String,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    val t = { key: String -> Localizer.t(key, currentLanguage) }
    var selectedCategory by remember { mutableStateOf("personal") }
    var isCustomCategory by remember { mutableStateOf(false) }
    var customCategoryLabel by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#818CF8") }

    val categoriesList = listOf("personal", "work", "finance", "other")
    val predefColors = listOf("#818CF8", "#34D399", "#FBBF24", "#FB7185", "#A78BFA", "#F472B6")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = t("select_category"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category predefined options
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoriesList.forEach { cat ->
                        val isSel = !isCustomCategory && selectedCategory == cat
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .clickable {
                                    isCustomCategory = false
                                    selectedCategory = cat
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSel,
                                onClick = {
                                    isCustomCategory = false
                                    selectedCategory = cat
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (cat) {
                                    "personal" -> t("category_personal")
                                    "work" -> t("category_work")
                                    "finance" -> t("category_finance")
                                    "other" -> t("category_other")
                                    else -> cat
                                },
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Custom tag toggle option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCustomCategory) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .clickable { isCustomCategory = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustomCategory,
                            onClick = { isCustomCategory = true },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Custom Label", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (isCustomCategory) {
                    OutlinedTextField(
                        value = customCategoryLabel,
                        onValueChange = { customCategoryLabel = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("E.g., Social, Family...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Custom colors selection row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(predefColors) { colorHex ->
                            val color = Color(android.graphics.Color.parseColor(colorHex))
                            val isSel = selectedColorHex == colorHex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSel) 3.dp else 0.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = colorHex }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalCategory = if (isCustomCategory) customCategoryLabel.ifBlank { "Custom" } else selectedCategory
                    val finalLabel = if (isCustomCategory) customCategoryLabel.ifBlank { "Custom" } else ""
                    val finalColor = if (isCustomCategory) selectedColorHex else {
                        when (selectedCategory) {
                            "personal" -> "#818CF8"
                            "work" -> "#34D399"
                            "finance" -> "#FBBF24"
                            else -> "#94A3B8"
                        }
                    }
                    onSave(finalCategory, finalLabel, finalColor)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text(t("save"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

