package com.example.unischedule.data.session

object UserSession {
    var userId: Long? = null
    var userRole: Role? = null
    var userName: String? = null

    enum class Role {
        ADMIN, LECTURER, INSTRUCTOR
    }

    fun logout() {
        userId = null
        userRole = null
        userName = null
    }
}
