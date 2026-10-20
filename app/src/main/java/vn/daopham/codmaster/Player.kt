package vn.daopham.codmaster

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Trình phát so sánh nhiều nguồn, chuyển tức thì tại cùng vị trí:
 * ORIG = bản gốc, MASTER = bản master, VOCAL_ORIG = giọng gốc, VOCAL_NEW = giọng đã xử lý (chế độ 2 stem).
 */
class Player(private val onEnded: () -> Unit) {
    companion object {
        const val ORIG = 0
        const val MASTER = 1
        const val VOCAL_ORIG = 2
        const val VOCAL_NEW = 3
    }

    @Volatile var sel = MASTER
    @Volatile var masterGain = 1f        // hạ bản master về bằng độ lớn bản gốc khi bật "cân âm lượng"

    private var src: Decoded? = null
    @Volatile private var masterL: FloatArray? = null
    @Volatile private var masterR: FloatArray? = null
    @Volatile private var vocalOrig: Decoded? = null
    @Volatile private var vocalNewL: FloatArray? = null
    @Volatile private var vocalNewR: FloatArray? = null

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
        vocalOrig = null; vocalNewL = null; vocalNewR = null
        pausedAt = 0
    }

    fun setMaster(l: FloatArray?, r: FloatArray?) {
        masterL = l; masterR = r
    }

    fun setVocalOrig(d: Decoded?) { vocalOrig = d }
    fun setVocalNew(l: FloatArray?, r: FloatArray?) { vocalNewL = l; vocalNewR = r }

    fun has(i: Int) = when (i) {
        ORIG -> src != null
        MASTER -> masterL != null
        VOCAL_ORIG -> vocalOrig != null
        else -> vocalNewL != null
    }

    /** Nguồn thực sự đang phát (nguồn đã chọn chưa có thì về bản gốc). */
    fun effective(): Int = if (has(sel)) sel else ORIG

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
                val s = effective()
                val ml = masterL; val mr = masterR
                val vo = vocalOrig
                val vl = vocalNewL; val vr = vocalNewR
                if (s == MASTER && ml != null && mr != null) {
                    val g = masterGain
                    for (i in 0 until m) {
                        buf[2 * i] = ml[pos + i] * g
                        buf[2 * i + 1] = mr[pos + i] * g
                    }
                } else if (s == VOCAL_NEW && vl != null && vr != null) {
                    for (i in 0 until m) {
                        val p = pos + i
                        buf[2 * i] = if (p < vl.size) vl[p] else 0f
                        buf[2 * i + 1] = if (p < vr.size) vr[p] else 0f
                    }
                } else {
                    val dd = if (s == VOCAL_ORIG && vo != null) vo else d
                    val l = dd.left
                    val r = dd.right
                    val nn = dd.frames
                    for (i in 0 until m) {
                        val p = pos + i
                        buf[2 * i] = if (p < nn) l[p] * sc else 0f
                        buf[2 * i + 1] = if (p < nn) r[p] * sc else 0f
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
