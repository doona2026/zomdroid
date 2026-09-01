package com.zomdroid.ui.settings

/** Compatibility codec for the CSV mapping persisted by GamepadManager. */
object GamepadMappingCodec {
    fun encode(mapping: IntArray): String = mapping.joinToString(",")

    fun decode(value: String?): IntArray? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(',')
        val result = parts.map { it.toIntOrNull() ?: return null }.toIntArray()
        return result.takeIf { it.isNotEmpty() }
    }
}
