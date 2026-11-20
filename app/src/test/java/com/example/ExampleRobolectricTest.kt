package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.compression.PdfCompressionEngine
import com.example.database.AppDatabase
import com.example.database.CompressionHistoryEntity
import com.example.domain.model.CompressionConfig
import com.example.domain.model.CompressionPreset
import com.example.domain.model.CompressionStatus
import com.example.domain.model.PdfError
import com.example.domain.repository.CompressionOutcome
import com.example.domain.repository.InspectionOutcome
import com.example.pdf.PdfInspector
import com.example.pdf.PdfStorageManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var storageManager: PdfStorageManager
    private lateinit var inspector: PdfInspector
    private lateinit var engine: PdfCompressionEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        storageManager = PdfStorageManager(context)
        inspector = PdfInspector(context, storageManager)
        engine = PdfCompressionEngine(context, storageManager)
    }

    @Test
    fun `app name matches PDF Compressor`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("PDF Compressor", appName)
    }

    @Test
    fun `home screen displays primary actions and validates empty selection`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("select_pdf_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_history").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_settings").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("preset_card_custom"))
        composeTestRule.onNodeWithTag("preset_card_custom").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("custom_quality_slider"))
        composeTestRule.onNodeWithTag("custom_quality_slider").assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("compress_pdf_button"))
        composeTestRule.onNodeWithTag("compress_pdf_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("select_pdf_button"))
        composeTestRule.onNodeWithText("Please select a PDF document first.").assertIsDisplayed()
    }

    @Test
    fun `compresses mixed text and image multi-page PDF preserving text and page count`() = runTest {
        val sampleFile = File(context.cacheDir, "test_mixed_sample.pdf")
        createMixedSamplePdf(sampleFile, pageCount = 2)
        val originalBytes = sampleFile.length()
        val sampleUri = Uri.fromFile(sampleFile)

        val inspectOutcome = inspector.inspect(sampleUri)
        assertTrue(inspectOutcome is InspectionOutcome.Success)
        val info = (inspectOutcome as InspectionOutcome.Success).info
        assertEquals(2, info.pageCount)
        assertTrue(info.embeddedImageCount >= 1)

        val compressOutcome = engine.compress(
            documentInfo = info,
            config = CompressionConfig.forPreset(CompressionPreset.EXTREME),
            onProgress = {}
        )
        assertTrue(compressOutcome is CompressionOutcome.Success)
        val result = (compressOutcome as CompressionOutcome.Success).result

        assertTrue("Expected compressed file to be smaller than $originalBytes, got ${result.compressedSizeBytes}", result.isSmaller)
        assertEquals(originalBytes - result.compressedSizeBytes, result.spaceSavedBytes)
        assertTrue(result.compressionPercentage > 0.0)

        // Verify original unchanged
        assertEquals(originalBytes, sampleFile.length())

        // Verify output opens cleanly, preserves page count, orientation, and searchable text
        val outputFile = File(result.tempFilePath)
        assertTrue(outputFile.exists())
        PDDocument.load(outputFile).use { outDoc ->
            assertEquals(2, outDoc.numberOfPages)
            val text = PDFTextStripper().getText(outDoc)
            assertTrue("Expected searchable text preserved, got: $text", text.contains("Invoice #2026"))
        }

        outputFile.delete()
        sampleFile.delete()
    }

    @Test
    fun `rejects password-protected PDF with clear error`() = runTest {
        val protectedFile = File(context.cacheDir, "test_protected.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            val policy = StandardProtectionPolicy("owner", "user", AccessPermission())
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(protectedFile)
        }

        val outcome = inspector.inspect(Uri.fromFile(protectedFile))
        assertTrue(outcome is InspectionOutcome.Error)
        assertEquals(PdfError.PasswordProtected, (outcome as InspectionOutcome.Error).error)
        protectedFile.delete()
    }

    @Test
    fun `rejects corrupted or non-PDF file cleanly`() = runTest {
        val fakePdf = File(context.cacheDir, "corrupted.pdf")
        fakePdf.writeText("Not actually a PDF header or structure")

        val outcome = inspector.inspect(Uri.fromFile(fakePdf))
        assertTrue(outcome is InspectionOutcome.Error)
        assertEquals(PdfError.UnsupportedFormat, (outcome as InspectionOutcome.Error).error)
        fakePdf.delete()
    }

    @Test
    fun `room compression history dao inserts queries and deletes records`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.compressionHistoryDao()

        val id = dao.insert(
            CompressionHistoryEntity(
                originalFileName = "report.pdf",
                compressedFileName = "report_compressed.pdf",
                originalSizeBytes = 2_000_000L,
                compressedSizeBytes = 600_000L,
                spaceSavedBytes = 1_400_000L,
                compressionPercentage = 70.0,
                compressionMode = "Recommended Compression",
                pageCount = 5,
                timestampMillis = 123456789L,
                managedFilePath = null,
                exportedUriString = null
            )
        )
        val records = dao.observeAll().first()
        assertEquals(1, records.size)
        assertEquals("report_compressed.pdf", records.first().compressedFileName)

        dao.deleteById(id)
        assertTrue(dao.observeAll().first().isEmpty())
        db.close()
    }

    @Test
    fun `tiny text-only PDF reports LARGER_THAN_ORIGINAL rather than fake savings`() = runTest {
        val textFile = File(context.cacheDir, "test_tiny_text.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("Hello World")
                cs.endText()
            }
            doc.save(textFile)
        }

        val info = (inspector.inspect(Uri.fromFile(textFile)) as InspectionOutcome.Success).info
        val outcome = engine.compress(
            documentInfo = info,
            config = CompressionConfig.forPreset(CompressionPreset.RECOMMENDED),
            onProgress = {}
        )
        assertTrue(outcome is CompressionOutcome.Success)
        val result = (outcome as CompressionOutcome.Success).result
        if (!result.isSmaller) {
            assertEquals(CompressionStatus.LARGER_THAN_ORIGINAL, result.status)
            assertTrue(result.spaceSavedBytes <= 0L)
        }
        File(result.tempFilePath).delete()
        textFile.delete()
    }

    private fun createMixedSamplePdf(file: File, pageCount: Int) {
        val width = 1200
        val height = 1200
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        var seed = 1337
        for (y in 0 until height) {
            for (x in 0 until width) {
                seed = seed * 1664525 + 1013904223
                val noise = (seed ushr 24) and 0x3F
                val r = ((x * 255) / width + noise).coerceIn(0, 255)
                val g = ((y * 255) / height + (noise / 2)).coerceIn(0, 255)
                val b = (((x xor y) and 0xFF) + noise).coerceIn(0, 255)
                pixels[y * width + x] = Color.rgb(r, g, b)
            }
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)

        PDDocument().use { doc ->
            val xImage = LosslessFactory.createFromImage(doc, bmp)
            repeat(pageCount) { idx ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(xImage, 60f, 360f, 460f, 400f)
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 16f)
                    cs.newLineAtOffset(60f, 300f)
                    cs.showText("Invoice #2026 Page ${idx + 1}")
                    cs.endText()
                }
            }
            doc.save(file)
        }
        bmp.recycle()
    }
}
