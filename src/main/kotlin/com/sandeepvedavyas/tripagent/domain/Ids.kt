package com.sandeepvedavyas.tripagent.domain

object Ids {
    val SESSION: Regex = Regex("[A-Za-z0-9._-]{1,80}")

    fun isSession(value: String): Boolean = SESSION.matches(value)
}
