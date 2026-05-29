package com.example.data.repository

import com.example.data.dao.NoteDao
import com.example.data.dao.PasswordDao
import com.example.data.entity.NoteEntry
import com.example.data.entity.PasswordEntry
import com.example.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VaultRepository(
    private val passwordDao: PasswordDao,
    private val noteDao: NoteDao
) {
    // PASSWORDS FLOW
    val allPasswords: Flow<List<PasswordEntry>> = passwordDao.getAllPasswords()

    suspend fun getPasswordById(id: Int): PasswordEntry? = withContext(Dispatchers.IO) {
        passwordDao.getPasswordById(id)
    }

    suspend fun insertPassword(entry: PasswordEntry) = withContext(Dispatchers.IO) {
        // Transparent encryption of sensitive fields
        val securePassword = CryptoManager.encrypt(entry.encryptedPassword)
        val secureDesc = if (entry.description.isNotBlank()) {
            CryptoManager.encrypt(entry.description)
        } else {
            ""
        }
        
        val encryptedEntry = entry.copy(
            encryptedPassword = securePassword,
            description = secureDesc,
            lastModifiedDate = System.currentTimeMillis()
        )
        passwordDao.insertPassword(encryptedEntry)
    }

    suspend fun deletePassword(entry: PasswordEntry) = withContext(Dispatchers.IO) {
        passwordDao.deletePassword(entry)
    }

    suspend fun deletePasswordById(id: Int) = withContext(Dispatchers.IO) {
        passwordDao.deletePasswordById(id)
    }

    // NOTES FLOW
    val allNotes: Flow<List<NoteEntry>> = noteDao.getAllNotes()

    suspend fun getNoteById(id: Int): NoteEntry? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(id)
    }

    suspend fun insertNote(entry: NoteEntry) = withContext(Dispatchers.IO) {
        // Transparent encryption of note body content
        val secureContent = CryptoManager.encrypt(entry.encryptedContent)
        val encryptedEntry = entry.copy(
            encryptedContent = secureContent,
            lastModifiedDate = System.currentTimeMillis()
        )
        noteDao.insertNote(encryptedEntry)
    }

    suspend fun deleteNote(entry: NoteEntry) = withContext(Dispatchers.IO) {
        noteDao.deleteNote(entry)
    }

    suspend fun deleteNoteById(id: Int) = withContext(Dispatchers.IO) {
        noteDao.deleteNoteById(id)
    }

    // UTILITIES
    /**
     * Helper to safely decrypt a password entry values
     */
    fun decryptPasswordValue(encryptedPass: String): String {
        return CryptoManager.decrypt(encryptedPass)
    }

    /**
     * Helper to safely decrypt a note content values
     */
    fun decryptNoteValue(encryptedContent: String): String {
        return CryptoManager.decrypt(encryptedContent)
    }
}
