package ru.yavasilek.netpulse.util

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FileSizeFormatterTest {
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun formatsDownloadSizes() {
        assertEquals("0 Б", FileSizeFormatter.format(0))
        assertEquals("512 КБ", FileSizeFormatter.format(512 * 1_024L))
        assertEquals("1.50 МБ", FileSizeFormatter.format(1_572_864))
    }
}
