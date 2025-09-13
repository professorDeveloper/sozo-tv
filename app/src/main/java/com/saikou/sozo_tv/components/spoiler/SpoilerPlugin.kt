package com.saikou.sozo_tv.components.spoiler
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
                tp.bgColor = Color.DKGRAY
                tp.color = Color.DKGRAY
            } else {
                tp.bgColor = ColorUtils.applyAlpha(Color.DKGRAY, 25)
            }
        }

        fun setRevealed(revealed: Boolean) {
            this.revealed = revealed
        }
    }

    private class HideSpoilerSyntaxSpan : CharacterStyle() {
        override fun updateDrawState(tp: TextPaint) {
            tp.color = 0
        }
    }


    companion object {
        private val RE = Pattern.compile("~!(.+?)!~")

        private const val AUTO_SPOILER_PHRASE = "Downloads/SozoTv"

        private fun applySpoilerSpans(spannable: Spannable) {
            val text = spannable.toString()

            val matcher = RE.matcher(text)
            while (matcher.find()) {
                val s = matcher.start()
                val e = matcher.end()
                attachSpoiler(spannable, s, e)

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
                }
            }
            spannable.setSpan(spoilerSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(clickable, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

}