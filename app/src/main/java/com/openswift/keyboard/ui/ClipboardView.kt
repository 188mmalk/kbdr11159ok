package com.openswift.keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import com.openswift.keyboard.R
import com.openswift.keyboard.data.ClipboardHistory
import com.openswift.keyboard.theme.Themes
import kotlin.math.abs
import kotlin.math.max

/**
 * High-performance, ultra-smooth Clipboard View with:
 * 1. Pinned items at the TOP for immediate zero-scroll access!
 * 2. Instant O(1) text truncation caching (zero per-frame string chopping).
 * 3. Viewport culling (only rendering visible rows).
 * 4. Physics-based smooth inertial fling scrolling via OverScroller and VelocityTracker.
 * 5. Full dialog actions: Paste, Pin/Unpin, Delete, Clear unpinned.
 */
class ClipboardView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var onItemSelected: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onReturnToKeyboard: (() -> Unit)? = null
    var onOpenHub: (() -> Unit)? = null
    var onOpenEmoji: (() -> Unit)? = null
    var onOpenVoice: (() -> Unit)? = null
    var onOpenTextEditing: (() -> Unit)? = null
    var onHide: (() -> Unit)? = null

    var clipboard = ClipboardHistory(ctx)
    var settings: com.openswift.keyboard.data.Settings? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(ctx).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(ctx).scaledMaximumFlingVelocity
    @Suppress("DEPRECATION")
    private val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    var isDeleteMode = false
        set(value) {
            field = value
            invalidate()
        }

    var keyHeightDp = 78
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var explicitHeightPx: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    // Modal Action Dialog for long-press
    private var activeDialogCard: ClipCard? = null
    private val dialogRect = RectF()
    private val dialogPasteBounds = RectF()
    private val dialogPinBounds = RectF()
    private val dialogDeleteBounds = RectF()
    private val dialogCancelBounds = RectF()

    // Paints
    private val bgPaint = Paint().apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.FILL
    }
    private val scrimPaint = Paint().apply {
        color = 0xAA000000.toInt()
        style = Paint.Style.FILL
    }
    private val dialogBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B2028.toInt()
        style = Paint.Style.FILL
    }
    private val dialogBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 17f * density
        color = 0xFFFFFFFF.toInt()
        isFakeBoldText = true
    }
    private val toolbarHeight = 44f * density
    private val toolbarBarRect = RectF()
    private val toolbarBounds = mutableMapOf<String, RectF>()

    private val toolbarBarBgPaint = Paint().apply {
        color = 0xFF0D1117.toInt()
        style = Paint.Style.FILL
    }
    private val toolbarBarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt() // Neon cyan border matching keyboard shortcuts bar
        style = Paint.Style.STROKE
    }
    private val toolbarDividerPaint = Paint().apply {
        color = 0x3300E5FF.toInt()
    }
    private val activeIconHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E3F42.toInt() // Dark teal highlight for active clipboard icon
        style = Paint.Style.FILL
    }
    private val sectionHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 14f * density
        color = 0xFF969DA9.toInt()
    }
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A4F58.toInt()
        style = Paint.Style.FILL
    }
    private val cardDeleteModeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3F2024.toInt()
        style = Paint.Style.FILL
    }
    private val cardDeleteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88EF4444.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val cardTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * density
        color = 0xFFFFFFFF.toInt()
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEF4444.toInt()
        style = Paint.Style.FILL
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        textAlign = Paint.Align.LEFT
    }
    private val swipeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFDC2626.toInt()
        style = Paint.Style.FILL
    }
    private val emptySubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
        color = 0xFF8A909D.toInt()
    }
    private val dialogButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2A313E.toInt()
    }
    private val dialogDeleteBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF5C1C24.toInt()
    }
    private val dialogLockedBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2A2E37.toInt()
    }
    private val dialogTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14.5f * density
        color = 0xFFFFFFFF.toInt()
        isFakeBoldText = true
    }

    // Drag & Reorder state for pinned items
    private var isDraggingPinned = false
    private var draggedCard: ClipCard? = null
    private var dragTouchX = 0f
    private var dragTouchY = 0f
    private var dragTargetIndex = -1
    private var hasMovedSignificantly = false

    private val dragShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88000000.toInt()
        style = Paint.Style.FILL
    }
    private val dragCardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF263238.toInt()
        style = Paint.Style.FILL
    }
    private val dragBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val dragSourceSlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF
        style = Paint.Style.FILL
    }
    private val dragTargetSlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val dragBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEE1E293B.toInt()
        style = Paint.Style.FILL
    }
    private val dragBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10.5f * density
        color = 0xFF00E5FF.toInt()
        isFakeBoldText = true
    }

    // Drawables
    private val hubIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_hub_monogram)?.mutate()?.apply {
        setTint(0xFF8E95A5.toInt())
    }
    private val emojiIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_sentiment_satisfied)?.mutate()?.apply {
        setTint(0xFF8E95A5.toInt())
    }
    private val micIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_mic)?.mutate()?.apply {
        setTint(0xFF8E95A5.toInt())
    }
    private val cursorIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_text_cursor)?.mutate()?.apply {
        setTint(0xFF8E95A5.toInt())
    }
    private val clipboardIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_content_paste)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt()) // Active icon
    }
    private val hideIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_keyboard_arrow_down)?.mutate()?.apply {
        setTint(0xFF8E95A5.toInt())
    }
    private val trashIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_delete)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }
    private val closeIcon = AppCompatResources.getDrawable(ctx, R.drawable.ic_close)?.mutate()?.apply {
        setTint(0xFFFFFFFF.toInt())
    }

    // Internal model for layout items
    private data class ClipCard(
        val text: String,
        val isPinned: Boolean,
        val bounds: RectF,
        val pinnedIndex: Int = -1
    )

    private val layoutCards = ArrayList<ClipCard>()

    // Text truncation cache to prevent UI lag on scroll
    private val textTruncateCache = HashMap<String, String>()

    // Swipe to delete state
    private var swipedCard: ClipCard? = null
    private var isSwiping = false
    private var swipeOffsetX = 0f

    // Dimensions
    private val horizontalMargin = 12f * density
    private val cardHeight = 44f * density
    private val cardRadius = 8f * density
    private val gridGap = 8f * density

    // Scroll state and smooth fling
    private var scrollYOffset = 0f
    private var maxScroll = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var isDragging = false
    private val scroller = OverScroller(ctx)
    private var velocityTracker: VelocityTracker? = null

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            scrollYOffset = scroller.currY.toFloat().coerceIn(0f, maxScroll)
            postInvalidateOnAnimation()
        }
    }

    // Long press detection for deleting / reordering
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var pressedCard: ClipCard? = null
    private var longPressTriggered = false
    private var ignoreNextUpForDialog = false
    private val longPressRunnable = Runnable {
        pressedCard?.let { card ->
            longPressTriggered = true
            vibrateFeedback()
            if (card.isPinned) {
                // Enter drag & drop reorder mode for pinned items!
                isDraggingPinned = true
                draggedCard = card
                dragTouchX = downX
                dragTouchY = downY
                hasMovedSignificantly = false
                dragTargetIndex = card.pinnedIndex
                invalidate()
            } else {
                ignoreNextUpForDialog = true
                activeDialogCard = card
                invalidate()
            }
        }
    }

    fun resetScroll() {
        longPressHandler.removeCallbacksAndMessages(null)
        scroller.forceFinished(true)
        scrollYOffset = 0f
        isDragging = false
        isSwiping = false
        swipeOffsetX = 0f
        activeDialogCard = null
        pressedCard = null
        swipedCard = null
        isDraggingPinned = false
        draggedCard = null
        dragTargetIndex = -1
        hasMovedSignificantly = false
    }

    fun refresh(resetScrollPosition: Boolean = true) {
        if (resetScrollPosition) {
            resetScroll()
        }
        clipboard.invalidateCache()
        textTruncateCache.clear()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resetScroll()
        refresh(resetScrollPosition = false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        longPressHandler.removeCallbacksAndMessages(null)
        scroller.abortAnimation()
        velocityTracker?.recycle()
        velocityTracker = null
        resetScroll()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) {
            resetScroll()
            refresh(resetScrollPosition = false)
        } else {
            longPressHandler.removeCallbacksAndMessages(null)
            resetScroll()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val targetHeight = if (explicitHeightPx > 0) {
            explicitHeightPx
        } else {
            val keyHeightPx = keyHeightDp * density
            ((4 * keyHeightPx) + (2f * density * 3) + (keyHeightPx * 0.82f) + (8f * density)).toInt()
        }
        setMeasuredDimension(w, targetHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Fill pitch-black background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Draw Top Toolbar (شريط الاختصارات في الكيبورد يظهر مع الحافظة)
        drawToolbar(canvas, w)

        // 3. Draw Scrollable Content (العناصر المثبتة في البداية + ثم الأحدث)
        canvas.save()
        canvas.clipRect(0f, toolbarHeight, w, h)

        layoutCards.clear()

        val pinnedItems = clipboard.pinnedItems()
        val recentItems = clipboard.items()

        if (recentItems.isEmpty() && pinnedItems.isEmpty()) {
            val emptyCenterY = (h - toolbarHeight) / 2f + toolbarHeight
            canvas.drawText("الحافظة فارغة", w / 2f, emptyCenterY - (10f * density), headerTitlePaint)
            canvas.drawText("قم بنسخ أي نص وسيظهر هنا تلقائياً", w / 2f, emptyCenterY + (16f * density), emptySubPaint)
            canvas.restore()
            if (activeDialogCard != null) drawActionDialog(canvas, w, h)
            return
        }

        val totalAvailableWidth = w - (horizontalMargin * 2f) - gridGap
        val colWidth = totalAvailableWidth / 2f
        var curY = toolbarHeight + (8f * density)

        // Section 1: الأحدث (Recent items) - Always on top
        if (recentItems.isNotEmpty()) {
            val sectionY = curY - scrollYOffset
            if (sectionY + (24f * density) >= toolbarHeight && sectionY <= h) {
                canvas.drawText("الأحدث", w - horizontalMargin, sectionY + (14f * density), sectionHeaderPaint)
            }
            curY += 24f * density

            for (i in recentItems.indices step 2) {
                val rightText = recentItems[i]
                val leftText = recentItems.getOrNull(i + 1)
                curY = recordAndDrawRow(canvas, w, curY, colWidth, rightText, leftText, false, h)
                curY += gridGap
            }
            curY += 8f * density
        }

        // Section 2: العناصر المثبَّتة (Pinned items) - Always underneath
        if (pinnedItems.isNotEmpty()) {
            val sectionY = curY - scrollYOffset
            if (sectionY + (24f * density) >= toolbarHeight && sectionY <= h) {
                canvas.drawText("العناصر المثبَّتة 📌", w - horizontalMargin, sectionY + (14f * density), sectionHeaderPaint)
            }
            curY += 24f * density

            for (i in pinnedItems.indices step 2) {
                val rightText = pinnedItems[i]
                val leftText = pinnedItems.getOrNull(i + 1)
                curY = recordAndDrawRow(
                    canvas, w, curY, colWidth,
                    rightText, leftText, true, h,
                    rightPinnedIndex = i,
                    leftPinnedIndex = if (leftText != null) i + 1 else -1
                )
                curY += gridGap
            }
        }

        val totalContentHeight = curY - toolbarHeight + (12f * density)
        maxScroll = max(0f, totalContentHeight - (h - toolbarHeight))

        canvas.restore()

        // 4. If dragging a pinned card, draw the floating card over everything else
        if (isDraggingPinned && draggedCard != null) {
            drawFloatingDraggedCard(canvas, w, h)
        }

        // 5. If Action Dialog is open, draw overlay modal
        if (activeDialogCard != null) {
            drawActionDialog(canvas, w, h)
        }
    }

    private fun recordAndDrawRow(
        canvas: Canvas,
        w: Float,
        contentY: Float,
        colWidth: Float,
        rightText: String?,
        leftText: String?,
        isPinned: Boolean,
        viewHeight: Float,
        rightPinnedIndex: Int = -1,
        leftPinnedIndex: Int = -1
    ): Float {
        val rowCardHeight = cardHeight

        // Right column card (in RTL: right side comes first)
        if (!rightText.isNullOrBlank()) {
            val rightLeft = w - horizontalMargin - colWidth
            val rect = RectF(rightLeft, contentY, rightLeft + colWidth, contentY + rowCardHeight)
            layoutCards.add(ClipCard(rightText, isPinned, rect, rightPinnedIndex))

            val drawY = contentY - scrollYOffset
            if (drawY + rowCardHeight >= toolbarHeight && drawY <= viewHeight) {
                val drawRect = RectF(rightLeft, drawY, rightLeft + colWidth, drawY + rowCardHeight)
                drawCard(canvas, drawRect, rightText, isPinned, rightPinnedIndex)
            }
        }

        // Left column card
        if (!leftText.isNullOrBlank()) {
            val rect = RectF(horizontalMargin, contentY, horizontalMargin + colWidth, contentY + rowCardHeight)
            layoutCards.add(ClipCard(leftText, isPinned, rect, leftPinnedIndex))

            val drawY = contentY - scrollYOffset
            if (drawY + rowCardHeight >= toolbarHeight && drawY <= viewHeight) {
                val drawRect = RectF(horizontalMargin, drawY, horizontalMargin + colWidth, drawY + rowCardHeight)
                drawCard(canvas, drawRect, leftText, isPinned, leftPinnedIndex)
            }
        }

        return contentY + rowCardHeight
    }

    private fun drawToolbar(canvas: Canvas, w: Float) {
        toolbarBounds.clear()
        val barMarginH = 4f * density
        val barTop = 2f * density
        val barBottom = toolbarHeight - (2f * density)
        val barRadius = 12f * density
        toolbarBarRect.set(barMarginH, barTop, w - barMarginH, barBottom)

        val activeTheme = settings?.theme?.let { Themes.byId(it) } ?: Themes.Amoled
        toolbarBarBgPaint.color = when (activeTheme.id) {
            "retro_1995" -> 0xFFB4B0A2.toInt()
            "ios_style" -> 0xFFC0C5CF.toInt()
            "honey_3d" -> 0xFF140B04.toInt()
            else -> 0xFF0D1117.toInt()
        }
        toolbarBarBorderPaint.color = activeTheme.keyAccent
        toolbarDividerPaint.color = activeTheme.keyAccent
        activeIconHighlightPaint.color = when (activeTheme.id) {
            "retro_1995" -> 0xFF000080.toInt()
            "ios_style" -> 0xFF007AFF.toInt()
            "honey_3d" -> 0xFFB45309.toInt()
            else -> 0xFF1E3F42.toInt()
        }

        // Fill toolbar background
        canvas.drawRoundRect(toolbarBarRect, barRadius, barRadius, toolbarBarBgPaint)

        // Border outline
        toolbarBarBorderPaint.strokeWidth = if (activeTheme.id == "ios_style") 1.2f * density else 2.2f * density
        canvas.drawRoundRect(toolbarBarRect, barRadius, barRadius, toolbarBarBorderPaint)

        val icons = listOf(
            "hub" to hubIcon,
            "emoji" to emojiIcon,
            "voice" to micIcon,
            "cursor" to cursorIcon,
            "clipboard" to clipboardIcon,
            "hide" to hideIcon
        )
        val numIcons = icons.size
        val slotWidth = toolbarBarRect.width() / numIcons.toFloat()

        // Divider lines between slots
        toolbarDividerPaint.strokeWidth = 1.5f * density
        val dividerHalfH = 10.5f * density
        val dividerCenterY = toolbarBarRect.centerY()
        for (i in 1 until numIcons) {
            val divX = toolbarBarRect.left + (i * slotWidth)
            canvas.drawLine(divX, dividerCenterY - dividerHalfH, divX, dividerCenterY + dividerHalfH, toolbarDividerPaint)
        }

        icons.forEachIndexed { i, (id, drawable) ->
            val centerX = toolbarBarRect.left + (i * slotWidth) + (slotWidth / 2f)
            val centerY = toolbarBarRect.centerY()

            val slotRect = RectF(
                toolbarBarRect.left + (i * slotWidth),
                barTop,
                toolbarBarRect.left + ((i + 1) * slotWidth),
                barBottom
            )
            toolbarBounds[id] = slotRect

            // Clipboard icon (index 4) is actively highlighted with cyan/teal rounded box
            if (id == "clipboard") {
                val boxWidth = slotWidth * 0.76f
                val boxHeight = (barBottom - barTop) * 0.76f
                val highlightRect = RectF(
                    centerX - boxWidth / 2f,
                    centerY - boxHeight / 2f,
                    centerX + boxWidth / 2f,
                    centerY + boxHeight / 2f
                )
                canvas.drawRoundRect(highlightRect, 8f * density, 8f * density, activeIconHighlightPaint)
            }

            if (drawable != null) {
                val iconTint = if (id == "clipboard") 0xFFFFFFFF.toInt() else (if (activeTheme.id == "retro_1995" || activeTheme.id == "ios_style") activeTheme.keyText else 0xFF8E95A5.toInt())
                drawable.setTint(iconTint)
                val iconSize = (22f * density).toInt()
                val left = (centerX - iconSize / 2f).toInt()
                val top = (centerY - iconSize / 2f).toInt()
                drawable.setBounds(left, top, left + iconSize, top + iconSize)
                drawable.draw(canvas)
            }
        }
    }

    private fun drawCard(canvas: Canvas, rect: RectF, text: String, isPinned: Boolean, pinnedIndex: Int = -1) {
        // If this is the pinned card currently being dragged, draw its source placeholder
        if (isDraggingPinned && isPinned && draggedCard?.text == text) {
            canvas.drawRoundRect(rect, cardRadius, cardRadius, dragSourceSlotPaint)
            val placeholderText = "الموضع السابق 📍"
            val textY = rect.centerY() - ((dragBadgeTextPaint.ascent() + dragBadgeTextPaint.descent()) / 2f)
            canvas.drawText(placeholderText, rect.centerX(), textY, dragBadgeTextPaint)
            return
        }

        val isTargetSlot = isDraggingPinned && hasMovedSignificantly && isPinned &&
            pinnedIndex == dragTargetIndex && pinnedIndex != (draggedCard?.pinnedIndex ?: -1)

        val isBeingSwiped = isSwiping && swipedCard?.text == text && swipedCard?.isPinned == isPinned && swipeOffsetX < 0f

        if (isBeingSwiped) {
            canvas.drawRoundRect(rect, cardRadius, cardRadius, swipeBgPaint)

            val iconSize = (18f * density).toInt()
            val iconRight = (rect.right - 10f * density).toInt()
            val iconLeft = iconRight - iconSize
            val iconTop = (rect.centerY() - iconSize / 2f).toInt()
            trashIcon?.setTint(0xFFFFFFFF.toInt())
            trashIcon?.setBounds(iconLeft, iconTop, iconRight, iconTop + iconSize)
            trashIcon?.draw(canvas)

            canvas.save()
            canvas.translate(swipeOffsetX, 0f)
        }

        val paintToUse = if (isDeleteMode) cardDeleteModeBgPaint else cardBgPaint
        canvas.drawRoundRect(rect, cardRadius, cardRadius, paintToUse)
        if (isDeleteMode) {
            canvas.drawRoundRect(rect, cardRadius, cardRadius, cardDeleteBorderPaint)
        }

        // If hovered target slot during drag reordering, draw highlighted glow outline
        if (isTargetSlot) {
            val activeTheme = settings?.theme?.let { Themes.byId(it) } ?: Themes.Amoled
            dragTargetSlotPaint.color = activeTheme.keyAccent
            dragTargetSlotPaint.strokeWidth = 2.5f * density
            canvas.drawRoundRect(rect, cardRadius, cardRadius, dragTargetSlotPaint)
        }

        val textY = rect.centerY() - ((cardTextPaint.ascent() + cardTextPaint.descent()) / 2f)
        val maxTextWidth = rect.width() - (if (isDeleteMode) 34f * density else 16f * density)
        val clippedText = getCachedTruncatedText(text, maxTextWidth)
        canvas.drawText(clippedText, rect.centerX(), textY, cardTextPaint)

        // Draw pin indicator for pinned items
        if (isPinned) {
            canvas.drawText("📌", rect.left + (6f * density), rect.top + (14f * density), pinPaint)
        }

        // In delete mode, draw a red ✕ badge at the top-left of the card for unpinned items ONLY
        if (isDeleteMode && !isPinned) {
            val badgeRadius = 9f * density
            val badgeCenterX = rect.left + badgeRadius + (4f * density)
            val badgeCenterY = rect.top + badgeRadius + (4f * density)
            canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius, badgeBgPaint)

            val xSize = (12f * density).toInt()
            val xLeft = (badgeCenterX - xSize / 2f).toInt()
            val xTop = (badgeCenterY - xSize / 2f).toInt()
            closeIcon?.setBounds(xLeft, xTop, xLeft + xSize, xTop + xSize)
            closeIcon?.draw(canvas)
        }

        if (isBeingSwiped) {
            canvas.restore()
        }
    }

    private fun drawFloatingDraggedCard(canvas: Canvas, w: Float, h: Float) {
        val card = draggedCard ?: return
        val activeTheme = settings?.theme?.let { Themes.byId(it) } ?: Themes.Amoled

        val totalAvailableWidth = w - (horizontalMargin * 2f) - gridGap
        val colWidth = totalAvailableWidth / 2f
        val floatWidth = colWidth * 1.04f
        val floatHeight = cardHeight * 1.04f

        val left = dragTouchX - (floatWidth / 2f)
        val top = (dragTouchY - (floatHeight / 2f)).coerceIn(toolbarHeight + (2f * density), h - floatHeight - (2f * density))
        val floatRect = RectF(left, top, left + floatWidth, top + floatHeight)

        // 1. Soft dark drop shadow underneath
        val shadowRect = RectF(floatRect.left + (2f * density), floatRect.top + (4f * density), floatRect.right + (2f * density), floatRect.bottom + (8f * density))
        canvas.drawRoundRect(shadowRect, cardRadius * 1.2f, cardRadius * 1.2f, dragShadowPaint)

        // 2. Elevated card background
        dragCardBgPaint.color = when (activeTheme.id) {
            "retro_1995" -> 0xFFD4D0C8.toInt()
            "ios_style" -> 0xFF3A3A3C.toInt()
            "honey_3d" -> 0xFF2A1B0E.toInt()
            else -> 0xFF263238.toInt()
        }
        canvas.drawRoundRect(floatRect, cardRadius, cardRadius, dragCardBgPaint)

        // 3. Glowing 3D accent border
        dragBorderPaint.color = activeTheme.keyAccent
        dragBorderPaint.strokeWidth = 2.5f * density
        canvas.drawRoundRect(floatRect, cardRadius, cardRadius, dragBorderPaint)

        // 4. Pin icon + Drag indicator
        canvas.drawText("📌 ↕️", floatRect.left + (8f * density), floatRect.top + (16f * density), pinPaint)

        // 5. Truncated text
        val textY = floatRect.centerY() - ((cardTextPaint.ascent() + cardTextPaint.descent()) / 2f)
        val maxTextWidth = floatRect.width() - (24f * density)
        val clippedText = getCachedTruncatedText(card.text, maxTextWidth)
        canvas.drawText(clippedText, floatRect.centerX(), textY, cardTextPaint)

        // 6. Floating guidance badge
        val badgeText = if (dragTargetIndex != -1 && dragTargetIndex != card.pinnedIndex) {
            "إفلات هنا للترتيب في هذا الموضع 📍"
        } else {
            "اسحب للجهة الأخرى أو للأسفل لإعادة الترتيب ↕️"
        }
        dragBadgeTextPaint.textSize = 10.5f * density
        val badgeW = dragBadgeTextPaint.measureText(badgeText) + (16f * density)
        val badgeH = 20f * density
        val badgeTop = if (floatRect.bottom + (28f * density) < h) {
            floatRect.bottom + (6f * density)
        } else {
            floatRect.top - (6f * density) - badgeH
        }
        val badgeRect = RectF(
            (floatRect.centerX() - badgeW / 2f).coerceIn(4f * density, w - badgeW - (4f * density)),
            badgeTop,
            (floatRect.centerX() + badgeW / 2f).coerceIn(badgeW + (4f * density), w - (4f * density)),
            badgeTop + badgeH
        )
        canvas.drawRoundRect(badgeRect, 10f * density, 10f * density, dragBadgeBgPaint)
        val badgeTextY = badgeRect.centerY() - ((dragBadgeTextPaint.ascent() + dragBadgeTextPaint.descent()) / 2f)
        canvas.drawText(badgeText, badgeRect.centerX(), badgeTextY, dragBadgeTextPaint)
    }

    private fun findPinnedTargetIndex(x: Float, y: Float): Int {
        val contentY = y + scrollYOffset
        val pinnedCards = layoutCards.filter { it.isPinned }
        if (pinnedCards.isEmpty()) return -1

        // 1. Direct hit on an existing card's bounds
        for (c in pinnedCards) {
            if (c.bounds.contains(x, contentY)) {
                return c.pinnedIndex
            }
        }

        // 2. Proximity: find card with closest center
        var bestIndex = -1
        var bestDistSq = Float.MAX_VALUE
        for (c in pinnedCards) {
            val cb = c.bounds
            val dx = x - cb.centerX()
            val dy = contentY - cb.centerY()
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestIndex = c.pinnedIndex
            }
        }
        return bestIndex
    }

    private fun drawActionDialog(canvas: Canvas, w: Float, h: Float) {
        val card = activeDialogCard ?: return
        val item = card.text
        val isPinned = card.isPinned

        // Scrim
        canvas.drawRect(0f, 0f, w, h, scrimPaint)

        // Dialog dimensions
        val dialogWidth = (w - (36f * density)).coerceAtMost(380f * density)
        val dialogHeight = 220f * density
        val dLeft = (w - dialogWidth) / 2f
        val dTop = (h - dialogHeight) / 2f
        dialogRect.set(dLeft, dTop, dLeft + dialogWidth, dTop + dialogHeight)

        // Draw Dialog Background
        canvas.drawRoundRect(dialogRect, 14f * density, 14f * density, dialogBgPaint)
        canvas.drawRoundRect(dialogRect, 14f * density, 14f * density, dialogBorderPaint)

        // Title preview
        val titleText = getCachedTruncatedText(item, dialogWidth - (32f * density))
        val titleY = dTop + (26f * density)
        headerTitlePaint.textSize = 15f * density
        headerTitlePaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText(titleText, dialogRect.centerX(), titleY, headerTitlePaint)

        // Buttons
        val btnHeight = 36f * density
        val btnMarginH = 16f * density
        val btnWidth = dialogWidth - (btnMarginH * 2f)

        // Button 1: Paste [ لصق النص في المحادثة ]
        val b1Top = dTop + (42f * density)
        dialogPasteBounds.set(dLeft + btnMarginH, b1Top, dLeft + btnMarginH + btnWidth, b1Top + btnHeight)
        canvas.drawRoundRect(dialogPasteBounds, 8f * density, 8f * density, dialogButtonPaint)
        dialogTextPaint.color = 0xFF2DD4BF.toInt()
        val b1TextY = dialogPasteBounds.centerY() - ((dialogTextPaint.ascent() + dialogTextPaint.descent()) / 2f)
        canvas.drawText("📋 لصق النص في المحادثة", dialogPasteBounds.centerX(), b1TextY, dialogTextPaint)

        // Button 2: Pin / Unpin
        val pinLabel = if (isPinned) "📌 إلغاء التثبيت" else "📌 تثبيت النص (حماية من الحذف)"
        val b2Top = b1Top + btnHeight + (8f * density)
        dialogPinBounds.set(dLeft + btnMarginH, b2Top, dLeft + btnMarginH + btnWidth, b2Top + btnHeight)
        canvas.drawRoundRect(dialogPinBounds, 8f * density, 8f * density, dialogButtonPaint)
        dialogTextPaint.color = 0xFFFFFFFF.toInt()
        val b2TextY = dialogPinBounds.centerY() - ((dialogTextPaint.ascent() + dialogTextPaint.descent()) / 2f)
        canvas.drawText(pinLabel, dialogPinBounds.centerX(), b2TextY, dialogTextPaint)

        // Button 3: Delete or Reorder
        val b3Top = b2Top + btnHeight + (8f * density)
        dialogDeleteBounds.set(dLeft + btnMarginH, b3Top, dLeft + btnMarginH + btnWidth, b3Top + btnHeight)
        if (isPinned) {
            canvas.drawRoundRect(dialogDeleteBounds, 8f * density, 8f * density, dialogButtonPaint)
            dialogTextPaint.color = 0xFF38BDF8.toInt()
            val b3TextY = dialogDeleteBounds.centerY() - ((dialogTextPaint.ascent() + dialogTextPaint.descent()) / 2f)
            canvas.drawText("🔄 تحريك النص (نقل للجهة الأخرى / للأسفل)", dialogDeleteBounds.centerX(), b3TextY, dialogTextPaint)
        } else {
            canvas.drawRoundRect(dialogDeleteBounds, 8f * density, 8f * density, dialogDeleteBtnPaint)
            dialogTextPaint.color = 0xFFF87171.toInt()
            val b3TextY = dialogDeleteBounds.centerY() - ((dialogTextPaint.ascent() + dialogTextPaint.descent()) / 2f)
            canvas.drawText("🗑️ حذف من الحافظة", dialogDeleteBounds.centerX(), b3TextY, dialogTextPaint)
        }

        // Button 4: Cancel [ إلغاء ]
        val b4Top = b3Top + btnHeight + (8f * density)
        dialogCancelBounds.set(dLeft + btnMarginH, b4Top, dLeft + btnMarginH + btnWidth, b4Top + (28f * density))
        dialogTextPaint.color = 0xFF9CA3AF.toInt()
        val b4TextY = dialogCancelBounds.centerY() - ((dialogTextPaint.ascent() + dialogTextPaint.descent()) / 2f)
        canvas.drawText("إلغاء", dialogCancelBounds.centerX(), b4TextY, dialogTextPaint)
    }

    private fun getCachedTruncatedText(text: String, maxWidth: Float): String {
        val cacheKey = "$text|$maxWidth"
        textTruncateCache[cacheKey]?.let { return it }

        if (cardTextPaint.measureText(text) <= maxWidth) {
            textTruncateCache[cacheKey] = text
            return text
        }

        // Fast binary search for truncation boundary instead of linear dropLast
        var low = 0
        var high = text.length
        var best = 0
        while (low <= high) {
            val mid = (low + high) / 2
            val candidate = text.substring(0, mid) + "…"
            if (cardTextPaint.measureText(candidate) <= maxWidth) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        val result = if (best > 0) text.substring(0, best) + "…" else text.take(3)
        textTruncateCache[cacheKey] = result
        return result
    }

    fun deleteItem(item: String): Boolean {
        clipboard.remove(item, context)
        textTruncateCache.clear()
        invalidate()
        return true
    }

    private fun vibrateFeedback() {
        try {
            val activeSettings = settings ?: com.openswift.keyboard.data.Settings(context).also { settings = it }
            if (!activeSettings.hapticFeedback || activeSettings.powerSaveMode) return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (_: Exception) {}
    }

    private fun findCardAt(x: Float, y: Float): ClipCard? {
        val contentY = y + scrollYOffset
        for (card in layoutCards) {
            if (card.bounds.contains(x, contentY)) {
                return card
            }
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        // If Action Dialog is visible, handle dialog clicks
        if (activeDialogCard != null) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                if (ignoreNextUpForDialog) {
                    ignoreNextUpForDialog = false
                    return true
                }
                val card = activeDialogCard!!
                val item = card.text
                val x = event.x
                val y = event.y

                if (dialogPasteBounds.contains(x, y)) {
                    vibrateFeedback()
                    resetScroll()
                    activeDialogCard = null
                    onItemSelected?.invoke(item)
                    invalidate()
                    return true
                } else if (dialogPinBounds.contains(x, y)) {
                    vibrateFeedback()
                    if (card.isPinned) {
                        clipboard.removePinned(item)
                        clipboard.add(item)
                        Toast.makeText(context, "تم إلغاء التثبيت (يمكنك حذفه الآن) ✓", Toast.LENGTH_SHORT).show()
                    } else {
                        clipboard.pin(item)
                        clipboard.remove(item, context)
                        Toast.makeText(context, "تم تثبيت النص (محمي من الحذف) 📌", Toast.LENGTH_SHORT).show()
                    }
                    textTruncateCache.clear()
                    activeDialogCard = null
                    invalidate()
                    return true
                } else if (dialogDeleteBounds.contains(x, y)) {
                    vibrateFeedback()
                    if (card.isPinned) {
                        val currentPinned = clipboard.pinnedItems()
                        val curIdx = currentPinned.indexOf(item)
                        if (curIdx != -1 && currentPinned.size > 1) {
                            val nextIdx = (curIdx + 1) % currentPinned.size
                            clipboard.reorderPinned(curIdx, nextIdx)
                            textTruncateCache.clear()
                            Toast.makeText(context, "تم نقل النص إلى الموقع التالي ✓", Toast.LENGTH_SHORT).show()
                            activeDialogCard = null
                            invalidate()
                        } else {
                            Toast.makeText(context, "اضغط مطولاً على أي نص مثبت لسحبه وإعادة ترتيبه بحرية ↕️", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        clipboard.remove(item, context)
                        textTruncateCache.clear()
                        Toast.makeText(context, "تم حذف النص من الحافظة", Toast.LENGTH_SHORT).show()
                        activeDialogCard = null
                        invalidate()
                    }
                    return true
                } else if (dialogCancelBounds.contains(x, y) || !dialogRect.contains(x, y)) {
                    activeDialogCard = null
                    invalidate()
                    return true
                }
            } else if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                ignoreNextUpForDialog = false
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                downX = event.x
                downY = event.y
                lastY = event.y
                isDragging = false
                isSwiping = false
                swipeOffsetX = 0f
                longPressTriggered = false
                pressedCard = null
                swipedCard = null
                isDraggingPinned = false
                draggedCard = null
                dragTargetIndex = -1
                hasMovedSignificantly = false

                // If touched below the toolbar, find if a card was hit
                if (event.y >= toolbarHeight) {
                    val card = findCardAt(event.x, event.y)
                    if (card != null) {
                        pressedCard = card
                        swipedCard = card
                        // 280ms threshold for responsive long-press & drag
                        longPressHandler.postDelayed(longPressRunnable, 280L)
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // If dragging a pinned card to reorder it
                if (isDraggingPinned) {
                    val moveDist = Math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()).toFloat()
                    if (moveDist > touchSlop) {
                        hasMovedSignificantly = true
                    }
                    dragTouchX = event.x
                    dragTouchY = event.y

                    val newTarget = findPinnedTargetIndex(event.x, event.y)
                    if (newTarget != -1 && newTarget != dragTargetIndex) {
                        dragTargetIndex = newTarget
                        vibrateFeedback()
                    }

                    // Auto-scrolling when hovering near top/bottom content edge
                    if (event.y < toolbarHeight + (32f * density) && scrollYOffset > 0f) {
                        scrollYOffset = (scrollYOffset - (6f * density)).coerceAtLeast(0f)
                    } else if (event.y > height - (32f * density) && scrollYOffset < maxScroll) {
                        scrollYOffset = (scrollYOffset + (6f * density)).coerceAtMost(maxScroll)
                    }

                    invalidate()
                    return true
                }

                val dx = event.x - downX
                val totalDy = event.y - downY
                val dy = event.y - lastY

                val effectiveSlop = if (pressedCard?.isPinned == true) touchSlop * 1.6f else touchSlop.toFloat()

                if (!isDragging && !isSwiping) {
                    // Check if swiping right-to-left on an unpinned card
                    if (dx < -touchSlop && abs(dx) > abs(totalDy) * 1.1f && swipedCard != null) {
                        if (!swipedCard!!.isPinned) {
                            isSwiping = true
                            longPressHandler.removeCallbacks(longPressRunnable)
                        } else {
                            swipedCard = null
                        }
                    } else if (abs(totalDy) > effectiveSlop) {
                        isDragging = true
                        longPressHandler.removeCallbacks(longPressRunnable)
                        swipedCard = null
                        pressedCard = null
                    }
                }

                if (isSwiping && swipedCard != null) {
                    swipeOffsetX = (event.x - downX).coerceAtMost(0f)
                    invalidate()
                } else if (isDragging && maxScroll > 0f) {
                    val newScroll = (scrollYOffset - dy).coerceIn(0f, maxScroll)
                    if (newScroll != scrollYOffset) {
                        scrollYOffset = newScroll
                        invalidate()
                    }
                    lastY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)

                // If user was dragging a pinned card to reorder it
                if (isDraggingPinned) {
                    val card = draggedCard
                    val fromIndex = card?.pinnedIndex ?: -1
                    val toIndex = dragTargetIndex

                    if (hasMovedSignificantly && card != null && fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                        val reordered = clipboard.reorderPinned(fromIndex, toIndex)
                        if (reordered) {
                            vibrateFeedback()
                            textTruncateCache.clear()
                            Toast.makeText(context, "تم حفظ الترتيب الجديد ✓", Toast.LENGTH_SHORT).show()
                        }
                    } else if (!hasMovedSignificantly && card != null) {
                        // Finger held and lifted without dragging -> open Action Dialog
                        activeDialogCard = card
                        ignoreNextUpForDialog = true
                    }

                    isDraggingPinned = false
                    draggedCard = null
                    dragTargetIndex = -1
                    hasMovedSignificantly = false
                    longPressTriggered = false
                    pressedCard = null
                    swipedCard = null
                    velocityTracker?.recycle()
                    velocityTracker = null
                    invalidate()
                    return true
                }

                if (longPressTriggered) {
                    longPressTriggered = false
                    pressedCard = null
                    swipedCard = null
                    isSwiping = false
                    swipeOffsetX = 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return true
                }

                if (isSwiping && swipedCard != null) {
                    val swipedLeftDist = downX - event.x
                    val target = swipedCard!!
                    if (swipedLeftDist > 40f * density) {
                        if (target.isPinned) {
                            vibrateFeedback()
                            Toast.makeText(context, "النصوص المثبتة لا يتم مسحها إلا بعد إلغاء التثبيت 📌", Toast.LENGTH_SHORT).show()
                        } else {
                            vibrateFeedback()
                            clipboard.remove(target.text, context)
                            textTruncateCache.clear()
                            Toast.makeText(context, "تم حذف النص", Toast.LENGTH_SHORT).show()
                        }
                    }
                    swipedCard = null
                    isSwiping = false
                    swipeOffsetX = 0f
                    pressedCard = null
                    velocityTracker?.recycle()
                    velocityTracker = null
                    invalidate()
                    return true
                }

                if (isDragging) {
                    // Compute fling velocity
                    velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    val initialVelocityY = velocityTracker?.yVelocity ?: 0f
                    if (abs(initialVelocityY) > minFlingVelocity) {
                        scroller.fling(
                            0, scrollYOffset.toInt(),
                            0, -initialVelocityY.toInt(),
                            0, 0,
                            0, maxScroll.toInt()
                        )
                        postInvalidateOnAnimation()
                    }
                } else {
                    val x = event.x
                    val y = event.y

                    // Check Toolbar clicks
                    if (y <= toolbarHeight) {
                        for ((id, rect) in toolbarBounds) {
                            if (rect.contains(x, y)) {
                                resetScroll()
                                when (id) {
                                    "hub" -> onOpenHub?.invoke() ?: onClose?.invoke()
                                    "emoji" -> onOpenEmoji?.invoke() ?: onClose?.invoke()
                                    "voice" -> onOpenVoice?.invoke() ?: onClose?.invoke()
                                    "cursor" -> onOpenTextEditing?.invoke() ?: onClose?.invoke()
                                    "clipboard" -> onReturnToKeyboard?.invoke() ?: onClose?.invoke()
                                    "hide" -> onHide?.invoke() ?: onClose?.invoke()
                                }
                                velocityTracker?.recycle()
                                velocityTracker = null
                                return true
                            }
                        }
                    } else {
                        // Check card tap
                        val card = findCardAt(x, y)
                        if (card != null) {
                            vibrateFeedback()
                            if (isDeleteMode) {
                                if (card.isPinned) {
                                    Toast.makeText(context, "النصوص المثبتة لا يتم مسحها إلا بعد إلغاء التثبيت 📌", Toast.LENGTH_SHORT).show()
                                } else {
                                    clipboard.remove(card.text, context)
                                    textTruncateCache.clear()
                                    Toast.makeText(context, "تم حذف النص", Toast.LENGTH_SHORT).show()
                                    invalidate()
                                }
                            } else {
                                resetScroll()
                                onItemSelected?.invoke(card.text)
                            }
                            velocityTracker?.recycle()
                            velocityTracker = null
                            return true
                        }
                    }
                }

                isDragging = false
                isSwiping = false
                swipedCard = null
                swipeOffsetX = 0f
                pressedCard = null
                velocityTracker?.recycle()
                velocityTracker = null
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                isDraggingPinned = false
                draggedCard = null
                dragTargetIndex = -1
                hasMovedSignificantly = false
                isDragging = false
                isSwiping = false
                swipedCard = null
                swipeOffsetX = 0f
                pressedCard = null
                velocityTracker?.recycle()
                velocityTracker = null
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }
}
