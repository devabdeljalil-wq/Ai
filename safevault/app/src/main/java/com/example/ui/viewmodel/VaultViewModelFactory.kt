package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.SafeVaultDatabase
import com.example.data.repository.VaultRepository

class VaultViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
            val db = SafeVaultDatabase.getInstance(context)
            val repository = VaultRepository(db.passwordDao(), db.noteDao())
            @Suppress("UNCHECKED_CAST")
            return VaultViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
