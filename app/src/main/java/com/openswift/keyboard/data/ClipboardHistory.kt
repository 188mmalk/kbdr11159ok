package com.openswift.keyboard.data

import android.content.ClipboardManager as SystemClipboard
import android.content.ClipDescription
import android.content.Context
import org.json.JSONArray

class ClipboardHistory internal constructor(private val prefs: android.content.SharedPreferences) {

    constructor(ctx: Context) : this(SecurePreferences.open(ctx, TypedDataStores.CLIPBOARD_HISTORY))

    @Volatile
    private var cachedItems: List<String>? = null

    @Volatile
    private var cachedPinnedItems: List<String>? = null

    @Volatile
    private var cachedPinnedSet: Set<String>? = null

    // Tracks deleted texts (persisted in preferences) so old clips sitting in the system clipboard
    // never resurrect after closing and reopening the app, while any fresh copy is immediately allowed!
    private val deletedTimestamps = mutableMapOf<String, Long>()
    private var deletedItemsLoaded = false

    private fun ensureDeletedLoaded() {
        if (deletedItemsLoaded) return
        deletedItemsLoaded = true
        val raw = prefs.getString("deleted_items", null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            val now = System.currentTimeMillis()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i)
                if (item.isNotBlank()) {
                    // Retain transient deletion timestamp for a few seconds only
                    deletedTimestamps[item] = now - 5000L
                }
            }
        }
    }

    private fun persistDeletedItems() {
        val arr = JSONArray()
        deletedTimestamps.keys.take(200).forEach { arr.put(it) }
        prefs.edit().putString("deleted_items", arr.toString()).apply()
    }

    fun invalidateCache() {
        synchronized(this) {
            cachedItems = null
            cachedPinnedItems = null
            cachedPinnedSet = null
        }
    }

    fun items(): List<String> {
        cachedItems?.let { return it }
        synchronized(this) {
            cachedItems?.let { return it }
            val raw = prefs.getString("items", null)
            val list = if (raw == null) {
                emptyList()
            } else {
                runCatching {
                    val arr = JSONArray(raw)
                    List(arr.length()) { arr.getString(it) }
                }.getOrDefault(emptyList())
            }
            cachedItems = list
            return list
        }
    }

    fun add(text: String): Boolean {
        ensureDeletedLoaded()
        if (deletedTimestamps.remove(text) != null) {
            persistDeletedItems()
        }
        val current = items()
        val updated = withCapturedItem(current, text)
        if (updated == current) return false
        save(updated)
        return true
    }

    fun onSystemClipChanged() {
        // When the user performs a new copy in the system, clear transient deletion blocks
        ensureDeletedLoaded()
        if (deletedTimestamps.isNotEmpty()) {
            deletedTimestamps.clear()
            persistDeletedItems()
        }
    }

    fun markAsDeleted(text: String) {
        ensureDeletedLoaded()
        deletedTimestamps[text] = System.currentTimeMillis()
        persistDeletedItems()
    }

    fun remove(text: String, ctx: Context? = null) {
        markAsDeleted(text)
        save(items().filter { it != text })
        clearSystemClipIfMatching(ctx, text)
    }

    fun clear(ctx: Context? = null) {
        ensureDeletedLoaded()
        items().forEach { deletedTimestamps[it] = System.currentTimeMillis() }
        persistDeletedItems()
        save(emptyList())
        if (ctx != null) {
            clearSystemClip(ctx)
        }
    }

    private fun clearSystemClipIfMatching(ctx: Context?, text: String) {
        if (ctx == null || isPinned(text)) return
        try {
            val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? SystemClipboard ?: return
            val clip = cb.primaryClip ?: return
            if (clip.itemCount > 0) {
                val clipText = clip.getItemAt(0)?.coerceToText(ctx)?.toString()
                if (clipText == text) {
                    clearSystemClip(ctx)
                }
            }
        } catch (_: Exception) {}
    }

    private fun clearSystemClip(ctx: Context) {
        try {
            val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? SystemClipboard ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                cb.clearPrimaryClip()
            } else {
                cb.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {}
    }

    fun pinnedItems(): List<String> {
        cachedPinnedItems?.let { return it }
        synchronized(this) {
            cachedPinnedItems?.let { return it }
            val raw = prefs.getString("pinned_items", null)
            val list = if (raw == null) {
                emptyList()
            } else {
                runCatching {
                    val arr = JSONArray(raw)
                    List(arr.length()) { arr.getString(it) }
                }.getOrDefault(emptyList())
            }
            cachedPinnedItems = list
            cachedPinnedSet = list.toHashSet()
            return list
        }
    }

    fun pinnedSet(): Set<String> {
        cachedPinnedSet?.let { return it }
        pinnedItems()
        return cachedPinnedSet ?: emptySet()
    }

    fun isPinned(text: String): Boolean = pinnedSet().contains(text)

    fun removePinned(text: String) {
        savePinned(pinnedItems().filter { it != text })
    }

    fun pin(text: String) {
        val current = pinnedItems()
        if (text !in current) {
            savePinned(listOf(text) + current)
        }
    }

    fun clearPinned() = savePinned(emptyList())

    fun clearAll() {
        clear()
        clearPinned()
    }

    fun reorderPinned(fromIndex: Int, toIndex: Int): Boolean {
        val current = pinnedItems().toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) {
            return false
        }
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        savePinned(current)
        return true
    }

    fun movePinned(text: String, targetIndex: Int): Boolean {
        val current = pinnedItems().toMutableList()
        val fromIndex = current.indexOf(text)
        if (fromIndex == -1 || targetIndex !in current.indices || fromIndex == targetIndex) {
            return false
        }
        val item = current.removeAt(fromIndex)
        current.add(targetIndex, item)
        savePinned(current)
        return true
    }

    fun savePinned(list: List<String>) {
        cachedPinnedItems = list
        cachedPinnedSet = list.toHashSet()
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("pinned_items", arr.toString()).apply()
    }

    internal fun save(list: List<String>) {
        cachedItems = list
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun captureSystem(
        ctx: Context,
        enabled: Boolean,
        privateField: Boolean,
        minTimestamp: Long = 0L,
        ignoredText: String? = null
    ): Boolean {
        if (!enabled || privateField) return false
        ensureDeletedLoaded()
        val cb = try {
            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? SystemClipboard
        } catch (_: Exception) {
            null
        } ?: return false

        val clip = try {
            cb.primaryClip
        } catch (_: Exception) {
            null
        } ?: return false

        if (clip.itemCount == 0 || isSensitive(clip.description)) return false

        val now = System.currentTimeMillis()
        val clipTime = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                clip.description.timestamp
            } catch (_: Exception) {
                0L
            }
        } else {
            0L
        }

        if (minTimestamp > 0L && clipTime > 0L && clipTime < minTimestamp) {
            return false
        }

        var anyAdded = false
        // Iterate through all items in clip in reverse order so item 0 ends up at the top of history
        for (i in (clip.itemCount - 1) downTo 0) {
            val clipItem = clip.getItemAt(i) ?: continue
            val text = try {
                clipItem.coerceToText(ctx)?.toString()
            } catch (_: Exception) {
                null
            } ?: continue

            if (text.isBlank()) continue
            if (!ignoredText.isNullOrBlank() && text == ignoredText) continue

            // Check if this text was deleted earlier
            val deletedTime = deletedTimestamps[text]
            if (deletedTime != null) {
                val isRecentDeletion = (now - deletedTime) in 0..5000L
                if ((clipTime > 0L && clipTime > deletedTime) || !isRecentDeletion) {
                    // User explicitly re-copied this text after deleting it, or deletion was not recent
                    deletedTimestamps.remove(text)
                    persistDeletedItems()
                } else {
                    // Stale clip sitting in system buffer right after deletion in UI; skip it
                    continue
                }
            }

            if (!shouldCapture(enabled, privateField, sensitiveClip = false, text)) continue
            if (add(text)) {
                anyAdded = true
            }
        }
        return anyAdded
    }

    private fun isSensitive(description: ClipDescription): Boolean {
        val extras = description.extras ?: return false
        return extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) ||
            extras.getBoolean(SENSITIVE_CLIPBOARD_EXTRA, false)
    }

    companion object {
        const val MAX_ITEMS = 3000
        private const val SENSITIVE_CLIPBOARD_EXTRA = "android.content.extra.IS_SENSITIVE"

        internal fun shouldCapture(
            enabled: Boolean,
            privateField: Boolean,
            sensitiveClip: Boolean,
            text: String?
        ): Boolean = enabled && !privateField && !sensitiveClip && !text.isNullOrBlank()

        internal fun withCapturedItem(current: List<String>, text: String): List<String> {
            if (text.isBlank() || current.firstOrNull() == text) return current
            val filtered = current.filter { it != text }
            return (listOf(text) + filtered).take(MAX_ITEMS)
        }
    }
}
