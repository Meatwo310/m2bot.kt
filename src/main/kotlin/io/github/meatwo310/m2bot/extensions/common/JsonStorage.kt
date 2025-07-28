package io.github.meatwo310.m2bot.extensions.common

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class StorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

abstract class JsonStorage<T> {
    protected val data = mutableListOf<T>()
    private val mutex = Mutex()
    protected abstract val storageFile: File
    protected abstract val errorMessageLoad: String
    protected abstract val errorMessageSave: String

    protected abstract fun decodeFromString(content: String): List<T>
    protected abstract fun encodeToString(data: List<T>): String

    init {
        loadData()
    }

    private fun loadData() {
        try {
            if (!storageFile.exists()) {
                storageFile.parentFile.mkdirs()
                storageFile.createNewFile()
                saveData()
                return
            }
            val content = storageFile.readText()
            if (content.isNotEmpty()) {
                data.addAll(decodeFromString(content))
            }
        } catch (e: IOException) {
            throw StorageException(errorMessageLoad, e)
        }
    }

    private fun saveData() {
        try {
            storageFile.parentFile.mkdirs()
            storageFile.writeText(encodeToString(data))
        } catch (e: IOException) {
            throw StorageException(errorMessageSave, e)
        }
    }

    protected suspend fun <R> withDataLock(action: suspend (MutableList<T>) -> R): R {
        return mutex.withLock {
            val result = action(data)
            saveData()
            result
        }
    }

    protected suspend fun <R> readDataLock(action: suspend (List<T>) -> R): R {
        return mutex.withLock {
            action(data)
        }
    }
}

