package com.example.data.dao

import androidx.room.*
import com.example.data.entity.NoteEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY lastModifiedDate DESC")
    fun getAllNotes(): Flow<List<NoteEntry>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Int): NoteEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(entry: NoteEntry)

    @Delete
    suspend fun deleteNote(entry: NoteEntry)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)

    @Query("DELETE FROM notes")
    suspend fun clearNotes()
}
