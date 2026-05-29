package com.example.security

import android.util.Base64
import android.util.Log
import com.example.data.entity.NoteEntry
import com.example.data.entity.PasswordEntry
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecureCsvHelper {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_LENGTH_BITS = 128

    // Headers for the flat CSV format
    private val CSV_HEADER = listOf(
        "entry_type", "platform_or_title", "username", "decrypted_secret",
        "url", "category", "description", "category_label",
        "category_color", "requires_biometric", "created_date", "last_modified_date"
    )

    /**
     * Helper to encode CSV values correctly (escapes quotes and wraps values in quotes).
     */
    private fun escapeCsvValue(value: Any?): String {
        val str = value?.toString() ?: ""
        val escaped = str.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * State-machine parser that handles double quoted cells and embedded newlines correctly.
     */
    fun parseCsvText(csvText: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentCell = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = csvText.length
        
        while (i < len) {
            val ch = csvText[i]
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < len && csvText[i + 1] == '\"') {
                        currentCell.append('\"')
                        i++ // skip escaped double quote
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentCell.append(ch)
                }
            } else {
                when (ch) {
                    '\"' -> {
                        inQuotes = true
                    }
                    ',' -> {
                        currentRow.add(currentCell.toString())
                        currentCell.setLength(0)
                    }
                    '\n', '\r' -> {
                        currentRow.add(currentCell.toString())
                        currentCell.setLength(0)
                        if (currentRow.any { it.isNotEmpty() } || result.isEmpty()) {
                            result.add(currentRow.toList())
                        }
                        currentRow.clear()
                        if (ch == '\r' && i + 1 < len && csvText[i + 1] == '\n') {
                            i++ // skip LF in CRLF
                        }
                    }
                    else -> {
                        currentCell.append(ch)
                    }
                }
            }
            i++
        }
        if (currentRow.isNotEmpty() || currentCell.isNotEmpty()) {
            currentRow.add(currentCell.toString())
            result.add(currentRow.toList())
        }
        return result
    }

    /**
     * Slices the list of database passwords and notes into a single unencrypted standard CSV text.
     */
    fun exportToUnencryptedCsv(
        passwords: List<PasswordEntry>,
        notes: List<NoteEntry>,
        decryptPassword: (String) -> String,
        decryptDesc: (String) -> String,
        decryptNote: (String) -> String
    ): String {
        val builder = java.lang.StringBuilder()
        // Write header
        builder.append(CSV_HEADER.joinToString(",")).append("\n")
        
        // Write passwords
        passwords.forEach { p ->
            val line = listOf(
                "password",
                p.platformName,
                p.username,
                decryptPassword(p.encryptedPassword),
                p.websiteUrl,
                p.category,
                decryptDesc(p.description),
                p.categoryLabel,
                p.categoryColor,
                "false",
                p.createdDate.toString(),
                p.lastModifiedDate.toString()
            ).joinToString(",") { escapeCsvValue(it) }
            builder.append(line).append("\n")
        }
        
        // Write notes
        notes.forEach { n ->
            val line = listOf(
                "note",
                n.title,
                "", // no username
                decryptNote(n.encryptedContent),
                "", // no url
                n.category,
                "", // no description
                n.categoryLabel,
                n.categoryColor,
                n.requiresBiometric.toString(),
                n.createdDate.toString(),
                n.lastModifiedDate.toString()
            ).joinToString(",") { escapeCsvValue(it) }
            builder.append(line).append("\n")
        }
        
        return builder.toString()
    }

    /**
     * Parses local CSV formatted cells into direct object models.
     */
    fun parseCsvToEntries(csvText: String): Pair<List<PasswordEntry>, List<NoteEntry>> {
        val passwords = mutableListOf<PasswordEntry>()
        val notes = mutableListOf<NoteEntry>()
        
        Log.i("SecureCsvHelper", "Beginning CSV parsing and schema validation.")
        
        if (csvText.isBlank()) {
            val errorMsg = "CSV structure validation failed: The decrypted CSV backup is empty or blank."
            Log.e("SecureCsvHelper", errorMsg)
            throw IllegalArgumentException(errorMsg)
        }

        val rows = try {
            parseCsvText(csvText)
        } catch (e: Exception) {
            val errorMsg = "CSV structure validation failed: Failed to tokenize CSV text due to syntax format issues. Details: ${e.localizedMessage}"
            Log.e("SecureCsvHelper", errorMsg, e)
            throw IllegalArgumentException(errorMsg, e)
        }
        
        Log.d("SecureCsvHelper", "Initially parsed ${rows.size} raw tokenized lines from CSV.")
        
        if (rows.isEmpty()) {
            val errorMsg = "CSV structure validation failed: No rows parsed from the backup text. The file is empty or lacks clear structure."
            Log.e("SecureCsvHelper", errorMsg)
            throw IllegalArgumentException(errorMsg)
        }
        
        val headers = rows[0].map { it.trim().lowercase() }
        val expectedHeadersStr = CSV_HEADER.joinToString(", ")
        val actualHeadersStr = headers.joinToString(", ")
        Log.d("SecureCsvHelper", "CSV Header Analysis -> Expected: [$expectedHeadersStr] | Found: [$actualHeadersStr]")
        
        // Ensure CSV has the correct exact number of columns matching the schema
        if (headers.size != CSV_HEADER.size) {
            val errorMsg = "CSV structure validation failed. Column count mismatch. Expected ${CSV_HEADER.size} columns, but the provided file contains ${headers.size}. Expected schema: [$expectedHeadersStr], but found: [$actualHeadersStr]."
            Log.e("SecureCsvHelper", errorMsg)
            throw IllegalArgumentException(errorMsg)
        }

        // Verify each column header matches the expected schema name and position exactly
        for (i in CSV_HEADER.indices) {
            val expected = CSV_HEADER[i].lowercase()
            val actual = headers[i]
            if (expected != actual) {
                val errorMsg = "CSV structure validation failed. Invalid column header name at position ${i + 1}. Expected: '$expected', Found: '$actual'. Full expected schema: [$expectedHeadersStr], but found: [$actualHeadersStr]."
                Log.e("SecureCsvHelper", errorMsg)
                throw IllegalArgumentException(errorMsg)
            }
        }
        
        Log.i("SecureCsvHelper", "CSV Schema validation passed successfully. Expected matching headers of ${CSV_HEADER.size} elements found.")
        
        if (rows.size <= 1) {
            Log.w("SecureCsvHelper", "CSV contains valid headers but does not contain any data rows to import.")
            return Pair(emptyList(), emptyList())
        }
        
        var skippedRowsCount = 0
        var successfullyParsedPasswordsCount = 0
        var successfullyParsedNotesCount = 0
        
        for (i in 1 until rows.size) {
            val columns = rows[i]
            if (columns.all { it.isBlank() }) {
                Log.d("SecureCsvHelper", "Skipping purely empty or blank row at CSV line index $i.")
                skippedRowsCount++
                continue
            }
            
            if (columns.size < 4) {
                Log.w("SecureCsvHelper", "Skipping malformed CSV line $i: Column count (${columns.size}) is less than the minimum required (top 4 fields). Content: ${columns.joinToString(", ")}")
                skippedRowsCount++
                continue
            }
            
            try {
                val type = columns.getOrNull(0) ?: ""
                val field1 = columns.getOrNull(1) ?: ""
                val field2 = columns.getOrNull(2) ?: ""
                val field3 = columns.getOrNull(3) ?: ""
                val field4 = columns.getOrNull(4) ?: ""
                val field5 = columns.getOrNull(5) ?: ""
                val field6 = columns.getOrNull(6) ?: ""
                val field7 = columns.getOrNull(7) ?: ""
                val field8 = columns.getOrNull(8) ?: ""
                val field9 = columns.getOrNull(9) ?: "false"
                val field10 = columns.getOrNull(10) ?: ""
                val field11 = columns.getOrNull(11) ?: ""
                
                if (type != "password" && type != "note") {
                    Log.w("SecureCsvHelper", "Skipping CSV line $i: Unknown entry type '$type' found. Supported types are 'password' or 'note'.")
                    skippedRowsCount++
                    continue
                }
                
                if (type == "password") {
                    if (field1.isBlank()) {
                        Log.w("SecureCsvHelper", "Skipping password CSV line $i: platform_or_title/platformName field is empty.")
                        skippedRowsCount++
                        continue
                    }
                    if (field3.isBlank()) {
                        Log.w("SecureCsvHelper", "Skipping password CSV line $i: decrypted_secret/encryptedPassword field is empty.")
                        skippedRowsCount++
                        continue
                    }
                    
                    val passEntry = PasswordEntry(
                        platformName = field1,
                        username = field2,
                        encryptedPassword = field3, // Repo inserts will automatically encrypt this plaintext field
                        websiteUrl = field4,
                        category = field5,
                        description = field6,
                        categoryLabel = field7,
                        categoryColor = field8,
                        createdDate = field10.toLongOrNull() ?: System.currentTimeMillis(),
                        lastModifiedDate = field11.toLongOrNull() ?: System.currentTimeMillis()
                    )
                    passwords.add(passEntry)
                    successfullyParsedPasswordsCount++
                    Log.d("SecureCsvHelper", "Parsed PasswordEntry successfully [platform: '${field1}', username: '${field2}'] at CSV line $i.")
                } else if (type == "note") {
                    if (field1.isBlank()) {
                        Log.w("SecureCsvHelper", "Skipping note CSV line $i: platform_or_title/title field is empty.")
                        skippedRowsCount++
                        continue
                    }
                    if (field3.isBlank()) {
                        Log.w("SecureCsvHelper", "Skipping note CSV line $i: decrypted_secret/encryptedContent field is empty.")
                        skippedRowsCount++
                        continue
                    }
                    
                    val noteEntry = NoteEntry(
                        title = field1,
                        encryptedContent = field3, // Repo inserts will automatically encrypt this plaintext field
                        category = field5,
                        categoryLabel = field7,
                        categoryColor = field8,
                        requiresBiometric = field9.lowercase() == "true",
                        createdDate = field10.toLongOrNull() ?: System.currentTimeMillis(),
                        lastModifiedDate = field11.toLongOrNull() ?: System.currentTimeMillis()
                    )
                    notes.add(noteEntry)
                    successfullyParsedNotesCount++
                    Log.d("SecureCsvHelper", "Parsed NoteEntry successfully [title: '${field1}'] at CSV line $i.")
                }
            } catch (ex: Exception) {
                Log.e("SecureCsvHelper", "Fatal error occurred when parsing row fields at CSV line $i. Skipping row. Details: ${ex.localizedMessage}", ex)
                skippedRowsCount++
            }
        }
        
        Log.i(
            "SecureCsvHelper", 
            "CSV import processing finished. Summary: [Total Rows Analyzed: ${rows.size - 1}, Successfully Parsed Passwords: $successfullyParsedPasswordsCount, Successfully Parsed Notes: $successfullyParsedNotesCount, Failed/Skipped Rows: $skippedRowsCount]"
        )
        return Pair(passwords, notes)
    }

    /**
     * AES-GCM password-based encryption key generation using PBKDF2 structure wrapper.
     */
    fun encryptCsv(csvString: String, passphrase: String): String {
        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_SIZE)
        secureRandom.nextBytes(salt)

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKeyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val derivedKeyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        val iv = ByteArray(IV_SIZE)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        val encryptedBytes = cipher.doFinal(csvString.toByteArray(Charsets.UTF_8))

        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        // Return a clean composite safe string format
        return "$saltBase64:$ivBase64:$encryptedBase64"
    }

    /**
     * Decrypts the given payload with specified passphrase. Returns unencrypted CSV.
     */
    fun decryptCsv(encryptedPayload: String, passphrase: String): String {
        val parts = encryptedPayload.split(":")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid backup payload format")
        }

        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(parts[2], Base64.NO_WRAP)

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKeyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val derivedKeyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
