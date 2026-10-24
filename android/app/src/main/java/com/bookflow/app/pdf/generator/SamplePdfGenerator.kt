package com.bookflow.app.pdf.generator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SamplePdfGenerator {

    /**
     * Generates the "Aircraft Systems" PDF file matching the screenshot
     * Annotated Aircraft Systems Textbook Interface.png
     */
    suspend fun generateAircraftTextbook(context: Context): File = withContext(Dispatchers.IO) {
        val booksDir = File(context.filesDir, "books")
        if (!booksDir.exists()) booksDir.mkdirs()

        val destFile = File(booksDir, "aircraft_systems_textbook.pdf")
        if (destFile.exists() && destFile.length() > 5000) {
            return@withContext destFile
        }

        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        // PAGE 1: Chapter 3 Aircraft Systems (Page 126 of 1245)
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Chapter 3 Tag (Purple / Indigo)
            paint.color = Color.parseColor("#4F46E5")
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("Chapter 3", 45f, 75f, paint)

            // Main Chapter Title
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 32f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            canvas.drawText("Aircraft Systems", 45f, 115f, paint)

            // Key Points Callout Card on Top Right (Light Blue/Purple container)
            val keyCardLeft = 370f
            val keyCardTop = 60f
            val keyCardRight = 550f
            val keyCardBottom = 210f

            paint.color = Color.parseColor("#F1F5FD")
            canvas.drawRoundRect(RectF(keyCardLeft, keyCardTop, keyCardRight, keyCardBottom), 12f, 12f, paint)

            paint.color = Color.parseColor("#4F46E5")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("Key Points", keyCardLeft + 16f, keyCardTop + 24f, paint)

            paint.color = Color.parseColor("#334155")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            val bullets = listOf(
                "• Provide power and control",
                "• Ensure safe operation",
                "• Work under all conditions",
                "• Integrated with aircraft structure",
                "• Regular maintenance is essential"
            )
            var bulletY = keyCardTop + 48f
            bullets.forEach {
                canvas.drawText(it, keyCardLeft + 16f, bulletY, paint)
                bulletY += 22f
            }

            // Section 3.1 Introduction
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            canvas.drawText("3.1  Introduction", 45f, 155f, paint)

            paint.color = Color.parseColor("#1E293B")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

            val p1 = "The aircraft system includes all the mechanical, electrical, hydraulic, pneumatic and electronic systems necessary for the safe and efficient operation of the aircraft. These systems work together to provide power, control, navigation, communication, environmental control and other essential functions."
            drawWrappedText(canvas, p1, 45f, 180f, 310f, 16f, paint)

            // Yellow Highlight block on text
            val highlightText = "The primary function of the aircraft systems is to ensure a continuous and reliable supply of power and services under all operating conditions."
            paint.color = Color.parseColor("#FFF59D") // Bright soft yellow
            canvas.drawRect(45f, 245f, 550f, 280f, paint)

            paint.color = Color.parseColor("#0F172A")
            drawWrappedText(canvas, highlightText, 48f, 260f, 500f, 16f, paint)

            // Section Turbofan Engine Diagram (Figure 3.1)
            drawTurbofanEngineDiagram(canvas, paint, 45f, 305f)

            // Caption
            paint.color = Color.parseColor("#1E293B")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            canvas.drawText("Figure 3.1  Typical Turbofan Engine Section View", 160f, 570f, paint)

            // Section 3.2 Power Plant
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            canvas.drawText("3.2  Power Plant", 45f, 605f, paint)

            paint.color = Color.parseColor("#1E293B")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            val p2 = "The power plant consists of an engine or engines, associated systems and installations. In modern aircraft, gas turbine engines are widely used due to their high power-to-weight ratio, reliability and operational efficiency."
            drawWrappedText(canvas, p2, 45f, 630f, 505f, 16f, paint)

            // Pink Highlight on second sentence
            paint.color = Color.parseColor("#FBCFE8") // Soft Pink highlight
            canvas.drawRect(75f, 680f, 490f, 700f, paint)

            paint.color = Color.parseColor("#E11D48")
            paint.textSize = 13f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("★", 52f, 694f, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            canvas.drawText("The engine converts chemical energy from fuel into mechanical energy, which", 78f, 694f, paint)
            canvas.drawText("is then used to produce thrust and to drive various aircraft systems.", 45f, 715f, paint)

            // Red Handwritten Note: "Important for exam!" with curved arrow
            paint.color = Color.parseColor("#DC2626")
            paint.textSize = 16f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            canvas.save()
            canvas.rotate(-10f, 490f, 700f)
            canvas.drawText("Important", 450f, 710f, paint)
            canvas.drawText("for exam!", 465f, 728f, paint)
            canvas.restore()

            // Curved arrow
            paint.color = Color.parseColor("#DC2626")
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            val arrowPath = Path().apply {
                moveTo(505f, 735f)
                quadTo(520f, 755f, 485f, 765f)
            }
            canvas.drawPath(arrowPath, paint)
            paint.style = Paint.Style.FILL

            pdfDocument.finishPage(page)
        }

        // PAGE 2: Additional Systems details
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.parseColor("#4F46E5")
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            canvas.drawText("Chapter 3 • Continued", 45f, 75f, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            canvas.drawText("3.3  Hydraulic Distribution Architecture", 45f, 115f, paint)

            pdfDocument.finishPage(page)
        }

        FileOutputStream(destFile).use { output ->
            pdfDocument.writeTo(output)
        }
        pdfDocument.close()

        destFile
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint
    ) {
        val words = text.split(" ")
        var currentLine = StringBuilder()
        var y = startY

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)
            if (width > maxWidth) {
                canvas.drawText(currentLine.toString(), x, y, paint)
                currentLine = StringBuilder(word)
                y += lineHeight
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine.toString(), x, y, paint)
        }
    }

    private fun drawTurbofanEngineDiagram(canvas: Canvas, paint: Paint, startX: Float, startY: Float) {
        // Diagram frame container
        paint.color = Color.parseColor("#F8FAFC")
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(startX, startY, startX + 505f, startY + 245f), 12f, 12f, paint)

        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(startX, startY, startX + 505f, startY + 245f), 12f, 12f, paint)

        // Draw jet engine graphic representation
        paint.style = Paint.Style.FILL
        val engineX = startX + 180f
        val engineY = startY + 120f

        // Nacelle & Casing
        paint.color = Color.parseColor("#334155")
        canvas.drawRoundRect(RectF(engineX - 100f, engineY - 50f, engineX + 170f, engineY + 50f), 20f, 20f, paint)

        // Core Combustion & Flame (Orange/Yellow glow)
        paint.color = Color.parseColor("#F97316")
        canvas.drawOval(RectF(engineX + 70f, engineY - 35f, engineX + 210f, engineY + 35f), paint)
        paint.color = Color.parseColor("#FBBF24")
        canvas.drawOval(RectF(engineX + 90f, engineY - 20f, engineX + 180f, engineY + 20f), paint)

        // Fan Blades & Spinner (Front)
        paint.color = Color.parseColor("#0284C7")
        canvas.drawCircle(engineX - 80f, engineY, 40f, paint)
        paint.color = Color.parseColor("#38BDF8")
        canvas.drawCircle(engineX - 80f, engineY, 20f, paint)

        // Turbine Stages & Compressor Discs
        paint.color = Color.parseColor("#64748B")
        for (i in 0..6) {
            canvas.drawRect(engineX - 30f + (i * 15f), engineY - 35f, engineX - 25f + (i * 15f), engineY + 35f, paint)
        }

        // Callout boxes with pointers (Matching Screenshot Figure 3.1)
        drawCalloutBox(canvas, paint, "Fan", "Draws in air and\nincreases its pressure.", startX + 15f, startY + 15f, engineX - 70f, engineY - 30f)
        drawCalloutBox(canvas, paint, "Compressor", "Compresses the air\nto a high pressure.", startX + 140f, startY + 15f, engineX + 10f, engineY - 30f)
        drawCalloutBox(canvas, paint, "Combustion Chamber", "Fuel is sprayed and\nburned with compressed air...", startX + 310f, startY + 15f, engineX + 110f, engineY - 20f)
        drawCalloutBox(canvas, paint, "Turbine", "Extracts energy from hot\ngases to drive the compressor...", startX + 100f, startY + 185f, engineX + 90f, engineY + 30f)
        drawCalloutBox(canvas, paint, "Exhaust", "Expels the remaining\ngases at high velocity...", startX + 330f, startY + 185f, engineX + 190f, engineY + 20f)
    }

    private fun drawCalloutBox(
        canvas: Canvas,
        paint: Paint,
        title: String,
        desc: String,
        boxX: Float,
        boxY: Float,
        targetX: Float,
        targetY: Float
    ) {
        val boxWidth = 145f
        val boxHeight = 46f

        // Callout box container
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(boxX, boxY, boxX + boxWidth, boxY + boxHeight), 6f, 6f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#CBD5E1")
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(boxX, boxY, boxX + boxWidth, boxY + boxHeight), 6f, 6f, paint)

        // Pointer Line
        paint.color = Color.parseColor("#0284C7")
        paint.strokeWidth = 1.5f
        val sourceX = if (boxX < targetX) boxX + boxWidth else boxX
        val sourceY = if (boxY < targetY) boxY + boxHeight else boxY
        canvas.drawLine(sourceX, sourceY, targetX, targetY, paint)
        canvas.drawCircle(targetX, targetY, 2.5f, paint)

        // Text
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText(title, boxX + 8f, boxY + 15f, paint)

        paint.color = Color.parseColor("#475569")
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val lines = desc.split("\n")
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, boxX + 8f, boxY + 27f + (i * 11f), paint)
        }
    }
}
