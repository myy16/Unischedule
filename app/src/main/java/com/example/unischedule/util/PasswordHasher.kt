package com.example.unischedule.util

import java.security.MessageDigest

object PasswordHasher {
    fun sha256(rawPassword: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(rawPassword.toByteArray(Charsets.UTF_8))
        return hashedBytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}