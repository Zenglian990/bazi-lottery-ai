package com.zeng.lottery.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.data.model.SavedBirthProfile
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户偏好与生辰档案持久化契约。
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
 * 同步轻量偏好管理器：支持 1900~2100 农历生辰、真太阳时经度、深浅主题与多亲友生辰档案。
 */
class UserPreferencesManager(
    context: Context? = null,
    private val inMemoryFallback: ConcurrentHashMap<String, Any>? = null
) : UserPreferencesStore {

    private val gson = Gson()

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
        const val KEY_BIRTH_LONGITUDE = "birth_longitude"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_BIRTH_PROFILES = "saved_birth_profiles_v1"
        const val KEY_HAS_CUSTOMIZED_BIRTH = "has_customized_birth_v1"
        const val KEY_HAS_ACCEPTED_COMPLIANCE = "has_accepted_compliance_v1"

        val DEFAULT_LUNAR_DATE = LunarDate(1990, 6, false, 24, 8, 30)
        const val DEFAULT_IS_LUNAR = true
        const val DEFAULT_IS_MALE = true
        const val DEFAULT_BET_COUNT = 5
        val DEFAULT_LOTTERY_TYPE = LotteryType.SSQ
        const val DEFAULT_STRATEGY = "命理吉数优先"
        const val DEFAULT_USER_NAME = "缘主"
        const val DEFAULT_LONGITUDE = 120.0
        const val DEFAULT_DARK_THEME = true

        const val MIN_YEAR = 1900
        const val MAX_YEAR = 2100
        const val MIN_BET_COUNT = 1
        const val MAX_BET_COUNT = 20

        private val jvmTestPrefs = ConcurrentHashMap<String, Any>()

        fun resetJvmTestPrefs() {
            jvmTestPrefs.clear()
        }
    }

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
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

        try {
            prefs?.edit()
                ?.putInt(KEY_LUNAR_YEAR, clampedYear)
                ?.putInt(KEY_LUNAR_MONTH, clampedMonth)
                ?.putBoolean(KEY_LUNAR_IS_LEAP, effectiveIsLeap)
                ?.putInt(KEY_LUNAR_DAY, clampedDay)
                ?.putInt(KEY_LUNAR_HOUR, clampedHour)
                ?.putInt(KEY_LUNAR_MINUTE, clampedMinute)
                ?.putBoolean(KEY_HAS_CUSTOMIZED_BIRTH, true)
                ?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_LUNAR_YEAR] = clampedYear
        memoryStore[KEY_LUNAR_MONTH] = clampedMonth
        memoryStore[KEY_LUNAR_IS_LEAP] = effectiveIsLeap
        memoryStore[KEY_LUNAR_DAY] = clampedDay
        memoryStore[KEY_LUNAR_HOUR] = clampedHour
        memoryStore[KEY_LUNAR_MINUTE] = clampedMinute
        memoryStore[KEY_HAS_CUSTOMIZED_BIRTH] = true
    }

    override fun getLunarDate(): LunarDate {
        val hasSaved = try {
            (prefs != null && prefs.contains(KEY_LUNAR_YEAR)) || memoryStore.containsKey(KEY_LUNAR_YEAR)
        } catch (_: Throwable) {
            memoryStore.containsKey(KEY_LUNAR_YEAR)
        }
        if (!hasSaved) return DEFAULT_LUNAR_DATE

        fun readInt(key: String, def: Int): Int = try {
            if (prefs != null && prefs.contains(key)) prefs.getInt(key, def)
            else (memoryStore[key] as? Int) ?: def
        } catch (_: Throwable) {
            (memoryStore[key] as? Int) ?: def
        }

        fun readBool(key: String, def: Boolean): Boolean = try {
            if (prefs != null && prefs.contains(key)) prefs.getBoolean(key, def)
            else (memoryStore[key] as? Boolean) ?: def
        } catch (_: Throwable) {
            (memoryStore[key] as? Boolean) ?: def
        }

        val clampedYear = readInt(KEY_LUNAR_YEAR, DEFAULT_LUNAR_DATE.year).coerceIn(MIN_YEAR, MAX_YEAR)
        val clampedMonth = readInt(KEY_LUNAR_MONTH, DEFAULT_LUNAR_DATE.month).coerceIn(1, 12)
        val isLeap = readBool(KEY_LUNAR_IS_LEAP, DEFAULT_LUNAR_DATE.isLeap)
        val leapMonth = BaziEngine.getLeapMonth(clampedYear)
        val effectiveIsLeap = isLeap && (leapMonth == clampedMonth && leapMonth > 0)
        val maxDays = BaziEngine.getDaysInLunarMonth(clampedYear, clampedMonth, effectiveIsLeap)
        val clampedDay = readInt(KEY_LUNAR_DAY, DEFAULT_LUNAR_DATE.day).coerceIn(1, maxDays)
        val clampedHour = readInt(KEY_LUNAR_HOUR, DEFAULT_LUNAR_DATE.hour).coerceIn(0, 23)
        val clampedMinute = readInt(KEY_LUNAR_MINUTE, DEFAULT_LUNAR_DATE.minute).coerceIn(0, 59)

        return LunarDate(clampedYear, clampedMonth, effectiveIsLeap, clampedDay, clampedHour, clampedMinute)
    }

    override fun saveGender(isMale: Boolean) {
        try {
            prefs?.edit()?.putBoolean(KEY_IS_MALE, isMale)?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_IS_MALE] = isMale
    }

    override fun getGender(): Boolean {
        return try {
            if (prefs != null && prefs.contains(KEY_IS_MALE)) prefs.getBoolean(KEY_IS_MALE, DEFAULT_IS_MALE)
            else (memoryStore[KEY_IS_MALE] as? Boolean) ?: DEFAULT_IS_MALE
        } catch (_: Throwable) {
            (memoryStore[KEY_IS_MALE] as? Boolean) ?: DEFAULT_IS_MALE
        }
    }

    override fun saveBetCount(count: Int) {
        val clamped = count.coerceIn(MIN_BET_COUNT, MAX_BET_COUNT)
        try {
            prefs?.edit()?.putInt(KEY_BET_COUNT, clamped)?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_BET_COUNT] = clamped
    }

    override fun getBetCount(): Int {
        val raw = try {
            if (prefs != null && prefs.contains(KEY_BET_COUNT)) prefs.getInt(KEY_BET_COUNT, DEFAULT_BET_COUNT)
            else (memoryStore[KEY_BET_COUNT] as? Int) ?: DEFAULT_BET_COUNT
        } catch (_: Throwable) {
            (memoryStore[KEY_BET_COUNT] as? Int) ?: DEFAULT_BET_COUNT
        }
        return raw.coerceIn(MIN_BET_COUNT, MAX_BET_COUNT)
    }

    override fun saveSelectedLottery(type: LotteryType) {
        try {
            prefs?.edit()?.putString(KEY_SELECTED_LOTTERY, type.code)?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_SELECTED_LOTTERY] = type.code
    }

    override fun getSelectedLottery(): LotteryType {
        val code = try {
            if (prefs != null && prefs.contains(KEY_SELECTED_LOTTERY)) {
                prefs.getString(KEY_SELECTED_LOTTERY, DEFAULT_LOTTERY_TYPE.code) ?: DEFAULT_LOTTERY_TYPE.code
            } else {
                (memoryStore[KEY_SELECTED_LOTTERY] as? String) ?: DEFAULT_LOTTERY_TYPE.code
            }
        } catch (_: Throwable) {
            (memoryStore[KEY_SELECTED_LOTTERY] as? String) ?: DEFAULT_LOTTERY_TYPE.code
        }
        return LotteryType.fromCode(code)
    }

    override fun saveSelectedStrategy(strategy: String) {
        val cleanStrategy = if (strategy.isBlank()) DEFAULT_STRATEGY else strategy.trim()
        try {
            prefs?.edit()?.putString(KEY_SELECTED_STRATEGY, cleanStrategy)?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_SELECTED_STRATEGY] = cleanStrategy
    }

    override fun getSelectedStrategy(): String {
        val raw = try {
            if (prefs != null && prefs.contains(KEY_SELECTED_STRATEGY)) {
                prefs.getString(KEY_SELECTED_STRATEGY, DEFAULT_STRATEGY) ?: DEFAULT_STRATEGY
            } else {
                (memoryStore[KEY_SELECTED_STRATEGY] as? String) ?: DEFAULT_STRATEGY
            }
        } catch (_: Throwable) {
            (memoryStore[KEY_SELECTED_STRATEGY] as? String) ?: DEFAULT_STRATEGY
        }
        return if (raw.isBlank()) DEFAULT_STRATEGY else raw.trim()
    }

    override fun saveUserName(name: String) {
        val cleanName = if (name.isBlank()) DEFAULT_USER_NAME else name.trim()
        try {
            prefs?.edit()?.putString(KEY_USER_NAME, cleanName)?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_USER_NAME] = cleanName
    }

    override fun getUserName(): String {
        val raw = try {
            if (prefs != null && prefs.contains(KEY_USER_NAME)) {
                prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME
            } else {
                (memoryStore[KEY_USER_NAME] as? String) ?: DEFAULT_USER_NAME
            }
        } catch (_: Throwable) {
            (memoryStore[KEY_USER_NAME] as? String) ?: DEFAULT_USER_NAME
        }
        return if (raw.isBlank()) DEFAULT_USER_NAME else raw.trim()
    }

    override fun saveIsLunar(isLunar: Boolean) {
        try {
            prefs?.edit()?.putBoolean(KEY_IS_LUNAR, isLunar)?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_IS_LUNAR] = isLunar
    }

    override fun getIsLunar(): Boolean {
        return try {
            if (prefs != null && prefs.contains(KEY_IS_LUNAR)) prefs.getBoolean(KEY_IS_LUNAR, DEFAULT_IS_LUNAR)
            else (memoryStore[KEY_IS_LUNAR] as? Boolean) ?: DEFAULT_IS_LUNAR
        } catch (_: Throwable) {
            (memoryStore[KEY_IS_LUNAR] as? Boolean) ?: DEFAULT_IS_LUNAR
        }
    }

    fun saveBirthLongitude(longitude: Double) {
        val clamped = longitude.coerceIn(-180.0, 180.0)
        try {
            prefs?.edit()?.putFloat(KEY_BIRTH_LONGITUDE, clamped.toFloat())?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_BIRTH_LONGITUDE] = clamped
    }

    fun getBirthLongitude(): Double {
        return try {
            if (prefs != null && prefs.contains(KEY_BIRTH_LONGITUDE)) {
                prefs.getFloat(KEY_BIRTH_LONGITUDE, DEFAULT_LONGITUDE.toFloat()).toDouble()
            } else {
                (memoryStore[KEY_BIRTH_LONGITUDE] as? Number)?.toDouble() ?: DEFAULT_LONGITUDE
            }
        } catch (_: Throwable) {
            (memoryStore[KEY_BIRTH_LONGITUDE] as? Number)?.toDouble() ?: DEFAULT_LONGITUDE
        }
    }

    fun saveDarkTheme(isDark: Boolean) {
        try {
            prefs?.edit()?.putBoolean(KEY_DARK_THEME, isDark)?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_DARK_THEME] = isDark
    }

    fun getDarkTheme(): Boolean {
        return try {
            if (prefs != null && prefs.contains(KEY_DARK_THEME)) {
                prefs.getBoolean(KEY_DARK_THEME, DEFAULT_DARK_THEME)
            } else {
                (memoryStore[KEY_DARK_THEME] as? Boolean) ?: DEFAULT_DARK_THEME
            }
        } catch (_: Throwable) {
            (memoryStore[KEY_DARK_THEME] as? Boolean) ?: DEFAULT_DARK_THEME
        }
    }

    fun getBirthProfiles(): List<SavedBirthProfile> {
        val raw = try {
            if (prefs != null && prefs.contains(KEY_BIRTH_PROFILES)) {
                prefs.getString(KEY_BIRTH_PROFILES, null)
            } else {
                memoryStore[KEY_BIRTH_PROFILES] as? String
            }
        } catch (_: Throwable) {
            memoryStore[KEY_BIRTH_PROFILES] as? String
        }
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<SavedBirthProfile>>() {}.type
            gson.fromJson<List<SavedBirthProfile>>(raw, type).orEmpty().filterNotNull()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveBirthProfiles(profiles: List<SavedBirthProfile>) {
        val trimmed = profiles.take(12)
        val json = gson.toJson(trimmed)
        try {
            prefs?.edit()?.putString(KEY_BIRTH_PROFILES, json)?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_BIRTH_PROFILES] = json
    }

    fun upsertBirthProfile(profile: SavedBirthProfile): List<SavedBirthProfile> {
        val current = getBirthProfiles().toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id || it.label == profile.label }
        if (idx >= 0) {
            current[idx] = profile
        } else {
            current.add(0, profile)
        }
        saveBirthProfiles(current)
        return current
    }

    fun deleteBirthProfile(profileId: String): List<SavedBirthProfile> {
        val updated = getBirthProfiles().filterNot { it.id == profileId }
        saveBirthProfiles(updated)
        return updated
    }

    /** 是否已录入过个人生辰（兼容老版本已存过 KEY_LUNAR_YEAR 的老用户，不误弹引导）。 */
    fun hasCustomizedBirth(): Boolean {
        return try {
            (prefs != null && (prefs.getBoolean(KEY_HAS_CUSTOMIZED_BIRTH, false) || prefs.contains(KEY_LUNAR_YEAR))) ||
                (memoryStore[KEY_HAS_CUSTOMIZED_BIRTH] as? Boolean == true) ||
                memoryStore.containsKey(KEY_LUNAR_YEAR)
        } catch (_: Throwable) {
            (memoryStore[KEY_HAS_CUSTOMIZED_BIRTH] as? Boolean == true) || memoryStore.containsKey(KEY_LUNAR_YEAR)
        }
    }

    fun hasAcceptedCompliance(): Boolean {
        return try {
            if (prefs != null && prefs.contains(KEY_HAS_ACCEPTED_COMPLIANCE)) {
                prefs.getBoolean(KEY_HAS_ACCEPTED_COMPLIANCE, false)
            } else {
                (memoryStore[KEY_HAS_ACCEPTED_COMPLIANCE] as? Boolean) ?: false
            }
        } catch (_: Throwable) {
            (memoryStore[KEY_HAS_ACCEPTED_COMPLIANCE] as? Boolean) ?: false
        }
    }

    fun saveAcceptedCompliance(accepted: Boolean) {
        try {
            prefs?.edit()?.putBoolean(KEY_HAS_ACCEPTED_COMPLIANCE, accepted)?.apply()
        } catch (_: Throwable) {
        }
        memoryStore[KEY_HAS_ACCEPTED_COMPLIANCE] = accepted
    }

    override fun clear() {
        try {
            prefs?.edit()
                ?.remove(KEY_LUNAR_YEAR)
                ?.remove(KEY_LUNAR_MONTH)
                ?.remove(KEY_LUNAR_IS_LEAP)
                ?.remove(KEY_LUNAR_DAY)
                ?.remove(KEY_LUNAR_HOUR)
                ?.remove(KEY_LUNAR_MINUTE)
                ?.remove(KEY_IS_LUNAR)
                ?.remove(KEY_IS_MALE)
                ?.remove(KEY_BET_COUNT)
                ?.remove(KEY_SELECTED_LOTTERY)
                ?.remove(KEY_SELECTED_STRATEGY)
                ?.remove(KEY_USER_NAME)
                ?.remove(KEY_BIRTH_LONGITUDE)
                ?.apply()
        } catch (_: Throwable) {
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
        memoryStore.remove(KEY_BIRTH_LONGITUDE)
    }
}
