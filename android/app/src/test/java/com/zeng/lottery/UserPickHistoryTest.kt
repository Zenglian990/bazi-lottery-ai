package com.zeng.lottery

import com.zeng.lottery.data.local.SavedTicket
import com.zeng.lottery.data.local.UserPickHistoryRepository
import com.zeng.lottery.data.local.UserPickHistoryStore
import com.zeng.lottery.data.model.LotteryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Comprehensive unit tests for UserPickHistoryStore verifying:
 * - Empty state handling
 * - Single ticket save and retrieval with full field fidelity
 * - Strict newest-first sorting by timestamp
 * - Single ticket deletion by ID
 * - Clear all records
 * - Batch ticket persistence
 * - Upsert behavior on duplicate IDs
 * - Cross-lottery type preservation (SSQ, DLT, FC3D, QXC)
 * - Fault tolerance on corrupted JSON
 * - Thread-safe concurrent operations
 * - Interface compliance
 */
class UserPickHistoryTest {

    private lateinit var historyStore: UserPickHistoryStore

    @Before
    fun setUp() {
        UserPickHistoryStore.resetJvmTestStore()
        historyStore = UserPickHistoryStore(context = null)
    }

    @Test
    fun testEmptyStateOnNewInstall() {
        val tickets = historyStore.getAllTickets()
        assertNotNull(tickets)
        assertTrue("Newly installed store must have empty history", tickets.isEmpty())
        assertEquals(0, historyStore.getTicketCount())
    }

    @Test
    fun testSaveAndRetrieveSingleTicket() {
        val ticket = SavedTicket(
            id = "test-ticket-001",
            timestamp = 1731234567890L,
            lotteryType = LotteryType.SSQ,
            userName = "曾先生",
            primaryNumbers = listOf(1, 5, 12, 19, 23, 31),
            secondaryNumbers = listOf(8),
            strategy = "命理吉数优先",
            rationale = "金水相涵，天干透辛"
        )

        historyStore.saveTicket(ticket)

        val retrieved = historyStore.getAllTickets()
        assertEquals(1, retrieved.size)
        assertEquals(1, historyStore.getTicketCount())

        val first = retrieved.first()
        assertEquals("test-ticket-001", first.id)
        assertEquals(1731234567890L, first.timestamp)
        assertEquals(LotteryType.SSQ, first.lotteryType)
        assertEquals("曾先生", first.userName)
        assertEquals(listOf(1, 5, 12, 19, 23, 31), first.primaryNumbers)
        assertEquals(listOf(8), first.secondaryNumbers)
        assertEquals("命理吉数优先", first.strategy)
        assertEquals("金水相涵，天干透辛", first.rationale)
    }

    @Test
    fun testOrderNewestFirst() {
        val t1 = SavedTicket(
            id = "t1",
            timestamp = 1000L,
            lotteryType = LotteryType.SSQ,
            userName = "缘主",
            primaryNumbers = listOf(1, 2, 3, 4, 5, 6),
            secondaryNumbers = listOf(7)
        )
        val t2 = SavedTicket(
            id = "t2",
            timestamp = 3000L,
            lotteryType = LotteryType.DLT,
            userName = "缘主",
            primaryNumbers = listOf(2, 4, 6, 8, 10),
            secondaryNumbers = listOf(1, 2)
        )
        val t3 = SavedTicket(
            id = "t3",
            timestamp = 2000L,
            lotteryType = LotteryType.FC3D,
            userName = "缘主",
            primaryNumbers = listOf(3, 6, 9)
        )

        // Save in arbitrary order: t1, t2, t3
        historyStore.saveTicket(t1)
        historyStore.saveTicket(t2)
        historyStore.saveTicket(t3)

        val list = historyStore.getAllTickets()
        assertEquals(3, list.size)

        // Must be strictly ordered descending: t2 (3000L), t3 (2000L), t1 (1000L)
        assertEquals("t2", list[0].id)
        assertEquals(3000L, list[0].timestamp)

        assertEquals("t3", list[1].id)
        assertEquals(2000L, list[1].timestamp)

        assertEquals("t1", list[2].id)
        assertEquals(1000L, list[2].timestamp)
    }

    @Test
    fun testDeleteSingleTicket() {
        val t1 = SavedTicket(id = "item-a", timestamp = 100L, lotteryType = LotteryType.SSQ, primaryNumbers = listOf(1))
        val t2 = SavedTicket(id = "item-b", timestamp = 200L, lotteryType = LotteryType.DLT, primaryNumbers = listOf(2))
        val t3 = SavedTicket(id = "item-c", timestamp = 300L, lotteryType = LotteryType.QXC, primaryNumbers = listOf(3))

        historyStore.saveTicket(t1)
        historyStore.saveTicket(t2)
        historyStore.saveTicket(t3)
        assertEquals(3, historyStore.getTicketCount())

        // Delete middle ticket item-b
        historyStore.deleteTicket("item-b")

        val remaining = historyStore.getAllTickets()
        assertEquals(2, remaining.size)
        assertEquals(2, historyStore.getTicketCount())

        val ids = remaining.map { it.id }
        assertTrue(ids.contains("item-a"))
        assertTrue(ids.contains("item-c"))
        assertFalse(ids.contains("item-b"))

        // Deleting non-existent ID has no negative effect
        historyStore.deleteTicket("non-existent-id")
        assertEquals(2, historyStore.getTicketCount())
    }

    @Test
    fun testClearAllTickets() {
        for (i in 1..5) {
            historyStore.saveTicket(
                SavedTicket(
                    id = "ticket-$i",
                    timestamp = 1000L * i,
                    lotteryType = LotteryType.SSQ,
                    primaryNumbers = listOf(i)
                )
            )
        }
        assertEquals(5, historyStore.getTicketCount())

        historyStore.clearAll()

        val emptyList = historyStore.getAllTickets()
        assertTrue(emptyList.isEmpty())
        assertEquals(0, historyStore.getTicketCount())
    }

    @Test
    fun testBatchSaveTickets() {
        val batch = listOf(
            SavedTicket(id = "b1", timestamp = 100L, lotteryType = LotteryType.SSQ, primaryNumbers = listOf(1, 2, 3)),
            SavedTicket(id = "b2", timestamp = 200L, lotteryType = LotteryType.DLT, primaryNumbers = listOf(4, 5, 6)),
            SavedTicket(id = "b3", timestamp = 300L, lotteryType = LotteryType.FC3D, primaryNumbers = listOf(7, 8, 9))
        )

        historyStore.saveTickets(batch)

        val retrieved = historyStore.getAllTickets()
        assertEquals(3, retrieved.size)
        assertEquals("b3", retrieved[0].id)
        assertEquals("b2", retrieved[1].id)
        assertEquals("b1", retrieved[2].id)
    }

    @Test
    fun testUpsertDuplicateId() {
        val initial = SavedTicket(
            id = "same-id",
            timestamp = 1000L,
            lotteryType = LotteryType.SSQ,
            userName = "初始缘主",
            primaryNumbers = listOf(1, 2, 3)
        )
        historyStore.saveTicket(initial)
        assertEquals(1, historyStore.getTicketCount())

        val updated = SavedTicket(
            id = "same-id",
            timestamp = 2000L,
            lotteryType = LotteryType.SSQ,
            userName = "更新后缘主",
            primaryNumbers = listOf(7, 8, 9)
        )
        historyStore.saveTicket(updated)

        // Still exactly 1 ticket, with updated data
        assertEquals(1, historyStore.getTicketCount())
        val retrieved = historyStore.getAllTickets().first()
        assertEquals("更新后缘主", retrieved.userName)
        assertEquals(listOf(7, 8, 9), retrieved.primaryNumbers)
        assertEquals(2000L, retrieved.timestamp)
    }

    @Test
    fun testAllLotteryTypesPreserved() {
        val types = listOf(LotteryType.SSQ, LotteryType.DLT, LotteryType.FC3D, LotteryType.QXC)

        types.forEachIndexed { index, type ->
            historyStore.saveTicket(
                SavedTicket(
                    id = "type-$index",
                    timestamp = (index + 1) * 1000L,
                    lotteryType = type,
                    primaryNumbers = listOf(index + 1)
                )
            )
        }

        val all = historyStore.getAllTickets()
        assertEquals(4, all.size)

        val savedTypes = all.map { it.lotteryType }.toSet()
        assertEquals(types.toSet(), savedTypes)
    }

    @Test
    fun testCorruptedJsonGracefulRecovery() {
        val isolatedMap = ConcurrentHashMap<String, String>()
        isolatedMap[UserPickHistoryStore.KEY_SAVED_TICKETS] = "INVALID_CORRUPT_JSON_CONTENT{{{{"

        val storeWithCorruptData = UserPickHistoryStore(context = null, inMemoryFallback = isolatedMap)
        val result = storeWithCorruptData.getAllTickets()

        assertNotNull(result)
        assertTrue("Corrupted JSON must safely fallback to empty list without crashing", result.isEmpty())
        assertEquals(0, storeWithCorruptData.getTicketCount())
    }

    @Test
    fun testIsolatedInMemoryFallback() {
        val customMap = ConcurrentHashMap<String, String>()
        val isolatedStore = UserPickHistoryStore(context = null, inMemoryFallback = customMap)

        isolatedStore.saveTicket(
            SavedTicket(
                id = "isolated-1",
                lotteryType = LotteryType.SSQ,
                userName = "隔离用户",
                primaryNumbers = listOf(1, 2)
            )
        )

        assertEquals(1, isolatedStore.getTicketCount())
        assertEquals("隔离用户", isolatedStore.getAllTickets().first().userName)

        // Global default store remains clean
        val globalStore = UserPickHistoryStore(context = null)
        assertEquals(0, globalStore.getTicketCount())
    }

    @Test
    fun testUserPickHistoryRepositoryInterfaceCompliance() {
        val repo: UserPickHistoryRepository = historyStore
        repo.saveTicket(
            SavedTicket(
                id = "repo-test",
                lotteryType = LotteryType.DLT,
                primaryNumbers = listOf(10, 20)
            )
        )
        assertEquals(1, repo.getTicketCount())
        assertEquals(1, repo.getAllTickets().size)

        repo.deleteTicket("repo-test")
        assertEquals(0, repo.getTicketCount())
    }

    @Test
    fun testThreadSafetyConcurrentSaves() {
        val threadCount = 10
        val ticketsPerThread = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    for (j in 0 until ticketsPerThread) {
                        historyStore.saveTicket(
                            SavedTicket(
                                id = "thread-$i-ticket-$j",
                                timestamp = System.currentTimeMillis() + (i * 10 + j),
                                lotteryType = LotteryType.SSQ,
                                primaryNumbers = listOf(i, j)
                            )
                        )
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("All concurrent saves must finish in 5 seconds", latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(threadCount * ticketsPerThread, historyStore.getTicketCount())
    }
}
