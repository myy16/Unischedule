package com.example.unischedule.util

object StringUtil {
    fun formatUsername(username: String?): String {
        if (username.isNullOrBlank()) return "Guest"
        return username.split("_", "-")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
