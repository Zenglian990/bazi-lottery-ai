package com.zeng.lottery.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.zeng.lottery.data.model.LotteryType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Saved lottery ticket entity representing a user's favorited or divined pick.
 */
data class SavedTicket(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("lotteryType")
    val lotteryType: LotteryType,
    @SerializedName("userName")
    val userName: String = "缘主",
    @SerializedName("primaryNumbers")
    val primaryNumbers: List<Int>,
    @SerializedName("secondaryNumbers")
    val secondaryNumbers: List<Int> = emptyList(),
    @SerializedName("strategy")
    val strategy: String = "命理吉数优先",
    @SerializedName("rationale")
    val rationale: String = ""
)

/**
 * Interface contract for persisting and retrieving user lottery pick histories.
 */
interface UserPickHistoryRepository {
    fun saveTicket(ticket: SavedTicket)
    fun saveTickets(tickets: List<SavedTicket>)
    fun getAllTickets(): List<SavedTicket>
    fun deleteTicket(id: String)
    fun clearAll()
    fun getTicketCount(): Int
}

/**
 * High-performance, thread-safe storage for user lottery ticket history.
 * Uses Android SharedPreferences with JSON serialization in production,
 * and a concurrent in-memory fallback for JVM unit testing environments.
 */
class UserPickHistoryStore(
    context: Context? = null,
    private val inMemoryFallback: ConcurrentHashMap<String, String>? = null
) : UserPickHistoryRepository {

    companion object {
        const val PREFS_NAME = "lottery_user_pick_history"
        const val KEY_SAVED_TICKETS = "saved_tickets_json"
        const val MAX_TICKETS = 200

        // Shared in-memory storage for JVM unit test execution across instances when Context is null
        private val jvmTestStorage = ConcurrentHashMap<String, String>()

        fun resetJvmTestStore() {
            jvmTestStorage.clear()
        }
    }

    private fun ticketFingerprint(t: SavedTicket): String {
        return "${t.lotteryType.code}|${t.primaryNumbers.joinToString(",")}|${t.secondaryNumbers.joinToString(",")}"
    }

    private val prefs: SharedPreferences? = try {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: Throwable) {
        null
    }

    private val memoryStore: ConcurrentHashMap<String, String>
        get() = inMemoryFallback ?: jvmTestStorage

    private val gson: Gson = GsonBuilder().create()

    private val listType = object : TypeToken<List<SavedTicket>>() {}.type

    override fun getAllTickets(): List<SavedTicket> {
        val json = try {
            if (prefs != null && prefs.contains(KEY_SAVED_TICKETS)) {
                prefs.getString(KEY_SAVED_TICKETS, null) ?: memoryStore[KEY_SAVED_TICKETS]
            } else {
                memoryStore[KEY_SAVED_TICKETS]
            }
        } catch (e: Throwable) {
            memoryStore[KEY_SAVED_TICKETS]
        }

        if (json.isNullOrBlank()) {
            return emptyList()
        }

        return try {
            val list: List<SavedTicket>? = gson.fromJson(json, listType)
            list?.filterNotNull()
                ?.filter {
                    @Suppress("SENSELESS_COMPARISON")
                    it.lotteryType != null && it.primaryNumbers != null
                }
                ?.mapNotNull { ticket ->
                    val pri = (ticket.primaryNumbers as? List<*>)?.mapNotNull { (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull() } ?: emptyList()
                    if (pri.isEmpty()) return@mapNotNull null
                    val sec = (ticket.secondaryNumbers as? List<*>)?.mapNotNull { (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull() } ?: emptyList()
                    ticket.copy(
                        id = ticket.id ?: UUID.randomUUID().toString(),
                        userName = ticket.userName ?: "缘主",
                        primaryNumbers = pri,
                        secondaryNumbers = sec,
                        strategy = ticket.strategy ?: "命理吉数优先",
                        rationale = ticket.rationale ?: ""
                    )
                }
                ?.sortedByDescending { it.timestamp } ?: emptyList()
        } catch (e: Throwable) {
            emptyList()
        }
    }

    override fun saveTicket(ticket: SavedTicket) {
        synchronized(this) {
            val current = getAllTickets().toMutableList()
            current.removeAll { it.id == ticket.id }
            current.add(0, ticket)
            val sorted = current.sortedByDescending { it.timestamp }
            writeTickets(sorted)
        }
    }

    override fun saveTickets(tickets: List<SavedTicket>) {
        if (tickets.isEmpty()) return
        synchronized(this) {
            val current = getAllTickets().toMutableList()
            val incomingIds = tickets.map { it.id }.toSet()
            current.removeAll { it.id in incomingIds }
            current.addAll(0, tickets)
            val sorted = current.sortedByDescending { it.timestamp }
            writeTickets(sorted)
        }
    }

    /**
     * 业务层收藏写入：按（彩种 + 前后区号码）去重防手滑重复收藏，并限制上限防止本地存储无限膨胀。
     * 返回 true 表示新增，false 表示已存在同号记录（已刷新置顶）。
     */
    fun saveUniqueTicketWithCap(ticket: SavedTicket, maxTickets: Int = MAX_TICKETS): Boolean {
        synchronized(this) {
            val fp = ticketFingerprint(ticket)
            val current = getAllTickets().toMutableList()
            val existed = current.any { it.id == ticket.id || ticketFingerprint(it) == fp }
            current.removeAll { it.id == ticket.id || ticketFingerprint(it) == fp }
            current.add(0, ticket)
            val sorted = current.sortedByDescending { it.timestamp }.take(maxTickets.coerceAtLeast(1))
            writeTickets(sorted)
            return !existed
        }
    }

    /**
     * 业务层批量收藏写入：合并同彩种同号码记录并保留最新 maxTickets 条。
     */
    fun saveUniqueTicketsWithCap(tickets: List<SavedTicket>, maxTickets: Int = MAX_TICKETS) {
        if (tickets.isEmpty()) return
        synchronized(this) {
            val current = getAllTickets().toMutableList()
            val dedupIncoming = LinkedHashMap<String, SavedTicket>()
            for (t in tickets) {
                dedupIncoming[ticketFingerprint(t)] = t
            }
            val incomingIds = dedupIncoming.values.map { it.id }.toSet()
            val incomingFps = dedupIncoming.keys
            current.removeAll { it.id in incomingIds || ticketFingerprint(it) in incomingFps }
            current.addAll(0, dedupIncoming.values)
            val sorted = current.sortedByDescending { it.timestamp }.take(maxTickets.coerceAtLeast(1))
            writeTickets(sorted)
        }
    }

    override fun deleteTicket(id: String) {
        synchronized(this) {
            val current = getAllTickets().toMutableList()
            val removed = current.removeAll { it.id == id }
            if (removed) {
                writeTickets(current)
            }
        }
    }

    override fun clearAll() {
        synchronized(this) {
            try {
                prefs?.edit()?.remove(KEY_SAVED_TICKETS)?.apply()
            } catch (e: Throwable) {
                // Defensive swallow
            }
            memoryStore.remove(KEY_SAVED_TICKETS)
        }
    }

    override fun getTicketCount(): Int {
        return getAllTickets().size
    }

    private fun writeTickets(list: List<SavedTicket>) {
        try {
            val json = gson.toJson(list)
            try {
                prefs?.edit()?.putString(KEY_SAVED_TICKETS, json)?.apply()
            } catch (e: Throwable) {
                // Defensive swallow
            }
            memoryStore[KEY_SAVED_TICKETS] = json
        } catch (e: Throwable) {
            // Defensive swallow: prevent serialization errors from crashing callers
        }
    }
}
