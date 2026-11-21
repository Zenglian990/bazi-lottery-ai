package com.zeng.lottery

import com.zeng.lottery.data.local.BaziEngine
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Unit tests for UserPreferencesManager ensuring genuine state persistence,
 * JVM fallback fidelity, parameter save/restore, and boundary clamping.
 */
class UserPreferencesTest {

    private lateinit var prefsManager: UserPreferencesManager

    @Before
    fun setUp() {
        UserPreferencesManager.resetJvmTestPrefs()
        prefsManager = UserPreferencesManager(context = null)
    }

    @Test
    fun testCleanInstallDefaults() {
        val date = prefsManager.getLunarDate()
        assertNotNull(date)
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
    fun testSaveAndRestoreAllParameters() {
        val testDate = LunarDate(1988, 8, false, 8, 9, 30)
        prefsManager.saveLunarDate(testDate)
        prefsManager.saveGender(false)
        prefsManager.saveBetCount(12)
        prefsManager.saveSelectedLottery(LotteryType.DLT)
        prefsManager.saveSelectedStrategy("稳健大数据")
        prefsManager.saveUserName("玄易大师")
        prefsManager.saveIsLunar(false)

        // Verify on current instance
        assertEquals(testDate, prefsManager.getLunarDate())
        assertFalse(prefsManager.getGender())
        assertEquals(12, prefsManager.getBetCount())
        assertEquals(LotteryType.DLT, prefsManager.getSelectedLottery())
        assertEquals("稳健大数据", prefsManager.getSelectedStrategy())
        assertEquals("玄易大师", prefsManager.getUserName())
        assertFalse(prefsManager.getIsLunar())

        // Recreate instance to simulate process recreation / ViewModel cold restart
        val restoredManager = UserPreferencesManager(context = null)
        assertEquals(testDate, restoredManager.getLunarDate())
        assertFalse(restoredManager.getGender())
        assertEquals(12, restoredManager.getBetCount())
        assertEquals(LotteryType.DLT, restoredManager.getSelectedLottery())
        assertEquals("稳健大数据", restoredManager.getSelectedStrategy())
        assertEquals("玄易大师", restoredManager.getUserName())
        assertFalse(restoredManager.getIsLunar())
    }

    @Test
    fun testBoundaryClampingBetCount() {
        // Below minimum (1)
        prefsManager.saveBetCount(0)
        assertEquals(1, prefsManager.getBetCount())

        prefsManager.saveBetCount(-10)
        assertEquals(1, prefsManager.getBetCount())

        // Exact minimum
        prefsManager.saveBetCount(1)
        assertEquals(1, prefsManager.getBetCount())

        // Exact maximum
        prefsManager.saveBetCount(20)
        assertEquals(20, prefsManager.getBetCount())

        // Above maximum (20)
        prefsManager.saveBetCount(21)
        assertEquals(20, prefsManager.getBetCount())

        prefsManager.saveBetCount(999)
        assertEquals(20, prefsManager.getBetCount())
    }

    @Test
    fun testBoundaryClampingLunarDateYear() {
        // Below 1930
        prefsManager.saveLunarDate(LunarDate(1900, 5, false, 15, 10, 0))
        val lowDate = prefsManager.getLunarDate()
        assertEquals(1930, lowDate.year)

        // Above 2030
        prefsManager.saveLunarDate(LunarDate(2050, 5, false, 15, 10, 0))
        val highDate = prefsManager.getLunarDate()
        assertEquals(2030, highDate.year)

        // Month clamping
        prefsManager.saveLunarDate(LunarDate(1995, 15, false, 10, 10, 0))
        assertEquals(12, prefsManager.getLunarDate().month)

        prefsManager.saveLunarDate(LunarDate(1995, 0, false, 10, 10, 0))
        assertEquals(1, prefsManager.getLunarDate().month)

        // Hour clamping
        prefsManager.saveLunarDate(LunarDate(1995, 5, false, 10, 25, 0))
        assertEquals(23, prefsManager.getLunarDate().hour)

        prefsManager.saveLunarDate(LunarDate(1995, 5, false, 10, -5, 0))
        assertEquals(0, prefsManager.getLunarDate().hour)

        // Minute clamping
        prefsManager.saveLunarDate(LunarDate(1995, 5, false, 10, 10, 75))
        assertEquals(59, prefsManager.getLunarDate().minute)

        prefsManager.saveLunarDate(LunarDate(1995, 5, false, 10, 10, -15))
        assertEquals(0, prefsManager.getLunarDate().minute)

        // Day clamping
        prefsManager.saveLunarDate(LunarDate(1995, 5, false, 45, 10, 0))
        val maxDays = BaziEngine.getDaysInLunarMonth(1995, 5, false)
        assertEquals(maxDays, prefsManager.getLunarDate().day)
    }

    @Test
    fun testLeapMonthValidation() {
        // In 1990, leap month is 5. Month 6 is NOT a leap month.
        val leapMonth1990 = BaziEngine.getLeapMonth(1990)
        assertEquals(5, leapMonth1990)

        // Attempting to set isLeap=true for month 6 must normalize to false
        prefsManager.saveLunarDate(LunarDate(1990, 6, isLeap = true, day = 15, hour = 8, minute = 30))
        val savedDate1990 = prefsManager.getLunarDate()
        assertFalse("Month 6 in 1990 is not leap, isLeap must be false", savedDate1990.isLeap)

        // In 2020, leap month is 4.
        val leapMonth2020 = BaziEngine.getLeapMonth(2020)
        assertEquals(4, leapMonth2020)

        // Setting isLeap=true for month 4 in 2020 should be preserved
        prefsManager.saveLunarDate(LunarDate(2020, 4, isLeap = true, day = 15, hour = 8, minute = 30))
        val savedDate2020 = prefsManager.getLunarDate()
        assertTrue("Month 4 in 2020 is a genuine leap month, isLeap must be true", savedDate2020.isLeap)
    }

    @Test
    fun testBlankUserNameAndStrategyFallbacks() {
        prefsManager.saveUserName("")
        assertEquals("缘主", prefsManager.getUserName())

        prefsManager.saveUserName("   ")
        assertEquals("缘主", prefsManager.getUserName())

        prefsManager.saveUserName("  张道长  ")
        assertEquals("张道长", prefsManager.getUserName())

        prefsManager.saveSelectedStrategy("")
        assertEquals("命理吉数优先", prefsManager.getSelectedStrategy())

        prefsManager.saveSelectedStrategy("   ")
        assertEquals("命理吉数优先", prefsManager.getSelectedStrategy())

        prefsManager.saveSelectedStrategy("  五行克抑均衡  ")
        assertEquals("五行克抑均衡", prefsManager.getSelectedStrategy())
    }

    @Test
    fun testLotteryTypePersistenceAllEnums() {
        for (type in LotteryType.entries) {
            prefsManager.saveSelectedLottery(type)
            assertEquals(type, prefsManager.getSelectedLottery())
        }
    }

    @Test
    fun testClearPreferencesResetsToDefaults() {
        prefsManager.saveLunarDate(LunarDate(1985, 3, false, 12, 14, 0))
        prefsManager.saveGender(false)
        prefsManager.saveBetCount(15)
        prefsManager.saveSelectedLottery(LotteryType.FC3D)
        prefsManager.saveSelectedStrategy("冷号反弹")
        prefsManager.saveUserName("常客")
        prefsManager.saveIsLunar(false)

        prefsManager.clear()

        assertEquals(UserPreferencesManager.DEFAULT_LUNAR_DATE, prefsManager.getLunarDate())
        assertEquals(UserPreferencesManager.DEFAULT_IS_MALE, prefsManager.getGender())
        assertEquals(UserPreferencesManager.DEFAULT_BET_COUNT, prefsManager.getBetCount())
        assertEquals(UserPreferencesManager.DEFAULT_LOTTERY_TYPE, prefsManager.getSelectedLottery())
        assertEquals(UserPreferencesManager.DEFAULT_STRATEGY, prefsManager.getSelectedStrategy())
        assertEquals(UserPreferencesManager.DEFAULT_USER_NAME, prefsManager.getUserName())
        assertEquals(UserPreferencesManager.DEFAULT_IS_LUNAR, prefsManager.getIsLunar())
    }

    @Test
    fun testIsolatedInMemoryStore() {
        val isolatedMap = ConcurrentHashMap<String, Any>()
        val isolatedManager = UserPreferencesManager(context = null, inMemoryFallback = isolatedMap)

        isolatedManager.saveUserName("独立隔离缘主")
        isolatedManager.saveBetCount(18)

        assertEquals("独立隔离缘主", isolatedManager.getUserName())
        assertEquals(18, isolatedManager.getBetCount())

        // Ensure global JVM test map is still clean
        val globalManager = UserPreferencesManager(context = null)
        assertEquals("缘主", globalManager.getUserName())
        assertEquals(5, globalManager.getBetCount())
    }

    @Test
    fun testUserPreferencesStoreInterfaceCompliance() {
        val store: UserPreferencesStore = prefsManager
        store.saveBetCount(7)
        assertEquals(7, store.getBetCount())
        store.saveGender(true)
        assertTrue(store.getGender())
    }
}
