package com.vroum.app.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.vroum.app.network.DiagnosticApiException
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

    const val MAX_DIMENSION_PX = 1024
    const val JPEG_QUALITY = 80

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
        val decodedBitmap = try {
            decodeAndOrientBitmap(context, uri)
        } catch (e: Exception) {
            throw DiagnosticApiException.UnreadablePhoto(e.localizedMessage ?: e.javaClass.simpleName)
        } ?: throw DiagnosticApiException.UnreadablePhoto("Impossible de décoder cette image (format non supporté ou fichier vide).")

        val scaled = scaleDownIfNeeded(decodedBitmap, MAX_DIMENSION_PX)
        if (scaled !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        val out = ByteArrayOutputStream()
        val compressed = scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val jpegBytes = out.toByteArray()
        if (!compressed || jpegBytes.isEmpty()) {
            scaled.recycle()
            throw DiagnosticApiException.UnreadablePhoto("Échec de la compression JPEG de la photo.")
        }

        val previewBitmap = scaleDownIfNeeded(scaled, 640)
        if (previewBitmap !== scaled) {
            scaled.recycle()
        }

        PreparedVehiclePhoto(
            jpegBytes = jpegBytes,
            previewBitmap = previewBitmap
        )
    }

    private fun decodeAndOrientBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = resolver.openInputStream(uri)
            ?: throw DiagnosticApiException.UnreadablePhoto("Impossible d'ouvrir le fichier image.")
        boundsInput.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
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
            }
            if (decoded != null) {
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
                return rotated
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return runCatching {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val size = info.size
                    val largest = max(size.width, size.height)
                    if (largest > MAX_DIMENSION_PX && largest > 0) {
                        val ratio = MAX_DIMENSION_PX.toFloat() / largest.toFloat()
                        val targetW = (size.width * ratio).roundToInt().coerceAtLeast(1)
                        val targetH = (size.height * ratio).roundToInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                }
            }.getOrNull()
        }

        return null
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int
    ): Int {
        var inSampleSize = 1
        if (width > maxDimension || height > maxDimension) {
            val halfW = width / 2
            val halfH = height / 2
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
