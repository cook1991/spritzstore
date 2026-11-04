package net.spritzlive.spritzstore.install

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries a terminal install outcome from [InstallResultReceiver] (which Android can
 * invoke as a fresh receiver instance, independent of whether a ViewModel exists) to
 * whichever [net.spritzlive.spritzstore.SpritzStoreViewModel] is currently alive, so the
 * row in the UI can leave "Installing..." without the person having to hit Refresh.
 */
data class InstallOutcome(val applicationId: String, val success: Boolean, val detail: String?)

object InstallResultBus {
    private val _results = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 4)
    val results: SharedFlow<InstallOutcome> = _results.asSharedFlow()

    fun post(outcome: InstallOutcome) {
        _results.tryEmit(outcome)
    }
}
