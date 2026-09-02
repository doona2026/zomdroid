package com.zomdroid.ui.state

import com.zomdroid.ui.common.UiText

sealed interface UiResult<out T> {
    data object Loading : UiResult<Nothing>
    data class Success<T>(val value: T) : UiResult<T>
    data class Error(val message: UiText) : UiResult<Nothing>
}
