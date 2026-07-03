package dev.balaji.moonrose.logtrain.auth

interface AuthTokenStore {
    fun getToken(): String?
    fun setToken(token: String)
    fun clearToken()
}

expect fun createAuthTokenStore(): AuthTokenStore
