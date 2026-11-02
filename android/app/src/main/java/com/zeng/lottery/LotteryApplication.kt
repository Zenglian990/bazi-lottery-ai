package com.zeng.lottery

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log

class LotteryApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. NeverCrash Main Looper Guardian: Catches unhandled UI / IME / Compose loop exceptions
        // (e.g. Samsung Honeyboard IME composing span mismatch, Compose layout/drawing glitches)
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                } catch (t: Throwable) {
                    Log.e("LotteryApplication", "Caught and suppressed main looper crash: ${t.message}", t)
                }
            }
        }

        // 2. Global Uncaught Exception Fallback for background threads
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("LotteryApplication", "Fatal uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            } catch (_: Throwable) {}

            if (thread == Looper.getMainLooper().thread) {
                defaultHandler?.uncaughtException(thread, throwable)
            } else {
                Log.w("LotteryApplication", "Swallowed background thread exception to prevent OS crash dialog.")
            }
        }
    }
}
