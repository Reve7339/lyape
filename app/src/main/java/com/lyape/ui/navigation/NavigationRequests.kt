package com.lyape.ui.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Solicitudes de navegación que llegan desde una notificación o intent externo. */
object NavigationRequests {

    private val settingsChannel = Channel<Unit>(Channel.CONFLATED)
    val openSettings = settingsChannel.receiveAsFlow()

    fun requestOpenSettings() {
        settingsChannel.trySend(Unit)
    }
}
