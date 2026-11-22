package edu.unicauca.multimedivida

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform