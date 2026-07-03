package dev.balaji.moonrose.logtrain.auth

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_NAME = "logtrain_auth"
private const val TOKEN_KEY = "token"

class AndroidAuthTokenStore : AuthTokenStore {
    private val prefs by lazy {
        val context = AndroidContextHolder.appContext
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getToken(): String? = prefs.getString(TOKEN_KEY, null)

    override fun setToken(token: String) {
        prefs.edit().putString(TOKEN_KEY, token).apply()
    }

    override fun clearToken() {
        prefs.edit().remove(TOKEN_KEY).apply()
    }
}

actual fun createAuthTokenStore(): AuthTokenStore = AndroidAuthTokenStore()
