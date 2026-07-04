package dev.balaji.moonrose.logtrain.auth

import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlin.js.JsExport

object MoonroseConfig {
    var baseUrl: String = "http://localhost:3000"
}

expect fun openInSystemBrowser(url: String)

// Computes the platform's OAuth redirect target. For Desktop this also starts
// the loopback server that will receive the callback, so it must be called
// right before opening the login URL, not eagerly at app startup.
expect fun prepareRedirectUri(onToken: (String) -> Unit): String

@JsExport
class LoginController(private val tokenStore: AuthTokenStore) {
    private val listeners = mutableListOf<() -> Unit>()

    fun isSignedIn(): Boolean = tokenStore.getToken() != null

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun startLogin() {
        val redirectUri = prepareRedirectUri(onToken = ::completeLoginWithToken)
        val url = "${MoonroseConfig.baseUrl}/mobile/login?redirect_uri=${redirectUri.encodeURLParameter()}"
        openInSystemBrowser(url)
    }

    fun completeLogin(callbackUrl: String) {
        val token = Url(callbackUrl).parameters["token"] ?: return
        completeLoginWithToken(token)
    }

    fun signOut() {
        tokenStore.clearToken()
        notifyListeners()
    }

    private fun completeLoginWithToken(token: String) {
        tokenStore.setToken(token)
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }
}

@JsExport
object AppAuth {
    val loginController = LoginController(createAuthTokenStore())
}
