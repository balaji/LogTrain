package dev.balaji.moonrose.trainfree.db

import android.content.Context

/**
 * Holds the application context needed to build the Room database.
 * MainActivity sets this before any database access.
 */
object DbContextHolder {
    lateinit var appContext: Context
}
