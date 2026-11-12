package com.cramdown.sorta.data

import java.util.UUID

// Copied from Stethoscore (data/Models.kt:5,89): a note and its ids, as the
// library file writes them, so step 3's import reads Stethoscore's export.

fun newId(): String = UUID.randomUUID().toString()

data class Note(val id: String = newId(), val title: String, val body: String = "", val folderId: String? = null, val kind: String = "idea", val links: List<String> = emptyList(), val tags: List<String> = emptyList(), val boardX: Double = 0.0, val boardY: Double = 0.0, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt, val rawJson: String = "{}")
