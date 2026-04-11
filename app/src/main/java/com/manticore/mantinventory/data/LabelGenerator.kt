package com.manticore.mantinventory.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.File
import java.io.FileOutputStream

object LabelGenerator {
    fun generateQr(content: String, size: Int = 512): Bitmap {
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        return matrix.toBitmap()
    }

    fun saveLabelPng(
        context: Context,
        labelCode: String,
        deepLink: String
    ): String {
        val bitmap = generateQr(deepLink)
        val dir = File(context.filesDir, "labels").apply { mkdirs() }
        val file = File(dir, "box_${labelCode.replace("[^A-Za-z0-9_-]".toRegex(), "_")}.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return file.absolutePath
    }

    private fun BitMatrix.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
