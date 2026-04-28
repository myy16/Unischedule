package com.example.unischedule.data.repository

import com.example.unischedule.data.session.UserSession

data class AuthenticatedUser(
    val id: Long,
    val username: String,
    val role: UserSession.Role,
    val mustChangePassword: Boolean
)