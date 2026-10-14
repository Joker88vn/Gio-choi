package vn.daopham.codmaster

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Bài nhạc đã giải mã, lưu dạng 16-bit stereo để tiết kiệm bộ nhớ. */
class Decoded(
    val sampleRate: Int,
    val left: ShortArray,
    val right: ShortArray,
    val frames: Int,
    val srcChannels: Int,
)

class CancelledException : RuntimeException("Đã huỷ")

object AudioIO {

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val s = c.getString(0)
                    if (!s.isNullOrBlank()) return s
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "bai-nhac"
    }

    /** Giải mã mọi định dạng Android hỗ trợ (MP3, M4A/AAC, WAV, FLAC, OGG, OPUS…) thành PCM. */
    fun decode(ctx: Context, uri: Uri, progress: (Float) -> Boolean): Decoded {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
        } catch (e: Exception) {
            ex.release()
            throw IOException("Không mở được file này.")
        }
        var track = -1
        for (i in 0 until ex.trackCount) {
            val m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (m.startsWith("audio/")) { track = i; break }
        }
        if (track < 0) {
            ex.release()
            throw IOException("File không có luồng âm thanh.")
        }
        val fmt = ex.getTrackFormat(track)
        ex.selectTrack(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val srcCh = ch
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durUs > 30L * 60 * 1_000_000) {
            ex.release()
            throw IOException("Bài dài quá 30 phút, app chưa hỗ trợ.")
        }

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            ex.release()
            throw IOException("Máy không giải mã được định dạng này ($mime).")
        }

        var cap = if (durUs > 0) ((durUs / 1_000_000.0) * sr).toInt() + sr else sr * 240
        var left = ShortArray(cap)
        var right = ShortArray(cap)
        var n = 0
        var enc = AudioFormat.ENCODING_PCM_16BIT
        val info = MediaCodec.BufferInfo()
        var inEos = false
        var outEos = false
        var lastTick = 0L

        try {
            codec.configure(fmt, null, null, 0)
            codec.start()
            while (!outEos) {
                if (!inEos) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            codec.queueInputBuffer(ii, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) enc = of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else if (oi >= 0) {
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(oi)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val bb = ob.slice().order(ByteOrder.LITTLE_ENDIAN)
                        val bps = when (enc) {
                            AudioFormat.ENCODING_PCM_FLOAT -> 4
                            AudioFormat.ENCODING_PCM_8BIT -> 1
                            21 /* PCM_24BIT_PACKED */ -> 3
                            22 /* PCM_32BIT */ -> 4
                            else -> 2
                        }
                        val frames = info.size / (bps * ch)
                        if (n + frames > cap) {
                            cap = maxOf(cap * 3 / 2, n + frames + sr)
                            left = left.copyOf(cap)
                            right = right.copyOf(cap)
                        }
                        for (f in 0 until frames) {
                            val base = f * ch * bps
                            val l = readSample(bb, base, enc, bps)
                            val r = if (ch > 1) readSample(bb, base + bps, enc, bps) else l
                            left[n + f] = l
                            right[n + f] = r
                        }
                        n += frames
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outEos = true
                    val now = System.currentTimeMillis()
                    if (now - lastTick > 120) {
                        lastTick = now
                        val f = if (durUs > 0) (ex.sampleTime.coerceAtLeast(0) / durUs.toFloat()) else 0f
                        if (!progress(if (inEos) 1f else f.coerceIn(0f, 1f))) throw CancelledException()
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            ex.release()
        }
        if (n < sr / 10) throw IOException("File quá ngắn hoặc không giải mã được.")
        return Decoded(sr, left, right, n, srcCh)
    }

    private fun readSample(b: ByteBuffer, off: Int, enc: Int, bps: Int): Short = when (bps) {
        2 -> b.getShort(off)
        1 -> (((b.get(off).toInt() and 0xFF) - 128) shl 8).toShort()
        3 -> {
            val v = (b.get(off).toInt() and 0xFF) or ((b.get(off + 1).toInt() and 0xFF) shl 8) or (b.get(off + 2).toInt() shl 16)
            (v shr 8).toShort()
        }
        else -> if (enc == AudioFormat.ENCODING_PCM_FLOAT) {
            val v = (b.getFloat(off) * 32767f).roundToInt()
            v.coerceIn(-32768, 32767).toShort()
        } else {
            (b.getInt(off) shr 16).toShort()
        }
    }

    /** Ghi WAV PCM 16 hoặc 24 bit. Bản 16-bit có dither TPDF để giữ chi tiết ở mức nhỏ. */
    fun writeWav(
        os: OutputStream,
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        sampleRate: Int,
        bits: Int,
        progress: (Float) -> Unit,
    ) {
        val bytesPer = bits / 8
        val dataLen = frames.toLong() * 2 * bytesPer
        val out = BufferedOutputStream(os, 1 shl 16)
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt((36 + dataLen).toInt())
        h.put("WAVE".toByteArray()).put("fmt ".toByteArray())
        h.putInt(16).putShort(1).putShort(2).putInt(sampleRate)
        h.putInt(sampleRate * 2 * bytesPer).putShort((2 * bytesPer).toShort()).putShort(bits.toShort())
        h.put("data".toByteArray()).putInt(dataLen.toInt())
        out.write(h.array())

        val chunk = 4096
        val buf = ByteArray(chunk * 2 * bytesPer)
        val rnd = java.util.Random(7)
        var i = 0
        while (i < frames) {
            val m = minOf(chunk, frames - i)
            var p = 0
            for (k in 0 until m) {
                for (c in 0..1) {
                    val x = if (c == 0) left[i + k] else right[i + k]
                    if (bits == 16) {
                        val d = (rnd.nextFloat() - rnd.nextFloat())   // TPDF ±1 LSB
                        val v = (x * 32767f + d).roundToInt().coerceIn(-32768, 32767)
                        buf[p++] = v.toByte(); buf[p++] = (v shr 8).toByte()
                    } else {
                        val v = (x * 8388607f).roundToInt().coerceIn(-8388608, 8388607)
                        buf[p++] = v.toByte(); buf[p++] = (v shr 8).toByte(); buf[p++] = (v shr 16).toByte()
                    }
                }
            }
            out.write(buf, 0, p)
            i += m
            if ((i / chunk) % 64 == 0) progress(i / frames.toFloat())
        }
        out.flush()
        progress(1f)
    }
}
