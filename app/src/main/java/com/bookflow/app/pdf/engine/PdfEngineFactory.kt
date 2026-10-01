package com.bookflow.app.pdf.engine

import android.content.Context

enum class EngineType {
    ANDROID_NATIVE,
    PDFIUM
}

class PdfEngineFactory(private val context: Context) {

    fun createEngine(type: EngineType = EngineType.ANDROID_NATIVE): PdfEngine {
        return when (type) {
            EngineType.ANDROID_NATIVE -> AndroidPdfRendererEngine(context)
            EngineType.PDFIUM -> PdfiumEngineAdapter(context)
        }
    }
}
