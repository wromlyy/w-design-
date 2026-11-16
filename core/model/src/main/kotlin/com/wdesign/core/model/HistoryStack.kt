package com.wdesign.core.model

/**
 * Bounded undo/redo stack. [push] records the new present state; [undo]/[redo] move through
 * history. While coalescing (e.g. during a drag gesture), pushes replace the pending present so
 * a whole gesture becomes a single undo step.
 */
class HistoryStack<T>(initial: T, private val capacity: Int = 100) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()
    private var coalesceBase: T? = null
    private var coalescing = false

    var present: T = initial
        private set

    val canUndo: Boolean get() = past.isNotEmpty() || (coalescing && coalesceBase != null && coalesceBase != present)
    val canRedo: Boolean get() = future.isNotEmpty()
    val undoDepth: Int get() = past.size
    val redoDepth: Int get() = future.size

    fun push(state: T) {
        if (state == present) return
        if (coalescing) {
            present = state
            return
        }
        past.addLast(present)
        while (past.size > capacity) past.removeFirst()
        future.clear()
        present = state
    }

    /** Start grouping subsequent pushes into one history entry. */
    fun beginCoalesce() {
        if (coalescing) return
        coalescing = true
        coalesceBase = present
    }

    /** Finish grouping: records one entry from the state before [beginCoalesce] to now. */
    fun endCoalesce() {
        if (!coalescing) return
        coalescing = false
        val base = coalesceBase
        coalesceBase = null
        if (base != null && base != present) {
            past.addLast(base)
            while (past.size > capacity) past.removeFirst()
            future.clear()
        }
    }

    fun undo(): T? {
        if (coalescing) endCoalesce()
        val prev = past.removeLastOrNull() ?: return null
        future.addLast(present)
        present = prev
        return prev
    }

    fun redo(): T? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(present)
        present = next
        return next
    }

    /** Replace everything with a new baseline (e.g. after restoring a snapshot). */
    fun reset(state: T) {
        past.clear(); future.clear(); coalescing = false; coalesceBase = null
        present = state
    }
}
