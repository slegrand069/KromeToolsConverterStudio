package com.studiostefan.krometools

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform