package com.example.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "links",
    indices = [
        Index(value = ["url"]),
        Index(value = ["category"]),
        Index(value = ["timestamp"]),
        Index(value = ["userEmail"])
    ]
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "Uncategorized",
    val isFavorite: Boolean = false,
    val note: String = "",
    val userEmail: String = "",
    val openCount: Int = 0
)

@Immutable
data class LocalUser(
    val email: String,
    val name: String,
    val passcode: String,
    val passcodeType: String = "PIN",
    val biometricAllowed: Boolean = false,
    val lockEnabled: Boolean = false
)

