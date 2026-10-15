package com.openswift.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipboardHistoryTest {

    @Test
    fun captureRequiresOptInAndNonPrivateNonSensitiveText() {
        assertFalse(ClipboardHistory.shouldCapture(false, false, false, "copy"))
        assertFalse(ClipboardHistory.shouldCapture(true, true, false, "copy"))
        assertFalse(ClipboardHistory.shouldCapture(true, false, true, "copy"))
        assertFalse(ClipboardHistory.shouldCapture(true, false, false, "  "))
        assertTrue(ClipboardHistory.shouldCapture(true, false, false, "copy"))
    }

    @Test
    fun emptyAndAlreadyFirstValuesAreUnchanged() {
        val current = listOf("newest", "older")

        assertSame(current, ClipboardHistory.withCapturedItem(current, ""))
        assertSame(current, ClipboardHistory.withCapturedItem(current, "newest"))
    }

    @Test
    fun olderCopiedItemIsPromotedToTop() {
        val current = listOf("newest", "older", "ancient")

        val updated = ClipboardHistory.withCapturedItem(current, "older")
        assertEquals(listOf("older", "newest", "ancient"), updated)
    }

    @Test
    fun newItemsAreNewestFirstAndRetentionIsBounded() {
        val current = (1..ClipboardHistory.MAX_ITEMS).map { "item-$it" }

        val updated = ClipboardHistory.withCapturedItem(current, "latest")

        assertEquals(ClipboardHistory.MAX_ITEMS, updated.size)
        assertEquals("latest", updated.first())
        assertEquals("item-${ClipboardHistory.MAX_ITEMS - 1}", updated.last())
    }

    @Test
    fun initialInstallClipboardIsEmpty() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_clipboard_history", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)
        assertTrue("Clipboard recent items should be empty", history.items().isEmpty())
        assertTrue("Clipboard pinned items should be empty", history.pinnedItems().isEmpty())
    }

    @Test
    fun multipleSequentialCopiedItemsAreAllPreserved() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_multicopy_history", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        history.add("First Copied Text")
        history.add("Second Copied Text")
        history.add("Third Copied Text")

        val items = history.items()
        assertEquals(3, items.size)
        assertEquals("Third Copied Text", items[0])
        assertEquals("Second Copied Text", items[1])
        assertEquals("First Copied Text", items[2])
    }

    @Test
    fun multiItemClipDataCapturesAllItems() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_multiitem_clip", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("item1", "Item One")
        clip.addItem(android.content.ClipData.Item("Item Two"))
        clip.addItem(android.content.ClipData.Item("Item Three"))
        cb.setPrimaryClip(clip)

        val captured = history.captureSystem(context, enabled = true, privateField = false)
        assertTrue(captured)

        val items = history.items()
        assertEquals(3, items.size)
        assertEquals("Item One", items[0])
        assertEquals("Item Two", items[1])
        assertEquals("Item Three", items[2])
    }

    @Test
    fun reCopyingPreviouslyDeletedItemWorks() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_recopy_deleted", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        history.add("Temporary Text")
        assertEquals(listOf("Temporary Text"), history.items())

        // Delete it
        history.remove("Temporary Text", context)
        assertTrue(history.items().isEmpty())

        // Re-copy fresh
        history.onSystemClipChanged()
        history.add("Temporary Text")
        assertEquals(listOf("Temporary Text"), history.items())
    }

    @Test
    fun captureSystemWithIgnoredTextSkipsIt() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_ignored_text", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cb.setPrimaryClip(android.content.ClipData.newPlainText("label", "Text From Other Keyboard"))

        val captured = history.captureSystem(
            context,
            enabled = true,
            privateField = false,
            ignoredText = "Text From Other Keyboard"
        )
        assertFalse(captured)
        assertTrue(history.items().isEmpty())
    }

    @Test
    fun captureSystemWithMinTimestampSkipsOlderClips() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_mintimestamp", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cb.setPrimaryClip(android.content.ClipData.newPlainText("label", "Old Clip"))

        // Set minTimestamp far in the future
        val futureTime = System.currentTimeMillis() + 1000000L
        val captured = history.captureSystem(
            context,
            enabled = true,
            privateField = false,
            minTimestamp = futureTime
        )
        assertFalse(captured)
        assertTrue(history.items().isEmpty())
    }

    @Test
    fun reorderingPinnedItemsPersistsCorrectly() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_reorder_pinned", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        history.pin("Item A")
        history.pin("Item B")
        history.pin("Item C")
        // pin adds to beginning, so list is ["Item C", "Item B", "Item A"]
        assertEquals(listOf("Item C", "Item B", "Item A"), history.pinnedItems())

        // Move "Item C" from index 0 to index 2 (downwards)
        val success = history.reorderPinned(0, 2)
        assertTrue(success)
        assertEquals(listOf("Item B", "Item A", "Item C"), history.pinnedItems())

        // Move "Item A" to index 0 (to the other side/upwards)
        val moveSuccess = history.movePinned("Item A", 0)
        assertTrue(moveSuccess)
        assertEquals(listOf("Item A", "Item B", "Item C"), history.pinnedItems())
    }

    @Test
    fun copyingPinnedItemAppearsInRecentItemsWhileRemainingPinned() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_pinned_recopy", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val history = ClipboardHistory(prefs)

        // User pins an item
        history.pin("مهم جداً")
        assertTrue(history.isPinned("مهم جداً"))
        assertEquals(listOf("مهم جداً"), history.pinnedItems())
        assertTrue(history.items().isEmpty())

        // User copies this pinned text again
        val added = history.add("مهم جداً")
        assertTrue(added)

        // It is now present in recent items (items) and STILL in pinned items
        assertEquals(listOf("مهم جداً"), history.items())
        assertEquals(listOf("مهم جداً"), history.pinnedItems())
        assertTrue(history.isPinned("مهم جداً"))

        // Removing the recent copy does not remove it from pinned items
        history.remove("مهم جداً")
        assertTrue(history.items().isEmpty())
        assertEquals(listOf("مهم جداً"), history.pinnedItems())
    }
}

