package com.omiyawaki.osrswiki.ui.main

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsTabHostOpacityContractTest {
    @Test
    fun tabHostsPaintOpaquePaperSoPrewarmCannotShowThrough() {
        val activity = File("src/main/res/layout/activity_main.xml").readText()
        val news = File("src/main/res/layout/fragment_news.xml").readText()
        val search = File("src/main/res/layout/fragment_search.xml").readText()
        val more = File("src/main/res/layout/fragment_more.xml").readText()
        val saved = File("src/main/res/layout/fragment_saved_pages.xml").readText()
        val map = File("src/main/res/layout/fragment_map.xml").readText()

        assertTrue(activity.contains("android:background=\"?attr/paper_color\""))
        assertTrue(activity.contains("android:id=\"@+id/nav_host_container\""))
        val navHost = activity.substringAfter("android:id=\"@+id/nav_host_container\"")
            .substringBefore("/>")
        assertTrue(navHost.contains("android:background=\"?attr/paper_color\""))
        listOf(news, search, more, saved, map).forEach { layout ->
            assertTrue(layout.contains("android:background=\"?attr/paper_color\""))
        }
    }
}
