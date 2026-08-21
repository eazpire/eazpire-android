package com.eazpire.creator.ui.creator

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File

internal object VideoClipperMedia {
    fun durationSeconds(context: Context, uri: Uri): Double {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            (raw?.toLongOrNull() ?: 0L) / 1000.0
        } catch (_: Exception) {
            0.0
        } finally {
            retriever.release()
        }
    }

    fun extractAudioRange(
        context: Context,
        uri: Uri,
        startUs: Long,
        endUs: Long,
        outFile: File,
    ): Boolean = copyTrackRange(context, uri, startUs, endUs, outFile, audioOnly = true)

    fun extractVideoRange(
        context: Context,
        uri: Uri,
        startUs: Long,
        endUs: Long,
        outFile: File,
    ): Boolean = copyTrackRange(context, uri, startUs, endUs, outFile, audioOnly = false)

    private fun copyTrackRange(
        context: Context,
        uri: Uri,
        startUs: Long,
        endUs: Long,
        outFile: File,
        audioOnly: Boolean,
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, uri, null)
            val indexes = mutableListOf<Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                val keep = if (audioOnly) mime.startsWith("audio/") else (
                    mime.startsWith("audio/") || mime.startsWith("video/")
                )
                if (keep) indexes += i
            }
            if (indexes.isEmpty()) return false
            if (outFile.exists()) outFile.delete()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val map = LinkedHashMap<Int, Int>()
            indexes.forEach { src ->
                extractor.selectTrack(src)
                map[src] = muxer.addTrack(extractor.getTrackFormat(src))
            }
            muxer.start()
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            indexes.forEach { src ->
                extractor.selectTrack(src)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                while (true) {
                    info.offset = 0
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size < 0) break
                    val time = extractor.sampleTime
                    if (time < 0 || time > endUs) {
                        extractor.advance()
                        if (time > endUs) break
                        continue
                    }
                    info.presentationTimeUs = time - startUs
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(map.getValue(src), buffer, info)
                    extractor.advance()
                }
                extractor.unselectTrack(src)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }
}
