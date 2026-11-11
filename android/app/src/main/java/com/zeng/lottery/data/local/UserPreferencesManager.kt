package com.zeng.lottery.data.local

import android.content.Context
import android.content.SharedPreferences
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.LunarDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface contract for persisting and retrieving user preferences.
 */
interface UserPreferencesStore {
    fun saveLunarDate(date: LunarDate)
    fun getLunarDate(): LunarDate
    fun saveGender(isMale: Boolean)
    fun getGender(): Boolean
    fun saveBetCount(count: Int)
    fun getBetCount(): Int
    fun saveSelectedLottery(type: LotteryType)
    fun getSelectedLottery(): LotteryType
    fun saveSelectedStrategy(strategy: String)
    fun getSelectedStrategy(): String
    fun saveUserName(name: String)
    fun getUserName(): String
    fun saveIsLunar(isLunar: Boolean)
    fun getIsLunar(): Boolean
    fun clear()
}

/**
 * Manager for user preferences and state persistence.
 * Uses Android SharedPreferences for synchronous < 0.5ms cold-start access,
 * with concurrent in-memory fallback for JVM unit testing environments where
 * Android Context or SharedPreferences is unavailable.
 */
class UserPreferencesManager(
    context: Context? = null,
    private val inMemoryFallback: ConcurrentHashMap<String, Any>? = null
) : UserPreferencesStore {

    companion object {
        const val PREFS_NAME = "lottery_app_settings"

        const val KEY_LUNAR_YEAR = "lunar_year"
        const val KEY_LUNAR_MONTH = "lunar_month"
        const val KEY_LUNAR_IS_LEAP = "lunar_is_leap"
        const val KEY_LUNAR_DAY = "lunar_day"
        const val KEY_LUNAR_HOUR = "lunar_hour"
        const val KEY_LUNAR_MINUTE = "lunar_minute"
        const val KEY_IS_LUNAR = "is_lunar"
        const val KEY_IS_MALE = "is_male"
        const val KEY_BET_COUNT = "bet_count"
        const val KEY_SELECTED_LOTTERY = "selected_lottery"
        const val KEY_SELECTED_STRATEGY = "selected_strategy"
        const val KEY_USER_NAME = "user_name"

        // Default values
        val DEFAULT_LUNAR_DATE = LunarDate(1990, 6, false, 24, 8, 30)
        const val DEFAULT_IS_LUNAR = true
        const val DEFAULT_IS_MALE = true
        const val DEFAULT_BET_COUNT = 5
        val DEFAULT_LOTTERY_TYPE = LotteryType.SSQ
        const val DEFAULT_STRATEGY = "命理吉数优先"
        const val DEFAULT_USER_NAME = "缘主"

        const val MIN_YEAR = 1930
        const val MAX_YEAR = 2030
        const val MIN_BET_COUNT = 1
        const val MAX_BET_COUNT = 20

        // Shared in-memory storage for JVM unit test execution across instances when Context is null
        private val jvmTestPrefs = ConcurrentHashMap<String, Any>()

        fun resetJvmTestPrefs() {
            jvmTestPrefs.clear()
        }
    }

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: Throwable) {
        null
    }

    private val memoryStore: ConcurrentHashMap<String, Any>
        get() = inMemoryFallback ?: jvmTestPrefs

    override fun saveLunarDate(date: LunarDate) {
        val clampedYear = date.year.coerceIn(MIN_YEAR, MAX_YEAR)
        val clampedMonth = date.month.coerceIn(1, 12)
        val leapMonth = BaziEngine.getLeapMonth(clampedYear)
        val effectiveIsLeap = date.isLeap && (leapMonth == clampedMonth && leapMonth > 0)
        val maxDays = BaziEngine.getDaysInLunarMonth(clampedYear, clampedMonth, effectiveIsLeap)
        val clampedDay = date.day.coerceIn(1, maxDays)
        val clampedHour = date.hour.coerceIn(0, 23)
        val clampedMinute = date.minute.coerceIn(0, 59)

        if (prefs != null) {
            prefs.edit()
                .putInt(KEY_LUNAR_YEAR, clampedYear)
                .putInt(KEY_LUNAR_MONTH, clampedMonth)
                .putBoolean(KEY_LUNAR_IS_LEAP, effectiveIsLeap)
                .putInt(KEY_LUNAR_DAY, clampedDay)
                .putInt(KEY_LUNAR_HOUR, clampedHour)
                .putInt(KEY_LUNAR_MINUTE, clampedMinute)
                .apply()
        } else {
            memoryStore[KEY_LUNAR_YEAR] = clampedYear
            memoryStore[KEY_LUNAR_MONTH] = clampedMonth
            memoryStore[KEY_LUNAR_IS_LEAP] = effectiveIsLeap
            memoryStore[KEY_LUNAR_DAY] = clampedDay
            memoryStore[KEY_LUNAR_HOUR] = clampedHour
            memoryStore[KEY_LUNAR_MINUTE] = clampedMinute
        }
    }

    override fun getLunarDate(): LunarDate {
        val hasSaved = try {
            if (prefs != null) {
                prefs.contains(KEY_LUNAR_YEAR)
            } else {
                memoryStore.containsKey(KEY_LUNAR_YEAR)
            }
        } catch (e: Throwable) {
            false
        }

        if (!hasSaved) {
            return DEFAULT_LUNAR_DATE
        }

        val year = try {
            if (prefs != null) {
                prefs.getInt(KEY_LUNAR_YEAR, DEFAULT_LUNAR_DATE.year)
            } else {
                (memoryStore[KEY_LUNAR_YEAR] as? Int) ?: DEFAULT_LUNAR_DATE.year
            }
        } catch (e: Throwable) {
            DEFAULT_LUNAR_DATE.year
        }
        val month = try {
            if (prefs != null) {
                prefs.getInt(KEY_LUNAR_MONTH, DEFAULT_LUNAR_DATE.month)
            } else {
                (memoryStore[KEY_LUNAR_MONTH] as? Int) ?: DEFAULT_LUNAR_DATE.month
            }
        } catch (e: Throwable) {
            DEFAULT_LUNAR_DATE.month
        }
        val isLeap = try {
            if (prefs != null) {
                prefs.getBoolean(KEY_LUNAR_IS_LEAP, DEFAULT_LUNAR_DATE.isLeap)
            } else {
                (memoryStore[KEY_LUNAR_IS_LEAP] as? Boolean) ?: DEFAULT_LUNAR_DATE.isLeap
            }
        } catch (e: Throwable) {
            DEFAULT_LUNAR_DATE.isLeap
        }
        val day = try {
            if (prefs != null) {
                prefs.getInt(KEY_LUNAR_DAY, DEFAULT_LUNAR_DATE.day)
            } else {
                (memoryStore[KEY_LUNAR_DAY] as? Int) ?: DEFAULT_LUNAR_DATE.day
            }
        } catch (e: Throwable) {
            DEFAULT_LUNAR_DATE.day
        }
        val hour = try {
            if (prefs != null) {
                prefs.getInt(KEY_LUNAR_HOUR, DEFAULT_LUNAR_DATE.hour)
            } else {
                (memoryStore[KEY_LUNAR_HOUR] as? Int) ?: DEFAULT_LUNAR_DATE.hour
            }
        } catch (e: Throwable) {
            DEFAULT_LUNAR_DATE.hour
        }
        val minute = try {
            if (prefs != null) {
                prefs.getInt(KEY_LUNAR_MINUTE, DEFAULT_LUNAR_DATE.minute)
            } else {
                (memoryStore[KEY_LUNAR_MINUTE] as? Int) ?: DEFAULT_LUNAR_DATE.minute
            }
        } catch (e: Throwable) {
            DEFAULT_LUNAR_DATE.minute
        }

        val clampedYear = year.coerceIn(MIN_YEAR, MAX_YEAR)
        val clampedMonth = month.coerceIn(1, 12)
        val leapMonth = BaziEngine.getLeapMonth(clampedYear)
        val effectiveIsLeap = isLeap && (leapMonth == clampedMonth && leapMonth > 0)
        val maxDays = BaziEngine.getDaysInLunarMonth(clampedYear, clampedMonth, effectiveIsLeap)
        val clampedDay = day.coerceIn(1, maxDays)
        val clampedHour = hour.coerceIn(0, 23)
        val clampedMinute = minute.coerceIn(0, 59)

        return LunarDate(
            year = clampedYear,
            month = clampedMonth,
            isLeap = effectiveIsLeap,
            day = clampedDay,
            hour = clampedHour,
            minute = clampedMinute
        )
    }

    override fun saveGender(isMale: Boolean) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_IS_MALE, isMale).apply()
        } else {
            memoryStore[KEY_IS_MALE] = isMale
        }
    }

    override fun getGender(): Boolean {
        return try {
            if (prefs != null) {
                prefs.getBoolean(KEY_IS_MALE, DEFAULT_IS_MALE)
            } else {
                (memoryStore[KEY_IS_MALE] as? Boolean) ?: DEFAULT_IS_MALE
            }
        } catch (e: Throwable) {
            DEFAULT_IS_MALE
        }
    }

    override fun saveBetCount(count: Int) {
        val clamped = count.coerceIn(MIN_BET_COUNT, MAX_BET_COUNT)
        if (prefs != null) {
            prefs.edit().putInt(KEY_BET_COUNT, clamped).apply()
        } else {
            memoryStore[KEY_BET_COUNT] = clamped
        }
    }

    override fun getBetCount(): Int {
        val raw = try {
            if (prefs != null) {
                prefs.getInt(KEY_BET_COUNT, DEFAULT_BET_COUNT)
            } else {
                (memoryStore[KEY_BET_COUNT] as? Int) ?: DEFAULT_BET_COUNT
            }
        } catch (e: Throwable) {
            DEFAULT_BET_COUNT
        }
        return raw.coerceIn(MIN_BET_COUNT, MAX_BET_COUNT)
    }

    override fun saveSelectedLottery(type: LotteryType) {
        if (prefs != null) {
            prefs.edit().putString(KEY_SELECTED_LOTTERY, type.code).apply()
        } else {
            memoryStore[KEY_SELECTED_LOTTERY] = type.code
        }
    }

    override fun getSelectedLottery(): LotteryType {
        val code = try {
            if (prefs != null) {
                prefs.getString(KEY_SELECTED_LOTTERY, DEFAULT_LOTTERY_TYPE.code) ?: DEFAULT_LOTTERY_TYPE.code
            } else {
                (memoryStore[KEY_SELECTED_LOTTERY] as? String) ?: DEFAULT_LOTTERY_TYPE.code
            }
        } catch (e: Throwable) {
            DEFAULT_LOTTERY_TYPE.code
        }
        return LotteryType.fromCode(code)
    }

    override fun saveSelectedStrategy(strategy: String) {
        val cleanStrategy = if (strategy.isBlank()) DEFAULT_STRATEGY else strategy.trim()
        if (prefs != null) {
            prefs.edit().putString(KEY_SELECTED_STRATEGY, cleanStrategy).apply()
        } else {
            memoryStore[KEY_SELECTED_STRATEGY] = cleanStrategy
        }
    }

    override fun getSelectedStrategy(): String {
        val raw = try {
            if (prefs != null) {
                prefs.getString(KEY_SELECTED_STRATEGY, DEFAULT_STRATEGY) ?: DEFAULT_STRATEGY
            } else {
                (memoryStore[KEY_SELECTED_STRATEGY] as? String) ?: DEFAULT_STRATEGY
            }
        } catch (e: Throwable) {
            DEFAULT_STRATEGY
        }
        return if (raw.isBlank()) DEFAULT_STRATEGY else raw.trim()
    }

    override fun saveUserName(name: String) {
        val cleanName = if (name.isBlank()) DEFAULT_USER_NAME else name.trim()
        if (prefs != null) {
            prefs.edit().putString(KEY_USER_NAME, cleanName).apply()
        } else {
            memoryStore[KEY_USER_NAME] = cleanName
        }
    }

    override fun getUserName(): String {
        val raw = try {
            if (prefs != null) {
                prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME
            } else {
                (memoryStore[KEY_USER_NAME] as? String) ?: DEFAULT_USER_NAME
            }
        } catch (e: Throwable) {
            DEFAULT_USER_NAME
        }
        return if (raw.isBlank()) DEFAULT_USER_NAME else raw.trim()
    }

    override fun saveIsLunar(isLunar: Boolean) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_IS_LUNAR, isLunar).apply()
        } else {
            memoryStore[KEY_IS_LUNAR] = isLunar
        }
    }

    override fun getIsLunar(): Boolean {
        return try {
            if (prefs != null) {
                prefs.getBoolean(KEY_IS_LUNAR, DEFAULT_IS_LUNAR)
            } else {
                (memoryStore[KEY_IS_LUNAR] as? Boolean) ?: DEFAULT_IS_LUNAR
            }
        } catch (e: Throwable) {
            DEFAULT_IS_LUNAR
        }
    }

    override fun clear() {
        if (prefs != null) {
            prefs.edit()
                .remove(KEY_LUNAR_YEAR)
                .remove(KEY_LUNAR_MONTH)
                .remove(KEY_LUNAR_IS_LEAP)
                .remove(KEY_LUNAR_DAY)
                .remove(KEY_LUNAR_HOUR)
                .remove(KEY_LUNAR_MINUTE)
                .remove(KEY_IS_LUNAR)
                .remove(KEY_IS_MALE)
                .remove(KEY_BET_COUNT)
                .remove(KEY_SELECTED_LOTTERY)
                .remove(KEY_SELECTED_STRATEGY)
                .remove(KEY_USER_NAME)
                .apply()
        }
        memoryStore.remove(KEY_LUNAR_YEAR)
        memoryStore.remove(KEY_LUNAR_MONTH)
        memoryStore.remove(KEY_LUNAR_IS_LEAP)
        memoryStore.remove(KEY_LUNAR_DAY)
        memoryStore.remove(KEY_LUNAR_HOUR)
        memoryStore.remove(KEY_LUNAR_MINUTE)
        memoryStore.remove(KEY_IS_LUNAR)
        memoryStore.remove(KEY_IS_MALE)
        memoryStore.remove(KEY_BET_COUNT)
        memoryStore.remove(KEY_SELECTED_LOTTERY)
        memoryStore.remove(KEY_SELECTED_STRATEGY)
        memoryStore.remove(KEY_USER_NAME)
    }
}
