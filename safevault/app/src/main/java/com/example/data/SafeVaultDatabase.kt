package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.NoteDao
import com.example.data.dao.PasswordDao
import com.example.data.entity.NoteEntry
import com.example.data.entity.PasswordEntry
import com.example.security.SecurePreferenceHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(entities = [PasswordEntry::class, NoteEntry::class], version = 4, exportSchema = false)
abstract class SafeVaultDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao
    abstract fun noteDao(): NoteDao

    companion object {
        private const val DB_NAME = "safe_vault.db"
        @Volatile
        private var INSTANCE: SafeVaultDatabase? = null

        fun getInstance(context: Context): SafeVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val db = buildDatabase(context.applicationContext)
                    // Trigger database open to verify key is correct.
                    db.openHelper.writableDatabase
                    INSTANCE = db
                    db
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Destructive fallback: delete database file and recreate
                    try {
                        val dbFile = context.getDatabasePath(DB_NAME)
                        if (dbFile.exists()) {
                            dbFile.delete()
                        }
                        val dbJournal = File(dbFile.path + "-journal")
                        if (dbJournal.exists()) {
                            dbJournal.delete()
                        }
                        val dbWal = File(dbFile.path + "-wal")
                        if (dbWal.exists()) {
                            dbWal.delete()
                        }
                        val dbShm = File(dbFile.path + "-shm")
                        if (dbShm.exists()) {
                            dbShm.delete()
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                    val db = buildDatabase(context.applicationContext)
                    INSTANCE = db
                    db
                }
            }
        }

        private fun buildDatabase(context: Context): SafeVaultDatabase {
            // Load SQLCipher native libraries
            System.loadLibrary("sqlcipher")

            // Retrieve secure passphrase for SQLCipher database encryption
            val passphrase = SecurePreferenceHelper.getDbPassphrase()
            val factory = SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))

            return Room.databaseBuilder(
                context,
                SafeVaultDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Clears all tables, closes instance, and deletes the physically stored database files completely.
         */
        fun selfDestruct(context: Context) {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                    INSTANCE = null
                    
                    val dbFile = context.getDatabasePath(DB_NAME)
                    if (dbFile.exists()) {
                        dbFile.delete()
                    }
                    val dbJournal = File(dbFile.path + "-journal")
                    if (dbJournal.exists()) {
                        dbJournal.delete()
                    }
                    val dbWal = File(dbFile.path + "-wal")
                    if (dbWal.exists()) {
                        dbWal.delete()
                    }
                    val dbShm = File(dbFile.path + "-shm")
                    if (dbShm.exists()) {
                        dbShm.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
