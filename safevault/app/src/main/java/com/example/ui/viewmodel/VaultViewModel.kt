package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SafeVaultDatabase
import com.example.data.entity.NoteEntry
import com.example.data.entity.PasswordEntry
import com.example.data.repository.VaultRepository
import com.example.security.SecurePreferenceHelper
import com.example.security.SecureCsvHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.SecureRandom

enum class NoteSortField {
    LAST_MODIFIED,
    CREATED_DATE
}

enum class NoteSortOrder {
    ASCENDING,
    DESCENDING
}

class VaultViewModel(
    private val repository: VaultRepository,
    private val context: Context
) : ViewModel() {

    // APPS SECURITY STATE
    private val _isConfigured = MutableStateFlow(SecurePreferenceHelper.isConfigured())
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _isLocked = MutableStateFlow(SecurePreferenceHelper.isConfigured())
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isSelfDestructed = MutableStateFlow(false)
    val isSelfDestructed: StateFlow<Boolean> = _isSelfDestructed.asStateFlow()

    private val _currentLanguage = MutableStateFlow(SecurePreferenceHelper.getLanguage())
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _isDarkMode = MutableStateFlow(SecurePreferenceHelper.isDarkMode())
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _activeTab = MutableStateFlow("vault") // "vault", "notes", "gen", "settings"
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    // CLIPBOARD SECURITY TIMER
    private val _clipboardDelay = MutableStateFlow(SecurePreferenceHelper.getClipboardDelay())
    val clipboardDelay: StateFlow<Int> = _clipboardDelay.asStateFlow()

    // AUTO-LOCK INACTIVITY TIMEOUT (in minutes)
    private val _autoLockTimeout = MutableStateFlow(SecurePreferenceHelper.getAutoLockTimeout())
    val autoLockTimeout: StateFlow<Int> = _autoLockTimeout.asStateFlow()

    // CREATED FOLDERS SYSTEM
    private val _createdFolders = MutableStateFlow(SecurePreferenceHelper.getCreatedFolders())
    val createdFolders: StateFlow<Set<String>> = _createdFolders.asStateFlow()

    // BIOMETRICS & SELF-DESTRUCT PREFS
    private val _biometricsEnabled = MutableStateFlow(SecurePreferenceHelper.isBiometricsEnabled())
    val biometricsEnabled: StateFlow<Boolean> = _biometricsEnabled.asStateFlow()

    private val _selfDestructEnabled = MutableStateFlow(SecurePreferenceHelper.isSelfDestructEnabled())
    val selfDestructEnabled: StateFlow<Boolean> = _selfDestructEnabled.asStateFlow()

    // BACKUP AND RESTORE STATE ENGINES
    val exportPayloadText = MutableStateFlow("")
    val importPayloadText = MutableStateFlow("")

    // SEARCH & FILTER STATE
    private val _passwordSearchQuery = MutableStateFlow("")
    val passwordSearchQuery: StateFlow<String> = _passwordSearchQuery.asStateFlow()

    private val _passwordCategoryFilter = MutableStateFlow("")
    val passwordCategoryFilter: StateFlow<String> = _passwordCategoryFilter.asStateFlow()

    private val _noteSearchQuery = MutableStateFlow("")
    val noteSearchQuery: StateFlow<String> = _noteSearchQuery.asStateFlow()

    private val _noteCategoryFilter = MutableStateFlow("")
    val noteCategoryFilter: StateFlow<String> = _noteCategoryFilter.asStateFlow()

    private val _noteSortField = MutableStateFlow(NoteSortField.LAST_MODIFIED)
    val noteSortField: StateFlow<NoteSortField> = _noteSortField.asStateFlow()

    private val _noteSortOrder = MutableStateFlow(NoteSortOrder.DESCENDING)
    val noteSortOrder: StateFlow<NoteSortOrder> = _noteSortOrder.asStateFlow()

    fun setNoteSortField(field: NoteSortField) {
        _noteSortField.value = field
    }

    fun setNoteSortOrder(order: NoteSortOrder) {
        _noteSortOrder.value = order
    }

    // REACTIVE LISTS FROM REPOSITORY
    val passwords: StateFlow<List<PasswordEntry>> = combine(
        repository.allPasswords,
        _passwordSearchQuery,
        _passwordCategoryFilter
    ) { list, search, cat ->
        list.filter {
            val matchesSearch = it.platformName.contains(search, ignoreCase = true) ||
                    it.username.contains(search, ignoreCase = true)
            val matchesCat = cat.isBlank() || it.category.equals(cat, ignoreCase = true)
            matchesSearch && matchesCat
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntry>> = combine(
        repository.allNotes,
        _noteSearchQuery,
        _noteCategoryFilter,
        _noteSortField,
        _noteSortOrder
    ) { list, search, cat, sortField, sortOrder ->
        val filtered = list.filter {
            val decryptedBody = try {
                decryptNote(it.encryptedContent)
            } catch (e: Exception) {
                ""
            }
            val matchesSearch = it.title.contains(search, ignoreCase = true) ||
                    decryptedBody.contains(search, ignoreCase = true)
            val matchesCat = cat.isBlank() || it.category.equals(cat, ignoreCase = true)
            matchesSearch && matchesCat
        }
        when (sortField) {
            NoteSortField.LAST_MODIFIED -> {
                if (sortOrder == NoteSortOrder.DESCENDING) {
                    filtered.sortedByDescending { it.lastModifiedDate }
                } else {
                    filtered.sortedBy { it.lastModifiedDate }
                }
            }
            NoteSortField.CREATED_DATE -> {
                if (sortOrder == NoteSortOrder.DESCENDING) {
                    filtered.sortedByDescending { it.createdDate }
                } else {
                    filtered.sortedBy { it.createdDate }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val passwordCategories: StateFlow<List<String>> = repository.allPasswords.map { list ->
        val defaults = listOf("Personal", "Work", "Finance", "Other")
        val custom = list.map { it.category }.filter { it.isNotBlank() && !defaults.map { d -> d.lowercase() }.contains(it.lowercase()) }.distinct()
        defaults + custom
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Personal", "Work", "Finance", "Other"))

    val noteCategories: StateFlow<List<String>> = repository.allNotes.map { list ->
        val defaults = listOf("Personal", "Work", "Finance", "Other")
        val custom = list.map { it.category }.filter { it.isNotBlank() && !defaults.map { d -> d.lowercase() }.contains(it.lowercase()) }.distinct()
        defaults + custom
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Personal", "Work", "Finance", "Other"))

    // PASSWORD GENERATOR STATE
    private val _generatedPassword = MutableStateFlow("")
    val generatedPassword: StateFlow<String> = _generatedPassword.asStateFlow()

    // INITIALIZATION
    init {
        _isConfigured.value = SecurePreferenceHelper.isConfigured()
        _isLocked.value = SecurePreferenceHelper.isConfigured()
    }

    // AUTH ACTIONS
    fun setupMasterPasscode(passcode: String): Boolean {
        val success = SecurePreferenceHelper.setupMasterPassword(passcode)
        if (success) {
            _isConfigured.value = true
            _isLocked.value = false
            SecurePreferenceHelper.resetFailedAttempts()
        }
        return success
    }

    fun unlockWithPasscode(passcode: String): Boolean {
        if (SecurePreferenceHelper.verifyMasterPassword(passcode)) {
            _isLocked.value = false
            SecurePreferenceHelper.resetFailedAttempts()
            return true
        } else {
            val failed = SecurePreferenceHelper.incrementFailedAttempts()
            if (SecurePreferenceHelper.isSelfDestructEnabled() && failed >= 5) {
                executeSelfDestruct()
            }
            return false
        }
    }

    fun lockVault() {
        if (_isConfigured.value) {
            _isLocked.value = true
        }
    }

    fun unlockBiometrically() {
        _isLocked.value = false
        SecurePreferenceHelper.resetFailedAttempts()
    }

    private fun executeSelfDestruct() {
        _isSelfDestructed.value = true
        _isLocked.value = true
        _isConfigured.value = false
        SecurePreferenceHelper.wipeEverything()
        viewModelScope.launch {
            SafeVaultDatabase.selfDestruct(context)
        }
    }

    // SET ACTIVE TAB
    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    // PASSWORD CONTROL
    fun addPassword(password: PasswordEntry) {
        viewModelScope.launch {
            repository.insertPassword(password)
        }
    }

    fun deletePassword(password: PasswordEntry) {
        viewModelScope.launch {
            repository.deletePassword(password)
        }
    }

    fun deletePasswordsBatch(passwordsToDelete: List<PasswordEntry>) {
        viewModelScope.launch {
            passwordsToDelete.forEach {
                repository.deletePassword(it)
            }
        }
    }

    fun categorizePasswordsBatch(passwordsToUpdate: List<PasswordEntry>, newCategory: String, newLabel: String, newColor: String) {
        viewModelScope.launch {
            passwordsToUpdate.forEach { entry ->
                val rawPass = decryptPassword(entry.encryptedPassword)
                val rawDesc = decryptDesc(entry.description)
                val updatedEntry = entry.copy(
                    encryptedPassword = rawPass,
                    description = rawDesc,
                    category = newCategory,
                    categoryLabel = newLabel,
                    categoryColor = newColor
                )
                repository.insertPassword(updatedEntry)
            }
        }
    }

    fun decryptPassword(encryptedPass: String): String {
        return repository.decryptPasswordValue(encryptedPass)
    }

    fun decryptDesc(encryptedDesc: String): String {
        if (encryptedDesc.isBlank()) return ""
        return repository.decryptPasswordValue(encryptedDesc)
    }

    // NOTE CONTROL
    fun addNote(note: NoteEntry) {
        viewModelScope.launch {
            repository.insertNote(note)
        }
    }

    fun deleteNote(note: NoteEntry) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun deleteNotesBatch(notesToDelete: List<NoteEntry>) {
        viewModelScope.launch {
            notesToDelete.forEach {
                repository.deleteNote(it)
            }
        }
    }

    fun categorizeNotesBatch(notesToUpdate: List<NoteEntry>, newCategory: String, newLabel: String, newColor: String) {
        viewModelScope.launch {
            notesToUpdate.forEach { entry ->
                val rawContent = decryptNote(entry.encryptedContent)
                val updatedEntry = entry.copy(
                    encryptedContent = rawContent,
                    category = newCategory,
                    categoryLabel = newLabel,
                    categoryColor = newColor
                )
                repository.insertNote(updatedEntry)
            }
        }
    }

    fun decryptNote(encryptedBody: String): String {
        return repository.decryptNoteValue(encryptedBody)
    }

    // SEARCH STATEMENTS
    fun setPasswordSearchQuery(query: String) {
        _passwordSearchQuery.value = query
    }

    fun setPasswordCategoryFilter(category: String) {
        _passwordCategoryFilter.value = category
    }

    fun setNoteSearchQuery(query: String) {
        _noteSearchQuery.value = query
    }

    fun setNoteCategoryFilter(category: String) {
        _noteCategoryFilter.value = category
    }

    // SETTINGS PANEL MODIFIERS
    fun toggleBiometrics(context: Context) {
        val nextMode = !_biometricsEnabled.value
        _biometricsEnabled.value = nextMode
        SecurePreferenceHelper.setBiometricsEnabled(nextMode)
    }

    fun toggleSelfDestruct() {
        val nextMode = !_selfDestructEnabled.value
        _selfDestructEnabled.value = nextMode
        SecurePreferenceHelper.setSelfDestructEnabled(nextMode)
    }

    fun cycleClipboardDelay() {
        val nextDelay = if (_clipboardDelay.value == 30) 60 else 30
        _clipboardDelay.value = nextDelay
        SecurePreferenceHelper.setClipboardDelay(nextDelay)
    }

    fun cycleAutoLockTimeout() {
        val nextTimeout = when (_autoLockTimeout.value) {
            0 -> 1
            1 -> 5
            5 -> 10
            else -> 0
        }
        _autoLockTimeout.value = nextTimeout
        SecurePreferenceHelper.setAutoLockTimeout(nextTimeout)
    }

    fun addFolder(path: String) {
        val normalized = path.trim().removePrefix("/").removeSuffix("/")
        if (normalized.isNotBlank()) {
            SecurePreferenceHelper.addCreatedFolder(normalized)
            _createdFolders.value = SecurePreferenceHelper.getCreatedFolders()
        }
    }

    fun removeFolder(path: String) {
        val normalized = path.trim().removePrefix("/").removeSuffix("/")
        SecurePreferenceHelper.removeCreatedFolder(normalized)
        _createdFolders.value = SecurePreferenceHelper.getCreatedFolders()
    }

    fun setAppLanguage(langCode: String) {
        _currentLanguage.value = langCode
        SecurePreferenceHelper.setLanguage(langCode)
    }

    fun toggleDarkMode() {
        val nextMode = !_isDarkMode.value
        _isDarkMode.value = nextMode
        SecurePreferenceHelper.setDarkMode(nextMode)
    }

    // PASSWORD STRENGTH GENERATION
    fun calculatePasswordStrength(password: String): String {
        if (password.length < 6) return "pw_strength_weak"
        var criteriaMet = 0
        if (password.any { it.isUpperCase() }) criteriaMet++
        if (password.any { it.isLowerCase() }) criteriaMet++
        if (password.any { it.isDigit() }) criteriaMet++
        if (password.any { "@#$%^&*()_+-=[]{}|;':\",./<>?".contains(it) }) criteriaMet++
        
        return when {
            password.length >= 12 && criteriaMet >= 4 -> "pw_strength_strong"
            password.length >= 8 && criteriaMet >= 3 -> "pw_strength_medium"
            else -> "pw_strength_weak"
        }
    }

    fun generateCustomPassword(
        length: Int,
        includeUpper: Boolean,
        includeLower: Boolean,
        includeNumbers: Boolean,
        includeSpecial: Boolean
    ) {
        val upperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowerChars = "abcdefghijklmnopqrstuvwxyz"
        val numbers = "0123456789"
        val special = "@#$%^&*()_+-"

        var allowedChars = ""
        if (includeUpper) allowedChars += upperChars
        if (includeLower) allowedChars += lowerChars
        if (includeNumbers) allowedChars += numbers
        if (includeSpecial) allowedChars += special

        if (allowedChars.isEmpty()) {
            _generatedPassword.value = ""
            return
        }

        val secureRandom = SecureRandom()
        val pw = StringBuilder()
        for (i in 0 until length) {
            val index = secureRandom.nextInt(allowedChars.length)
            pw.append(allowedChars[index])
        }
        _generatedPassword.value = pw.toString()
    }

    // CSV IMPORT / EXPORT ENGINES
    fun exportVaultSecurely(passwordPhrase: String, callback: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val pwList = repository.allPasswords.first()
                val notesList = repository.allNotes.first()
                val csvString = SecureCsvHelper.exportToUnencryptedCsv(
                    passwords = pwList,
                    notes = notesList,
                    decryptPassword = { repository.decryptPasswordValue(it) },
                    decryptDesc = { if (it.isBlank()) "" else repository.decryptPasswordValue(it) },
                    decryptNote = { repository.decryptNoteValue(it) }
                )
                val encrypted = SecureCsvHelper.encryptCsv(csvString, passwordPhrase)
                exportPayloadText.value = encrypted
                callback(encrypted)
            } catch (e: Exception) {
                e.printStackTrace()
                callback("")
            }
        }
    }

    fun importVaultSecurely(
        encryptedPayload: String,
        passwordPhrase: String,
        successCallback: (importedPasswords: Int, importedNotes: Int) -> Unit,
        errorCallback: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val csvString = SecureCsvHelper.decryptCsv(encryptedPayload, passwordPhrase)
                val (passwords, notes) = SecureCsvHelper.parseCsvToEntries(csvString)
                
                if (passwords.isEmpty() && notes.isEmpty()) {
                    errorCallback("No valid entries found inside decrypted package.")
                    return@launch
                }
                
                passwords.forEach { repository.insertPassword(it) }
                notes.forEach { repository.insertNote(it) }
                
                successCallback(passwords.size, notes.size)
            } catch (e: Exception) {
                e.printStackTrace()
                errorCallback(e.message ?: "Decryption failed. Please check your backup passphrase.")
            }
        }
    }
}
