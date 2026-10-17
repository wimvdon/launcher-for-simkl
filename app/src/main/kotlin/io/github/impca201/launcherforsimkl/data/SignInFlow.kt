package io.github.impca201.launcherforsimkl.data

import io.github.impca201.launcherforsimkl.data.api.Parsers
import io.github.impca201.launcherforsimkl.data.api.PinStatus
import io.github.impca201.launcherforsimkl.data.api.SimklApi
import kotlinx.coroutines.delay
import java.io.IOException

sealed interface SignInState {
    data object Starting : SignInState

    /** The user enters [code] at [url] on a phone or computer. */
    data class ShowCode(
        val code: String,
        val url: String,
    ) : SignInState

    data object Expired : SignInState

    data object Done : SignInState

    data class Failed(
        val error: SyncError,
    ) : SignInState
}

/**
 * SIMKL's PIN sign-in, made for TVs: the launcher shows a short code, the user approves it at
 * simkl.com/pin, and the launcher checks every few seconds (as SIMKL says) until it gets a token or
 * the code expires.
 */
class SignInFlow(
    private val api: SimklApi,
    private val settings: SettingsStore,
    private val content: ContentRepository,
) {
    suspend fun run(update: (SignInState) -> Unit) {
        update(SignInState.Starting)
        if (!api.hasClientId) return update(SignInState.Failed(SyncError.NoClientId))
        val pin =
            try {
                Parsers.pinCode(api.startPin())
            } catch (_: IOException) {
                null
            } ?: return update(SignInState.Failed(SyncError.Offline))
        update(SignInState.ShowCode(pin.userCode, pin.verificationUrl))
        val deadline = System.currentTimeMillis() + pin.expiresInSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            delay(pin.intervalSeconds * 1000)
            val status =
                try {
                    Parsers.pinStatus(api.checkPin(pin.userCode))
                } catch (_: IOException) {
                    PinStatus.Pending
                }
            if (status is PinStatus.Approved) {
                settings.signIn(status.accessToken)
                content.refreshUser()
                content.syncNow()
                return update(SignInState.Done)
            }
        }
        update(SignInState.Expired)
    }
}
