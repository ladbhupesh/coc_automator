package com.example.coc_automator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

interface OcrCaptureSaver {
    suspend fun saveGoldElixirCrops(
        gold: Bitmap,
        elixir: Bitmap,
        goldText: String?,
        elixirText: String?,
    )
}

data class OcrCaptureEntry(
    val id: String,
    val timeMs: Long,
    val goldFile: File,
    val elixirFile: File,
    val goldText: String?,
    val elixirText: String?,
)

object OcrCaptureStore {

    private const val ROOT = "ocr_captures"
    private const val META = "meta.json"
    private const val GOLD = "gold.png"
    private const val ELIXIR = "elixir.png"

    private fun rootDir(context: Context): File = File(context.applicationContext.filesDir, ROOT)

    suspend fun saveCapture(
        context: Context,
        gold: Bitmap,
        elixir: Bitmap,
        goldText: String?,
        elixirText: String?,
    ) = withContext(Dispatchers.IO) {
        val id = System.currentTimeMillis().toString()
        val dir = File(rootDir(context), id).apply { mkdirs() }
        FileOutputStream(File(dir, GOLD)).use { gold.compress(Bitmap.CompressFormat.PNG, 92, it) }
        FileOutputStream(File(dir, ELIXIR)).use { elixir.compress(Bitmap.CompressFormat.PNG, 92, it) }
        val meta = JSONObject().apply {
            put("timeMs", id.toLong())
            put("gold", goldText ?: JSONObject.NULL)
            put("elixir", elixirText ?: JSONObject.NULL)
        }
        File(dir, META).writeText(meta.toString())
    }

    fun listCaptures(context: Context): List<OcrCaptureEntry> {
        val root = rootDir(context)
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val metaFile = File(dir, META)
                val g = File(dir, GOLD)
                val e = File(dir, ELIXIR)
                if (!g.isFile || !e.isFile) return@mapNotNull null
                val timeMs = dir.name.toLongOrNull() ?: 0L
                val (goldText, elixirText) = if (metaFile.isFile) {
                    runCatching {
                        val o = JSONObject(metaFile.readText())
                        val gt = if (!o.has("gold") || o.isNull("gold")) null else o.getString("gold")
                        val ex = if (!o.has("elixir") || o.isNull("elixir")) null else o.getString("elixir")
                        gt to ex
                    }.getOrNull() ?: (null to null)
                } else {
                    null to null
                }
                OcrCaptureEntry(dir.name, timeMs, g, e, goldText, elixirText)
            }
            ?.sortedByDescending { it.timeMs }
            ?: emptyList()
    }

    fun clearAll(context: Context) {
        val root = rootDir(context)
        if (root.isDirectory) {
            root.listFiles()?.forEach { f ->
                if (f.isDirectory) f.deleteRecursively()
            }
        }
    }

    fun decodeBitmap(file: File): Bitmap? =
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
}
