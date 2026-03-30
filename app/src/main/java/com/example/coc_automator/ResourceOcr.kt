package com.example.coc_automator

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern

/**
 * OCR and number parsing aligned with extract_number_from_region() in automated_attack.py.
 */
object ResourceOcr {

    private val digitRun = Pattern.compile("[\\d\\s,]+")

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun extractNumberFromOcrText(ocrText: String): String? {
        val trimmed = ocrText.trim()
        if (trimmed.isEmpty()) return null
        val matcher = digitRun.matcher(trimmed)
        val cleanedNumbers = mutableListOf<String>()
        while (matcher.find()) {
            val chunk = matcher.group().replace(Regex("[\\s,]+"), "")
            if (chunk.length >= 4) cleanedNumbers.add(chunk)
        }
        if (cleanedNumbers.isEmpty()) return null
        val first = cleanedNumbers.first()
        return if (first.startsWith("0")) "1$first" else first
    }

    fun extractNumberFromBitmapSync(bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = Tasks.await(recognizer.process(image))
        val raw = result.text
        DebugLog.d("OCR raw (${bitmap.width}x${bitmap.height}): '${raw.trim()}'")
        if (raw.isBlank()) return null
        val parsed = extractNumberFromOcrText(raw)
            ?: raw.replace(Regex("[^0-9]"), "").takeIf { it.length >= 4 }
        DebugLog.d("OCR parsed number: $parsed")
        return parsed
    }
}
