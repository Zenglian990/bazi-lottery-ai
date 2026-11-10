package com.zeng.lottery.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.zeng.lottery.data.model.LotteryType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Saved lottery ticket entity representing a user's favorited or divined pick.
 */
data class SavedTicket(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val lotteryType: LotteryType,
    val userName: String = "缘主",
    val primaryNumbers: List<Int>,
    val secondaryNumbers: List<Int> = emptyList(),
    val strategy: String = "命理吉数优先",
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

        // Shared in-memory storage for JVM unit test execution across instances when Context is null
        private val jvmTestStorage = ConcurrentHashMap<String, String>()

        fun resetJvmTestStore() {
            jvmTestStorage.clear()
        }
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
        val json = if (prefs != null) {
            prefs.getString(KEY_SAVED_TICKETS, null)
        } else {
            memoryStore[KEY_SAVED_TICKETS]
        }

        if (json.isNullOrBlank()) {
            return emptyList()
        }

        return try {
            val list: List<SavedTicket>? = gson.fromJson(json, listType)
            list?.sortedByDescending { it.timestamp } ?: emptyList()
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
            if (prefs != null) {
                prefs.edit().remove(KEY_SAVED_TICKETS).apply()
            }
            memoryStore.remove(KEY_SAVED_TICKETS)
        }
    }

    override fun getTicketCount(): Int {
        return getAllTickets().size
    }

    private fun writeTickets(list: List<SavedTicket>) {
        val json = gson.toJson(list)
        if (prefs != null) {
            prefs.edit().putString(KEY_SAVED_TICKETS, json).apply()
        } else {
            memoryStore[KEY_SAVED_TICKETS] = json
        }
    }
}
