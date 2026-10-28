package com.mercureal.nana

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Shared UI state written by NanaService and read by the Compose screen. */
object NanaState {
    private val listeningFlow = MutableStateFlow(false)
    private val statusFlow = MutableStateFlow("off")
    private val logFlow = MutableStateFlow<List<String>>(emptyList())

    val listening: StateFlow<Boolean> = listeningFlow
    val status: StateFlow<String> = statusFlow
    val log: StateFlow<List<String>> = logFlow

    fun setListening(on: Boolean, status: String) {
        listeningFlow.value = on
        statusFlow.value = status
    }

    @Synchronized
    fun addLog(line: String) {
        logFlow.value = (logFlow.value + line).takeLast(60)
    }
}
