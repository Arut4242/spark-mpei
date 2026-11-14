package ru.mpei.assistant.security

import org.jsoup.Jsoup
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

object EmailHtmlSanitizer {
    fun sanitize(html: String): String {
        val dirty = Jsoup.parseBodyFragment(html)
        dirty.select("script,style,iframe,frame,frameset,form,input,button,select,textarea," +
            "object,embed,applet,meta,link,base,svg,math,template,noscript,xmp,plaintext").remove()
        val safelist = Safelist.none()
            .addTags("p", "br", "div", "span", "b", "strong", "i", "em", "u", "s", "del",
                "blockquote", "pre", "code", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6",
                "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption", "hr", "a", "sub", "sup")
            .addAttributes("a", "href", "title")
            .addProtocols("a", "href", "https", "http")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan")
        val clean = Cleaner(safelist).clean(dirty)
        clean.select("a[href]").forEach { link ->
            if (WebUrlPolicy.externalWebUrl(link.attr("href")) == null) link.removeAttr("href")
        }
        clean.outputSettings().prettyPrint(false)
        return clean.body().html()
    }
}
