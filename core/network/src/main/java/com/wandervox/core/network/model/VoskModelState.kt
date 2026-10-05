package com.wandervox.core.network.model

import java.io.File

sealed interface VoskModelState {
    data object Missing : VoskModelState
    data object Extracting : VoskModelState
    data class Ready(val directory: File) : VoskModelState
    data class Failed(val cause: Throwable) : VoskModelState
}
