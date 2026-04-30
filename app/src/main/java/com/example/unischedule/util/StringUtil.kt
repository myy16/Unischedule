package com.example.unischedule.util

import java.util.Locale
import kotlin.random.Random

object StringUtil {

    private val turkishCharMap = mapOf(
        'ş' to 's', 'Ş' to 's', 'ç' to 'c', 'Ç' to 'c',
        'ü' to 'u', 'Ü' to 'u', 'ö' to 'o', 'Ö' to 'o',
        'ğ' to 'g', 'Ğ' to 'g', 'ı' to 'i', 'İ' to 'i'
    )

    private val initialPasswordCharset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun formatUsername(username: String?): String {
        if (username.isNullOrBlank()) return "Guest"
        return username.split("_", "-")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    fun String.normalizeTurkish(): String =
        map { turkishCharMap[it] ?: it }.joinToString("").lowercase(Locale.getDefault())

    /**
     * Phase 2: Strips academic titles from a full name.
     * Example: "Assoc. Prof. Dr. Halit Bakır" → "Halit Bakır"
     */
    fun stripAcademicTitle(fullName: String): String {
        val titles = listOf(
            "Prof. Dr.", "Assoc. Prof. Dr.", "Assist. Prof. Dr.",
            "Prof.", "Assoc. Prof.", "Assist. Prof.", "Dr.",
            "Öğr. Gör.", "Arş. Gör.", "Doç. Dr.", "Yrd. Doç. Dr."
        )
        var name = fullName.trim()
        for (title in titles) {
            if (name.startsWith(title, ignoreCase = true)) {
                name = name.removePrefix(title).trim()
                break
            }
        }
        return name
    }

    fun generateUsername(fullName: String): String {
        val cleanedName = stripAcademicTitle(fullName)
        val parts = cleanedName
            .trim()
            .split(Regex("[\\s_\\-]+"))
            .filter { it.isNotBlank() }

        return when {
            parts.size >= 2 -> "${parts.first().normalizeTurkish()}_${parts.last().normalizeTurkish()}"
            parts.size == 1 -> parts.first().normalizeTurkish()
            else -> "lecturer"
        }
    }

    fun generateInitialPassword(length: Int = 6): String {
        return buildString(length) {
            repeat(length) {
                append(initialPasswordCharset.random(Random.Default))
            }
        }
    }
}
