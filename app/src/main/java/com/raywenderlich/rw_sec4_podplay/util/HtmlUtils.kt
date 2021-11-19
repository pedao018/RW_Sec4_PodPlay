package com.raywenderlich.rw_sec4_podplay.util

import android.os.Build
import android.text.Html
import android.text.Spanned

object HtmlUtils {
    fun htmlToSpannable(htmlDesc: String): Spanned {
        /*
        Before converting the text to a Spanned object, some initial cleanup is required.
        These two lines strip out all \n characters and <img> elements from the text.
        * */
        var newHtmlDesc = htmlDesc.replace("\n".toRegex(), "")
        newHtmlDesc = newHtmlDesc.replace(
            "(<(/)img>)|(<img.+?>)".toRegex(), ""
        )

        // 2
        /*
        Android’s Html.fromHtml method is used to convert the text to a Spanned object.
        This breaks the text down into multiple sections that Android will render with different styles.

        Note: The second parameter to fromHtml() is a flag added in Android N.
        This version of the call is only made if the app is running on Android N or higher.
        The flag can be set to either Html.FROM_HTML_MODE_LEGACY or Html.FROM_HTML_MODE_COMPACT, and controls how much space is added between block-level elements.
        The earlier version of fromHtml() has been deprecated, but it’s still required when running on Android M or lower.
        @Suppress("DEPRECATION") is used to allow the code to compile even though it is deprecated.
        * */
        val descSpan: Spanned
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            descSpan = Html.fromHtml(newHtmlDesc, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            descSpan = Html.fromHtml(newHtmlDesc)
        }
        return descSpan
    }
}
