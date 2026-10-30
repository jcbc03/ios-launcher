package com.jcbc03.ioslauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class LayoutTest {
    private val a = Item.App("p/a"); private val b = Item.App("p/b"); private val f = Item.Folder("F", listOf("p/c", "p/d"))

    @Test fun reflowRechunks() {
        val r = reflow(listOf(listOf(a, b, f), listOf(a)), 2)
        assertEquals(listOf(listOf(a, b), listOf(f, a)), r)
    }

    @Test fun reflowEmptyKeepsOnePage() = assertEquals(listOf(emptyList<Item>()), reflow(emptyList(), 4))

    @Test fun jsonRoundTrip() {
        val l = Layout(listOf(listOf(a, f), listOf(b)), listOf("p/x"), listOf(3, 7))
        assertEquals(l, layoutFromJson(layoutToJson(l)))
    }

    @Test fun badJsonIsNull() = assertEquals(null, layoutFromJson("{nope"))

    @Test fun parsesAppFilter() {
        val xml = """<resources><item component="ComponentInfo{com.x/com.x.Main}" drawable="x_icon"/><item component=":LAUNCHER" drawable="y"/></resources>"""
        val p = KXmlParser().apply { setInput(StringReader(xml)) }
        val out = HashMap<String, String>(); parseAppFilter(p, out)
        assertEquals(mapOf("com.x/com.x.Main" to "x_icon"), out)
    }
}
