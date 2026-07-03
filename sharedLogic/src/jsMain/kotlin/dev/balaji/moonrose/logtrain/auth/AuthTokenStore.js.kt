package dev.balaji.moonrose.logtrain.auth

import kotlinx.browser.localStorage

private const val TOKEN_KEY = "dev.balaji.moonrose.logtrain.auth.token"

class LocalStorageAuthTokenStore : AuthTokenStore {
    override fun getToken(): String? = localStorage.getItem(TOKEN_KEY)

    override fun setToken(token: String) {
        localStorage.setItem(TOKEN_KEY, token)
    }

    override fun clearToken() {
        localStorage.removeItem(TOKEN_KEY)
    }
}

actual fun createAuthTokenStore(): AuthTokenStore = LocalStorageAuthTokenStore()
