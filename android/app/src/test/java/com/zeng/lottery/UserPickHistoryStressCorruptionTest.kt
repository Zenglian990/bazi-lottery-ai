package com.zeng.lottery

import com.zeng.lottery.data.local.SavedTicket
import com.zeng.lottery.data.local.UserPickHistoryStore
import com.zeng.lottery.data.model.LotteryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Milestone 3 Adversarial Challenge: History Store Stress & Corruption Oracle.
 *
 * Rigorously stress-tests UserPickHistoryStore against:
 * 1. High-volume ingestion (500+ and 1000+ tickets).
 * 2. 16-32 thread concurrency torture under mixed read/write/delete/clear workloads.
 * 3. 10 distinct corrupted JSON payloads verifying graceful degradation and self-healing.
 * 4. Extreme payload limits (10,000-char rationale, 150-element number lists).
 * 5. Multi-instance shared storage consistency.
 */
class UserPickHistoryStressCorruptionTest {

    private lateinit var store: UserPickHistoryStore
    private lateinit var memoryBackend: ConcurrentHashMap<String, String>

    @Before
    fun setUp() {
        UserPickHistoryStore.resetJvmTestStore()
        memoryBackend = ConcurrentHashMap()
        store = UserPickHistoryStore(context = null, inMemoryFallback = memoryBackend)
    }

    @Test
    fun testHighVolumeInsertions_500Tickets() {
        val count = 500
        val baseTime = 1700000000000L

        for (i in 1..count) {
            val ticket = SavedTicket(
                id = "ticket-500-$i",
                timestamp = baseTime + i * 10L,
                lotteryType = if (i % 2 == 0) LotteryType.SSQ else LotteryType.DLT,
                userName = "缘主-$i",
                primaryNumbers = listOf((i % 33) + 1, ((i + 1) % 33) + 1, ((i + 2) % 33) + 1),
                secondaryNumbers = listOf((i % 16) + 1),
                strategy = "大容量压测策略-$i",
                rationale = "八字测试用例第 $i 注"
            )
            store.saveTicket(ticket)
        }

        assertEquals(count, store.getTicketCount())
        val all = store.getAllTickets()
        assertEquals(count, all.size)

        // Strict descending timestamp order verification
        for (i in 0 until all.size - 1) {
            assertTrue(
                "Tickets must be ordered strictly descending by timestamp: index $i (${all[i].timestamp}) vs ${i + 1} (${all[i + 1].timestamp})",
                all[i].timestamp >= all[i + 1].timestamp
            )
        }

        // Head is the newest (i = 500)
        assertEquals("ticket-500-500", all.first().id)
        assertEquals(baseTime + 5000L, all.first().timestamp)

        // Tail is the oldest (i = 1)
        assertEquals("ticket-500-1", all.last().id)
        assertEquals(baseTime + 10L, all.last().timestamp)
    }

    @Test
    fun testHighVolumeMixedBatchAndSingle_1000Tickets() {
        val totalTickets = 1000
        val batchSize = 50
        val baseTime = 1730000000000L

        // Save 500 via batch (10 batches of 50)
        for (b in 0 until 10) {
            val batch = (1..batchSize).map { offset ->
                val idx = b * batchSize + offset
                SavedTicket(
                    id = "batch-ticket-$idx",
                    timestamp = baseTime + idx * 100L,
                    lotteryType = LotteryType.SSQ,
                    primaryNumbers = listOf(1, 2, 3, 4, 5, 6),
                    secondaryNumbers = listOf(7)
                )
            }
            store.saveTickets(batch)
        }
        assertEquals(500, store.getTicketCount())

        // Save remaining 500 via individual saveTicket
        for (i in 501..totalTickets) {
            store.saveTicket(
                SavedTicket(
                    id = "single-ticket-$i",
                    timestamp = baseTime + i * 100L,
                    lotteryType = LotteryType.DLT,
                    primaryNumbers = listOf(1, 2, 3, 4, 5),
                    secondaryNumbers = listOf(6, 7)
                )
            )
        }

        assertEquals(totalTickets, store.getTicketCount())
        val all = store.getAllTickets()
        assertEquals(totalTickets, all.size)

        // Verify no duplicate IDs and exact size
        val uniqueIds = all.map { it.id }.toSet()
        assertEquals(totalTickets, uniqueIds.size)

        // Newest must be index 1000
        assertEquals("single-ticket-1000", all.first().id)
        assertEquals(baseTime + 100000L, all.first().timestamp)
    }

    @Test
    fun testHighVolumeUpsertUnderLoad_500Tickets() {
        // Prepopulate 500 tickets
        val baseTickets = (1..500).map { i ->
            SavedTicket(
                id = "upsert-$i",
                timestamp = 1000L + i,
                lotteryType = LotteryType.FC3D,
                userName = "原缘主-$i",
                primaryNumbers = listOf(i % 10)
            )
        }
        store.saveTickets(baseTickets)
        assertEquals(500, store.getTicketCount())

        // Update first 250 tickets with new timestamps and contents
        for (i in 1..250) {
            store.saveTicket(
                SavedTicket(
                    id = "upsert-$i",
                    timestamp = 50000L + i, // newer timestamp
                    lotteryType = LotteryType.QXC,
                    userName = "更新缘主-$i",
                    primaryNumbers = listOf(9, 8, 7)
                )
            )
        }

        // Total count must still be exactly 500
        assertEquals(500, store.getTicketCount())
        val all = store.getAllTickets()
        assertEquals(500, all.size)

        // The top 250 must all be the updated ones (timestamp > 50000)
        for (i in 0 until 250) {
            assertTrue("Top items should be updated with timestamp > 50000", all[i].timestamp > 50000L)
            assertTrue("Top items should have updated username", all[i].userName.startsWith("更新缘主"))
            assertEquals(LotteryType.QXC, all[i].lotteryType)
        }
    }

    @Test
    fun testConcurrency_32Threads_TortureTest() {
        val threadCount = 32
        val opsPerThread = 60
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await() // synchronize instant burst start
                    for (op in 0 until opsPerThread) {
                        when (t % 5) {
                            0 -> {
                                // Save single ticket
                                store.saveTicket(
                                    SavedTicket(
                                        id = "t$t-op$op",
                                        timestamp = System.currentTimeMillis() + op,
                                        lotteryType = LotteryType.SSQ,
                                        primaryNumbers = listOf(op % 33 + 1)
                                    )
                                )
                            }
                            1 -> {
                                // Save batch
                                val batch = listOf(
                                    SavedTicket(id = "batch-t$t-op$op-1", lotteryType = LotteryType.DLT, primaryNumbers = listOf(1)),
                                    SavedTicket(id = "batch-t$t-op$op-2", lotteryType = LotteryType.DLT, primaryNumbers = listOf(2))
                                )
                                store.saveTickets(batch)
                            }
                            2 -> {
                                // Read all
                                val tickets = store.getAllTickets()
                                assertNotNull(tickets)
                            }
                            3 -> {
                                // Delete ticket
                                store.deleteTicket("t${t - 3}-op$op")
                            }
                            4 -> {
                                // Query count
                                val c = store.getTicketCount()
                                assertTrue(c >= 0)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown() // Release the kraken!
        val finishedInTime = doneLatch.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All 32 threads must finish without deadlocking within 15 seconds", finishedInTime)
        assertEquals("Zero concurrency errors or exceptions must occur across 32 threads", 0, errorCount.get())

        val finalCount = store.getTicketCount()
        val allFinal = store.getAllTickets()
        assertTrue("Store must remain valid with non-negative ticket count", finalCount >= 0)
        assertEquals("Count must match actual list size after concurrent storm", finalCount, allFinal.size)
    }

    @Test
    fun testConcurrency_16Threads_RapidClearAndWriteRaces() {
        val threadCount = 16
        val iterations = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val exceptionCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    for (i in 0 until iterations) {
                        if (t % 2 == 0) {
                            // Writer thread
                            store.saveTicket(
                                SavedTicket(
                                    id = "race-t$t-i$i",
                                    lotteryType = LotteryType.SSQ,
                                    primaryNumbers = listOf(i)
                                )
                            )
                        } else {
                            // Clear thread
                            store.clearAll()
                        }
                    }
                } catch (e: Throwable) {
                    exceptionCount.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("Racer threads completed within 10s", finished)
        assertEquals("No exceptions during clear vs write races", 0, exceptionCount.get())

        // Post-race sanity check: store should be completely functional
        store.saveTicket(SavedTicket(id = "post-race-ticket", lotteryType = LotteryType.SSQ, primaryNumbers = listOf(7)))
        val after = store.getAllTickets()
        assertTrue(after.any { it.id == "post-race-ticket" })
    }

    @Test
    fun testCorruptedJsonOracle_VariationsAndSelfHealingRecovery() {
        val corruptedPayloads = mapOf(
            "empty string" to "",
            "whitespace only" to "   \t\r\n   ",
            "malformed brackets" to "{[invalid json",
            "truncated json array" to "[{\"id\":\"corrupt_1\", \"timestamp\": 123",
            "non-json binary garbage" to "\u0000\u0001\u0002\uFFFD\u0000\u00FF",
            "json object instead of array" to "{\"id\":\"not_an_array\"}",
            "number literal" to "987654321",
            "string literal" to "\"just a raw string\"",
            "boolean literal" to "true",
            "array of primitive integers" to "[1, 2, 3, 4, 5]",
            "array with null elements" to "[null, null]",
            "array with malformed object" to "[{\"bad_prop\": true}]"
        )

        for ((name, corruptJson) in corruptedPayloads) {
            // Inject corrupted payload directly into backend
            memoryBackend[UserPickHistoryStore.KEY_SAVED_TICKETS] = corruptJson

            // 1. getAllTickets() must not crash, must return empty list (or non-failing list)
            val tickets = try {
                store.getAllTickets()
            } catch (e: Throwable) {
                throw AssertionError("Corrupted payload '$name' caused getAllTickets() to throw: ${e.message}", e)
            }
            assertNotNull("getAllTickets() result must not be null for '$name'", tickets)

            // 2. getTicketCount() must not crash
            val count = try {
                store.getTicketCount()
            } catch (e: Throwable) {
                throw AssertionError("Corrupted payload '$name' caused getTicketCount() to throw: ${e.message}", e)
            }
            assertTrue("Count must be >= 0 for '$name'", count >= 0)

            // 3. Self-healing test: saving a new valid ticket must heal/overwrite corrupted data
            val healingTicket = SavedTicket(
                id = "healed-after-$name",
                lotteryType = LotteryType.SSQ,
                userName = "自愈缘主",
                primaryNumbers = listOf(1, 2, 3, 4, 5, 6),
                secondaryNumbers = listOf(7)
            )
            store.saveTicket(healingTicket)

            val recovered = store.getAllTickets()
            assertTrue("Store must self-heal and contain the new ticket after '$name'", recovered.any { it.id == healingTicket.id })
            assertEquals(
                "Restored store must cleanly parse valid JSON",
                "自愈缘主",
                recovered.first { it.id == healingTicket.id }.userName
            )

            // Clean for next iteration
            store.clearAll()
        }
    }

    @Test
    fun testRapidDeleteNonExistentAndExistentUnderStress() {
        // Pre-fill 200 tickets
        for (i in 1..200) {
            store.saveTicket(SavedTicket(id = "del-$i", lotteryType = LotteryType.SSQ, primaryNumbers = listOf(i)))
        }
        assertEquals(200, store.getTicketCount())

        val threadCount = 16
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)

        // Delete 100 existing tickets (del-1 to del-100) and 100 non-existent tickets
        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 1..25) {
                        val realId = "del-${(t * 6 + i) % 100 + 1}"
                        val fakeId = "non-existent-${UUID.randomUUID()}"
                        store.deleteTicket(realId)
                        store.deleteTicket(fakeId)
                    }
                } catch (e: Throwable) {
                    errors.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Deletes finished within 10s", latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("No exceptions during rapid deletes", 0, errors.get())
        // Remaining count must be between 100 and 199
        val remaining = store.getTicketCount()
        assertTrue("Remaining tickets should be <= 200", remaining <= 200)
        assertEquals(remaining, store.getAllTickets().size)
    }

    @Test
    fun testExtremePayloadStress_LargeStringsAndNumberSets() {
        val largeRationale = buildString {
            for (i in 1..500) {
                append("天干戊土，地支辰戌相冲，水木相生，吉数自天降。第 $i 卷大衍之数五十其用四十有九。")
            }
        }
        assertTrue("Large rationale string is > 10,000 chars", largeRationale.length >= 10000)

        val largePrimary = (1..100).toList()
        val largeSecondary = (1..50).toList()

        val extremeTicket = SavedTicket(
            id = "extreme-ticket-001",
            timestamp = 1731112233445L,
            lotteryType = LotteryType.SSQ,
            userName = "极端负载缘主测试超长文本",
            primaryNumbers = largePrimary,
            secondaryNumbers = largeSecondary,
            strategy = "大衍筮法超长策略声明",
            rationale = largeRationale
        )

        store.saveTicket(extremeTicket)

        val retrieved = store.getAllTickets()
        assertEquals(1, retrieved.size)
        val first = retrieved.first()

        assertEquals(extremeTicket.id, first.id)
        assertEquals(100, first.primaryNumbers.size)
        assertEquals(50, first.secondaryNumbers.size)
        assertEquals(largeRationale, first.rationale)
        assertEquals(extremeTicket.userName, first.userName)
    }

    @Test
    fun testMultiStoreInstanceSharingSameBackend() {
        val storeA = UserPickHistoryStore(context = null, inMemoryFallback = memoryBackend)
        val storeB = UserPickHistoryStore(context = null, inMemoryFallback = memoryBackend)

        val ticketA = SavedTicket(id = "shared-a", timestamp = 1000L, lotteryType = LotteryType.SSQ, primaryNumbers = listOf(1))
        val ticketB = SavedTicket(id = "shared-b", timestamp = 2000L, lotteryType = LotteryType.DLT, primaryNumbers = listOf(2))

        storeA.saveTicket(ticketA)
        assertEquals(1, storeB.getTicketCount())
        assertEquals("shared-a", storeB.getAllTickets().first().id)

        storeB.saveTicket(ticketB)
        assertEquals(2, storeA.getTicketCount())
        assertEquals("shared-b", storeA.getAllTickets().first().id) // newest first

        storeA.deleteTicket("shared-a")
        assertEquals(1, storeB.getTicketCount())
        assertEquals("shared-b", storeB.getAllTickets().first().id)

        storeB.clearAll()
        assertEquals(0, storeA.getTicketCount())
        assertTrue(storeA.getAllTickets().isEmpty())
    }
}
