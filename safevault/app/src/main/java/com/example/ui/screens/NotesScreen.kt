package com.example.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.example.data.entity.NoteEntry
import com.example.ui.locale.Localizer
import com.example.ui.viewmodel.VaultViewModel
import com.example.ui.viewmodel.NoteSortField
import com.example.ui.viewmodel.NoteSortOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    viewModel: VaultViewModel,
    currentLanguage: String
) {
    val notes by viewModel.notes.collectAsState()
    val searchQuery by viewModel.noteSearchQuery.collectAsState()
    val categoryFilter by viewModel.noteCategoryFilter.collectAsState()
    val dynamicCategories by viewModel.noteCategories.collectAsState()

    var showEditorDialog by remember { mutableStateOf(false) }
    var selectedNoteForEdit by remember { mutableStateOf<NoteEntry?>(null) }

    var selectedNoteIds by remember { mutableStateOf(emptySet<Int>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchCategorizeDialog by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<NoteEntry?>(null) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricsEnabledGlobally by viewModel.biometricsEnabled.collectAsState()
    val unlockedNotes = remember { mutableStateMapOf<Int, Boolean>() }

    var currentFolderPath by remember { mutableStateOf("") }
    val createdFolders by viewModel.createdFolders.collectAsState()
    val notesFolders = remember(notes) { notes.map { it.folderPath }.toSet() }
    val allFolderPaths = remember(notesFolders, createdFolders) { notesFolders + createdFolders }

    val currentLevelFolders = remember(allFolderPaths, currentFolderPath, searchQuery) {
        if (searchQuery.isNotEmpty()) {
            emptyList()
        } else {
            val children = mutableSetOf<String>()
            for (path in allFolderPaths) {
                if (path.isEmpty()) continue
                if (currentFolderPath.isEmpty()) {
                    val firstSegment = path.split("/").first()
                    children.add(firstSegment)
                } else {
                    if (path.startsWith("$currentFolderPath/")) {
                        val suffix = path.removePrefix("$currentFolderPath/")
                        val nextSegment = suffix.split("/").first()
                        if (nextSegment.isNotEmpty()) {
                            children.add(nextSegment)
                        }
                    }
                }
            }
            children.sorted()
        }
    }

    val currentLevelNotes = remember(notes, searchQuery, currentFolderPath) {
        if (searchQuery.isNotEmpty()) {
            notes
        } else {
            notes.filter { it.folderPath == currentFolderPath }
        }
    }

    val t = { key: String -> Localizer.t(key, currentLanguage) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedNoteForEdit = null
                    showEditorDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add note entry", modifier = Modifier.size(28.dp))
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
            if (selectedNoteIds.isNotEmpty()) {
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
                        IconButton(onClick = { selectedNoteIds = emptySet() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel selection", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = String.format(t("selected_count"), selectedNoteIds.size),
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
                        val allSel = notes.isNotEmpty() && notes.all { it.id in selectedNoteIds }
                        IconButton(onClick = {
                            if (allSel) {
                                selectedNoteIds = selectedNoteIds - notes.map { it.id }.toSet()
                            } else {
                                selectedNoteIds = selectedNoteIds + notes.map { it.id }.toSet()
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
                            text = t("notes_tab"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Confidential Encrypted Notepad",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setNoteSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(t("search_notes_hint"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Notes", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
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

            // Category Horizontal Tabs for Notes
            val categories = listOf("") + dynamicCategories
            androidx.compose.foundation.lazy.LazyRow(
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
                            .clickable { viewModel.setNoteCategoryFilter(cat) }
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

            Spacer(modifier = Modifier.height(8.dp))

            // Notes Sorting Row Toolbar
            val sortField by viewModel.noteSortField.collectAsState()
            val sortOrder by viewModel.noteSortOrder.collectAsState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = t("sort_by"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = t("sort_by") + ":",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )

                    // Last Modified Selection
                    val isModifiedSelected = sortField == NoteSortField.LAST_MODIFIED
                    SuggestionChip(
                        onClick = { viewModel.setNoteSortField(NoteSortField.LAST_MODIFIED) },
                        label = { Text(text = t("last_modified"), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isModifiedSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            labelColor = if (isModifiedSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isModifiedSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(26.dp)
                    )

                    // Created Date Selection
                    val isCreatedSelected = sortField == NoteSortField.CREATED_DATE
                    SuggestionChip(
                        onClick = { viewModel.setNoteSortField(NoteSortField.CREATED_DATE) },
                        label = { Text(text = t("created_date"), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isCreatedSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            labelColor = if (isCreatedSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isCreatedSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(26.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val newOrder = if (sortOrder == NoteSortOrder.DESCENDING) {
                            NoteSortOrder.ASCENDING
                        } else {
                            NoteSortOrder.DESCENDING
                        }
                        viewModel.setNoteSortOrder(newOrder)
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (sortOrder == NoteSortOrder.DESCENDING) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = if (sortOrder == NoteSortOrder.DESCENDING) t("descending") else t("ascending"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Notes List
            if (notes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val composition by com.airbnb.lottie.compose.rememberLottieComposition(com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(com.example.R.raw.empty_animation))
                            com.airbnb.lottie.compose.LottieAnimation(
                                composition = composition,
                                iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                modifier = Modifier.size(120.dp)
                            )

                            Text(
                                text = t("empty_notes"),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Text(
                                text = if (currentLanguage == "ar") {
                                    "هل تريد استكشاف ما يمكنك فعله؟ أضف دليل الميزات الآمن لمعرفة كيفية تنسيق الملاحظات بأسلوب ماركداون، الحماية بالبصمة الحيوية، النسخ الاحتياطي المشفر والمزيد!"
                                } else if (currentLanguage == "fr") {
                                    "Envie de voir ce que vous pouvez faire ? Ajoutez notre guide des fonctionnalités SafeVault pour explorer le formatage Markdown, la protection biométrique, et les sauvegardes sécurisées !"
                                } else {
                                    "Want to see what you can do? Add our ultimate features guide note to explore rich Markdown formatting, biometric locking, military-grade backups, and self-destruct protection!"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            Button(
                                onClick = {
                                    val (noteTitle, noteContent) = when (currentLanguage) {
                                        "ar" -> Pair(
                                            "دليل ميزات SafeVault 🔐",
                                            "مرحباً بك في الخزنة الآمنة SafeVault – مدير كلمات المرور والملاحظات عالية الأمان. يتم تشفير هذه الملاحظة بالكامل باستخدام خوارزمية AES-GCM 256 بت من الدرجة العسكرية.\n\nإليك تفصيل لأهم الميزات:\n\n📌 1. الحماية الحيوية (البصمة)\nقم بتفعيل \"قفل البصمة\" على أي ملاحظة حساسة. عند تفعيلها، سيتطلب عرض الملاحظة أو حذفها استخدام بصمة إصبعك أو وجهك لحماية أسرارك!\n\n📌 2. التنسيق الغني للملاحظات\nاستخدم شريط التنسيق المرن لتنظيم ملاحظاتك باستخدام عناصر ماركداون:\n- **نص عريض** للتأكيد\n- *نص مائل* للتوضيحات\n- __نص مسطر__ للتركيز\n- ~~نص مشطوب~~ للمهام المكتملة\n- `رموز برمجية` للمصطلحات الخاصة\n- • قوائم نقطية منظمة\n\n📌 3. آلية التدمير الذاتي\nقم بتفعيلها من الإعدادات. إذا حاول أي شخص تخمين رمز المرور الرئيسي الخاص بك، ستقوم الخزنة تلقائياً بمسح وتدمير كافة قواعد البيانات وملفات الخزنة بعد 5 محاولات خاطئة متتالية!\n\n📌 4. النسخ الاحتياطي الآمن بدون إنترنت\nقم بتصدير وإرسال جميع بياناتك وملاحظاتك المشفرة في ملف واحد آمن، وحفظه مباشرة بجهازك دون أي تعقيد.\n\nنصيحة: اضغط على أيقونة \"النسخ\" أو \"المشاركة\" على كارت الملاحظة لنسخ النص المشفر بسرعة خاطفة بدون الحاجة لفتحها وتعديلها!"
                                        )
                                        "fr" -> Pair(
                                            "Guide de SafeVault 🔐",
                                            "Bienvenue sur SafeVault – Votre gestionnaire de mots de passe et de notes hautement sécurisé. Cette note est protégée par un chiffrement AES-GCM 256 bits de niveau militaire.\n\nVoici un aperçu de nos fonctionnalités phares :\n\n📌 1. PROTECTION BIOMÉTRIQUE\nActivez la \"Protection Biométrique\" sur vos notes sensibles. Une fois active, la lecture ou la suppression nécessite votre empreinte digitale pour une confidentialité absolue.\n\n📌 2. FORMATAGE ENRICHI\nUtilisez notre barre d'outils pour rédiger vos notes avec des balises de style :\n- **Texte en gras** pour insister\n- *Texte en italique* pour les annotations\n- __Texte souligné__ pour surligner\n- ~~Texte barré~~ pour les tâches terminées\n- `code informatique` pour vos secrets techniques\n- • Des listes à puces élégantes\n\n📌 3. MÉCANISME D'AUTO-DESTRUCTION\nActivez cela dans les Paramètres. Si un intrus tente de deviner votre PIN, SafeVault détruira automatiquement vos données après 5 tentatives infructueuses.\n\n📌 4. SAUVEGARDES OFFLINE\nExportez vos authentifications et notes au format sécurisé pour les stocker sur votre appareil.\n\nAstuce de pro : Cliquez directement sur les boutons \"Copier\" ou \"Partager\" d'une carte de note pour un accès ultra-rapide !"
                                        )
                                        else -> Pair(
                                            "SafeVault Features & Guide 🔐",
                                            "Welcome to SafeVault – Your ultimate sovereign password & secure notes manager. Under the hood, this note is protected with military-grade 256-bit AES-GCM encryption.\n\nHere is a breakdown of our high-level features:\n\n📌 1. BIO PROTECTION\nToggle \"Biometric Lock\" on any sensitive note. When active, viewing or deleting that note requires your device fingerprint/face authentication, shielding secrets from prying eyes!\n\n📌 2. RICH FORMATTING\nUse our formatting action bar to compose notes with markdown layout elements:\n- **Bold text** for emphasis\n- *Italic text* for annotations\n- __Underline text__ for highlights\n- ~~Strikethrough~~ for completed checklist items\n- `inline code` for APIs & parameters\n- • Beautiful bullet lists\n\n📌 3. SELF-DESTRUCT MECHANISM\nEnable this trigger in Settings. If a threat tries guessing your master PIN, SafeVault automatically shreds and wipes all local database records after 5 consecutive failed entries!\n\n📌 4. SAFE OFFLINE BACKUPS\nUse the Export option under settings to package all logins and notes into a robust, encrypted, offline-first backup file. The backup file is saved directly to your device and is immune to timing bugs!\n\nPro-Tip: Tap the \"Copy\" icon or the \"Share\" icon on any unlocked note card to quickly copy or send your encrypted memories without even editing them!"
                                        )
                                    }

                                    val featuresNote = NoteEntry(
                                        title = noteTitle,
                                        encryptedContent = noteContent,
                                        category = "Other",
                                        categoryLabel = if (currentLanguage == "ar") "دليل" else "Guide",
                                        categoryColor = "#A78BFA",
                                        requiresBiometric = false
                                    )
                                    viewModel.addNote(featuresNote)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (currentLanguage == "ar") "إنشاء دليل ميزات التطبيق" else if (currentLanguage == "fr") "Générer le Guide de SafeVault" else "Add Features Guide Note",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // ---------------- BREADCRUMBS AND ADD FOLDER BAR ----------------
                if (searchQuery.isEmpty()) {
                    var showAddFolderDialog by remember { mutableStateOf(false) }
                    var newFolderName by remember { mutableStateOf("") }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Breadcrumbs path
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Active Folder Directory",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )

                                // Home / Root node
                                Text(
                                    text = t("root_directory"),
                                    color = if (currentFolderPath.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (currentFolderPath.isEmpty()) FontWeight.Bold else FontWeight.Medium,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.clickable { currentFolderPath = "" }
                                )

                                if (currentFolderPath.isNotEmpty()) {
                                    val segments = currentFolderPath.split("/")
                                    var accum = ""
                                    for (i in segments.indices) {
                                        val seg = segments[i]
                                        accum = if (accum.isEmpty()) seg else "$accum/$seg"
                                        val thisAccum = accum

                                        Text(text = "/", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)

                                        Text(
                                            text = seg,
                                            color = if (i == segments.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (i == segments.size - 1) FontWeight.Bold else FontWeight.Medium,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.clickable { currentFolderPath = thisAccum }
                                        )
                                    }
                                }
                            }

                            // New folder button
                            IconButton(
                                onClick = {
                                    newFolderName = ""
                                    showAddFolderDialog = true
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = t("add_folder"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (showAddFolderDialog) {
                        AlertDialog(
                            onDismissRequest = { showAddFolderDialog = false },
                            title = { Text(text = t("new_folder"), fontWeight = FontWeight.Bold) },
                            text = {
                                OutlinedTextField(
                                    value = newFolderName,
                                    onValueChange = { newFolderName = it },
                                    label = { Text(text = t("folder_name"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val nameTrimmed = newFolderName.trim()
                                        if (nameTrimmed.isNotEmpty()) {
                                            val fullNewPath = if (currentFolderPath.isEmpty()) nameTrimmed else "$currentFolderPath/$nameTrimmed"
                                            viewModel.addFolder(fullNewPath)
                                        }
                                        showAddFolderDialog = false
                                    }
                                ) {
                                    Text(text = t("add_folder"))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddFolderDialog = false }) {
                                    Text(text = t("cancel"))
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }

                // Folder deletion confirmation dialog
                var folderToDelete by remember { mutableStateOf<String?>(null) }
                var showFolderDeleteConfirm by remember { mutableStateOf(false) }

                if (showFolderDeleteConfirm && folderToDelete != null) {
                    val pathToDelete = folderToDelete!!
                    val subNotes = notes.filter { it.folderPath == pathToDelete || it.folderPath.startsWith("$pathToDelete/") }
                    val isFolderEmpty = subNotes.isEmpty()

                    AlertDialog(
                        onDismissRequest = { showFolderDeleteConfirm = false },
                        title = { Text(text = t("delete_folder"), fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                text = if (isFolderEmpty) "Are you sure you want to delete this folder?" else t("delete_folder_confirm")
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (subNotes.isNotEmpty()) {
                                        viewModel.deleteNotesBatch(subNotes)
                                    }
                                    viewModel.removeFolder(pathToDelete)
                                    showFolderDeleteConfirm = false
                                    folderToDelete = null
                                    if (currentFolderPath == pathToDelete || currentFolderPath.startsWith("$pathToDelete/")) {
                                        currentFolderPath = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(text = t("batch_delete"), color = MaterialTheme.colorScheme.onError)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showFolderDeleteConfirm = false }) {
                                Text(text = t("cancel"))
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                if (currentLevelFolders.isEmpty() && currentLevelNotes.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val composition by com.airbnb.lottie.compose.rememberLottieComposition(com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(com.example.R.raw.empty_animation))
                                com.airbnb.lottie.compose.LottieAnimation(
                                    composition = composition,
                                    iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                    modifier = Modifier.size(100.dp)
                                )
                                Text(
                                    text = "This folder is empty",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Create folders to build a hierarchy or add a secure encrypted note inside this directory.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        // Render Subfolders first
                        items(currentLevelFolders) { folderName ->
                            val fullPath = if (currentFolderPath.isEmpty()) folderName else "$currentFolderPath/$folderName"
                            val directCount = notes.count { it.folderPath == fullPath }
                            val nestedCount = notes.count { it.folderPath == fullPath || it.folderPath.startsWith("$fullPath/") }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable {
                                        currentFolderPath = fullPath
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Folder Icon",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(32.dp)
                                        )

                                        Column {
                                            Text(
                                                text = folderName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (nestedCount == 1) "1 item" else "$nestedCount items",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            folderToDelete = fullPath
                                            showFolderDeleteConfirm = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = t("delete_folder"),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Render Notes inside this directory level
                        items(currentLevelNotes) { note ->
                            val isUnlocked = !note.requiresBiometric || (unlockedNotes[note.id] == true)
                            val isSelected = selectedNoteIds.contains(note.id)
                            NoteItemRow(
                                note = note,
                                viewModel = viewModel,
                                currentLanguage = currentLanguage,
                                isUnlocked = isUnlocked,
                                isSelectionMode = selectedNoteIds.isNotEmpty(),
                                isSelected = isSelected,
                                onItemClicked = {
                                    if (selectedNoteIds.isNotEmpty()) {
                                        selectedNoteIds = if (isSelected) {
                                            selectedNoteIds - note.id
                                        } else {
                                            selectedNoteIds + note.id
                                        }
                                    } else {
                                        if (note.requiresBiometric && !isUnlocked) {
                                            if (activity != null && biometricsEnabledGlobally) {
                                                val executor = ContextCompat.getMainExecutor(context)
                                                val biometricPrompt = BiometricPrompt(activity, executor,
                                                    object : BiometricPrompt.AuthenticationCallback() {
                                                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                            super.onAuthenticationSucceeded(result)
                                                            unlockedNotes[note.id] = true
                                                            selectedNoteForEdit = note
                                                            showEditorDialog = true
                                                        }
                                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                            super.onAuthenticationError(errorCode, errString)
                                                        }
                                                        override fun onAuthenticationFailed() {
                                                            super.onAuthenticationFailed()
                                                        }
                                                    })

                                                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                    .setTitle(note.title)
                                                    .setSubtitle(t("biometric_unlock_required"))
                                                    .setNegativeButtonText(t("cancel"))
                                                    .build()

                                                biometricPrompt.authenticate(promptInfo)
                                            } else {
                                                android.widget.Toast.makeText(context, t("requires_biometrics_on_device"), android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            selectedNoteForEdit = note
                                            showEditorDialog = true
                                        }
                                    }
                                },
                                onLongClick = {
                                    selectedNoteIds = if (isSelected) {
                                        selectedNoteIds - note.id
                                    } else {
                                        selectedNoteIds + note.id
                                    }
                                },
                                onDelete = {
                                    noteToDelete = note
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Interactive Note Editor dialog
    if (showEditorDialog) {
        NoteEditorDialog(
            noteEntry = selectedNoteForEdit,
            isBiometricsEnabled = biometricsEnabledGlobally,
            currentLanguage = currentLanguage,
            defaultFolderPath = currentFolderPath,
            onDismiss = { showEditorDialog = false },
            onSave = { title, body, category, catLabel, catColor, requiresBio, folderPath ->
                val updatedNote = selectedNoteForEdit?.copy(
                    title = title,
                    encryptedContent = body,
                    category = category,
                    categoryLabel = catLabel,
                    categoryColor = catColor,
                    requiresBiometric = requiresBio,
                    folderPath = folderPath,
                    lastModifiedDate = System.currentTimeMillis()
                ) ?: NoteEntry(
                    title = title,
                    encryptedContent = body,
                    category = category,
                    categoryLabel = catLabel,
                    categoryColor = catColor,
                    requiresBiometric = requiresBio,
                    folderPath = folderPath
                )
                viewModel.addNote(updatedNote)
                showEditorDialog = false
            }
        )
    }

    // Batch Categorize Dialog Modal for Notes
    if (showBatchCategorizeDialog) {
        NoteBatchCategorizeDialog(
            currentLanguage = currentLanguage,
            viewModel = viewModel,
            onDismiss = { showBatchCategorizeDialog = false },
            onSave = { finalCategory: String, finalLabel: String, finalColor: String ->
                viewModel.categorizeNotesBatch(
                    notes.filter { it.id in selectedNoteIds },
                    finalCategory,
                    finalLabel,
                    finalColor
                )
                selectedNoteIds = emptySet()
                showBatchCategorizeDialog = false
            }
        )
    }

    // Batch Delete Confirm Dialog Modal for Notes
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Text(text = t("batch_delete"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = String.format(t("batch_delete_confirm"), selectedNoteIds.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNotesBatch(notes.filter { it.id in selectedNoteIds })
                        selectedNoteIds = emptySet()
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

    // Individual Delete Confirm Dialog Modal for Notes
    if (noteToDelete != null) {
        val targetNote = noteToDelete!!
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = {
                Text(text = t("delete"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = t("confirm_delete"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isUnlocked = !targetNote.requiresBiometric || (unlockedNotes[targetNote.id] == true)
                        if (targetNote.requiresBiometric && !isUnlocked) {
                            if (activity != null && biometricsEnabledGlobally) {
                                val executor = ContextCompat.getMainExecutor(context)
                                val biometricPrompt = BiometricPrompt(activity, executor,
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                            super.onAuthenticationSucceeded(result)
                                            viewModel.deleteNote(targetNote)
                                            noteToDelete = null
                                        }
                                    })
                                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                    .setTitle(targetNote.title)
                                    .setSubtitle(t("biometric_unlock_required"))
                                    .setNegativeButtonText(t("cancel"))
                                    .build()
                                biometricPrompt.authenticate(promptInfo)
                            } else {
                                android.widget.Toast.makeText(context, t("requires_biometrics_on_device"), android.widget.Toast.LENGTH_LONG).show()
                                noteToDelete = null
                            }
                        } else {
                            viewModel.deleteNote(targetNote)
                            noteToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) {
                    Text(t("delete"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text(t("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItemRow(
    note: NoteEntry,
    viewModel: VaultViewModel,
    currentLanguage: String,
    isUnlocked: Boolean = true,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onItemClicked: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val rawBody = if (isUnlocked) viewModel.decryptNote(note.encryptedContent) else ""
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateText = sdf.format(Date(note.lastModifiedDate))

    val t = { key: String -> Localizer.t(key, currentLanguage) }

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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    if (note.requiresBiometric) {
                        Icon(
                            imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Requires Biometric Unlock Badge",
                            tint = if (isUnlocked) Color(0xFF10B981) else Color(0xFFF43F5E),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                val context = LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isUnlocked && rawBody.isNotBlank()) {
                        IconButton(
                            onClick = {
                                com.example.security.ClipboardHelper.copyToClipboard(context, rawBody)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Note Content",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, note.title)
                                    putExtra(android.content.Intent.EXTRA_TEXT, "${note.title}\n\n$rawBody")
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share Note"))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Note",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Render a short snippet of the decrypted note content (or masked if locked)
            val previewText = if (isUnlocked) {
                rawBody.take(100) + (if (rawBody.length > 100) "..." else "")
            } else {
                "••••••••••••••••••••"
            }
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isUnlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

                val catColor = if (note.categoryColor.isNotBlank()) {
                    try {
                        Color(android.graphics.Color.parseColor(note.categoryColor))
                    } catch (e: Exception) {
                        Color(0xFF10B981)
                    }
                } else {
                    when (note.category.lowercase()) {
                        "personal" -> Color(0xFF818CF8) // Indigo
                        "work" -> Color(0xFF34D399) // Emerald
                        "finance" -> Color(0xFFFBBF24) // Yellow
                        else -> Color(0xFF94A3B8)
                    }
                }
                val badgeText = if (note.categoryLabel.isNotBlank()) note.categoryLabel else note.category
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(catColor.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = badgeText, style = MaterialTheme.typography.labelSmall, color = catColor, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
fun NoteEditorDialog(
    noteEntry: NoteEntry?,
    isBiometricsEnabled: Boolean,
    currentLanguage: String,
    defaultFolderPath: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Boolean, String) -> Unit
) {
    val t = { key: String -> Localizer.t(key, currentLanguage) }

    var title by remember { mutableStateOf(noteEntry?.title ?: "") }
    var folderPath by remember { mutableStateOf(noteEntry?.folderPath ?: defaultFolderPath) }
    // Initialize decrypted content if editing, otherwise empty
    var body by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(noteEntry?.category ?: "Personal") }

    var isCustomCategory by remember { mutableStateOf(noteEntry != null && noteEntry.categoryColor.isNotBlank() && !listOf("personal", "work", "finance", "other").contains(noteEntry.category.lowercase())) }
    var customCategoryLabel by remember { mutableStateOf(if (isCustomCategory) (noteEntry?.categoryLabel ?: noteEntry?.category ?: "") else "") }
    var selectedColorHex by remember { mutableStateOf(if (noteEntry != null && noteEntry.categoryColor.isNotBlank()) noteEntry.categoryColor else "#818CF8") }
    var requiresBiometric by remember { mutableStateOf(noteEntry?.requiresBiometric ?: false) }

    if (noteEntry != null && body.isEmpty() && noteEntry.encryptedContent.isNotEmpty()) {
        try {
            // Decrypt on load
            body = com.example.security.CryptoManager.decrypt(noteEntry.encryptedContent)
        } catch (e: Exception) {
            body = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (noteEntry == null) t("add_note") else t("edit_note"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("note_title"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                    value = folderPath,
                    onValueChange = { folderPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("folder"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    placeholder = { Text("e.g. Work or Work/Secrets", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // RICH STYLING ACTION BAR (LazyRow for smooth horizontal scrolling)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        TextButton(
                            onClick = { body += "**BoldText**" },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "B", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    item {
                        TextButton(
                            onClick = { body += "*ItalicText*" },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "I", fontStyle = FontStyle.Italic, fontSize = 13.sp)
                        }
                    }
                    item {
                        TextButton(
                            onClick = { body += "__UnderlineText__" },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "U", style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), fontSize = 13.sp)
                        }
                    }
                    item {
                        TextButton(
                            onClick = { body += "~~Strikethrough~~" },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "S", style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), fontSize = 13.sp)
                        }
                    }
                    item {
                        TextButton(
                            onClick = { body += "==Highlighted==" },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "Highlight", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    item {
                        TextButton(
                            onClick = { body += "`code`" },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "</>", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                    item {
                        TextButton(
                            onClick = { body += "\n• Item" },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "• Bullet", fontSize = 12.sp)
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        label = { Text(t("note_body"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    val words = body.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                    val characters = body.length
                    Text(
                        text = "Words: $words | Characters: $characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp, end = 4.dp)
                    )
                }

                Column {
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
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isColorSelected) 2.dp else 1.dp,
                                            color = if (isColorSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable { selectedColorHex = hex }
                                )
                            }
                        }
                    }
                }

                // Biometric lock toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("biometric_lock_note"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isBiometricsEnabled) "Requires extra biometric authorization when opening or deleting." else t("requires_biometrics_on_device"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBiometricsEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = requiresBiometric && isBiometricsEnabled,
                        onCheckedChange = {
                            if (isBiometricsEnabled) {
                                requiresBiometric = it
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        enabled = isBiometricsEnabled
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && body.isNotBlank()) {
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
                        onSave(
                            title,
                            body,
                            finalCategory,
                            finalLabel,
                            finalColor,
                            requiresBiometric,
                            folderPath.trim().removePrefix("/").removeSuffix("/")
                        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteBatchCategorizeDialog(
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
