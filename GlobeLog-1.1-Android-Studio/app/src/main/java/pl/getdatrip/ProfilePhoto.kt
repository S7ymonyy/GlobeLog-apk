package pl.getdatrip

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import com.google.firebase.firestore.Blob
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private const val PHOTO_SIZE = 160
private const val MAX_PHOTO_BYTES = 24 * 1024
internal const val MAX_PROFILE_NAME_LENGTH = 80

internal fun loadProfilePhoto(resolver: ContentResolver, uri: Uri): Blob? {
    val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val size = info.size
            val scale = minOf(1f, (PHOTO_SIZE * 2f) / maxOf(size.width, size.height))
            decoder.setTargetSize((size.width * scale).roundToInt().coerceAtLeast(1),
                (size.height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            while (longest / inSampleSize.coerceAtLeast(1) > PHOTO_SIZE * 4) {
                inSampleSize = inSampleSize.coerceAtLeast(1) * 2
            }
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    }

    val thumbnail = ThumbnailUtils.extractThumbnail(decoded, PHOTO_SIZE, PHOTO_SIZE)
    if (thumbnail !== decoded) decoded.recycle()
    try {
        val output = ByteArrayOutputStream()
        for (quality in listOf(85, 65, 45, 30)) {
            output.reset()
            if (thumbnail.compress(Bitmap.CompressFormat.JPEG, quality, output) && output.size() <= MAX_PHOTO_BYTES) {
                return Blob.fromBytes(output.toByteArray())
            }
        }
        return null
    } finally {
        thumbnail.recycle()
    }
}

internal fun decodeProfilePhoto(photo: Blob?): Bitmap? {
    val bytes = photo?.toBytes() ?: return null
    if (bytes.size !in 1..MAX_PHOTO_BYTES) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth != PHOTO_SIZE || bounds.outHeight != PHOTO_SIZE || bounds.outMimeType != "image/jpeg") return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
