package com.zeng.lottery

import android.app.Application
import android.util.Log

class LotteryApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Standard uncaught exception logging adhering to Android engineering best practices.
        // Avoids infinite loops or swallowing fatal errors (like OOM or UI tree corruptions).
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(
                    "LotteryApplication",
                    "Uncaught exception on thread ${thread.name}: ${throwable.message}",
                    throwable
                )
            } catch (_: Throwable) {
                // Avoid crashing inside the logger
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
