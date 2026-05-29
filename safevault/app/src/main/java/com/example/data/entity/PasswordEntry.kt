package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "passwords")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val platformName: String,
    val username: String,
    val encryptedPassword: String,
    val websiteUrl: String,
    val category: String,
    val description: String,
    val categoryLabel: String = "",
    val categoryColor: String = "",
    val createdDate: Long = System.currentTimeMillis(),
    val lastModifiedDate: Long = System.currentTimeMillis()
) : Serializable
