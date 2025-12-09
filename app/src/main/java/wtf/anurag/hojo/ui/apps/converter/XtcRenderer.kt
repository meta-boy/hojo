package wtf.anurag.hojo.ui.apps.converter

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * Handles rendering of content (HTML, Text) into Bitmaps for E-Paper conversion.
 * Extracted from XtcEncoder.
 */
object XtcRenderer {

    private const val PAGE_WIDTH = 480
    private const val PAGE_HEIGHT = 800
    private const val FONT_SCALE_FACTOR = 1.4f // 140% font scaling
    private const val FOOTER_HEIGHT = 40
    private const val FOOTER_PADDING = 8

    /**
     * Creates a TextPaint configured for e-paper rendering.
     */
    fun createTextPaint(settings: ConverterSettings): TextPaint {
        return TextPaint().apply {
            isAntiAlias = true
            textSize = 16f * (settings.fontSize / 100f) * FONT_SCALE_FACTOR
            color = Color.BLACK
            if (settings.fontFamily.isNotEmpty()) {
                try {
                    typeface = Typeface.createFromFile(settings.fontFamily)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Converts HTML body content to a Spanned object with proper styling.
     */
    fun htmlToSpanned(
        bodyHtml: String,
        imageGetter: Html.ImageGetter,
        textPaint: TextPaint,
        settings: ConverterSettings
    ): Spanned {
        val spanned = Html.fromHtml(bodyHtml, Html.FROM_HTML_MODE_COMPACT, imageGetter, null)

        return if (textPaint.typeface != null && settings.fontFamily.isNotEmpty()) {
            val ssb = SpannableStringBuilder(spanned)
            ssb.setSpan(
                CustomTypefaceSpan(textPaint.typeface),
                0,
                ssb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ssb
        } else {
            spanned
        }
    }

    /**
     * Data class to hold pagination info for footer rendering.
     */
    data class PageInfo(
        val globalCurrentPage: Int,
        val globalTotalPages: Int
    )

    /**
     * Calculates page breaks for the content.
     */
    fun calculatePageBreaks(
        spanned: Spanned,
        textPaint: TextPaint,
        settings: ConverterSettings
    ): List<Int> {
        val width = PAGE_WIDTH - (settings.margin * 2)
        val layout = StaticLayout.Builder.obtain(
            spanned,
            0,
            spanned.length,
            textPaint,
            width
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, settings.lineHeight)
            .setIncludePad(true)
            .build()

        val contentHeight = PAGE_HEIGHT - (settings.margin * 2) - FOOTER_HEIGHT

        val breaks = mutableListOf<Int>()
        var yOffset = 0
        breaks.add(0)

        while (yOffset < layout.height) {
            val proposedBottom = yOffset + contentHeight
            var nextOffset = proposedBottom

            if (proposedBottom < layout.height) {
                val lineIndex = layout.getLineForVertical(proposedBottom)
                val lineTop = layout.getLineTop(lineIndex)

                if (lineTop < proposedBottom && lineTop > yOffset) {
                    nextOffset = lineTop
                }
            }

            if (nextOffset < layout.height) {
                breaks.add(nextOffset)
            }
            yOffset = nextOffset
        }

        return breaks
    }

    /**
     * Renders spanned content into Bitmaps.
     * Invokes [onPageRendered] for each rendered page to avoid holding all bitmaps in memory.
     */
    fun renderPages(
        spanned: Spanned,
        textPaint: TextPaint,
        settings: ConverterSettings,
        title: String? = null,
        pageInfo: PageInfo? = null,
        onPageRendered: (Bitmap) -> Unit
    ) {
        val width = PAGE_WIDTH - (settings.margin * 2)
        val layout = StaticLayout.Builder.obtain(
            spanned,
            0,
            spanned.length,
            textPaint,
            width
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, settings.lineHeight)
            .setIncludePad(true)
            .build()

        val contentHeight = PAGE_HEIGHT - (settings.margin * 2) - FOOTER_HEIGHT

        var yOffset = 0
        var localPageCount = 0

        while (yOffset < layout.height) {
            val proposedBottom = yOffset + contentHeight
            var nextOffset = proposedBottom

            if (proposedBottom < layout.height) {
                val lineIndex = layout.getLineForVertical(proposedBottom)
                val lineTop = layout.getLineTop(lineIndex)

                if (lineTop < proposedBottom && lineTop > yOffset) {
                    nextOffset = lineTop
                }
            }

            val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            // Draw content
            // Calculate where this page visually ends
            val visibleHeight = nextOffset - yOffset
            val clipBottom = settings.margin + visibleHeight

            canvas.save()
            // Clip exactly to the page content height to prevent drawing the next page's starting line
            canvas.clipRect(
                0,
                0,
                PAGE_WIDTH,
                clipBottom
            )
            canvas.translate(
                settings.margin.toFloat(),
                settings.margin.toFloat() - yOffset.toFloat()
            )
            layout.draw(canvas)
            canvas.restore()

            // Draw Footer
            if (title != null && pageInfo != null) {
                val currentGlobal = pageInfo.globalCurrentPage + localPageCount
                drawFooter(canvas, title, currentGlobal, pageInfo.globalTotalPages, settings)
            }

            onPageRendered(bitmap)

            yOffset = nextOffset
            localPageCount++
        }
    }

    private fun drawFooter(
        canvas: Canvas,
        title: String,
        currentPage: Int,
        totalPages: Int,
        settings: ConverterSettings
    ) {
        val footerPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 12f * FONT_SCALE_FACTOR
            color = Color.BLACK
            typeface = Typeface.DEFAULT_BOLD
        }

        val footerY = (PAGE_HEIGHT - FOOTER_PADDING).toFloat()
        val margin = settings.margin.toFloat()

        // 1. Progress % (Left)
        val progress = if (totalPages > 0) (currentPage.toFloat() / totalPages * 100).toInt() else 0
        val progressText = "$progress%"
        canvas.drawText(progressText, margin, footerY, footerPaint)

        // 2. Title (Center)
        val availableWidth = PAGE_WIDTH - (margin * 2)
        val maxTitleWidth = availableWidth * 0.5f // 50% for title
        val titleText = android.text.TextUtils.ellipsize(
            title.uppercase(),
            footerPaint,
            maxTitleWidth,
            android.text.TextUtils.TruncateAt.END
        ).toString()

        val titleWidth = footerPaint.measureText(titleText)
        val titleX = (PAGE_WIDTH - titleWidth) / 2
        canvas.drawText(titleText, titleX, footerY, footerPaint)

        // 3. Page Number (Right)
        val pageText = "$currentPage"
        val pageWidth = footerPaint.measureText(pageText)
        val pageX = PAGE_WIDTH - margin - pageWidth
        canvas.drawText(pageText, pageX, footerY, footerPaint)
    }

    /**
     * Creates a scaled BitmapDrawable from image bytes, fitting within the available width.
     */
    fun createScaledDrawable(bytes: ByteArray, availableWidth: Int): Drawable? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val scale = if (bitmap.width > availableWidth) {
            availableWidth.toFloat() / bitmap.width
        } else {
            1f
        }

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }

        val drawable = BitmapDrawable(Resources.getSystem(), scaledBitmap)
        drawable.setBounds(0, 0, newWidth, newHeight)
        return drawable
    }

    class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(ds: TextPaint) {
            applyCustomTypeface(ds, typeface)
        }

        override fun updateMeasureState(paint: TextPaint) {
            applyCustomTypeface(paint, typeface)
        }

        private fun applyCustomTypeface(paint: Paint, tf: Typeface) {
            val old = paint.typeface
            val oldStyle = old?.style ?: 0

            val fake = oldStyle and tf.style.inv()
            if (fake and Typeface.BOLD != 0) {
                paint.isFakeBoldText = true
            }
            if (fake and Typeface.ITALIC != 0) {
                paint.textSkewX = -0.25f
            }

            paint.typeface = tf
        }
    }
}
