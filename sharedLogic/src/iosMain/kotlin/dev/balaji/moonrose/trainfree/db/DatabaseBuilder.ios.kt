package dev.balaji.moonrose.trainfree.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val documentsDir = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )!!.path!!
    return Room.databaseBuilder<AppDatabase>(name = "$documentsDir/trainfree.db")
}
