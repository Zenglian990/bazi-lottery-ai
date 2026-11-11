package com.zeng.lottery

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zeng.lottery.ai.AiConfig
import com.zeng.lottery.ai.AiProvider
import com.zeng.lottery.ai.KeyStoreManager
import com.zeng.lottery.data.api.ApiClient
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.OfflineLotteryEngine
import com.zeng.lottery.data.local.SavedTicket
import com.zeng.lottery.data.local.UserPickHistoryStore
import com.zeng.lottery.data.local.UserPreferencesManager
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.data.model.RecommendationItem
import com.zeng.lottery.ui.viewmodel.LotteryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Constructor
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Adversarial probe test suite designed by SWE Reviewer to aggressively challenge
 * LotteryViewModel reflection instantiation, cold start resilience, ANR avoidance,
 * and data integrity under boundary, corrupted, and concurrent inputs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdversarialReviewerProbeTest {

    private val testDispatcher = StandardTestDispatcher()

    class ThrowingApplication(private val throwOnContext: Boolean = true) : Application() {
        override fun getApplicationContext(): Context {
            if (throwOnContext) {
                throw IllegalStateException("Simulated unattached ApplicationContext during early cold boot")
            }
            return this
        }
    }

    class NullContextApplication : Application() {
        override fun getApplicationContext(): Context? {
            return null
        }
    }

    class ThrowingSharedPreferences : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
        override fun getString(key: String?, defValue: String?): String? = defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = false
        override fun edit(): SharedPreferences.Editor = throw RuntimeException("Simulated SQLite/disk error on edit()")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    class ThrowingEditorSharedPreferences : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
        override fun getString(key: String?, defValue: String?): String? = defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = false
        override fun edit(): SharedPreferences.Editor = ThrowingEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    class ThrowingEditor : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor = this
        override fun clear(): SharedPreferences.Editor = this
        override fun commit(): Boolean = throw RuntimeException("Simulated disk full commit failure")
        override fun apply() = throw RuntimeException("Simulated disk full apply failure")
    }

    class ThrowingPrefsApplication(private val prefs: SharedPreferences) : Application() {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getApplicationContext(): Context = this
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        UserPreferencesManager.resetJvmTestPrefs()
        UserPickHistoryStore.resetJvmTestStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testReflectionInstantiationWithThrowingApplicationContext() {
        // Attack: What if getApplicationContext() throws IllegalStateException on Android 12+ early boot?
        val constructor: Constructor<LotteryViewModel> =
            LotteryViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(constructor)

        val app = ThrowingApplication(throwOnContext = true)
        val vm = constructor.newInstance(app)
        assertNotNull("LotteryViewModel must survive throwing getApplicationContext()", vm)
        assertNotNull("Offline destiny must still prime successfully", vm.destiny.value)
        assertNotNull("Offline stats must prime successfully", vm.stats.value)
    }

    @Test
    fun testReflectionInstantiationWithNullApplicationContext() {
        // Attack: What if getApplicationContext() returns null?
        val constructor: Constructor<LotteryViewModel> =
            LotteryViewModel::class.java.getConstructor(Application::class.java)
        val app = NullContextApplication()
        val vm = constructor.newInstance(app)
        assertNotNull("LotteryViewModel must survive null getApplicationContext()", vm)
        assertEquals("缘主", vm.effectiveUserName)
    }

    @Test
    fun testPreferencesManagerCorruptedTypeHandling() {
        // Attack: Corrupt SharedPreferences where non-Int or malformed data is stored in memory store
        val memory = ConcurrentHashMap<String, Any>()
        memory[UserPreferencesManager.KEY_LUNAR_YEAR] = "NOT_AN_INT" // Type mismatch
        memory[UserPreferencesManager.KEY_BET_COUNT] = 99999 // Out of range
        memory[UserPreferencesManager.KEY_SELECTED_LOTTERY] = "INVALID_LOTTERY" // Invalid code
        memory[UserPreferencesManager.KEY_USER_NAME] = "   " // Blank user name

        val store = UserPreferencesManager(context = null, inMemoryFallback = memory)
        val lunar = store.getLunarDate()
        assertEquals("Should fall back to default year on class cast mismatch", UserPreferencesManager.DEFAULT_LUNAR_DATE.year, lunar.year)

        val betCount = store.getBetCount()
        assertEquals("Bet count must be clamped to MAX_BET_COUNT (20)", UserPreferencesManager.MAX_BET_COUNT, betCount)

        val lottery = store.getSelectedLottery()
        assertEquals("Invalid lottery code must fall back to default SSQ", LotteryType.SSQ, lottery)

        val userName = store.getUserName()
        assertEquals("Blank user name must fall back to default 缘主", "缘主", userName)
    }

    @Test
    fun testUserPickHistoryCorruptedAndNullElements() {
        val memory = ConcurrentHashMap<String, String>()
        val store = UserPickHistoryStore(context = null, inMemoryFallback = memory)

        // Inject JSON array containing null elements, missing fields, corrupted missing lotteryType/primaryNumbers, and lowercase enum strings
        val badJson = """
            [
                null,
                {"id": "valid-1", "lotteryType": "SSQ", "userName": "张三", "primaryNumbers": [1, 2, 3], "timestamp": 1000},
                null,
                {"id": "corrupt-no-primary", "lotteryType": "SSQ", "userName": "缺失红球", "timestamp": 1500},
                {"id": "corrupt-no-type", "primaryNumbers": [1, 2, 3], "userName": "缺失彩种", "timestamp": 1600},
                {"id": "valid-2", "lotteryType": "DLT", "userName": "李四", "primaryNumbers": [4, 5, 6], "timestamp": 2000},
                {"id": "legacy-ssq", "lotteryType": "ssq", "userName": "小写双色球", "primaryNumbers": [7, 8, 9, 10, 11, 12], "secondaryNumbers": [16], "timestamp": 3000},
                {"id": "legacy-dlt", "lotteryType": "dlt", "userName": "小写大乐透", "primaryNumbers": [1, 2, 3, 4, 5], "secondaryNumbers": [6, 7], "timestamp": 4000}
            ]
        """.trimIndent()
        memory[UserPickHistoryStore.KEY_SAVED_TICKETS] = badJson

        val tickets = store.getAllTickets()
        assertEquals("Must filter out null elements and corrupt tickets (no type or no primary numbers)", 4, tickets.size)
        // Ordered newest first
        assertEquals("legacy-dlt", tickets[0].id)
        assertEquals(LotteryType.DLT, tickets[0].lotteryType)
        assertEquals("legacy-ssq", tickets[1].id)
        assertEquals(LotteryType.SSQ, tickets[1].lotteryType)
        assertEquals("valid-2", tickets[2].id)
        assertEquals(LotteryType.DLT, tickets[2].lotteryType)
        assertEquals("valid-1", tickets[3].id)
        assertEquals(LotteryType.SSQ, tickets[3].lotteryType)
    }

    @Test
    fun testThrowingSharedPreferencesStartupAndSaveSafety() {
        val throwingPrefs = ThrowingSharedPreferences()
        val throwingApp = ThrowingPrefsApplication(throwingPrefs)

        // 1. Verify ViewModel reflection instantiation does not crash when prefs.edit() throws on startup URL migration
        val constructor = LotteryViewModel::class.java.getConstructor(Application::class.java)
        val vm = constructor.newInstance(throwingApp)
        assertNotNull("ViewModel must survive throwing SharedPreferences during cold start", vm)
        assertEquals(ApiClient.PRODUCTION_CLOUD_URL, vm.serverUrl.value)

        // 2. Verify UserPreferencesManager handles throwing SharedPreferences on save without crashing
        val memory = ConcurrentHashMap<String, Any>()
        val prefsManager = UserPreferencesManager(context = throwingApp, inMemoryFallback = memory)
        prefsManager.saveUserName("曾仙人")
        assertEquals("曾仙人", prefsManager.getUserName())
        prefsManager.saveBetCount(8)
        assertEquals(8, prefsManager.getBetCount())
        prefsManager.saveGender(false)
        assertFalse(prefsManager.getGender())

        // 3. Verify UserPickHistoryStore handles throwing SharedPreferences on save without crashing
        val historyMemory = ConcurrentHashMap<String, String>()
        val historyStore = UserPickHistoryStore(context = throwingApp, inMemoryFallback = historyMemory)
        val testTicket = SavedTicket(
            id = "resilience-ticket",
            lotteryType = LotteryType.SSQ,
            primaryNumbers = listOf(1, 2, 3, 4, 5, 6),
            secondaryNumbers = listOf(7)
        )
        historyStore.saveTicket(testTicket)
        assertEquals(1, historyStore.getTicketCount())
        assertEquals("resilience-ticket", historyStore.getAllTickets().first().id)
        historyStore.clearAll()
        assertEquals(0, historyStore.getTicketCount())
    }

    @Test
    fun testKeyStoreManagerEdgeCases() {
        // Plain text legacy keys
        val plain = "sk-1234567890abcdef"
        val decryptedPlain = KeyStoreManager.decrypt(plain)
        assertEquals(plain, decryptedPlain)

        // Empty string
        assertEquals("", KeyStoreManager.encrypt(""))
        assertEquals("", KeyStoreManager.decrypt(""))
        assertEquals("", KeyStoreManager.maskApiKey(""))

        // Corrupted payload with invalid base64
        val corrupted = "x1_@@@INVALID_BASE64@@@:###BAD###"
        val fallbackResult = KeyStoreManager.decrypt(corrupted)
        assertNotNull(fallbackResult)

        // Roundtrip encrypt/decrypt
        val secret = "AIzaSyTestKey1234567890!@#$%"
        val encrypted = KeyStoreManager.encrypt(secret)
        assertTrue(encrypted.startsWith("x1_"))
        val decrypted = KeyStoreManager.decrypt(encrypted)
        assertEquals(secret, decrypted)

        // Masking checks
        assertEquals("sk-••••••••cdef", KeyStoreManager.maskApiKey("sk-1234567890abcdef"))
        assertEquals("AIza••••••••7890", KeyStoreManager.maskApiKey("AIzaSyTestKey1234567890"))
        assertEquals("••••••••", KeyStoreManager.maskApiKey("12345"))
    }

    @Test
    fun testBaziAstronomicalBoundaryDates() {
        // Test lower bound year 1930
        val lunar1930 = LunarDate(1930, 1, false, 1, 0, 0)
        val solar1930 = BaziEngine.lunarToSolar(lunar1930)
        assertNotNull(solar1930)
        val destiny1930 = BaziEngine.analyzeDestinyFromLunar(lunar1930, true, "始源")
        assertNotNull(destiny1930)

        // Test upper bound year 2030
        val lunar2030 = LunarDate(2030, 12, false, 29, 23, 59)
        val solar2030 = BaziEngine.lunarToSolar(lunar2030)
        assertNotNull(solar2030)
        val destiny2030 = BaziEngine.analyzeDestinyFromLunar(lunar2030, false, "末日")
        assertNotNull(destiny2030)

        // Test leap month in a year with leap month (e.g. 2020 has leap month 4)
        val leapMonth = BaziEngine.getLeapMonth(2020)
        assertEquals(4, leapMonth)
        val lunarLeap = LunarDate(2020, 4, true, 15, 12, 0)
        val solarLeap = BaziEngine.lunarToSolar(lunarLeap)
        assertNotNull(solarLeap)
        val destinyLeap = BaziEngine.analyzeDestinyFromLunar(lunarLeap, true, "润月")
        assertTrue(destinyLeap.lunarDatetime.contains("闰四月") || destinyLeap.lunarDatetime.contains("4月"))
    }

    @Test
    fun testOfflineEngineBoundaryAndRecommendations() {
        val engine = OfflineLotteryEngine(context = null)
        val dt = LocalDateTime.of(2000, 1, 1, 0, 0)
        val destiny = BaziEngine.analyzeDestiny(dt, true)

        // Strategy extremes
        for (strategy in listOf("命理吉数优先", "稳健大数据", "易道奇正", "奇偶河图平衡", "五行中和", "UNKNOWN_FALLBACK")) {
            val rec = engine.generateRecommendation(destiny, LotteryType.SSQ, count = 3, strategy = strategy)
            assertEquals(3, rec.recommendations.size)
            for (item in rec.recommendations) {
                assertEquals(6, item.primaryNumbers.size)
                assertEquals(1, item.secondaryNumbers.size)
                assertTrue(item.scoreRationale.isNotBlank())
            }
        }
    }

    @Test
    fun testConcurrencyTortureOnViewModelOperations() {
        val app = ThrowingApplication(throwOnContext = false)
        val vm = LotteryViewModel(app)

        val threadCount = 8
        val iterationsPerThread = 25
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val item = RecommendationItem(
                            primaryNumbers = listOf("01", "05", "12", "18", "25", "30"),
                            secondaryNumbers = listOf("07"),
                            scoreRationale = "线程测试-$t-$i"
                        )
                        vm.saveCurrentTicket(item, i)
                        if (i % 5 == 0) {
                            vm.selectLottery(if (i % 2 == 0) LotteryType.SSQ else LotteryType.DLT)
                            vm.reroll()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Concurrent operations must finish without deadlock", latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue("Saved tickets must be non-empty after concurrent writes", vm.savedTickets.value.isNotEmpty())
    }
}
