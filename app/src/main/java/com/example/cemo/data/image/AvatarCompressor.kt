package com.example.cemo.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Turns whatever the photo picker hands back — JPEG, PNG, WEBP, HEIC, BMP, GIF —
 * into a small WEBP suitable for an avatar.
 *
 * Decoding happens through [BitmapFactory], so the input extension is irrelevant:
 * anything the platform can decode is accepted. The output is always WEBP because
 * re-encoding is what makes the file small, and WEBP beats JPEG at this size while
 * still supporting transparency (unlike JPEG, which would black out a PNG's alpha).
 */
object AvatarCompressor {
    private const val TAG = "AvatarCompressor"

    /** Longest edge of the stored avatar. The UI renders it at 120dp. */
    private const val MAX_DIMENSION = 512
    private const val QUALITY = 80

    const val CONTENT_TYPE = "image/webp"
    const val FILE_EXTENSION = "webp"

    suspend fun compress(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("That file isn't an image we can read")
        }

        // Downsample while decoding so a 12MP photo never lands in memory at full size.
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = openStream(context, uri).use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IOException("That image couldn't be decoded")

        // Re-encoding drops the EXIF block, so bake the rotation into the pixels
        // first — otherwise camera shots would come out sideways.
        val scaled = scaleDown(applyOrientation(context, uri, decoded))

        val output = ByteArrayOutputStream()
        if (!scaled.compress(webpFormat(), QUALITY, output)) {
            throw IOException("That image couldn't be compressed")
        }
        Log.d(
            TAG,
            "Compressed ${bounds.outWidth}x${bounds.outHeight} -> " +
                "${scaled.width}x${scaled.height}, ${output.size() / 1024}KB"
        )
        output.toByteArray()
    }

    private fun openStream(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IOException("Couldn't open the selected image")

    /** Largest power-of-two downscale that still leaves both edges above the target. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_DIMENSION && height / (sample * 2) >= MAX_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    private fun applyOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            openStream(context, uri).use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read EXIF orientation, assuming upright", e)
            ExifInterface.ORIENTATION_NORMAL
        }

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

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longestEdge = maxOf(bitmap.width, bitmap.height)
        if (longestEdge <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / longestEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
}
