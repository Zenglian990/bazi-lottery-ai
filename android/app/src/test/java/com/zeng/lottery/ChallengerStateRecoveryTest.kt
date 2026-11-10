package com.zeng.lottery

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.UserPreferencesManager
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.ui.viewmodel.LotteryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Challenger 2 Adversarial Verification Suite for Milestone 1.
 *
 * Focuses on:
 * 1. Process death & state recovery guarantees across ViewModel lifecycles.
 * 2. Eager offline priming (init {}) execution with restored user parameters vs 1990/Male defaults.
 * 3. Quick-reset (`resetToCurrentTime`) persistence across cold reboots.
 * 4. High-concurrency thread safety across parallel reads/writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChallengerStateRecoveryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        UserPreferencesManager.resetJvmTestPrefs()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Test double for Android Application that safely operates in JVM unit tests
     * without triggering 'Method not mocked' runtime exceptions.
     */
    open class MockTestApp : Application() {
        override fun getApplicationContext(): Context {
            return this
        }

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences? {
            return null
        }
    }

    private fun allocateMockApp(): Application {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstance.invoke(unsafe, MockTestApp::class.java) as Application
    }

    /**
     * Requirement 1: Adversarially verify process death and state recovery guarantees.
     * Construct ViewModel 1, mutate all 6 parameters, discard ViewModel 1,
     * construct ViewModel 2, assert all parameters are 100% identical.
     */
    @Test
    fun testProcessDeathAndStateRecoveryGuarantees() {
        // Step 1: Clean start with ViewModel 1
        var vm1: LotteryViewModel? = LotteryViewModel(allocateMockApp())
        assertNotNull(vm1)
        assertEquals("Clean start userName should be default", "缘主", vm1!!.userName.value)
        assertEquals("Clean start betCount should be default", 5, vm1!!.betCount.value)
        assertEquals("Clean start lottery should be SSQ", LotteryType.SSQ, vm1!!.selectedLottery.value)
        assertEquals("Clean start strategy should be default", "命理吉数优先", vm1!!.selectedStrategy.value)
        assertTrue("Clean start gender should be Male", vm1!!.isMale.value)
        assertTrue("Clean start isLunar should be true", vm1!!.isLunar.value)

        // Step 2: Mutate all parameters to customized user values
        val customizedDate = LunarDate(year = 1998, month = 10, isLeap = false, day = 15, hour = 18, minute = 45)
        vm1!!.setUserName("诸葛玄机")
        vm1!!.setBetCount(16)
        vm1!!.selectLottery(LotteryType.DLT)
        vm1!!.setLunarDate(customizedDate)
        vm1!!.setGender(false) // 坤造 (Female)
        vm1!!.selectStrategy("五行克抑均衡")
        vm1!!.setLunar(false) // 公历模式

        // Verify intermediate state on vm1
        assertEquals("诸葛玄机", vm1!!.userName.value)
        assertEquals(16, vm1!!.betCount.value)
        assertEquals(LotteryType.DLT, vm1!!.selectedLottery.value)
        assertEquals(customizedDate, vm1!!.lunarDate.value)
        assertFalse(vm1!!.isMale.value)
        assertEquals("五行克抑均衡", vm1!!.selectedStrategy.value)
        assertFalse(vm1!!.isLunar.value)

        // Step 3: Simulate process death (discard vm1 and trigger garbage collection)
        vm1 = null
        System.gc()

        // Step 4: Construct second ViewModel instance simulating cold process restart
        val vm2 = LotteryViewModel(allocateMockApp())
        assertNotNull(vm2)

        // Step 5: Assert all 6 parameters in vm2 are 100% identical to the mutated values
        assertEquals("Restored userName must match mutated value", "诸葛玄机", vm2.userName.value)
        assertEquals("Restored effectiveUserName must match", "诸葛玄机", vm2.effectiveUserName)
        assertEquals("Restored betCount must match mutated value", 16, vm2.betCount.value)
        assertEquals("Restored selectedLottery must match mutated value", LotteryType.DLT, vm2.selectedLottery.value)
        assertEquals("Restored lunarDate must match mutated value", customizedDate, vm2.lunarDate.value)
        assertEquals("Restored year must match", 1998, vm2.lunarDate.value.year)
        assertEquals("Restored month must match", 10, vm2.lunarDate.value.month)
        assertEquals("Restored day must match", 15, vm2.lunarDate.value.day)
        assertEquals("Restored hour must match", 18, vm2.lunarDate.value.hour)
        assertEquals("Restored minute must match", 45, vm2.lunarDate.value.minute)
        assertFalse("Restored isMale must match false (坤造)", vm2.isMale.value)
        assertEquals("Restored strategy must match mutated value", "五行克抑均衡", vm2.selectedStrategy.value)
        assertFalse("Restored isLunar must match false", vm2.isLunar.value)
    }

    /**
     * Requirement 2: Verify that eager offline priming (init {}) executes
     * with restored user values, NOT default 1990/Male/SSQ values.
     */
    @Test
    fun testEagerOfflinePrimingExecutesWithRestoredUserValues() {
        // Step 1: Pre-populate preferences with customized user profile
        val userDate = LunarDate(year = 1995, month = 8, isLeap = false, day = 12, hour = 14, minute = 20)
        val prefs = UserPreferencesManager(context = null)
        prefs.saveUserName("紫微道友")
        prefs.saveBetCount(12)
        prefs.saveSelectedLottery(LotteryType.DLT)
        prefs.saveLunarDate(userDate)
        prefs.saveGender(false) // 坤造
        prefs.saveSelectedStrategy("冷号反弹")
        prefs.saveIsLunar(true)

        // Step 2: Instantiate ViewModel (triggers init {})
        val vm = LotteryViewModel(allocateMockApp(), prefs)

        // Step 3: Compute expected destiny for restored values vs default 1990/Male values
        val expectedDestiny = BaziEngine.analyzeDestinyFromLunar(userDate, isMale = false, userName = "紫微道友")
        val defaultDestiny = BaziEngine.analyzeDestinyFromLunar(UserPreferencesManager.DEFAULT_LUNAR_DATE, isMale = true, userName = "缘主")

        // Step 4: Verify eager destiny priming
        val primedDestiny = vm.destiny.value
        assertNotNull("Eager primed destiny must not be null", primedDestiny)
        assertEquals("DayMaster must match restored date calculation", expectedDestiny.dayMaster, primedDestiny!!.dayMaster)
        assertEquals("Year pillar must match restored date", expectedDestiny.fourPillars.year.fullText, primedDestiny.fourPillars.year.fullText)
        assertEquals("Month pillar must match restored date", expectedDestiny.fourPillars.month.fullText, primedDestiny.fourPillars.month.fullText)
        assertEquals("Day pillar must match restored date", expectedDestiny.fourPillars.day.fullText, primedDestiny.fourPillars.day.fullText)
        assertEquals("Hour pillar must match restored date", expectedDestiny.fourPillars.hour.fullText, primedDestiny.fourPillars.hour.fullText)
        assertNotEquals("DayMaster must NOT equal default 1990 dayMaster", defaultDestiny.dayMaster, primedDestiny.dayMaster)

        // Step 5: Verify eager stats priming
        val primedStats = vm.stats.value
        assertNotNull("Eager primed stats must not be null", primedStats)
        assertEquals("Stats must be computed for DLT, NOT default SSQ", "dlt", primedStats!!.lotteryType)
        assertTrue("Stats totalDraws must be 100", primedStats.totalDraws >= 100)

        // Step 6: Verify eager history draws priming
        val primedHistory = vm.historyDraws.value
        assertNotNull("Eager primed history must not be null", primedHistory)
        assertTrue("History must contain 50 items", primedHistory.size >= 50)
        // DLT draw has 5 primary + 2 secondary numbers
        assertEquals("DLT draw must have 5 primary numbers", 5, primedHistory.first().primaryNumbers.size)
        assertEquals("DLT draw must have 2 secondary numbers", 2, primedHistory.first().secondaryNumbers?.size)

        // Step 7: Verify eager recommendation priming
        val primedRec = vm.recommendation.value
        assertNotNull("Eager primed recommendation must not be null", primedRec)
        assertEquals("Recommendation lotteryType must be DLT", "dlt", primedRec!!.lotteryType)
        assertEquals("Recommendation strategy must be '冷号反弹'", "冷号反弹", primedRec.strategy)
        assertEquals("Recommendation bet count must match restored 12 (NOT default 5)", 12, primedRec.recommendations.size)
        assertEquals("Recommendation userName must match restored userName", "紫微道友", primedRec.userName)

        // Assert all 12 tickets conform to DLT format
        primedRec.recommendations.forEach { item ->
            assertEquals(5, item.primaryNumbers.size)
            assertEquals(2, item.secondaryNumbers.size)
        }
    }

    /**
     * Requirement 3: Quick reset (`resetToCurrentTime`) persistence across cold reboots.
     */
    @Test
    fun testResetToCurrentTimePreservedAcrossProcessDeath() {
        var vm1: LotteryViewModel? = LotteryViewModel(allocateMockApp())
        assertNotNull(vm1)

        // Call resetToCurrentTime
        vm1!!.resetToCurrentTime()
        val resetLunar = vm1!!.lunarDate.value
        val resetSolar = vm1!!.birthDateTime.value
        assertNotNull(resetLunar)
        assertNotNull(resetSolar)
        assertTrue("Reset year should be recent", resetLunar.year in 2024..2028)

        // Discard vm1
        vm1 = null
        System.gc()

        // Recreate vm2
        val vm2 = LotteryViewModel(allocateMockApp())
        assertEquals("Restored lunarDate must match reset time", resetLunar, vm2.lunarDate.value)
        assertEquals("Restored birthDateTime must match reset solar", resetSolar, vm2.birthDateTime.value)

        // Verify primed destiny matches reset time
        assertNotNull(vm2.destiny.value)
        val expectedDestiny = BaziEngine.analyzeDestinyFromLunar(resetLunar, isMale = true, userName = "缘主")
        assertEquals(expectedDestiny.dayMaster, vm2.destiny.value!!.dayMaster)
    }

    /**
     * Requirement 4: Concurrency and thread safety across concurrent reads/writes on ViewModel.
     */
    @Test
    fun testConcurrentViewModelMutationsAndReads() {
        val app = allocateMockApp()
        val vm = LotteryViewModel(app)

        val numThreads = 16
        val iterations = 150
        val latch = CountDownLatch(numThreads)
        val errorCount = AtomicInteger(0)

        for (t in 0 until numThreads) {
            Thread {
                try {
                    for (i in 0 until iterations) {
                        val count = (i % 20) + 1
                        vm.setBetCount(count)
                        val readCount = vm.betCount.value
                        assertTrue("BetCount out of bounds: $readCount", readCount in 1..20)

                        vm.setGender(i % 2 == 0)
                        val readGender = vm.isMale.value
                        assertNotNull(readGender)

                        vm.setUserName("User_${t}_$i")
                        val readName = vm.userName.value
                        assertTrue("UserName should not be blank: $readName", readName.isNotBlank())

                        val lotteries = LotteryType.entries
                        val lottery = lotteries[i % lotteries.size]
                        vm.selectLottery(lottery)
                        val readLottery = vm.selectedLottery.value
                        assertTrue(readLottery in lotteries)

                        val year = 1950 + (i % 70)
                        vm.setLunarDate(LunarDate(year, (i % 12) + 1, false, (i % 28) + 1, (i % 24), 0))
                        val readDate = vm.lunarDate.value
                        assertTrue("Lunar year out of bounds: ${readDate.year}", readDate.year in 1930..2030)
                        assertTrue("Lunar month out of bounds: ${readDate.month}", readDate.month in 1..12)
                    }
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val completed = latch.await(15, TimeUnit.SECONDS)
        assertTrue("Concurrent stress test must complete within 15s", completed)
        assertEquals("Zero concurrent exceptions allowed on ViewModel", 0, errorCount.get())

        // Post-concurrency sanity check
        assertTrue("Final betCount in bounds", vm.betCount.value in 1..20)
        assertTrue("Final lunar year in bounds", vm.lunarDate.value.year in 1930..2030)
        assertTrue("Final lottery in entries", vm.selectedLottery.value in LotteryType.entries)
    }

    /**
     * Requirement 5: High-throughput stress test on UserPreferencesManager with 32 concurrent threads.
     */
    @Test
    fun testUserPreferencesStore32ThreadsStress() {
        val isolatedMap = ConcurrentHashMap<String, Any>()
        val prefs = UserPreferencesManager(context = null, inMemoryFallback = isolatedMap)

        val numThreads = 32
        val iterations = 500
        val latch = CountDownLatch(numThreads)
        val errorCount = AtomicInteger(0)

        for (t in 0 until numThreads) {
            Thread {
                try {
                    for (i in 0 until iterations) {
                        prefs.saveBetCount(i % 30 - 5)
                        val count = prefs.getBetCount()
                        assertTrue(count in 1..20)

                        val y = 1910 + (i % 140)
                        prefs.saveLunarDate(LunarDate(y, 6, false, 15, 12, 0))
                        val date = prefs.getLunarDate()
                        assertTrue(date.year in 1930..2030)

                        prefs.saveGender(i % 2 == 1)
                        val g = prefs.getGender()
                        assertNotNull(g)

                        prefs.saveSelectedLottery(LotteryType.entries[i % LotteryType.entries.size])
                        val l = prefs.getSelectedLottery()
                        assertTrue(l in LotteryType.entries)

                        prefs.saveSelectedStrategy("Strategy_${t % 5}")
                        val s = prefs.getSelectedStrategy()
                        assertTrue(s.isNotBlank())

                        prefs.saveUserName("Climber_${t}_$i")
                        val u = prefs.getUserName()
                        assertTrue(u.isNotBlank())
                    }
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val completed = latch.await(15, TimeUnit.SECONDS)
        assertTrue("32-thread stress test must finish within 15s", completed)
        assertEquals("Zero errors in 32-thread stress test", 0, errorCount.get())
    }
}
