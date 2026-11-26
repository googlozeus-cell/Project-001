package com.example.topicnotes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class QipEmoticon(
    val code: String,
    val emoji: String,
    val title: String
)

object QipSmilesCatalog {
    val items: List<QipEmoticon> = listOf(
        QipEmoticon(":)", "😊", "Улыбка"),
        QipEmoticon(";)", "😉", "Подмигивание"),
        QipEmoticon(":D", "😄", "Смех"),
        QipEmoticon("*ROFL*", "🤣", "Ржач"),
        QipEmoticon(":(", "😞", "Грусть"),
        QipEmoticon(":'(", "😢", "Плач"),
        QipEmoticon(":-O", "😮", "Удивление"),
        QipEmoticon("8-)", "😎", "Крутой"),
        QipEmoticon(":-*", "😘", "Поцелуй"),
        QipEmoticon("*THUMBS UP*", "👍", "Класс"),
        QipEmoticon("*NO*", "👎", "Не годится"),
        QipEmoticon("*FACEPALM*", "🤦", "Рука-лицо"),
        QipEmoticon("*OK*", "👌", "Окей"),
        QipEmoticon("*STOP*", "🛑", "Стоп"),
        QipEmoticon("*IDEA*", "💡", "Идея"),
        QipEmoticon("*WARNING*", "⚠️", "Внимание"),
        QipEmoticon("*TOOL*", "🔧", "Ремонт"),
        QipEmoticon("*CAR*", "🚗", "Машина")
    )
}

object LocalMediaStorage {
    private fun getMediaDir(context: Context): File {
        val dir = File(context.filesDir, "media_store")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun copyAndCompressImage(context: Context, uri: Uri, maxSidePx: Int = 1024): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(input)
            input.close()
            if (original == null) return null

            val ratio = minOf(
                maxSidePx.toFloat() / original.width.coerceAtLeast(1),
                maxSidePx.toFloat() / original.height.coerceAtLeast(1),
                1.0f
            )
            val targetW = (original.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (original.height * ratio).toInt().coerceAtLeast(1)

            val scaled = if (ratio < 1.0f) {
                Bitmap.createScaledBitmap(original, targetW, targetH, true)
            } else {
                original
            }

            val fileName = "img_${UUID.randomUUID()}.jpg"
            val outFile = File(getMediaDir(context), fileName)
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            if (scaled != original) original.recycle()
            original.recycle()
            fileName
        } catch (_: Exception) {
            null
        }
    }

    fun loadImageBitmap(context: Context, fileName: String?): ImageBitmap? {
        if (fileName.isNullOrBlank()) return null
        val file = File(getMediaDir(context), fileName)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun deleteMediaFile(context: Context, fileName: String?) {
        if (fileName.isNullOrBlank()) return
        try {
            val file = File(getMediaDir(context), fileName)
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
    }
}
