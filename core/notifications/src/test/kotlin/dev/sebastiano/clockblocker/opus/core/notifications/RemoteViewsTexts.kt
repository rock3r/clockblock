package dev.sebastiano.clockblocker.opus.core.notifications

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView

/** Inflates [this] like a host would and returns the text of every visible TextView, top to bottom. */
fun RemoteViews.texts(context: Context): List<String> = buildList { collectTexts(inflate(context)) }

/** Inflates [this] like a host would. */
fun RemoteViews.inflate(context: Context): View = apply(context, FrameLayout(context))

private fun MutableList<String>.collectTexts(view: View) {
    if (view.visibility != View.VISIBLE) return
    when (view) {
        // No-break spaces (which keep a zone tail together) read as plain spaces in assertions.
        is TextView -> add(view.text.toString().replace('\u00A0', ' '))
        is ViewGroup -> for (i in 0 until view.childCount) collectTexts(view.getChildAt(i))
    }
}
