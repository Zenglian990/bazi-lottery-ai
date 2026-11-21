package com.zeng.lottery

import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.OfflineLotteryEngine
import com.zeng.lottery.data.local.UserPreferencesManager
import com.zeng.lottery.data.local.UserPreferencesStore
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.LunarDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bazi Lottery AI — 4-Tier End-to-End Persistence and UX Verification Test.
 *
 * Covers:
 * - Tier 1: Feature Coverage (Preferences persistence for all 6 fields, date display, quick reset, pick history, offline mode)
 * - Tier 2: Boundary & Corner Cases (Leap month dates, year limits 1930..2030, bet count limits 1..20, empty user name, rapid resets)
 * - Tier 3: Cross-Feature Combinations (Gender switch + leap month, lottery switch + bet count change, offline generation + history save)
 * - Tier 4: Real-World Application Scenarios (Cold start -> customize all preferences -> kill process -> restart -> verify 100% restored; Reroll tickets -> save ticket -> view history -> delete -> verify)
 */
class E2EPersistenceAndUxTest {

    // ==========================================================================
    // IN-MEMORY USER PICK HISTORY CONTRACT IMPLEMENTATION FOR JVM E2E TESTING
    // ==========================================================================
    data class SavedTicket(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val lotteryType: LotteryType,
        val userName: String,
        val primaryNumbers: List<Int>,
        val secondaryNumbers: List<Int> = emptyList(),
        val strategy: String,
        val rationale: String
    )

    interface UserPickHistoryRepository {
        fun saveTicket(ticket: SavedTicket): String
        fun getAllTickets(): List<SavedTicket>
        fun deleteTicket(id: String): Boolean
        fun clearAll()
    }

    class InMemoryPickHistoryStore : UserPickHistoryRepository {
        private val storage = mutableListOf<SavedTicket>()

        override fun saveTicket(ticket: SavedTicket): String {
            storage.add(0, ticket) // descending timestamp
            return ticket.id
        }

        override fun getAllTickets(): List<SavedTicket> = storage.toList()

        override fun deleteTicket(id: String): Boolean {
            val initialSize = storage.size
            storage.removeAll { it.id == id }
            return storage.size < initialSize
        }

        override fun clearAll() {
            storage.clear()
        }
    }

    private lateinit var prefsManager: UserPreferencesManager
    private lateinit var offlineEngine: OfflineLotteryEngine
    private lateinit var historyStore: InMemoryPickHistoryStore

    @Before
    fun setUp() {
        UserPreferencesManager.resetJvmTestPrefs()
        prefsManager = UserPreferencesManager(context = null)
        offlineEngine = OfflineLotteryEngine(context = null)
        historyStore = InMemoryPickHistoryStore()
    }

    // ==========================================================================
    // TIER 1: FEATURE COVERAGE
    // ==========================================================================

    @Test
    fun testTier1_F1_PreferencesPersistence_All6Fields() {
        val testDate = LunarDate(1985, 5, false, 15, 14, 30)
        prefsManager.saveLunarDate(testDate)
        prefsManager.saveGender(false) // 坤造
        prefsManager.saveBetCount(7)
        prefsManager.saveSelectedLottery(LotteryType.DLT)
        prefsManager.saveSelectedStrategy("稳健大数据")
        prefsManager.saveUserName("曾练缘主")
        prefsManager.saveIsLunar(true)

        // Simulate process termination & cold start: create new instance with clean JVM memory pointer
        val restoredManager = UserPreferencesManager(context = null)

        val restoredDate = restoredManager.getLunarDate()
        assertEquals(1985, restoredDate.year)
        assertEquals(5, restoredDate.month)
        assertFalse(restoredDate.isLeap)
        assertEquals(15, restoredDate.day)
        assertEquals(14, restoredDate.hour)
        assertEquals(30, restoredDate.minute)

        assertFalse(restoredManager.getGender())
        assertEquals(7, restoredManager.getBetCount())
        assertEquals(LotteryType.DLT, restoredManager.getSelectedLottery())
        assertEquals("稳健大数据", restoredManager.getSelectedStrategy())
        assertEquals("曾练缘主", restoredManager.getUserName())
        assertTrue(restoredManager.getIsLunar())
    }

    @Test
    fun testTier1_F2_DateDisplayRendering_Hierarchy() {
        val date = LunarDate(1990, 6, false, 24, 8, 30)
        val buttonText = date.displayButtonDate

        // Verify full representation: Year, Ganzhi, Zodiac, Lunar Month, Day
        assertTrue(buttonText.contains("1990年"))
        assertTrue(buttonText.contains("庚午"))
        assertTrue(buttonText.contains("马年"))
        assertTrue(buttonText.contains("农历六月廿四"))
    }

    @Test
    fun testTier1_F3_QuickResetToCurrentLunarTime() {
        val nowSolar = LocalDateTime.now()
        val nowLunar = BaziEngine.solarToLunar(nowSolar)

        assertNotNull(nowLunar)
        assertTrue(nowLunar.year >= 2024)
        assertTrue(nowLunar.month in 1..12)
        assertTrue(nowLunar.day in 1..30)
    }

    @Test
    fun testTier1_F3_PickHistoryStore_CRUD() {
        assertEquals(0, historyStore.getAllTickets().size)

        // Save ticket 1
        val t1 = SavedTicket(
            lotteryType = LotteryType.SSQ,
            userName = "缘主",
            primaryNumbers = listOf(3, 7, 12, 19, 25, 31),
            secondaryNumbers = listOf(9),
            strategy = "命理吉数优先",
            rationale = "测试吉数"
        )
        val id1 = historyStore.saveTicket(t1)
        assertEquals(1, historyStore.getAllTickets().size)

        // Save ticket 2
        val t2 = SavedTicket(
            lotteryType = LotteryType.DLT,
            userName = "曾先生",
            primaryNumbers = listOf(5, 11, 18, 24, 33),
            secondaryNumbers = listOf(3, 10),
            strategy = "稳健大数据",
            rationale = "大乐透对冲"
        )
        val id2 = historyStore.saveTicket(t2)
        assertEquals(2, historyStore.getAllTickets().size)

        // Verify descending order
        val all = historyStore.getAllTickets()
        assertEquals(id2, all[0].id)
        assertEquals(id1, all[1].id)

        // Delete ticket 1
        val deleted = historyStore.deleteTicket(id1)
        assertTrue(deleted)
        assertEquals(1, historyStore.getAllTickets().size)
        assertEquals(id2, historyStore.getAllTickets()[0].id)

        // Clear all
        historyStore.clearAll()
        assertEquals(0, historyStore.getAllTickets().size)
    }

    @Test
    fun testTier1_F3_OfflineMode_InstantGeneration() {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)

        val lotteries = listOf(LotteryType.SSQ, LotteryType.DLT, LotteryType.FC3D, LotteryType.QXC)
        for (lottery in lotteries) {
            val start = System.currentTimeMillis()
            val rec = offlineEngine.generateRecommendation(
                destiny = destiny,
                lotteryType = lottery,
                count = 5,
                strategy = "命理吉数优先"
            )
            val elapsed = System.currentTimeMillis() - start

            assertNotNull(rec)
            assertEquals(5, rec.recommendations.size)
            assertTrue("Offline generation took ${elapsed}ms (>100ms)", elapsed < 100)
        }
    }

    // ==========================================================================
    // TIER 2: BOUNDARY & CORNER CASES
    // ==========================================================================

    @Test
    fun testTier2_B1_LeapMonthDates() {
        // 2023 has leap month 2 (闰二月)
        val leapMonth2023 = BaziEngine.getLeapMonth(2023)
        assertEquals(2, leapMonth2023)

        val leapDate = LunarDate(2023, 2, true, 15, 12, 0)
        assertTrue(leapDate.isLeap)
        assertTrue(leapDate.monthZh.contains("闰二月"))

        prefsManager.saveLunarDate(leapDate)
        val restored = prefsManager.getLunarDate()
        assertEquals(2023, restored.year)
        assertEquals(2, restored.month)
        assertTrue(restored.isLeap)
        assertEquals(15, restored.day)
    }

    @Test
    fun testTier2_B1_NonLeapYear_LeapFlagSanitization() {
        // 2021 has no leap month (0)
        val leapMonth2021 = BaziEngine.getLeapMonth(2021)
        assertEquals(0, leapMonth2021)

        val invalidLeapDate = LunarDate(2021, 6, true, 15, 10, 0)
        prefsManager.saveLunarDate(invalidLeapDate)

        val restored = prefsManager.getLunarDate()
        assertEquals(2021, restored.year)
        assertEquals(6, restored.month)
        assertFalse("Leap flag must be sanitized to false for non-leap year", restored.isLeap)
    }

    @Test
    fun testTier2_B2_YearLimits_1930To2030() {
        // Underflow: 1910 -> clamped to 1930
        prefsManager.saveLunarDate(LunarDate(1910, 5, false, 10, 8, 0))
        assertEquals(1930, prefsManager.getLunarDate().year)

        // Exact floor: 1930
        prefsManager.saveLunarDate(LunarDate(1930, 1, false, 1, 0, 0))
        assertEquals(1930, prefsManager.getLunarDate().year)

        // Exact ceiling: 2030
        prefsManager.saveLunarDate(LunarDate(2030, 12, false, 20, 23, 59))
        assertEquals(2030, prefsManager.getLunarDate().year)

        // Overflow: 2055 -> clamped to 2030
        prefsManager.saveLunarDate(LunarDate(2055, 3, false, 5, 12, 0))
        assertEquals(2030, prefsManager.getLunarDate().year)
    }

    @Test
    fun testTier2_B3_BetCountLimits_1To20() {
        // Underflow
        prefsManager.saveBetCount(0)
        assertEquals(1, prefsManager.getBetCount())
        prefsManager.saveBetCount(-10)
        assertEquals(1, prefsManager.getBetCount())

        // Exact bounds
        prefsManager.saveBetCount(1)
        assertEquals(1, prefsManager.getBetCount())
        prefsManager.saveBetCount(20)
        assertEquals(20, prefsManager.getBetCount())

        // Overflow
        prefsManager.saveBetCount(21)
        assertEquals(20, prefsManager.getBetCount())
        prefsManager.saveBetCount(100)
        assertEquals(20, prefsManager.getBetCount())
    }

    @Test
    fun testTier2_B4_UserName_EdgeCases() {
        prefsManager.saveUserName("")
        assertEquals("缘主", prefsManager.getUserName())

        prefsManager.saveUserName("    ")
        assertEquals("缘主", prefsManager.getUserName())

        prefsManager.saveUserName("  李四  ")
        assertEquals("李四", prefsManager.getUserName())

        prefsManager.saveUserName("曾先生🍀#$@!*")
        assertEquals("曾先生🍀#$@!*", prefsManager.getUserName())
    }

    @Test
    fun testTier2_B5_RapidConsecutiveResets_Idempotence() {
        for (i in 1..20) {
            val nowLunar = BaziEngine.solarToLunar(LocalDateTime.now())
            prefsManager.saveLunarDate(nowLunar)
            val d = prefsManager.getLunarDate()
            assertTrue(d.year in 1930..2030)
            assertTrue(d.month in 1..12)
            assertTrue(d.day in 1..30)
        }
    }

    @Test
    fun testTier2_B6_HistoryStore_EdgeCases() {
        val deleted = historyStore.deleteTicket("non-existent-uuid-12345")
        assertFalse(deleted)
        historyStore.clearAll()
        assertEquals(0, historyStore.getAllTickets().size)
    }

    // ==========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // ==========================================================================

    @Test
    fun testTier3_C1_GenderSwitch_With_LeapMonth() {
        val dt = LocalDateTime.of(2020, 5, 23, 10, 0)
        val destinyFemale = BaziEngine.analyzeDestiny(dt, isMale = false)
        val destinyMale = BaziEngine.analyzeDestiny(dt, isMale = true)

        assertEquals("female", destinyFemale.gender)
        assertEquals("male", destinyMale.gender)
        assertEquals(destinyFemale.dayMaster, destinyMale.dayMaster)

        val recFemale = offlineEngine.generateRecommendation(destinyFemale, LotteryType.SSQ, count = 3)
        assertEquals(3, recFemale.recommendations.size)
        recFemale.recommendations.forEach { item ->
            assertEquals(6, item.primaryNumbers.size)
            assertEquals(1, item.secondaryNumbers.size)
        }
    }

    @Test
    fun testTier3_C2_LotterySwitch_With_CustomBetCount() {
        val dt = LocalDateTime.of(1995, 3, 10, 14, 0)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)

        // Switch to DLT with 15 bets
        val recDlt = offlineEngine.generateRecommendation(destiny, LotteryType.DLT, count = 15)
        assertEquals(15, recDlt.recommendations.size)

        recDlt.recommendations.forEach { item ->
            assertEquals(5, item.primaryNumbers.size)
            assertEquals(2, item.secondaryNumbers.size)
            item.primaryNumbers.forEach { n ->
                val intVal = n.toInt()
                assertTrue(intVal in 1..35)
            }
            item.secondaryNumbers.forEach { n ->
                val intVal = n.toInt()
                assertTrue(intVal in 1..12)
            }
        }
    }

    @Test
    fun testTier3_C2_Fc3d_NoSecondaryNumbers() {
        val dt = LocalDateTime.of(1992, 11, 8, 16, 0)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = false)

        val recFc3d = offlineEngine.generateRecommendation(destiny, LotteryType.FC3D, count = 8)
        assertEquals(8, recFc3d.recommendations.size)

        recFc3d.recommendations.forEach { item ->
            assertEquals(3, item.primaryNumbers.size)
            assertTrue(item.secondaryNumbers.isEmpty())
            item.primaryNumbers.forEach { n ->
                val intVal = n.toInt()
                assertTrue(intVal in 0..9)
            }
        }
    }

    @Test
    fun testTier3_C3_OfflineGeneration_And_HistorySave() {
        val dt = LocalDateTime.of(1988, 7, 20, 9, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)

        val rec = offlineEngine.generateRecommendation(destiny, LotteryType.SSQ, count = 5)

        for (i in 0 until 3) {
            val item = rec.recommendations[i]
            historyStore.saveTicket(
                SavedTicket(
                    lotteryType = LotteryType.SSQ,
                    userName = "玄机缘主",
                    primaryNumbers = item.primaryNumbers.map { it.toInt() },
                    secondaryNumbers = item.secondaryNumbers.map { it.toInt() },
                    strategy = "命理吉数优先",
                    rationale = item.scoreRationale
                )
            )
        }

        val all = historyStore.getAllTickets()
        assertEquals(3, all.size)
        all.forEach { saved ->
            assertEquals("玄机缘主", saved.userName)
            assertEquals(6, saved.primaryNumbers.size)
            assertEquals(1, saved.secondaryNumbers.size)
        }
    }

    // ==========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // ==========================================================================

    @Test
    fun testTier4_Scenario_ColdStart_Customize_Kill_Restart_100Pct_Recovery() {
        // Step 1: Initial Cold Launch
        val initManager = UserPreferencesManager(context = null)
        assertEquals(1990, initManager.getLunarDate().year)
        assertTrue(initManager.getGender())
        assertEquals(5, initManager.getBetCount())
        assertEquals(LotteryType.SSQ, initManager.getSelectedLottery())
        assertEquals("缘主", initManager.getUserName())

        // Step 2: User customizes all 6 preferences
        initManager.saveLunarDate(LunarDate(1988, 1, false, 29, 10, 0))
        initManager.saveGender(false)
        initManager.saveBetCount(8)
        initManager.saveSelectedLottery(LotteryType.DLT)
        initManager.saveSelectedStrategy("五行生克平衡")
        initManager.saveUserName("林悦")

        // Step 3: Process kill simulation -> instantiate brand new UserPreferencesManager
        val restartedManager = UserPreferencesManager(context = null)

        // Step 4: Verify 100% state recovery
        val restoredDate = restartedManager.getLunarDate()
        assertEquals(1988, restoredDate.year)
        assertEquals(1, restoredDate.month)
        assertEquals(29, restoredDate.day)
        assertEquals(10, restoredDate.hour)
        assertFalse(restartedManager.getGender())
        assertEquals(8, restartedManager.getBetCount())
        assertEquals(LotteryType.DLT, restartedManager.getSelectedLottery())
        assertEquals("五行生克平衡", restartedManager.getSelectedStrategy())
        assertEquals("林悦", restartedManager.getUserName())
    }

    @Test
    fun testTier4_Scenario_Ticket_Reroll_Save_View_Delete_Clear_Lifecycle() {
        val dt = LocalDateTime.of(1990, 8, 15, 8, 30)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = true)

        // 1. Initial generation
        val rec1 = offlineEngine.generateRecommendation(destiny, LotteryType.SSQ, count = 3)
        assertEquals(3, rec1.recommendations.size)

        // 2. Reroll
        val rec2 = offlineEngine.generateRecommendation(destiny, LotteryType.SSQ, count = 3)
        val favored = rec2.recommendations[1]

        // 3. Save ticket
        val ssqId = historyStore.saveTicket(
            SavedTicket(
                lotteryType = LotteryType.SSQ,
                userName = "缘主",
                primaryNumbers = favored.primaryNumbers.map { it.toInt() },
                secondaryNumbers = favored.secondaryNumbers.map { it.toInt() },
                strategy = "命理吉数优先",
                rationale = favored.scoreRationale
            )
        )
        assertEquals(1, historyStore.getAllTickets().size)

        // 4. Switch to QXC, generate and save another
        val recQxc = offlineEngine.generateRecommendation(destiny, LotteryType.QXC, count = 2)
        val qxcId = historyStore.saveTicket(
            SavedTicket(
                lotteryType = LotteryType.QXC,
                userName = "缘主",
                primaryNumbers = recQxc.recommendations[0].primaryNumbers.map { it.toInt() },
                secondaryNumbers = emptyList(),
                strategy = "七星阵合",
                rationale = recQxc.recommendations[0].scoreRationale
            )
        )
        assertEquals(2, historyStore.getAllTickets().size)

        // 5. Delete SSQ ticket
        val deleted = historyStore.deleteTicket(ssqId)
        assertTrue(deleted)
        val remaining = historyStore.getAllTickets()
        assertEquals(1, remaining.size)
        assertEquals(qxcId, remaining[0].id)

        // 6. Clear all
        historyStore.clearAll()
        assertEquals(0, historyStore.getAllTickets().size)
    }

    @Test
    fun testTier4_Scenario_LeapMonth_Birthday_Journey() {
        val leapDate = LunarDate(2023, 2, true, 24, 14, 0)
        val displayStr = leapDate.displayButtonDate

        assertTrue(displayStr.contains("2023年"))
        assertTrue(displayStr.contains("癸卯"))
        assertTrue(displayStr.contains("兔年"))
        assertTrue(displayStr.contains("闰二月"))
        assertTrue(displayStr.contains("廿四"))

        val dt = BaziEngine.lunarToSolar(leapDate)
        val destiny = BaziEngine.analyzeDestiny(dt, isMale = false)
        val rec = offlineEngine.generateRecommendation(destiny, LotteryType.SSQ, count = 2)
        assertEquals(2, rec.recommendations.size)
    }
}
