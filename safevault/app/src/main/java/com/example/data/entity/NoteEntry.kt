package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "notes")
data class NoteEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val encryptedContent: String,
    val category: String,
    val categoryLabel: String = "",
    val categoryColor: String = "",
    val requiresBiometric: Boolean = false,
    val folderPath: String = "",
    val createdDate: Long = System.currentTimeMillis(),
    val lastModifiedDate: Long = System.currentTimeMillis()
) : Serializable
