package com.ipsat.ipsat_tv.components.spoiler
import android.graphics.Color
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.utils.ColorUtils
import java.util.regex.Pattern

class SpoilerPlugin : AbstractMarkwonPlugin() {
    override fun beforeSetText(textView: TextView, markdown: Spanned) {
        applySpoilerSpans(markdown as Spannable)
    }

    private class RedditSpoilerSpan : CharacterStyle() {
        private var revealed = false
        override fun updateDrawState(tp: TextPaint) {
            if (!revealed) {
                // use the same text color
                tp.bgColor = Color.DKGRAY
                tp.color = Color.DKGRAY
            } else {
                // for example keep a bit of black background to remind that it is a spoiler
                tp.bgColor = ColorUtils.applyAlpha(Color.DKGRAY, 25)
            }
        }

        fun setRevealed(revealed: Boolean) {
            this.revealed = revealed
        }
    }

    // we also could make text size smaller (but then MetricAffectingSpan should be used)
    private class HideSpoilerSyntaxSpan : CharacterStyle() {
        override fun updateDrawState(tp: TextPaint) {
            // set transparent color
            tp.color = 0
        }
    }


    companion object {
        // existing regex for ~!spoiler!~
        private val RE = Pattern.compile("~!(.+?)!~")

        // the literal phrase to auto-spoiler
        private const val AUTO_SPOILER_PHRASE = "Downloads/IPSAT"

        private fun applySpoilerSpans(spannable: Spannable) {
            val text = spannable.toString()

            // 1) first, handle the explicit ~!spoiler!~ syntax
            val matcher = RE.matcher(text)
            while (matcher.find()) {
                val s = matcher.start()
                val e = matcher.end()
                attachSpoiler(spannable, s, e)

                // hide the "~!" and "!~"
                spannable.setSpan(
                    HideSpoilerSyntaxSpan(),
                    s,
                    s + 2,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    HideSpoilerSyntaxSpan(),
                    e - 2,
                    e,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // 2) now, automatically spoiler every "Downloads/IPSAT"
            var index = text.indexOf(AUTO_SPOILER_PHRASE)
            while (index >= 0) {
                val start = index
                val end = index + AUTO_SPOILER_PHRASE.length
                attachSpoiler(spannable, start, end)
                index = text.indexOf(AUTO_SPOILER_PHRASE, end)
            }
        }

        private fun attachSpoiler(spannable: Spannable, start: Int, end: Int) {
            val spoilerSpan = RedditSpoilerSpan()
            val clickable = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    spoilerSpan.setRevealed(true)
                    widget.postInvalidateOnAnimation()
                }
                override fun updateDrawState(ds: TextPaint) {
                    // no underline, no-op
                }
            }
            spannable.setSpan(spoilerSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(clickable, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

}