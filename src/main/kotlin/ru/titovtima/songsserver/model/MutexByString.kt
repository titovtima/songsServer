package ru.titovtima.songsserver.model

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MutexByString {
    companion object {
        private val map = mutableMapOf<String, Mutex>()

        private fun getMutex(string: String): Mutex {
            val fromMap = map[string]
            if (fromMap != null) return fromMap
            val newMutex = Mutex()
            map[string] = newMutex
            return newMutex
        }

        suspend fun <T> withLock(string: String, action: () -> T): T {
            val mutex = getMutex(string)
            val result = mutex.withLock(action = action)
            map.remove(string)
            return result
        }
    }
}
