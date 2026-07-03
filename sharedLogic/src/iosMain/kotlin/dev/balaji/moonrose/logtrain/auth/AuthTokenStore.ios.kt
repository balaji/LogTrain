package dev.balaji.moonrose.logtrain.auth

import platform.Foundation.NSUserDefaults

private const val TOKEN_KEY = "dev.balaji.moonrose.logtrain.auth.token"

// NSUserDefaults, not Keychain: simplest correct option for a minimal first pass.
// Keychain storage is the right hardening step before shipping a release build.
class UserDefaultsAuthTokenStore : AuthTokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getToken(): String? = defaults.stringForKey(TOKEN_KEY)

    override fun setToken(token: String) {
        defaults.setObject(token, TOKEN_KEY)
    }

    override fun clearToken() {
        defaults.removeObjectForKey(TOKEN_KEY)
    }
}

actual fun createAuthTokenStore(): AuthTokenStore = UserDefaultsAuthTokenStore()
