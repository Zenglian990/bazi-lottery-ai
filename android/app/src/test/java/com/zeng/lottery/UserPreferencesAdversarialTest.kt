package com.zeng.lottery

import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.UserPreferencesManager
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.LunarDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adversarial Stress & Chaos Test Suite for UserPreferencesManager.
 * Conducted by Challenger 1.
 * Tests boundary violations, rapid concurrency, corrupt type injection,
 * leap month edge cases, and clean install invariants.
 */
class UserPreferencesAdversarialTest {

    private lateinit var prefsManager: UserPreferencesManager
    private lateinit var testStorage: ConcurrentHashMap<String, Any>

    @Before
    fun setUp() {
        UserPreferencesManager.resetJvmTestPrefs()
        testStorage = ConcurrentHashMap<String, Any>()
        prefsManager = UserPreferencesManager(context = null, inMemoryFallback = testStorage)
    }

    @Test
    fun testExtremeYearClampingBelowAndAbove() {
        val extremeYears = listOf(
            0 to 1930,
            -1990 to 1930,
            1800 to 1930,
            1929 to 1930,
            1930 to 1930,
            2000 to 2000,
            2030 to 2030,
            2031 to 2030,
            2050 to 2030,
            3000 to 2030,
            99999 to 2030
        )

        for ((inputYear, expectedYear) in extremeYears) {
            val date = LunarDate(inputYear, 5, false, 15, 12, 0)
            prefsManager.saveLunarDate(date)
            val retrieved = prefsManager.getLunarDate()
            assertEquals("Input year $inputYear should clamp to $expectedYear", expectedYear, retrieved.year)

            // Critically verify that BaziEngine astronomical conversion does not throw on clamped date
            val solar = BaziEngine.lunarToSolar(retrieved)
            assertNotNull("solar datetime must not be null for year $expectedYear", solar)
            assertTrue("solar year should be within astronomical range", solar.year in 1930..2031)
        }
    }

    @Test
    fun testLeapMonthAdversarialCombinations() {
        // 1. Year 2021 has NO leap month (leapMonth == 0)
        assertEquals(0, BaziEngine.getLeapMonth(2021))
        for (m in 1..12) {
            prefsManager.saveLunarDate(LunarDate(2021, m, isLeap = true, day = 10, hour = 10, minute = 0))
            val date = prefsManager.getLunarDate()
            assertFalse("Year 2021 month $m is NOT leap, isLeap must be normalized to false", date.isLeap)
        }

        // 2. Year 2020 has leap month 4
        assertEquals(4, BaziEngine.getLeapMonth(2020))
        // Non-leap months in 2020 set to true should normalize to false
        prefsManager.saveLunarDate(LunarDate(2020, 3, isLeap = true, day = 10, hour = 10, minute = 0))
        assertFalse("2020 month 3 is not leap", prefsManager.getLunarDate().isLeap)

        prefsManager.saveLunarDate(LunarDate(2020, 5, isLeap = true, day = 10, hour = 10, minute = 0))
        assertFalse("2020 month 5 is not leap", prefsManager.getLunarDate().isLeap)

        // Actual leap month 4 must be preserved
        prefsManager.saveLunarDate(LunarDate(2020, 4, isLeap = true, day = 10, hour = 10, minute = 0))
        assertTrue("2020 month 4 IS leap", prefsManager.getLunarDate().isLeap)

        // 3. Astronomical conversion of true leap month must succeed
        val solarLeap = BaziEngine.lunarToSolar(prefsManager.getLunarDate())
        assertNotNull(solarLeap)
        assertEquals(2020, solarLeap.year)
    }

    @Test
    fun testShortLunarMonthDayBoundaryClamping() {
        // In 1990, lunar month 1 is a small month (小月) with 29 days
        val days1990M1 = BaziEngine.getDaysInLunarMonth(1990, 1, false)
        assertEquals("1990 month 1 has 29 days", 29, days1990M1)

        // Adversarial input: day = 30
        prefsManager.saveLunarDate(LunarDate(1990, 1, false, day = 30, hour = 8, minute = 0))
        val retrieved30 = prefsManager.getLunarDate()
        assertEquals("Day 30 in a 29-day month must clamp to 29", 29, retrieved30.day)

        // Adversarial input: day = 0 and day = -5
        prefsManager.saveLunarDate(LunarDate(1990, 1, false, day = 0, hour = 8, minute = 0))
        assertEquals(1, prefsManager.getLunarDate().day)

        prefsManager.saveLunarDate(LunarDate(1990, 1, false, day = -5, hour = 8, minute = 0))
        assertEquals(1, prefsManager.getLunarDate().day)

        // Adversarial input: day = 100
        prefsManager.saveLunarDate(LunarDate(1990, 1, false, day = 100, hour = 8, minute = 0))
        assertEquals(29, prefsManager.getLunarDate().day)
    }

    @Test
    fun testBetCountExtremeStress() {
        val testVectors = listOf(
            Int.MIN_VALUE to 1,
            -1000000 to 1,
            -1 to 1,
            0 to 1,
            1 to 1,
            5 to 5,
            20 to 20,
            21 to 20,
            100 to 20,
            999999 to 20,
            Int.MAX_VALUE to 20
        )

        for ((input, expected) in testVectors) {
            prefsManager.saveBetCount(input)
            assertEquals("Bet count input $input must clamp to $expected", expected, prefsManager.getBetCount())
        }
    }

    @Test
    fun testStringExtremesAndSanitization() {
        // Blank fallbacks
        prefsManager.saveUserName("")
        assertEquals("缘主", prefsManager.getUserName())

        prefsManager.saveUserName(" \t \n \r ")
        assertEquals("缘主", prefsManager.getUserName())

        prefsManager.saveSelectedStrategy("")
        assertEquals("命理吉数优先", prefsManager.getSelectedStrategy())

        prefsManager.saveSelectedStrategy("   ")
        assertEquals("命理吉数优先", prefsManager.getSelectedStrategy())

        // Emojis & Special characters
        val specialName = "🎯 纯阳真人 666 🚀"
        prefsManager.saveUserName(specialName)
        assertEquals(specialName, prefsManager.getUserName())

        // SQL injection attempt string (must be stored safely without error)
        val sqlInjection = "' OR '1'='1'; DROP TABLE users;--"
        prefsManager.saveUserName(sqlInjection)
        assertEquals(sqlInjection, prefsManager.getUserName())

        // Massive string (10,000 chars)
        val massiveName = "道".repeat(10000)
        prefsManager.saveUserName(massiveName)
        assertEquals(10000, prefsManager.getUserName().length)
        assertEquals(massiveName, prefsManager.getUserName())
    }

    @Test
    fun testCorruptOrForeignTypeStorageRecovery() {
        // Inject non-expected types into storage keys to simulate external corruption
        testStorage[UserPreferencesManager.KEY_BET_COUNT] = "not_an_int"
        testStorage[UserPreferencesManager.KEY_LUNAR_YEAR] = 3.14159
        testStorage[UserPreferencesManager.KEY_IS_MALE] = 123
        testStorage[UserPreferencesManager.KEY_SELECTED_LOTTERY] = "corrupt_lottery_code_xyz"
        testStorage[UserPreferencesManager.KEY_USER_NAME] = 99999
        testStorage[UserPreferencesManager.KEY_SELECTED_STRATEGY] = true

        // None of these getters should throw ClassCastException
        val betCount = prefsManager.getBetCount()
        assertEquals(UserPreferencesManager.DEFAULT_BET_COUNT, betCount)

        val isMale = prefsManager.getGender()
        assertEquals(UserPreferencesManager.DEFAULT_IS_MALE, isMale)

        val lottery = prefsManager.getSelectedLottery()
        assertEquals(LotteryType.SSQ, lottery)

        val userName = prefsManager.getUserName()
        assertEquals(UserPreferencesManager.DEFAULT_USER_NAME, userName)

        val strategy = prefsManager.getSelectedStrategy()
        assertEquals(UserPreferencesManager.DEFAULT_STRATEGY, strategy)

        val lunarDate = prefsManager.getLunarDate()
        assertEquals(UserPreferencesManager.DEFAULT_LUNAR_DATE.year, lunarDate.year)
    }

    @Test
    fun testHighConcurrencyStress() {
        val numThreads = 16
        val iterationsPerThread = 500
        val latch = CountDownLatch(numThreads)
        val errorCount = AtomicInteger(0)

        for (i in 0 until numThreads) {
            Thread {
                try {
                    for (j in 0 until iterationsPerThread) {
                        val count = (j % 25) - 2
                        prefsManager.saveBetCount(count)
                        val retrievedCount = prefsManager.getBetCount()
                        assertTrue(retrievedCount in 1..20)

                        val year = 1920 + (j % 120)
                        prefsManager.saveLunarDate(LunarDate(year, 6, false, 15, 12, 0))
                        val retrievedDate = prefsManager.getLunarDate()
                        assertTrue(retrievedDate.year in 1930..2030)

                        prefsManager.saveGender(j % 2 == 0)
                        val retrievedGender = prefsManager.getGender()
                        assertNotNull(retrievedGender)

                        prefsManager.saveUserName("Thread_$i-$j")
                        val retrievedName = prefsManager.getUserName()
                        assertTrue("Expected Thread_ prefix but got '$retrievedName' in thread $i iteration $j", retrievedName.startsWith("Thread_"))
                    }
                } catch (t: Throwable) {
                    errorCount.incrementAndGet()
                    t.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        assertTrue("Concurrent stress run should finish within 10s", completed)
        assertEquals("No concurrent exceptions allowed", 0, errorCount.get())
    }

    @Test
    fun testCleanInstallStateExactness() {
        // Pristine memory store (empty)
        testStorage.clear()

        val date = prefsManager.getLunarDate()
        assertEquals(1990, date.year)
        assertEquals(6, date.month)
        assertFalse(date.isLeap)
        assertEquals(24, date.day)
        assertEquals(8, date.hour)
        assertEquals(30, date.minute)

        assertTrue(prefsManager.getGender())
        assertEquals(5, prefsManager.getBetCount())
        assertEquals(LotteryType.SSQ, prefsManager.getSelectedLottery())
        assertEquals("命理吉数优先", prefsManager.getSelectedStrategy())
        assertEquals("缘主", prefsManager.getUserName())
        assertTrue(prefsManager.getIsLunar())
    }

    @Test
    fun testColdRebootStatePreservationAcrossSeparateInstances() {
        val customDate = LunarDate(1995, 11, false, 18, 14, 20)
        prefsManager.saveLunarDate(customDate)
        prefsManager.saveGender(false)
        prefsManager.saveBetCount(17)
        prefsManager.saveSelectedLottery(LotteryType.QXC)
        prefsManager.saveSelectedStrategy("守正出奇")
        prefsManager.saveUserName("冷启动测试客")
        prefsManager.saveIsLunar(false)

        // Instantiate completely separate manager pointing to the same backing store
        val coldBootManager = UserPreferencesManager(context = null, inMemoryFallback = testStorage)
        assertEquals(customDate, coldBootManager.getLunarDate())
        assertFalse(coldBootManager.getGender())
        assertEquals(17, coldBootManager.getBetCount())
        assertEquals(LotteryType.QXC, coldBootManager.getSelectedLottery())
        assertEquals("守正出奇", coldBootManager.getSelectedStrategy())
        assertEquals("冷启动测试客", coldBootManager.getUserName())
        assertFalse(coldBootManager.getIsLunar())
    }

    @Test
    fun testClearSelectivelyPreservesNonPreferences() {
        testStorage["server_url"] = "https://ai-tutor.run.app"
        testStorage["ai_api_key"] = "test-key-12345"

        prefsManager.saveBetCount(15)
        prefsManager.saveUserName("临走前缘主")

        prefsManager.clear()

        // User preference keys should reset to defaults
        assertEquals(5, prefsManager.getBetCount())
        assertEquals("缘主", prefsManager.getUserName())

        // Non-preference keys must be preserved!
        assertEquals("https://ai-tutor.run.app", testStorage["server_url"])
        assertEquals("test-key-12345", testStorage["ai_api_key"])
    }
}
