package com.flixclusive.gradle.util

internal fun isValidFilename(filename: String): Boolean {
    for (c in filename) {
        if (isValidFilenameChar(c))
            continue

        return false
    }

    return true
}

internal fun isValidFilenameChar(c: Char): Boolean {
    val charByte = c.code.toByte()
    // Control characters (0x00 to 0x1F) are not allowed
    if (charByte in 0x00..0x1F) {
        return false
    }
    // Specific characters are also not allowed
    if (c.code == 0x7F) {
        return false
    }

    return when (c) {
        '"', '*', '/', ':', '<', '>', '?', '\\', '|' -> false
        else -> true
    }
}

