package com.zeng.lottery

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zeng.lottery.data.local.SavedTicket
import com.zeng.lottery.data.local.UserPickHistoryStore
import com.zeng.lottery.data.local.UserPreferencesManager
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.ui.viewmodel.LotteryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Constructor
import java.util.UUID

/**
 * Verification test suite for LotteryViewModel reflection factory compatibility (R1),
 * startup stability, and Gson TypeToken persistence resilience (R2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LotteryViewModelReflectionAndStartupTest {

    private val testDispatcher = StandardTestDispatcher()
    private val gson = Gson()

    open class TestApplication : Application() {
        override fun getApplicationContext(): Context {
            return this
        }
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
    fun testViewModelSingleArgConstructorReflectionCompatibility() {
        // Acceptance criteria: ViewModelProvider.AndroidViewModelFactory requires
        // a public constructor with exactly (Application) parameter via reflection.
        val constructor: Constructor<LotteryViewModel> = LotteryViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull("LotteryViewModel must declare a public single-arg (Application) constructor for factory reflection", constructor)

        val app = TestApplication()
        val instance = constructor.newInstance(app)
        assertNotNull("LotteryViewModel must instantiate via reflection without throwing NoSuchMethodException", instance)
        assertTrue("Instance must be an AndroidViewModel", instance is AndroidViewModel)
    }

    @Test
    fun testViewModelJvmOverloadsConstructorsCompleteness() {
        // Verify all 3 overloaded constructors exist in bytecode
        val singleArg = LotteryViewModel::class.java.getConstructor(Application::class.java)
        val twoArg = LotteryViewModel::class.java.getConstructor(
            Application::class.java,
            UserPreferencesManager::class.java
        )
        val threeArg = LotteryViewModel::class.java.getConstructor(
            Application::class.java,
            UserPreferencesManager::class.java,
            UserPickHistoryStore::class.java
        )

        assertNotNull(singleArg)
        assertNotNull(twoArg)
        assertNotNull(threeArg)

        val app = TestApplication()
        val vmFromSingle = singleArg.newInstance(app)
        val vmFromTwo = twoArg.newInstance(app, UserPreferencesManager())
        val vmFromThree = threeArg.newInstance(app, UserPreferencesManager(), UserPickHistoryStore())

        assertNotNull(vmFromSingle)
        assertNotNull(vmFromTwo)
        assertNotNull(vmFromThree)
    }

    @Test
    fun testStartupEagerPrimingExecutionSpeed() {
        val app = TestApplication()
        val startNano = System.nanoTime()

        val constructor = LotteryViewModel::class.java.getConstructor(Application::class.java)
        val vm = constructor.newInstance(app)

        val elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0

        // Cold construction and offline priming must be synchronous and fast (< 250ms on JVM)
        assertTrue("Cold initialization must execute without blocking network and finish under 250ms (took: ${elapsedMs}ms)", elapsedMs < 250.0)
        assertNotNull("Destiny must be primed eagerly from offline engine", vm.destiny.value)
        assertNotNull("Recommendation must be primed eagerly from offline engine", vm.recommendation.value)
        assertNotNull("Stats must be primed eagerly from offline engine", vm.stats.value)
    }

    @Test
    fun testSavedTicketGsonTypeTokenRoundtripSerialization() {
        val ticket = SavedTicket(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            lotteryType = LotteryType.SSQ,
            userName = "曾缘主",
            primaryNumbers = listOf(3, 7, 12, 19, 25, 31),
            secondaryNumbers = listOf(8),
            strategy = "命理吉数优先",
            rationale = "乾造生辰八字木火通明，取河图相合灵数"
        )

        val list = listOf(ticket)
        val listType = object : TypeToken<List<SavedTicket>>() {}.type
        val json = gson.toJson(list, listType)
        assertTrue("JSON must contain serialized field 'userName'", json.contains("曾缘主"))
        assertTrue("JSON must contain serialized field 'lotteryType'", json.contains("SSQ"))

        val deserialized: List<SavedTicket> = gson.fromJson(json, listType)
        assertEquals(1, deserialized.size)
        val recovered = deserialized.first()
        assertEquals(ticket.id, recovered.id)
        assertEquals(ticket.lotteryType, recovered.lotteryType)
        assertEquals(ticket.userName, recovered.userName)
        assertEquals(ticket.primaryNumbers, recovered.primaryNumbers)
        assertEquals(ticket.secondaryNumbers, recovered.secondaryNumbers)
        assertEquals(ticket.strategy, recovered.strategy)
        assertEquals(ticket.rationale, recovered.rationale)
    }

    @Test
    fun testUserPickHistoryStoreCorruptionResilience() {
        val store = UserPickHistoryStore(context = null)
        store.clearAll()

        // Test normal operation
        val t1 = SavedTicket(
            lotteryType = LotteryType.DLT,
            primaryNumbers = listOf(5, 11, 18, 24, 32),
            secondaryNumbers = listOf(4, 9)
        )
        store.saveTicket(t1)
        assertEquals(1, store.getTicketCount())

        // Simulate corrupted JSON in memory store
        val memoryField = UserPickHistoryStore::class.java.getDeclaredField("jvmTestStorage")
        memoryField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val storageMap = memoryField.get(null) as java.util.concurrent.ConcurrentHashMap<String, String>
        storageMap[UserPickHistoryStore.KEY_SAVED_TICKETS] = "{ INVALID MALFORMED JSON !!! }"

        // Must not throw exception; gracefully return empty list
        val ticketsAfterCorruption = store.getAllTickets()
        assertNotNull(ticketsAfterCorruption)
        assertEquals(0, ticketsAfterCorruption.size)
    }
}
