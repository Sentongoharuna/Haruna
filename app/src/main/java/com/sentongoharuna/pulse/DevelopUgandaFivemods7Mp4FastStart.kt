package com.sentongoharuna.pulse

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Makes a completed MP4 progressive-download friendly by moving its `moov`
 * atom ahead of media data and correcting chunk offsets. This runs only on a
 * derived delivery/annotated copy; a CameraX clean master is never rewritten.
 */
object DevelopUgandaFivemods7Mp4FastStart {

    private data class Box(
        val offset: Long,
        val size: Long,
        val headerSize: Int,
        val type: String
    )

    fun ensure(file: File) {
        require(file.exists() && file.length() > 0L) { "MP4 output is empty" }
        val boxes = readTopLevelBoxes(file)
        val moov = boxes.firstOrNull { it.type == "moov" } ?: error("MP4 moov atom is missing")
        val mdat = boxes.firstOrNull { it.type == "mdat" } ?: error("MP4 media atom is missing")

        if (moov.offset < mdat.offset) return
        require(moov.size in 8L..Int.MAX_VALUE.toLong()) { "MP4 moov atom is too large" }
        val ftyp = boxes.firstOrNull { it.type == "ftyp" } ?: error("MP4 ftyp atom is missing")

        val moovBytes = ByteArray(moov.size.toInt())
        RandomAccessFile(file, "r").use { input ->
            input.seek(moov.offset)
            input.readFully(moovBytes)
        }
        adjustChunkOffsets(moovBytes, moov.size)

        val temp = File(file.parentFile, "${file.name}.faststart")
        if (temp.exists()) temp.delete()
        try {
            FileOutputStream(temp).use { output ->
                boxes.forEach { box ->
                    when {
                        box.type == "moov" -> Unit
                        box === ftyp -> {
                            copyRange(file, output, box.offset, box.size)
                            output.write(moovBytes)
                        }
                        else -> copyRange(file, output, box.offset, box.size)
                    }
                }
            }
            require(isFastStart(temp)) { "Could not move MP4 metadata to the front" }
            if (!file.delete() || !temp.renameTo(file)) {
                error("Could not replace MP4 with fast-start copy")
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun isFastStart(file: File): Boolean {
        val boxes = readTopLevelBoxes(file)
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return false
        val mdat = boxes.firstOrNull { it.type == "mdat" } ?: return false
        return moov.offset < mdat.offset
    }

    private fun readTopLevelBoxes(file: File): List<Box> {
        val length = file.length()
        val boxes = mutableListOf<Box>()
        RandomAccessFile(file, "r").use { input ->
            var offset = 0L
            while (offset < length) {
                val box = readBox(input, offset, length)
                boxes += box
                offset += box.size
            }
            require(offset == length) { "MP4 top-level atom sizes do not match file length" }
        }
        return boxes
    }

    private fun readBox(input: RandomAccessFile, offset: Long, limit: Long): Box {
        require(offset + 8L <= limit) { "Truncated MP4 atom" }
        input.seek(offset)
        val size32 = input.readInt().toLong() and 0xFFFF_FFFFL
        val type = CharArray(4) { input.readUnsignedByte().toChar() }.concatToString()
        val headerSize: Int
        val size: Long
        when (size32) {
            0L -> {
                headerSize = 8
                size = limit - offset
            }
            1L -> {
                require(offset + 16L <= limit) { "Truncated extended MP4 atom" }
                headerSize = 16
                size = input.readLong()
            }
            else -> {
                headerSize = 8
                size = size32
            }
        }
        require(size >= headerSize && offset + size <= limit) { "Invalid MP4 atom size for $type" }
        return Box(offset, size, headerSize, type)
    }

    private fun adjustChunkOffsets(moov: ByteArray, delta: Long) {
        scanChildren(moov, 0, moov.size, delta)
    }

    private fun scanChildren(bytes: ByteArray, start: Int, end: Int, delta: Long) {
        var offset = start
        while (offset + 8 <= end) {
            val size32 = unsignedInt(bytes, offset)
            val type = fourCc(bytes, offset + 4)
            val headerSize: Int
            val sizeLong: Long
            when (size32) {
                0L -> {
                    headerSize = 8
                    sizeLong = (end - offset).toLong()
                }
                1L -> {
                    if (offset + 16 > end) return
                    headerSize = 16
                    sizeLong = unsignedLong(bytes, offset + 8)
                }
                else -> {
                    headerSize = 8
                    sizeLong = size32
                }
            }
            if (sizeLong < headerSize || sizeLong > (end - offset).toLong() || sizeLong > Int.MAX_VALUE) return
            val size = sizeLong.toInt()
            val boxEnd = offset + size
            when (type) {
                "stco" -> adjustStco(bytes, offset + headerSize, boxEnd, delta)
                "co64" -> adjustCo64(bytes, offset + headerSize, boxEnd, delta)
                else -> if (type in containerBoxes) {
                    val childStart =
                        if (type == "meta") offset + headerSize + 4 else offset + headerSize
                    if (childStart <= boxEnd) scanChildren(bytes, childStart, boxEnd, delta)
                }
            }
            offset = boxEnd
        }
    }

    private fun adjustStco(bytes: ByteArray, payloadStart: Int, boxEnd: Int, delta: Long) {
        if (payloadStart + 8 > boxEnd) return
        val count = unsignedInt(bytes, payloadStart + 4)
        val entriesStart = payloadStart + 8
        if (count > ((boxEnd - entriesStart) / 4).toLong()) return
        repeat(count.toInt()) { index ->
            val at = entriesStart + index * 4
            val updated = unsignedInt(bytes, at) + delta
            require(updated <= 0xFFFF_FFFFL) { "MP4 chunk offset overflow" }
            putUnsignedInt(bytes, at, updated)
        }
    }

    private fun adjustCo64(bytes: ByteArray, payloadStart: Int, boxEnd: Int, delta: Long) {
        if (payloadStart + 8 > boxEnd) return
        val count = unsignedInt(bytes, payloadStart + 4)
        val entriesStart = payloadStart + 8
        if (count > ((boxEnd - entriesStart) / 8).toLong()) return
        repeat(count.toInt()) { index ->
            val at = entriesStart + index * 8
            val current = unsignedLong(bytes, at)
            val updated = current + delta
            require(updated >= current) { "MP4 64-bit chunk offset overflow" }
            putUnsignedLong(bytes, at, updated)
        }
    }

    private fun copyRange(source: File, output: FileOutputStream, offset: Long, size: Long) {
        RandomAccessFile(source, "r").use { input ->
            input.seek(offset)
            var remaining = size
            val buffer = ByteArray(64 * 1024)
            while (remaining > 0L) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count <= 0) error("Unexpected end of MP4")
                output.write(buffer, 0, count)
                remaining -= count.toLong()
            }
        }
    }

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFFL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 8) or
            (bytes[offset + 3].toLong() and 0xFFL)

    private fun putUnsignedInt(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value shr 24).toByte()
        bytes[offset + 1] = (value shr 16).toByte()
        bytes[offset + 2] = (value shr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun unsignedLong(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        repeat(8) { index -> value = (value shl 8) or (bytes[offset + index].toLong() and 0xFFL) }
        return value
    }

    private fun putUnsignedLong(bytes: ByteArray, offset: Int, value: Long) {
        repeat(8) { index ->
            bytes[offset + 7 - index] = (value ushr (index * 8)).toByte()
        }
    }

    private fun fourCc(bytes: ByteArray, offset: Int): String =
        CharArray(4) { index -> bytes[offset + index].toInt().toChar() }.concatToString()

    private val containerBoxes = setOf(
        "moov", "trak", "mdia", "minf", "stbl", "edts", "dinf", "mvex", "udta", "meta", "ilst", "tref"
    )
}
