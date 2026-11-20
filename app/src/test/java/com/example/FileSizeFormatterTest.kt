package com.example

import com.example.utils.FileSizeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeFormatterTest {

    @Test
    fun `formatBytes formats B KB MB accurately`() {
        assertEquals("512 B", FileSizeFormatter.formatBytes(512L))
        assertEquals("2.0 KB", FileSizeFormatter.formatBytes(2048L))
        assertEquals("1.50 MB", FileSizeFormatter.formatBytes(1572864L))
    }

    @Test
    fun `buildDefaultCompressedFileName appends _compressed suffix`() {
        assertEquals(
            "quarterly_report_compressed.pdf",
            FileSizeFormatter.buildDefaultCompressedFileName("quarterly_report.pdf")
        )
        assertEquals(
            "scan_compressed.pdf",
            FileSizeFormatter.buildDefaultCompressedFileName("scan")
        )
        assertEquals(
            "invoice_compressed.pdf",
            FileSizeFormatter.buildDefaultCompressedFileName("invoice_compressed.pdf")
        )
    }

    @Test
    fun `ensurePdfExtension guarantees lowercase pdf suffix`() {
        assertEquals("my_doc.pdf", FileSizeFormatter.ensurePdfExtension("my_doc"))
        assertEquals("my_doc.PDF", FileSizeFormatter.ensurePdfExtension("my_doc.PDF"))
    }
}
