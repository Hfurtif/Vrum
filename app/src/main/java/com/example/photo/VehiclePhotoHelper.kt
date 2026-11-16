package com.example.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class PreparedVehiclePhoto(
    val jpegBytes: ByteArray,
    val previewBitmap: Bitmap
)

object VehiclePhotoHelper {

    private const val MAX_DIMENSION_PX = 1280
    private const val JPEG_QUALITY = 84

    fun createTempCameraUri(context: Context): Uri {
        val photosDir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(photosDir, "vroum_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun preparePhotoForGemini(
        context: Context,
        uri: Uri
    ): PreparedVehiclePhoto = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: throw IllegalStateException("Impossible de lire la photo sélectionnée.")

        val sampleSize = calculateInSampleSize(
            width = boundsOptions.outWidth,
            height = boundsOptions.outHeight,
            maxDimension = MAX_DIMENSION_PX
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Impossible de décoder cette image.")

        val exifOrientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val rotated = applyExifOrientation(decoded, exifOrientation)
        if (rotated !== decoded) {
            decoded.recycle()
        }

        val scaled = scaleDownIfNeeded(rotated, MAX_DIMENSION_PX)
        if (scaled !== rotated) {
            rotated.recycle()
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val jpegBytes = out.toByteArray()

        val previewBitmap = scaleDownIfNeeded(scaled, 640)
        if (previewBitmap !== scaled) {
            scaled.recycle()
        }

        PreparedVehiclePhoto(
            jpegBytes = jpegBytes,
            previewBitmap = previewBitmap
        )
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int
    ): Int {
        var inSampleSize = 1
        if (width > maxDimension || height > maxDimension) {
            var halfW = width / 2
            var halfH = height / 2
            while ((halfW / inSampleSize) >= maxDimension && (halfH / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension || largest <= 0) return bitmap
        val ratio = maxDimension.toFloat() / largest.toFloat()
        val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
