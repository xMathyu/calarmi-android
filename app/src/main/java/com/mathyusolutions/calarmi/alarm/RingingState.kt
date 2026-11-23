package com.mathyusolutions.calarmi.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Las alarmas que están sonando (la primera es la que se ve). Lo observa la pantalla. */
object RingingState {
    private val _queue = MutableStateFlow<List<RingItem>>(emptyList())
    val queue: StateFlow<List<RingItem>> = _queue.asStateFlow()

    val current: RingItem? get() = _queue.value.firstOrNull()

    internal fun set(items: List<RingItem>) {
        _queue.value = items
    }
}
