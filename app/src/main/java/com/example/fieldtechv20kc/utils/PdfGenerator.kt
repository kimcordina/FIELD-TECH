package com.example.fieldtechv20kc.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import androidx.exifinterface.media.ExifInterface
import com.example.fieldtechv20kc.data.constants.LegalText
import com.example.fieldtechv20kc.data.model.JobType
import com.example.fieldtechv20kc.data.model.Photo
import com.example.fieldtechv20kc.data.model.ReportSettings
import com.example.fieldtechv20kc.data.model.ReportWithDetails
import com.example.fieldtechv20kc.data.remote.firestore.ReportCloudDto
import com.example.fieldtechv20kc.data.remote.firestore.ReportsRemote
import com.example.fieldtechv20kc.data.remote.storage.ReportStorage
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.events.Event
import com.itextpdf.kernel.events.IEventHandler
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Div
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.layout.properties.VerticalAlignment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Generates a modern A4 field-service PDF via iText 7.
 *
 * Why iText (not Android PdfDocument): Android's PdfDocument paints the page
 * canvas at 72 DPI and tends to resample photo bitmaps into that grid, which
 * made camera photos look soft. iText embeds image XObjects at their native
 * pixel dimensions and scales them for layout — so logos and photos stay sharp.
 */
class PdfGenerator(
    private val context: Context,
    private val reportStorage: ReportStorage? = null,
    private val reportsRemote: ReportsRemote? = null
) {

    // Brand — sampled from the NCordina wordmark
    private val navy = DeviceRgb(0x20, 0x18, 0x48)
    private val navySoft = DeviceRgb(0xEE, 0xEC, 0xF5)
    private val ink = DeviceRgb(0x1A, 0x1A, 0x1A)
    private val muted = DeviceRgb(0x5C, 0x5F, 0x6B)
    private val rule = DeviceRgb(0xE4, 0xE5, 0xEB)
    private val softFill = DeviceRgb(0xF7, 0xF8, 0xFB)
    private val white = ColorConstants.WHITE

    private lateinit var fontRegular: PdfFont
    private lateinit var fontMedium: PdfFont
    private lateinit var fontBold: PdfFont
    private lateinit var fontExtraBold: PdfFont

    fun generateReportPdf(reportWithDetails: ReportWithDetails, settings: ReportSettings): String {
        val report = reportWithDetails.report
        val client = reportWithDetails.client
        val photos = reportWithDetails.photos

        loadFonts()

        val isInstallationType = if (report.isCustomJobType && !report.customJobTypeDisplayName.isNullOrEmpty()) {
            report.customJobTypeDisplayName.lowercase().contains("installation")
        } else {
            report.jobType == JobType.INSTALLATION_ON_LOAN ||
                report.jobType == JobType.INSTALLATION_PURCHASED
        }

        val titleText = if (isInstallationType) {
            "Installation Agreement / Report"
        } else {
            "Field Service Report"
        }

        val clientName = (client?.name ?: "Unknown").replace(Regex("[\\\\/\\s]+"), "_")
        val locality = (client?.locality ?: "Unknown").replace(Regex("[\\\\/\\s]+"), "_")
        val jobTypeFile = report.jobType.displayName.replace(Regex("[\\\\/\\s]+"), "_")
        val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        val prefix = when (report.jobType) {
            JobType.SERVICE_REPAIR -> "ServiceReport"
            JobType.INSTALLATION_ON_LOAN,
            JobType.INSTALLATION_PURCHASED -> "EquipmentAgreement"
        }
        val fileName = "${prefix}_${clientName}_${locality}_${jobTypeFile}_${fileDateFormat.format(report.createdAt)}_ID${report.id}.pdf"
        val reportsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val pdfFile = File(reportsDir, fileName)

        val writer = PdfWriter(FileOutputStream(pdfFile)).apply {
            setCompressionLevel(9)
        }
        val pdf = PdfDocument(writer)
        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, FooterHandler())

        Document(pdf, PageSize.A4).use { document ->
            document.setMargins(40f, 44f, 52f, 44f)

            drawHeader(document)
            drawTitleBlock(
                document,
                titleText = titleText,
                isInstallationType = isInstallationType,
                reportRef = report.reportRef
            )

            val dateFormat = SimpleDateFormat("dd MMMM yyyy  ·  HH:mm", Locale.getDefault())
            drawMetaStrip(
                document,
                date = dateFormat.format(report.createdAt),
                technician = report.technicianName,
                jobType = if (report.isCustomJobType && !report.customJobTypeDisplayName.isNullOrEmpty()) {
                    report.customJobTypeDisplayName
                } else {
                    report.jobType.displayName
                }
            )

            // Client
            sectionTitle(document, "Client Information")
            val clientRows = mutableListOf<Pair<String, String>>()
            clientRows += "Name" to (client?.name ?: "N/A")
            clientRows += "Locality" to (client?.locality ?: "N/A")
            if (settings.clientLegalNameEnabled && !client?.legalName.isNullOrEmpty()) {
                clientRows += "Legal Name" to client!!.legalName
            }
            if (settings.clientCompanyNumberEnabled && !client?.companyNumber.isNullOrEmpty()) {
                clientRows += "Company No." to client!!.companyNumber
            }
            if (settings.clientAddressEnabled && !client?.address.isNullOrEmpty()) {
                clientRows += "Address" to client!!.address
            }
            drawKeyValueCard(document, clientRows)

            // Job
            sectionTitle(document, "Job Information")
            val jobTypeDisplayName = if (report.isCustomJobType && !report.customJobTypeDisplayName.isNullOrEmpty()) {
                report.customJobTypeDisplayName
            } else {
                report.jobType.displayName
            }
            val jobRows = mutableListOf<Pair<String, String>>()
            jobRows += "Job Type" to jobTypeDisplayName
            if (report.serialNumbers.isNotEmpty()) {
                jobRows += "Serial Number/s" to report.serialNumbers
            }
            if (!report.timeStarted.isNullOrEmpty()) {
                jobRows += "Time Started" to report.timeStarted
            }
            if (!report.timeCompleted.isNullOrEmpty()) {
                jobRows += "Time Completed" to report.timeCompleted
            }
            drawKeyValueCard(document, jobRows)

            if (report.equipmentInstalledRepaired.isNotEmpty()) {
                fieldLabel(document, "Equipment Installed / Repaired")
                val equipmentItems = report.equipmentInstalledRepaired.split("\n").filter { it.isNotBlank() }
                equipmentItems.forEachIndexed { index, item ->
                    document.add(
                        Paragraph("${index + 1}.  $item")
                            .setFont(fontRegular)
                            .setFontSize(10f)
                            .setFontColor(ink)
                            .setMargin(0f)
                            .setMarginBottom(3f)
                            .setMarginLeft(2f)
                    )
                }
                document.add(Paragraph("\u00A0").setFontSize(4f).setMargin(0f))
            }

            fieldLabel(document, "Work Carried Out")
            drawBodyPanel(document, report.workCarriedOut.ifBlank { "—" })

            // Legal
            val legalTitle = if (report.isCustomJobType && !report.customJobTypeLegalTitle.isNullOrEmpty()) {
                report.customJobTypeLegalTitle
            } else {
                "Legal Acknowledgement"
            }
            sectionTitle(document, legalTitle)
            val legalText = if (report.isCustomJobType && !report.customJobTypeLegalText.isNullOrEmpty()) {
                report.customJobTypeLegalText
            } else {
                LegalText.getLegalText(report.jobType)
            }
            document.add(
                Paragraph(legalText)
                    .setFont(fontRegular)
                    .setFontSize(8.5f)
                    .setFontColor(muted)
                    .setTextAlignment(TextAlignment.JUSTIFIED)
                    .setMarginBottom(14f)
                    .setMultipliedLeading(1.35f)
            )

            // Signature
            sectionTitle(document, "Client Signature")
            document.add(
                Paragraph()
                    .add(Text("Signed by  ").setFont(fontMedium).setFontSize(9f).setFontColor(muted))
                    .add(Text(report.signerName.ifBlank { "N/A" }).setFont(fontRegular).setFontSize(10.5f).setFontColor(ink))
                    .setMarginBottom(8f)
            )
            drawSignatureBlock(document, report.signatureData)

            // Photos — full-bleed quality embeds
            if (photos.isNotEmpty()) {
                sectionTitle(document, "Photographic Evidence")
                document.add(
                    Paragraph("${photos.size} photo${if (photos.size == 1) "" else "s"} attached")
                        .setFont(fontRegular)
                        .setFontSize(9f)
                        .setFontColor(muted)
                        .setMarginTop(0f)
                        .setMarginBottom(10f)
                )
                photos.forEachIndexed { index, photo ->
                    drawPhoto(document, photo, index + 1)
                }
            }
        }

        return pdfFile.absolutePath
    }

    // ─── Company contact (public letterhead) ────────────────────────────────

    private companion object {
        const val COMPANY_NAME = "N. Cordina Marketing Ltd"
        const val COMPANY_ADDRESS = "R. Farrugia Street, Qormi QRM 3111, Malta"
        const val COMPANY_PHONE = "+356 2148 6056"
        const val COMPANY_PHONE_ALT = "+356 2144 7433"
        const val COMPANY_EMAIL = "info@ncordina.com"
        const val COMPANY_WEB = "www.ncordina.com"
        const val COMPANY_REG = "C14215"
    }

    // ─── Header / title ─────────────────────────────────────────────────────

    private fun drawHeader(document: Document) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(52f, 48f)))
            .useAllAvailableWidth()
            .setMarginBottom(8f)

        val logoCell = Cell()
            .setBorder(Border.NO_BORDER)
            .setPadding(0f)
            .setPaddingRight(8f)
            .setVerticalAlignment(VerticalAlignment.MIDDLE)
        loadLogoImage()?.let { logo ->
            logo.setHorizontalAlignment(HorizontalAlignment.LEFT)
            logoCell.add(logo)
        } ?: logoCell.add(
            Paragraph("NCORDINA")
                .setFont(fontExtraBold)
                .setFontSize(22f)
                .setFontColor(navy)
                .setMargin(0f)
        )
        table.addCell(logoCell)

        val info = Cell()
            .setBorder(Border.NO_BORDER)
            .setPadding(0f)
            .setTextAlignment(TextAlignment.RIGHT)
            .setVerticalAlignment(VerticalAlignment.MIDDLE)
        info.add(
            Paragraph(COMPANY_NAME)
                .setFont(fontBold)
                .setFontSize(10f)
                .setFontColor(ink)
                .setMargin(0f)
                .setTextAlignment(TextAlignment.RIGHT)
        )
        info.add(
            Paragraph(COMPANY_ADDRESS)
                .setFont(fontRegular)
                .setFontSize(7.5f)
                .setFontColor(muted)
                .setMarginTop(2f)
                .setMarginBottom(0f)
                .setTextAlignment(TextAlignment.RIGHT)
        )
        info.add(
            Paragraph("$COMPANY_PHONE  ·  $COMPANY_PHONE_ALT")
                .setFont(fontMedium)
                .setFontSize(8f)
                .setFontColor(ink)
                .setMarginTop(4f)
                .setMarginBottom(0f)
                .setTextAlignment(TextAlignment.RIGHT)
        )
        info.add(
            Paragraph("$COMPANY_EMAIL  ·  $COMPANY_WEB")
                .setFont(fontRegular)
                .setFontSize(7.5f)
                .setFontColor(navy)
                .setMarginTop(2f)
                .setMarginBottom(0f)
                .setTextAlignment(TextAlignment.RIGHT)
        )
        info.add(
            Paragraph("Company Reg. $COMPANY_REG")
                .setFont(fontRegular)
                .setFontSize(7f)
                .setFontColor(muted)
                .setMarginTop(2f)
                .setMarginBottom(0f)
                .setTextAlignment(TextAlignment.RIGHT)
        )
        table.addCell(info)
        document.add(table)

        // Brand accent bar
        val bar = Table(1).useAllAvailableWidth().setMarginBottom(14f)
        bar.addCell(
            Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(navy)
                .setHeight(2.5f)
                .setPadding(0f)
        )
        document.add(bar)
    }

    private fun drawTitleBlock(
        document: Document,
        titleText: String,
        isInstallationType: Boolean,
        reportRef: String
    ) {
        val titleRow = Table(UnitValue.createPercentArray(floatArrayOf(62f, 38f)))
            .useAllAvailableWidth()
            .setMarginBottom(4f)

        titleRow.addCell(
            Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(0f)
                .add(
                    Paragraph(titleText)
                        .setFont(fontExtraBold)
                        .setFontSize(19f)
                        .setFontColor(ink)
                        .setMargin(0f)
                )
        )

        val refLabel = reportRef.ifBlank { "—" }
        val refCell = Cell()
            .setBorder(Border.NO_BORDER)
            .setPadding(0f)
            .setTextAlignment(TextAlignment.RIGHT)
            .setVerticalAlignment(VerticalAlignment.BOTTOM)
        refCell.add(
            Paragraph("REPORT REF")
                .setFont(fontMedium)
                .setFontSize(7f)
                .setFontColor(muted)
                .setCharacterSpacing(0.8f)
                .setMargin(0f)
                .setTextAlignment(TextAlignment.RIGHT)
        )
        refCell.add(
            Paragraph(refLabel)
                .setFont(fontBold)
                .setFontSize(11f)
                .setFontColor(navy)
                .setMarginTop(1f)
                .setMarginBottom(0f)
                .setTextAlignment(TextAlignment.RIGHT)
        )
        titleRow.addCell(refCell)
        document.add(titleRow)

        if (isInstallationType) {
            document.add(
                Paragraph("An agreement entered into between $COMPANY_NAME ($COMPANY_REG) and the Client as listed below.")
                    .setFont(fontRegular)
                    .setFontSize(9f)
                    .setFontColor(muted)
                    .setMarginTop(6f)
                    .setMarginBottom(12f)
                    .setMultipliedLeading(1.3f)
            )
        } else {
            document.add(Paragraph("\u00A0").setFontSize(6f).setMarginBottom(8f))
        }
    }

    private fun drawMetaStrip(document: Document, date: String, technician: String, jobType: String) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f)))
            .useAllAvailableWidth()
            .setMarginBottom(18f)

        fun chip(label: String, value: String): Cell {
            val cell = Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(softFill)
                .setPadding(8f)
                .setPaddingLeft(10f)
                .setPaddingRight(10f)
            cell.add(
                Paragraph(label.uppercase(Locale.getDefault()))
                    .setFont(fontMedium)
                    .setFontSize(7f)
                    .setFontColor(muted)
                    .setCharacterSpacing(0.6f)
                    .setMargin(0f)
            )
            cell.add(
                Paragraph(value.ifBlank { "—" })
                    .setFont(fontMedium)
                    .setFontSize(9f)
                    .setFontColor(ink)
                    .setMarginTop(2f)
                    .setMarginBottom(0f)
            )
            return cell
        }

        table.addCell(chip("Date", date))
        table.addCell(chip("Technician", technician.ifBlank { "—" }).setPaddingLeft(6f).setPaddingRight(6f))
        table.addCell(chip("Job Type", jobType))
        document.add(table)
    }

    // ─── Sections ───────────────────────────────────────────────────────────

    private fun sectionTitle(document: Document, title: String) {
        document.add(
            Paragraph(title.uppercase(Locale.getDefault()))
                .setFont(fontBold)
                .setFontSize(10f)
                .setFontColor(navy)
                .setCharacterSpacing(0.8f)
                .setMarginTop(6f)
                .setMarginBottom(4f)
        )
        val ruleTable = Table(1).useAllAvailableWidth().setMarginBottom(10f)
        ruleTable.addCell(
            Cell().setBorder(Border.NO_BORDER).setBackgroundColor(rule).setHeight(1f).setPadding(0f)
        )
        document.add(ruleTable)
    }

    private fun fieldLabel(document: Document, label: String) {
        document.add(
            Paragraph(label)
                .setFont(fontMedium)
                .setFontSize(9f)
                .setFontColor(muted)
                .setMarginTop(2f)
                .setMarginBottom(4f)
        )
    }

    private fun drawKeyValueCard(document: Document, rows: List<Pair<String, String>>) {
        if (rows.isEmpty()) return
        val table = Table(UnitValue.createPercentArray(floatArrayOf(28f, 72f)))
            .useAllAvailableWidth()
            .setMarginBottom(12f)

        rows.forEachIndexed { index, (label, value) ->
            val bg = if (index % 2 == 0) softFill else white
            table.addCell(
                Cell()
                    .setBorder(Border.NO_BORDER)
                    .setBackgroundColor(bg)
                    .setPadding(7f)
                    .setPaddingLeft(10f)
                    .add(
                        Paragraph(label)
                            .setFont(fontMedium)
                            .setFontSize(8.5f)
                            .setFontColor(muted)
                            .setMargin(0f)
                    )
            )
            table.addCell(
                Cell()
                    .setBorder(Border.NO_BORDER)
                    .setBackgroundColor(bg)
                    .setPadding(7f)
                    .setPaddingRight(10f)
                    .add(
                        Paragraph(value)
                            .setFont(fontRegular)
                            .setFontSize(10f)
                            .setFontColor(ink)
                            .setMargin(0f)
                    )
            )
        }

        // Left accent
        val wrap = Table(UnitValue.createPercentArray(floatArrayOf(1.2f, 98.8f)))
            .useAllAvailableWidth()
            .setMarginBottom(14f)
        wrap.addCell(
            Cell()
                .setBorder(Border.NO_BORDER)
                .setBackgroundColor(navy)
                .setPadding(0f)
        )
        wrap.addCell(
            Cell()
                .setBorder(SolidBorder(rule, 0.7f))
                .setBorderLeft(Border.NO_BORDER)
                .setPadding(0f)
                .add(table.setMarginBottom(0f))
        )
        document.add(wrap)
    }

    private fun drawBodyPanel(document: Document, text: String) {
        val panel = Div()
            .setBackgroundColor(softFill)
            .setBorder(SolidBorder(rule, 0.7f))
            .setPadding(12f)
            .setMarginBottom(14f)
        panel.add(
            Paragraph(text)
                .setFont(fontRegular)
                .setFontSize(10f)
                .setFontColor(ink)
                .setMargin(0f)
                .setMultipliedLeading(1.4f)
        )
        document.add(panel)
    }

    // ─── Signature ──────────────────────────────────────────────────────────

    private fun drawSignatureBlock(document: Document, signatureData: String) {
        val signatureBytes = renderSignaturePng(signatureData)
        val box = Table(1).useAllAvailableWidth().setMarginBottom(16f)
        val cell = Cell()
            .setBorder(SolidBorder(rule, 0.9f))
            .setBackgroundColor(softFill)
            .setPadding(12f)
            .setMinHeight(110f)
            .setVerticalAlignment(VerticalAlignment.MIDDLE)

        if (signatureBytes != null) {
            val img = Image(ImageDataFactory.create(signatureBytes))
                .setAutoScale(true)
                .setMaxHeight(96f)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
            cell.add(img)
        } else {
            cell.add(
                Paragraph("No signature available")
                    .setFont(fontRegular)
                    .setFontSize(9f)
                    .setFontColor(muted)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMargin(0f)
            )
        }
        box.addCell(cell)
        document.add(box)
    }

    private fun renderSignaturePng(signatureData: String): ByteArray? {
        // Prefer crisp vector strokes rasterised at high DPI into a PNG
        val vectorBmp = renderVectorSignatureBitmap(900, 360)
        if (vectorBmp != null) {
            return bitmapToPng(vectorBmp).also { if (!vectorBmp.isRecycled) vectorBmp.recycle() }
        }

        var signatureBitmap: Bitmap? = null
        if (signatureData.startsWith("/")) {
            try {
                val file = File(signatureData)
                if (file.exists()) signatureBitmap = BitmapFactory.decodeFile(file.absolutePath)
            } catch (_: Exception) {
            }
        }
        if (signatureBitmap == null && signatureData.isNotEmpty()) {
            signatureBitmap = decodeBase64ToBitmap(signatureData)
        }
        return signatureBitmap?.let { bmp ->
            val bytes = bitmapToPng(bmp)
            if (!bmp.isRecycled) bmp.recycle()
            bytes
        }
    }

    private fun renderVectorSignatureBitmap(width: Int, height: Int): Bitmap? {
        return try {
            val prefs = context.getSharedPreferences("signature_vector_data", Context.MODE_PRIVATE)
            val strokeCount = prefs.getInt("stroke_count", 0)
            if (strokeCount == 0) return null

            val boundsLeft = prefs.getFloat("bounds_left", 0f)
            val boundsTop = prefs.getFloat("bounds_top", 0f)
            val boundsRight = prefs.getFloat("bounds_right", 0f)
            val boundsBottom = prefs.getFloat("bounds_bottom", 0f)
            if (boundsRight <= boundsLeft || boundsBottom <= boundsTop) return null

            val bounds = RectF(boundsLeft, boundsTop, boundsRight, boundsBottom)
            val pad = 28f
            val availW = width - pad * 2
            val availH = height - pad * 2
            val scale = minOf(availW / bounds.width(), availH / bounds.height())
            val scaledW = bounds.width() * scale
            val scaledH = bounds.height() * scale
            val offsetX = pad + (availW - scaledW) / 2f
            val offsetY = pad + (availH - scaledH) / 2f

            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(AndroidColor.TRANSPARENT)

            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = AndroidColor.BLACK
            }

            for (i in 0 until strokeCount) {
                val pointsJson = prefs.getString("stroke_${i}_points", "") ?: ""
                val widthsJson = prefs.getString("stroke_${i}_widths", "") ?: ""
                val color = prefs.getInt("stroke_${i}_color", AndroidColor.BLACK)
                if (pointsJson.isEmpty() || widthsJson.isEmpty()) continue

                val points = pointsJson.split(",").chunked(2).mapNotNull { coords ->
                    if (coords.size == 2) {
                        android.graphics.PointF(
                            coords[0].toFloatOrNull() ?: return@mapNotNull null,
                            coords[1].toFloatOrNull() ?: return@mapNotNull null
                        )
                    } else null
                }
                val widths = widthsJson.split(",").mapNotNull { it.toFloatOrNull() }
                if (points.isEmpty() || widths.isEmpty()) continue

                val path = Path()
                val first = points[0]
                path.moveTo(
                    (first.x - bounds.left) * scale + offsetX,
                    (first.y - bounds.top) * scale + offsetY
                )
                for (j in 1 until points.size) {
                    val p0 = points[j - 1]
                    val p1 = points[j]
                    val x0 = (p0.x - bounds.left) * scale + offsetX
                    val y0 = (p0.y - bounds.top) * scale + offsetY
                    val x1 = (p1.x - bounds.left) * scale + offsetX
                    val y1 = (p1.y - bounds.top) * scale + offsetY
                    val dx = x1 - x0
                    val dy = y1 - y0
                    path.cubicTo(
                        x0 + dx * 0.33f, y0 + dy * 0.33f,
                        x0 + dx * 0.67f, y0 + dy * 0.67f,
                        x1, y1
                    )
                }
                paint.color = color
                paint.strokeWidth = (widths.average().toFloat() * scale).coerceAtLeast(1.6f)
                canvas.drawPath(path, paint)
            }
            bmp
        } catch (e: Exception) {
            android.util.Log.e("PdfGenerator", "Vector signature render failed", e)
            null
        }
    }

    // ─── Photos ─────────────────────────────────────────────────────────────

    private fun drawPhoto(document: Document, photo: Photo, index: Int) {
        val caption = if (photo.caption.isNotBlank()) {
            "Photo $index  ·  ${photo.caption}"
        } else {
            "Photo $index"
        }
        document.add(
            Paragraph(caption)
                .setFont(fontMedium)
                .setFontSize(9f)
                .setFontColor(muted)
                .setMarginTop(4f)
                .setMarginBottom(6f)
                .setKeepWithNext(true)
        )

        val imageBytes = loadPhotoBytesForPdf(photo.filePath)
        if (imageBytes != null) {
            val image = Image(ImageDataFactory.create(imageBytes))
                .setAutoScale(true)
                .setWidth(UnitValue.createPercentValue(100f))
                .setMaxHeight(420f)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setBorder(SolidBorder(rule, 0.7f))
                .setMarginBottom(14f)
            document.add(image)
        } else {
            document.add(
                Paragraph("Unable to load image")
                    .setFont(fontRegular)
                    .setFontSize(9f)
                    .setFontColor(muted)
                    .setMarginBottom(14f)
            )
        }
    }

    /**
     * Decode photo with EXIF orientation, keep a long edge up to 2800 px, and
     * re-encode as high-quality JPEG for iText to embed as a full-res XObject.
     */
    private fun loadPhotoBytesForPdf(filePathOrUri: String): ByteArray? {
        return try {
            val isContentUri = filePathOrUri.startsWith("content://")

            fun open(): InputStream? =
                if (isContentUri) context.contentResolver.openInputStream(Uri.parse(filePathOrUri))
                else FileInputStream(File(filePathOrUri))

            var exifRotation = 0
            try {
                exifRotation = when {
                    isContentUri -> {
                        context.contentResolver.openInputStream(Uri.parse(filePathOrUri)).use { s ->
                            if (s != null) ExifInterface(s).rotationDegrees else 0
                        }
                    }
                    else -> ExifInterface(filePathOrUri).rotationDegrees
                }
            } catch (_: Exception) {
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open().use { s ->
                if (s == null) return null
                BitmapFactory.decodeStream(s, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val (srcW, srcH) = if (exifRotation == 90 || exifRotation == 270) {
                bounds.outHeight to bounds.outWidth
            } else {
                bounds.outWidth to bounds.outHeight
            }

            // Target ~2800 px long edge (~220–250 DPI on a full-width A4 image)
            val maxLongEdge = 2800
            val sample = computeInSampleSize(srcW, srcH, maxLongEdge, maxLongEdge)

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inJustDecodeBounds = false
                inSampleSize = sample
            }
            val decoded = open().use { s ->
                if (s == null) null else BitmapFactory.decodeStream(s, null, opts)
            } ?: return null

            val rotated = if (exifRotation != 0) {
                val m = Matrix().apply { postRotate(exifRotation.toFloat()) }
                val r = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                if (r !== decoded) decoded.recycle()
                r
            } else {
                decoded
            }

            val finalBmp = downscaleHighQuality(rotated, maxLongEdge)
            val bytes = bitmapToJpeg(finalBmp, quality = 92)
            if (!finalBmp.isRecycled) finalBmp.recycle()
            bytes
        } catch (e: Exception) {
            android.util.Log.e("PdfGenerator", "loadPhotoBytesForPdf failed: ${e.message}")
            null
        }
    }

    // ─── Logo / fonts ───────────────────────────────────────────────────────

    private fun loadFonts() {
        fontRegular = fontFromAsset("fonts/onest_regular_1602_hint.ttf")
        fontMedium = fontFromAsset("fonts/onest_medium_1602_hint.ttf")
        fontBold = fontFromAsset("fonts/onest_bold_1602_hint.ttf")
        fontExtraBold = fontFromAsset("fonts/onest_extrabold_1602_hint.ttf")
    }

    private fun fontFromAsset(path: String): PdfFont {
        context.assets.open(path).use { stream ->
            val bytes = stream.readBytes()
            return PdfFontFactory.createFont(
                bytes,
                PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
            )
        }
    }

    private fun loadLogoImage(): Image? {
        return try {
            context.assets.open("logo.png").use { stream ->
                val bytes = stream.readBytes()
                Image(ImageDataFactory.create(bytes))
                    .setAutoScale(true)
                    .setMaxHeight(36f)
                    .setMaxWidth(200f)
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfGenerator", "Logo load failed", e)
            null
        }
    }

    // ─── Footer ─────────────────────────────────────────────────────────────

    private inner class FooterHandler : IEventHandler {
        override fun handleEvent(event: Event) {
            val docEvent = event as PdfDocumentEvent
            val page: PdfPage = docEvent.page
            val pageSize: Rectangle = page.pageSize
            val pdfCanvas = PdfCanvas(page)
            val y = 28f

            pdfCanvas.saveState()
            pdfCanvas.setStrokeColor(rule)
            pdfCanvas.setLineWidth(0.7f)
            pdfCanvas.moveTo(44.0, (y + 10).toDouble())
            pdfCanvas.lineTo((pageSize.width - 44).toDouble(), (y + 10).toDouble())
            pdfCanvas.stroke()

            val canvas = com.itextpdf.layout.Canvas(pdfCanvas, pageSize)
            canvas.showTextAligned(
                Paragraph("$COMPANY_NAME  ·  $COMPANY_PHONE  ·  $COMPANY_EMAIL")
                    .setFont(fontRegular)
                    .setFontSize(7f)
                    .setFontColor(muted),
                44f,
                y,
                TextAlignment.LEFT
            )
            canvas.showTextAligned(
                Paragraph("Confidential  ·  Page ${docEvent.document.getPageNumber(page)}")
                    .setFont(fontRegular)
                    .setFontSize(7f)
                    .setFontColor(muted),
                pageSize.width - 44f,
                y,
                TextAlignment.RIGHT
            )
            canvas.close()
            pdfCanvas.restoreState()

            // Soft top rule on continuation pages
            if (docEvent.document.getPageNumber(page) > 1) {
                pdfCanvas.saveState()
                val gs = PdfExtGState().setFillOpacity(1f)
                pdfCanvas.setExtGState(gs)
                pdfCanvas.setFillColor(navySoft)
                pdfCanvas.rectangle(44.0, (pageSize.height - 36).toDouble(), (pageSize.width - 88).toDouble(), 14.0)
                pdfCanvas.fill()
                pdfCanvas.restoreState()
                com.itextpdf.layout.Canvas(PdfCanvas(page), pageSize).use { c ->
                    c.showTextAligned(
                        Paragraph("$COMPANY_NAME  ·  continued")
                            .setFont(fontMedium)
                            .setFontSize(7.5f)
                            .setFontColor(navy),
                        50f,
                        pageSize.height - 32f,
                        TextAlignment.LEFT
                    )
                }
            }
        }
    }

    // ─── Bitmap helpers ─────────────────────────────────────────────────────

    private fun bitmapToPng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        // Flatten transparency onto white for JPEG
        val flat = if (bitmap.hasAlpha()) {
            val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(opaque)
            c.drawColor(AndroidColor.WHITE)
            c.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            opaque
        } else bitmap
        flat.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (flat !== bitmap && !flat.isRecycled) flat.recycle()
        return out.toByteArray()
    }

    private fun downscaleHighQuality(src: Bitmap, targetLong: Int): Bitmap {
        var bmp = src
        val longEdge = maxOf(bmp.width, bmp.height)
        if (longEdge <= targetLong) return bmp

        while (maxOf(bmp.width, bmp.height) / 2 >= targetLong) {
            val w = bmp.width / 2
            val h = bmp.height / 2
            val half = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(half)
            val p = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }
            c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), Rect(0, 0, w, h), p)
            if (half != bmp) bmp.recycle()
            bmp = half
        }

        val scale = targetLong.toFloat() / maxOf(bmp.width, bmp.height)
        val finalW = (bmp.width * scale).toInt().coerceAtLeast(1)
        val finalH = (bmp.height * scale).toInt().coerceAtLeast(1)
        if (finalW == bmp.width && finalH == bmp.height) return bmp

        val out = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), Rect(0, 0, finalW, finalH), paint)
        if (out != bmp) bmp.recycle()
        return out
    }

    private fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (srcH > reqH || srcW > reqW) {
            val halfH = srcH / 2
            val halfW = srcW / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * DISABLED: PDF upload now handled by OutboxWorker only.
     */
    @Deprecated("Use OutboxWorker instead", ReplaceWith("OutboxWorker"))
    @Suppress("unused")
    private fun uploadToCloud(reportId: Long, pdfFile: File, reportWithDetails: ReportWithDetails) {
        if (reportStorage == null || reportsRemote == null) return
        android.util.Log.d("FT/REPORT_UPLOAD", "uploadToCloud ignored for id=$reportId file=${pdfFile.name}")
        @Suppress("UNUSED_VARIABLE")
        val ignored = ReportCloudDto(
            id = reportId.toString(),
            pdfPath = "",
            updatedAt = System.currentTimeMillis()
        )
        @Suppress("UNUSED_EXPRESSION")
        reportWithDetails
    }
}
