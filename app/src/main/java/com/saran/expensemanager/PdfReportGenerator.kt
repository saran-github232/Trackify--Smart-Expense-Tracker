package com.saran.expensemanager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.util.Calendar

/** Simple text-table PDF reports, drawn with the platform's own PdfDocument — no PDF library. */
object PdfReportGenerator {
    private const val PAGE_WIDTH = 595 // A4 @ 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    fun generateMonthlyReport(context: Context): File? {
        val db = DatabaseHelper.getInstance(context)
        val cal = Calendar.getInstance()
        val monthName = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(cal.time)
        val total = db.getCurrentMonthTotal()
        val categories = db.getCurrentMonthCategoryTotals()
        if (total <= 0 && categories.isEmpty()) return null

        return renderPdf(context, "trackify_report_${monthName.replace(" ", "_")}.pdf") { canvas, paint, startY ->
            var y = startY
            y = drawTitle(canvas, paint, "Monthly Report — $monthName", y)
            y = drawLine(canvas, paint, "Total spent: ${CurrencyFormatter.format(context, total)}", y, bold = true)
            y = drawLine(canvas, paint, "Transactions: ${db.getTotalCount()}", y)
            y -= 12
            y = drawLine(canvas, paint, "Category breakdown:", y, bold = true)
            categories.forEach { (cat, amt) -> y = drawLine(canvas, paint, "  $cat — ${CurrencyFormatter.format(context, amt)}", y) }
            y
        }
    }

    fun generateYearlyReport(context: Context): File? {
        val db = DatabaseHelper.getInstance(context)
        val year = Calendar.getInstance()[Calendar.YEAR]
        val monthly = db.getMonthlyTotalsForYear(year)
        val yearTotal = monthly.sum()
        if (yearTotal <= 0) return null
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val highestIdx = monthly.indices.maxByOrNull { monthly[it] } ?: 0

        return renderPdf(context, "trackify_report_$year.pdf") { canvas, paint, startY ->
            var y = startY
            y = drawTitle(canvas, paint, "Yearly Report — $year", y)
            y = drawLine(canvas, paint, "Total spent: ${CurrencyFormatter.format(context, yearTotal)}", y, bold = true)
            y = drawLine(canvas, paint, "Average / month: ${CurrencyFormatter.format(context, yearTotal / 12)}", y)
            y = drawLine(canvas, paint, "Highest month: ${monthNames[highestIdx]} (${CurrencyFormatter.format(context, monthly[highestIdx])})", y)
            y -= 12
            y = drawLine(canvas, paint, "Month by month:", y, bold = true)
            monthNames.forEachIndexed { i, name -> y = drawLine(canvas, paint, "  $name — ${CurrencyFormatter.format(context, monthly[i])}", y) }
            y
        }
    }

    fun shareReport(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.export_report)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun renderPdf(context: Context, fileName: String, draw: (Canvas, Paint, Float) -> Float): File {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val paint = Paint().apply { isAntiAlias = true; textSize = 12f }
        draw(page.canvas, paint, MARGIN + 24f)
        document.finishPage(page)

        val file = File(context.cacheDir, fileName)
        document.writeTo(file.outputStream())
        document.close()
        return file
    }

    private fun drawTitle(canvas: Canvas, paint: Paint, text: String, y: Float): Float {
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText(text, MARGIN, y, paint)
        paint.isFakeBoldText = false
        paint.textSize = 12f
        return y + 32f
    }

    private fun drawLine(canvas: Canvas, paint: Paint, text: String, y: Float, bold: Boolean = false): Float {
        paint.isFakeBoldText = bold
        canvas.drawText(text, MARGIN, y, paint)
        paint.isFakeBoldText = false
        return y + 20f
    }
}
