package com.mipastudio.memostamp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun getCurrentEpochMillis(): Long
