package com.sandeepvedavyas.tripagent.catalog

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

@Component
class TripCart {
    private val carts = ConcurrentHashMap<String, MutableList<CartLine>>()

    fun get(sessionId: String): CartSnapshot {
        val stored = lines(sessionId)
        synchronized(stored) {
            return snapshot(sessionId, stored.toList())
        }
    }

    fun add(sessionId: String, line: CartLine): CartSnapshot {
        val stored = lines(sessionId)
        synchronized(stored) {
            stored.removeIf { it.activityId == line.activityId && it.date == line.date }
            stored.add(line)
            return snapshot(sessionId, stored.toList())
        }
    }

    /** Replaces the session cart. An empty list clears it. */
    fun replace(sessionId: String, lines: List<CartLine>): CartSnapshot {
        val stored = lines(sessionId)
        synchronized(stored) {
            stored.clear()
            stored.addAll(lines)
            return snapshot(sessionId, stored.toList())
        }
    }

    private fun lines(sessionId: String): MutableList<CartLine> =
        carts.computeIfAbsent(sessionId) { mutableListOf() }

    private fun snapshot(sessionId: String, items: List<CartLine>): CartSnapshot {
        val currency = items.firstOrNull()?.currency ?: "EUR"
        val total = items.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.total) }
            .setScale(2, RoundingMode.HALF_UP)
        return CartSnapshot(sessionId = sessionId, items = items, total = total, currency = currency)
    }
}
