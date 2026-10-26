package org.adaway.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceUrlTest {
    @Test
    fun acceptsHttpsUrlsAndPickedDocuments() {
        assertEquals("https://example.org/hosts", SourceRepository.normalizeUrl("  https://example.org/hosts "))
        assertEquals("https://example.org/Hosts", SourceRepository.normalizeUrl("HTTPS://example.org/Hosts"))
        assertEquals("https://example.org/hosts", SourceRepository.normalizeUrl("https://https://example.org/hosts"))
        assertEquals(
            "content://com.android.providers/document/42",
            SourceRepository.normalizeUrl("content://com.android.providers/document/42"),
        )
    }

    @Test
    fun rejectsOtherLocations() {
        for (url in listOf("", "https://", "http://example.org/hosts", "https://http://example.org/hosts", "file:///sdcard/hosts", "content://",
            "https://exa mple.org/hosts", "example.org/hosts")) {
            assertNull(url, SourceRepository.normalizeUrl(url))
        }
    }

    @Test
    fun parsesHttpDates() {
        assertEquals(784111777L, SourceDownloader.parseHttpDate("Sun, 06 Nov 1994 08:49:37 GMT"))
        assertNull(SourceDownloader.parseHttpDate("yesterday"))
        assertNull(SourceDownloader.parseHttpDate(null))
    }
}
