package com.zomdroid.ui.common

import android.content.Context
import androidx.annotation.StringRes

sealed interface UiText {
    data class Plain(val value: String) : UiText

    data class Resource(
        @StringRes val id: Int,
        val arguments: List<Any> = emptyList(),
    ) : UiText
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Plain -> value
    is UiText.Resource -> context.getString(id, *arguments.toTypedArray())
}
