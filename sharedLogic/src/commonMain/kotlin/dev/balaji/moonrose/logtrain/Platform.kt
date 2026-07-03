package dev.balaji.moonrose.logtrain

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform