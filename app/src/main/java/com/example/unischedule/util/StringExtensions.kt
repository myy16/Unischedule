package com.example.unischedule.util

fun String.toTitleCase(): String {
    return trim()
        .replace('_', ' ')
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { char -> char.titlecase() }
        }
}
