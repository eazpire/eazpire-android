package com.eazpire.creator

/** Process start — workers skip background notifications during cold-start window. */
object EazpireAppTiming {
    @Volatile
    var processStartMs: Long = 0L
        private set

    fun markProcessStart() {
        processStartMs = System.currentTimeMillis()
    }
}
