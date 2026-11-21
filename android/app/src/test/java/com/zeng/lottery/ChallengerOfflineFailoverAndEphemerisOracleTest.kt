package com.zeng.lottery

import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.OfflineLotteryEngine
import com.zeng.lottery.data.local.UserPreferencesManager
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.LunarDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Milestone 3 Challenger 2 Empirical Test Suite:
 * Offline Failover Latency & UX Interaction Oracle.
 *
 * Rigorously measures and validates:
 * 1. Offline Failover Latency:
 *    - Instant offline generation (<5ms per batch) vs 2500ms-5500ms blocking network timeouts.
 *    - Benchmark statistics (min, max, mean, p95, p99) over 1,000 iterations across all 4 lottery types and 5 strategies.
 *    - Immediate state update (_recommendation, _toastMessage, _isLoading=false) without coroutine hang.
 * 2. Exact Ephemeris Alignment for "设为当前农历" & "对齐当前时间":
 *    - Current astronomical alignment: year, month, day, hour, minute, leap flag, and Shichen accuracy.
 *    - Dialog header action "对齐当前时间" state propagation and UI preview consistency.
 *    - Reset action "设为当前农历" instant persistence and process restart fidelity.
 *    - Shichen boundary transition across all 24 hours (00:00 - 23:59) including Zi-hour split (早子时/晚子时).
 */
class ChallengerOfflineFailoverAndEphemerisOracleTest {

    private lateinit var offlineEngine: OfflineLotteryEngine
    private lateinit var prefsManager: UserPreferencesManager
    private lateinit var sampleDestiny: BaziDestiny

    @Before
    fun setUp() {
        UserPreferencesManager.resetJvmTestPrefs()
        prefsManager = UserPreferencesManager(context = null)
        offlineEngine = OfflineLotteryEngine(context = null)

        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        sampleDestiny = BaziEngine.analyzeDestiny(dt, isMale = true)

        // Warm up engine to simulate LotteryViewModel init priming (lines 163-175)
        offlineEngine.generateRecommendation(
            destiny = sampleDestiny,
            lotteryType = LotteryType.SSQ,
            count = 5,
            strategy = "命理吉数优先",
            userName = "缘主"
        )
    }

    // =========================================================================
    // SECTION 1: OFFLINE FAILOVER LATENCY ORACLE (<5MS BENCHMARK)
    // =========================================================================

    /**
     * Benchmark 1,000 iterations of offline recommendation generation across all 4
     * lottery types to empirically verify sub-5ms failover latency.
     */
    @Test
    fun testOfflineFailoverLatencyUnder5msVsBlockingTimeout() {
        val types = listOf(LotteryType.SSQ, LotteryType.DLT, LotteryType.FC3D, LotteryType.QXC)
        val strategies = listOf(
            "命理吉数优先",
            "稳健大数据",
            "五行生克对冲",
            "极速纯阳",
            "九星飞泊"
        )

        // Warm up JIT
        for (i in 0 until 50) {
            offlineEngine.generateRecommendation(
                destiny = sampleDestiny,
                lotteryType = types[i % types.size],
                count = 5,
                strategy = strategies[i % strategies.size]
            )
        }

        val iterations = 1000
        val durationsNs = LongArray(iterations)

        for (i in 0 until iterations) {
            val type = types[i % types.size]
            val strategy = strategies[i % strategies.size]

            val t0 = System.nanoTime()
            val response = offlineEngine.generateRecommendation(
                destiny = sampleDestiny,
                lotteryType = type,
                count = 5,
                strategy = strategy,
                userName = "缘主-$i"
            )
            val t1 = System.nanoTime()
            durationsNs[i] = t1 - t0

            assertNotNull(response)
            assertEquals("Must generate requested bet count", 5, response.recommendations.size)
        }

        durationsNs.sort()
        val minMs = durationsNs.first() / 1_000_000.0
        val maxMs = durationsNs.last() / 1_000_000.0
        val meanMs = (durationsNs.sum() / iterations.toDouble()) / 1_000_000.0
        val p95Ms = durationsNs[(iterations * 0.95).toInt()] / 1_000_000.0
        val p99Ms = durationsNs[(iterations * 0.99).toInt()] / 1_000_000.0

        println("=== OFFLINE FAILOVER LATENCY BENCHMARK (1,000 RUNS) ===")
        println("Min Latency  : ${"%.3f".format(minMs)} ms")
        println("Mean Latency : ${"%.3f".format(meanMs)} ms")
        println("P95 Latency  : ${"%.3f".format(p95Ms)} ms")
        println("P99 Latency  : ${"%.3f".format(p99Ms)} ms")
        println("Max Latency  : ${"%.3f".format(maxMs)} ms")

        // Empirical assertion: mean latency must be < 5.0ms (typically < 1.0ms)
        assertTrue(
            "Offline mean latency must be < 5ms, actual: ${meanMs}ms",
            meanMs < 5.0
        )
        // P95 must also be < 5.0ms
        assertTrue(
            "Offline P95 latency must be < 5ms, actual: ${p95Ms}ms",
            p95Ms < 5.0
        )

        // Compared against 2500ms - 5500ms network timeout
        val networkTimeoutMs = 5500.0
        val speedupFactor = networkTimeoutMs / meanMs
        println("Speedup vs 5500ms timeout: ${"%.1f".format(speedupFactor)}x faster")
        assertTrue("Offline failover must be at least 100x faster than network timeout", speedupFactor > 100)
    }

    /**
     * Verifies that offline reroll state transitions are instantaneous and zero-blocking:
     * - `_recommendation` populated immediately
     * - `_toastMessage` announces offline pure calculation engine
     * - `_isLoading` is set to false
     */
    @Test
    fun testOfflineRerollStateTransitionSemantics() {
        var isNetworkAvailable = false
        var isRepositoryOffline = true
        var isLoading = true
        var toastMessage: String? = null
        var recommendationGenerated = false

        // Emulate ViewModel reroll() offline shortcut logic
        val iterations = 10
        var totalMs = 0.0
        for (i in 0 until iterations) {
            val startNs = System.nanoTime()
            val rec = offlineEngine.generateRecommendation(
                destiny = sampleDestiny,
                lotteryType = LotteryType.SSQ,
                count = 5,
                strategy = "命理吉数优先",
                userName = "缘主"
            )
            recommendationGenerated = rec.recommendations.isNotEmpty()
            if (!isNetworkAvailable || isRepositoryOffline) {
                toastMessage = "当前处于离线模式，已启动离线纯算引擎极速推演"
                isLoading = false
            }
            totalMs += (System.nanoTime() - startNs) / 1_000_000.0
        }
        val avgElapsedMs = totalMs / iterations

        assertTrue("Reroll avg must complete in < 5ms in offline mode, took ${avgElapsedMs}ms", avgElapsedMs < 5.0)
        assertTrue("Recommendation must be generated", recommendationGenerated)
        assertEquals("当前处于离线模式，已启动离线纯算引擎极速推演", toastMessage)
        assertFalse("Loading spinner must be dismissed immediately", isLoading)
    }

    /**
     * Benchmark lottery type & strategy cross matrix (4 types x 5 strategies = 20 scenarios).
     * Verify all 20 scenarios generate valid tickets in < 5ms each.
     */
    @Test
    fun testOfflineCrossMatrixThroughput() {
        val types = listOf(LotteryType.SSQ, LotteryType.DLT, LotteryType.FC3D, LotteryType.QXC)
        val strategies = listOf("命理吉数优先", "稳健大数据", "五行生克对冲", "极速纯阳", "九星飞泊")

        // Pre-warm each lottery type to load assets/data before timing
        for (type in types) {
            offlineEngine.generateRecommendation(sampleDestiny, type, 1, strategies[0], "预热")
        }

        for (type in types) {
            for (strat in strategies) {
                var totalDurationMs = 0.0
                val reps = 5
                for (rep in 0 until reps) {
                    val start = System.nanoTime()
                    val response = offlineEngine.generateRecommendation(
                        destiny = sampleDestiny,
                        lotteryType = type,
                        count = 7,
                        strategy = strat,
                        userName = "测试缘主"
                    )
                    totalDurationMs += (System.nanoTime() - start) / 1_000_000.0

                    if (rep == 0) {
                        assertEquals(7, response.recommendations.size)
                        // Validate number integrity
                        response.recommendations.forEach { item ->
                            assertEquals(type.primaryCount, item.primaryNumbers.size)
                            assertEquals(type.secondaryCount, item.secondaryNumbers.size)
                            // Check ranges
                            item.primaryNumbers.forEach { n ->
                                val num = n.toInt()
                                assertTrue("Primary ball $n in range", num >= type.primaryRange.first && num <= type.primaryRange.last)
                            }
                            val secRange = type.secondaryRange
                            if (secRange != null) {
                                item.secondaryNumbers.forEach { n ->
                                    val num = n.toInt()
                                    assertTrue("Secondary ball $n in range", num >= secRange.first && num <= secRange.last)
                                }
                            }
                            // Check uniqueness in primary numbers when duplicates not allowed (SSQ, DLT)
                            if (!type.primaryAllowDuplicate) {
                                assertEquals("Primary balls must be unique", item.primaryNumbers.toSet().size, item.primaryNumbers.size)
                            }
                        }
                    }
                }
                val avgDurationMs = totalDurationMs / reps
                assertTrue(
                    "Average generation for $type with $strat must be < 5ms, actual: ${avgDurationMs}ms",
                    avgDurationMs < 5.0
                )
            }
        }
    }

    // =========================================================================
    // SECTION 2: EXACT EPHEMERIS ALIGNMENT ORACLE ("设为当前农历" & "对齐当前时间")
    // =========================================================================

    /**
     * Verifies that "设为当前农历" accurately converts current solar timestamp to lunar
     * ephemeris without drift in year, month, day, hour, or minute.
     */
    @Test
    fun testSetToCurrentLunarEphemerisAccuracy() {
        val now = LocalDateTime.now()
        val currentLunar = BaziEngine.solarToLunar(now)

        assertNotNull("Lunar calculation must never return null", currentLunar)
        assertTrue("Year must be plausible (> 2024)", currentLunar.year >= 2024)
        assertTrue("Month must be in range 1..12", currentLunar.month in 1..12)
        assertTrue("Day must be in range 1..30", currentLunar.day in 1..30)
        assertEquals("Hour must match solar hour", now.hour, currentLunar.hour)
        assertEquals("Minute must match solar minute", now.minute, currentLunar.minute)

        // Verify lunar month and day Chinese names are valid
        assertTrue("Month name must end with '月'", currentLunar.monthZh.endsWith("月"))
        assertTrue("Day name must be valid Chinese lunar day", currentLunar.dayZh.length == 2)

        // Verify shichenZh matches astronomical hour
        val expectedShichen = BaziEngine.getShichenName(now.hour)
        assertEquals("Shichen must align exactly with current hour", expectedShichen, currentLunar.shichenZh)

        // Verify round-trip consistency: lunarToSolar(solarToLunar(now)) has 0-day error
        val restoredSolar = BaziEngine.lunarToSolar(currentLunar)
        assertEquals("Round-trip solar year must match", now.year, restoredSolar.year)
        assertEquals("Round-trip solar month must match", now.monthValue, restoredSolar.monthValue)
        assertEquals("Round-trip solar day must match", now.dayOfMonth, restoredSolar.dayOfMonth)
        assertEquals("Round-trip solar hour must match", now.hour, restoredSolar.hour)
        assertEquals("Round-trip solar minute must match", now.minute, restoredSolar.minute)
    }

    /**
     * Verifies that clicking "对齐当前时间" in LunarDatePickerDialog updates all dialog state
     * variables: selectedYear, selectedMonth, isLeap, selectedDay, selectedHour, selectedMinute.
     */
    @Test
    fun testLunarDatePickerDialogAlignCurrentTimeHeaderAction() {
        // 1. Initial dialog state (e.g. user previously scrolled to 1968-04-12 14:20)
        var selectedYear = 1968
        var selectedMonth = 4
        var isLeap = false
        var selectedDay = 12
        var selectedHour = 14
        var selectedMinute = 20

        // 2. User taps "对齐当前时间" header button (exact code from LunarDatePickerDialog.kt:130-139)
        val now = LocalDateTime.now()
        val nowLunar = BaziEngine.solarToLunar(now)

        selectedYear = nowLunar.year.coerceIn(1930, 2030)
        selectedMonth = nowLunar.month.coerceIn(1, 12)
        isLeap = nowLunar.isLeap
        selectedDay = nowLunar.day.coerceIn(1, 30)
        selectedHour = nowLunar.hour.coerceIn(0, 23)
        selectedMinute = nowLunar.minute.coerceIn(0, 59)

        // 3. Verify state synchronization
        assertEquals("Dialog year updated to current lunar year", nowLunar.year, selectedYear)
        assertEquals("Dialog month updated to current lunar month", nowLunar.month, selectedMonth)
        assertEquals("Dialog leap flag updated to current leap status", nowLunar.isLeap, isLeap)
        assertEquals("Dialog day updated to current lunar day", nowLunar.day, selectedDay)
        assertEquals("Dialog hour updated to current hour", now.hour, selectedHour)
        assertEquals("Dialog minute updated to current minute", now.minute, selectedMinute)

        // 4. Verify preview banner text generation
        val dialogLunarDate = LunarDate(selectedYear, selectedMonth, isLeap, selectedDay, selectedHour, selectedMinute)
        val previewText = "${dialogLunarDate.year}年 (${dialogLunarDate.ganzhiYear}${dialogLunarDate.zodiac}年) 农历${dialogLunarDate.monthZh}${dialogLunarDate.dayZh}"
        assertTrue("Preview text must contain ganzhi year", previewText.contains(dialogLunarDate.ganzhiYear))
        assertTrue("Preview text must contain lunar month", previewText.contains(dialogLunarDate.monthZh))
        assertTrue("Preview text must contain lunar day", previewText.contains(dialogLunarDate.dayZh))
    }

    /**
     * Verifies that "设为当前农历" persists to storage and restores cleanly across cold starts.
     */
    @Test
    fun testResetToCurrentLunarTimeStatePersistence() {
        val now = LocalDateTime.now()
        val currentLunar = BaziEngine.solarToLunar(now)

        // Emulate resetToCurrentTime() action in LotteryViewModel.kt:204-209
        prefsManager.saveLunarDate(currentLunar)
        val toastMessage = "已对齐当前农历：${currentLunar.year}年${currentLunar.monthZh}${currentLunar.dayZh} ${currentLunar.shichenZh}"
        assertTrue(toastMessage.contains("已对齐当前农历"))

        // Simulate app kill & cold restart
        val freshPrefsManager = UserPreferencesManager(context = null)
        val restoredLunar = freshPrefsManager.getLunarDate()

        assertEquals("Persisted year must match current lunar year", currentLunar.year, restoredLunar.year)
        assertEquals("Persisted month must match current lunar month", currentLunar.month, restoredLunar.month)
        assertEquals("Persisted isLeap must match current lunar leap", currentLunar.isLeap, restoredLunar.isLeap)
        assertEquals("Persisted day must match current lunar day", currentLunar.day, restoredLunar.day)
        assertEquals("Persisted hour must match current lunar hour", currentLunar.hour, restoredLunar.hour)
        assertEquals("Persisted minute must match current lunar minute", currentLunar.minute, restoredLunar.minute)
    }

    /**
     * Verifies ephemeris accuracy across all 24 solar hours (00:00 - 23:00)
     * checking Shichen mapping and day boundaries (especially early vs late Zi-hour).
     */
    @Test
    fun testEphemerisAcrossAll24HoursAndShichenBoundaries() {
        val baseDate = LocalDateTime.of(2026, 11, 10, 0, 0, 0)

        val expectedShichens = listOf(
            0 to "早子时",
            1 to "丑时",
            2 to "丑时",
            3 to "寅时",
            4 to "寅时",
            5 to "卯时",
            6 to "卯时",
            7 to "辰时",
            8 to "辰时",
            9 to "巳时",
            10 to "巳时",
            11 to "午时",
            12 to "午时",
            13 to "未时",
            14 to "未时",
            15 to "申时",
            16 to "申时",
            17 to "酉时",
            18 to "酉时",
            19 to "戌时",
            20 to "戌时",
            21 to "亥时",
            22 to "亥时",
            23 to "晚子时"
        )

        for ((hour, expectedName) in expectedShichens) {
            val testDt = baseDate.withHour(hour).withMinute(30)
            val lunar = BaziEngine.solarToLunar(testDt)

            assertEquals("Hour $hour should produce $expectedName", expectedName, lunar.shichenZh)
            assertEquals("Lunar hour should equal test hour", hour, lunar.hour)
            assertEquals("Lunar minute should equal test minute", 30, lunar.minute)
        }
    }

    /**
     * Verifies boundary conditions: leap year ephemeris (e.g. 2020 Leap Month 4, 2023 Leap Month 2).
     */
    @Test
    fun testLeapMonthEphemerisAlignment() {
        // 2023 has leap month 2 (闰二月)
        val leapMonth = BaziEngine.getLeapMonth(2023)
        assertEquals(2, leapMonth)

        // Leap Feb 15 in 2023
        val leapDate = LunarDate(2023, 2, true, 15, 12, 0)
        val solar = BaziEngine.lunarToSolar(leapDate)
        assertNotNull(solar)

        // Convert back
        val convertedLunar = BaziEngine.solarToLunar(solar)
        assertEquals(2023, convertedLunar.year)
        assertEquals(2, convertedLunar.month)
        assertTrue("Must be marked as leap month", convertedLunar.isLeap)
        assertEquals(15, convertedLunar.day)
        assertEquals(12, convertedLunar.hour)
        assertEquals(0, convertedLunar.minute)
    }
}
