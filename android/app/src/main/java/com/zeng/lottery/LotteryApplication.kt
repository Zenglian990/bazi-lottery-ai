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

        // 2. Global Uncaught Exception Fallback: Catches any unhandled exception on ANY thread
        // Strictly prevents the system-level "屡次停止运行" (has stopped repeatedly) crash dialog from popping up!
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("LotteryApplication", "Fatal uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            } catch (_: Throwable) {}

            if (thread == Looper.getMainLooper().thread) {
                // If main thread had an uncaught exception escape, re-enter loop safely rather than killing process
                while (true) {
                    try {
                        Looper.loop()
                    } catch (t: Throwable) {
                        Log.e("LotteryApplication", "Main looper caught and suppressed in handler: ${t.message}", t)
                    }
                }
            } else {
                Log.w("LotteryApplication", "Swallowed background thread exception on ${thread.name} to prevent OS crash dialog.")
            }
        }
    }
}
