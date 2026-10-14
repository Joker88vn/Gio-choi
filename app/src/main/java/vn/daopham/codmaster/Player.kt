package vn.daopham.codmaster

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Trình phát so sánh A/B: chuyển giữa bản gốc và bản master tức thì, cùng vị trí.
 * Có tuỳ chọn cân âm lượng bản gốc cho bằng bản master để so sánh công bằng.
 */
class Player(private val onEnded: () -> Unit) {
    @Volatile var useMaster = true
    @Volatile var masterGain = 1f        // hạ bản master về bằng độ lớn bản gốc khi bật "cân âm lượng"

    private var src: Decoded? = null
    @Volatile private var masterL: FloatArray? = null
    @Volatile private var masterR: FloatArray? = null

    private var thread: Thread? = null
    @Volatile private var running = false
    private var track: AudioTrack? = null
    @Volatile private var startFrame = 0
    @Volatile private var pausedAt = 0

    val isPlaying get() = running
    val frames get() = src?.frames ?: 0
    val sampleRate get() = src?.sampleRate ?: 44100

    fun setSource(d: Decoded?) {
        stop()
        src = d
        masterL = null; masterR = null
        pausedAt = 0
    }

    fun setMaster(l: FloatArray?, r: FloatArray?) {
        masterL = l; masterR = r
    }

    fun hasMaster() = masterL != null

    /** Vị trí hiện tại (khung mẫu). */
    fun position(): Int {
        val t = track
        if (!running || t == null) return pausedAt
        return try {
            (startFrame + t.playbackHeadPosition).coerceAtMost(frames)
        } catch (_: Exception) {
            pausedAt
        }
    }

    fun play() {
        val d = src ?: return
        if (running) return
        if (pausedAt >= d.frames - 1) pausedAt = 0
        val sr = d.sampleRate
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, 2048 * 2 * 4 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        startFrame = pausedAt
        running = true
        t.play()
        val th = Thread {
            val block = 1024
            val buf = FloatArray(block * 2)
            var pos = startFrame
            val n = d.frames
            val sc = 1f / 32768f
            while (running && pos < n) {
                val m = minOf(block, n - pos)
                val ml = masterL
                val mr = masterR
                if (useMaster && ml != null && mr != null) {
                    val g = masterGain
                    for (i in 0 until m) {
                        buf[2 * i] = ml[pos + i] * g
                        buf[2 * i + 1] = mr[pos + i] * g
                    }
                } else {
                    val l = d.left
                    val r = d.right
                    for (i in 0 until m) {
                        buf[2 * i] = l[pos + i] * sc
                        buf[2 * i + 1] = r[pos + i] * sc
                    }
                }
                val w = t.write(buf, 0, m * 2, AudioTrack.WRITE_BLOCKING)
                if (w < 0) break
                pos += m
            }
            if (running && pos >= n) {
                // chờ phát hết phần đệm
                try {
                    t.stop()
                    var guard = 0
                    while (running && t.playbackHeadPosition < n - startFrame && guard++ < 200) Thread.sleep(10)
                } catch (_: Exception) {}
                running = false
                pausedAt = 0
                try { t.release() } catch (_: Exception) {}
                onEnded()
            }
        }
        th.name = "player"
        thread = th
        th.start()
    }

    fun pause() {
        if (!running) return
        pausedAt = position()
        stopInternal()
    }

    fun stop() {
        pause()
    }

    fun seek(frame: Int) {
        val was = running
        if (was) stopInternal()
        pausedAt = frame.coerceIn(0, maxOf(0, frames - 1))
        if (was) play()
    }

    private fun stopInternal() {
        running = false
        val t = track
        try { t?.pause(); t?.flush() } catch (_: Exception) {}
        try { thread?.join(500) } catch (_: Exception) {}
        try { t?.stop() } catch (_: Exception) {}
        try { t?.release() } catch (_: Exception) {}
        track = null
        thread = null
    }
}
