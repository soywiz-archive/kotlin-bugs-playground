package test

import kotlin.coroutines.experimental.*
import kotlin.math.*

// Copied from:
// https://github.com/korlibs/korlibs/commit/7c451ceb85b8c72dfa92a9952713c5e58a90c1d2

fun Byte.toUnsigned() = this.toInt() and 0xFF
fun Int.toUnsigned() = this.toLong() and 0xFFFFFFFFL
fun Int.signExtend(bits: Int) = (this shl (32 - bits)) shr (32 - bits)
fun Int.signExtend8(): Int = this shl 24 shr 24
fun Int.signExtend16(): Int = this shl 16 shr 16
inline fun Int.mask(): Int = (1 shl this) - 1
fun Int.extract(offset: Int, count: Int): Int = (this ushr offset) and count.mask()
fun Int.extract(offset: Int): Boolean = ((this ushr offset) and 1) != 0
fun Int.extract8(offset: Int): Int = (this ushr offset) and 0xFF
fun Int.extract16(offset: Int): Int = (this ushr offset) and 0xFFFF

fun unsupported(): Nothing = throw UnsupportedOperationException()
fun Int.reverseBytes(): Int {
    val v0 = ((this ushr 0) and 0xFF)
    val v1 = ((this ushr 8) and 0xFF)
    val v2 = ((this ushr 16) and 0xFF)
    val v3 = ((this ushr 24) and 0xFF)
    return (v0 shl 24) or (v1 shl 16) or (v2 shl 8) or (v3 shl 0)
}

abstract class Charset(val name: String) {
    abstract fun encode(out: ByteArrayBuilder, src: CharSequence, start: Int = 0, end: Int = src.length)
    abstract fun decode(out: StringBuilder, src: ByteArray, start: Int = 0, end: Int = src.size)
}

object ASCII : Charset("ASCII") {
    override fun encode(out: ByteArrayBuilder, src: CharSequence, start: Int, end: Int) {
        for (n in start until end) out.append(src[n].toByte())
    }

    override fun decode(out: StringBuilder, src: ByteArray, start: Int, end: Int) {
        for (n in start until end) out.append(src[n].toChar())
    }
}

fun String.toByteArray(charset: Charset): ByteArray {
    val out = ByteArrayBuilder()
    charset.encode(out, this)
    return out.toByteArray()
}

fun ByteArray.toString(charset: Charset): String {
    val out = StringBuilder()
    charset.decode(out, this)
    return out.toString()
}

interface AsyncCloseable {
    suspend fun close()

    companion object {
        val DUMMY = object : AsyncCloseable {
            suspend override fun close() = Unit
        }
    }
}

interface AsyncBaseStream : AsyncCloseable {
}

interface AsyncInputStream : AsyncBaseStream {
    suspend fun read(buffer: ByteArray, offset: Int, len: Int): Int
}

interface AsyncOutputStream : AsyncBaseStream {
    suspend fun write(buffer: ByteArray, offset: Int = 0, len: Int = buffer.size - offset)
}

interface AsyncGetPositionStream : AsyncBaseStream {
    suspend fun getPosition(): Long = throw UnsupportedOperationException()
}

interface AsyncGetLengthStream : AsyncBaseStream {
    suspend fun getLength(): Long = throw UnsupportedOperationException()
}

interface AsyncInputWithLengthStream : AsyncInputStream, AsyncGetPositionStream, AsyncGetLengthStream

suspend fun AsyncInputWithLengthStream.getAvailable() = this.getLength() - this.getPosition()
suspend fun AsyncInputWithLengthStream.hasAvailable() = getAvailable() > 0

suspend fun AsyncInputStream.readExact(buffer: ByteArray, offset: Int, len: Int) {
    var remaining = len
    var coffset = offset
    val reader = this
    while (remaining > 0) {
        val read = reader.read(buffer, coffset, remaining)
        if (read < 0) break
        if (read == 0) error("Not enough data. Expected=$len, Read=${len - remaining}, Remaining=$remaining")
        coffset += read
        remaining -= read
    }
}

suspend fun AsyncInputStream.readBytesExact(len: Int): ByteArray = ByteArray(len).apply { readExact(this, 0, len) }
private suspend fun AsyncInputStream.readSmallTempExact(len: Int): ByteArray = readBytesExact(len)
suspend fun AsyncInputStream.readU8(): Int = readSmallTempExact(1).readU8(0)

suspend fun AsyncInputStream.readS8(): Int = readSmallTempExact(1).readS8(0)
suspend fun AsyncInputStream.readU16_le(): Int = readSmallTempExact(2).readU16_le(0)
suspend fun AsyncInputStream.readU32_le(): Long = readSmallTempExact(4).readU32_le(0)
suspend fun AsyncInputStream.readS16_le(): Int = readSmallTempExact(2).readS16_le(0)
suspend fun AsyncInputStream.readS24_le(): Int = readSmallTempExact(3).readS24_le(0)
suspend fun AsyncInputStream.readS32_le(): Int = readSmallTempExact(4).readS32_le(0)
suspend fun AsyncInputStream.readU16_be(): Int = readSmallTempExact(2).readU16_be(0)
suspend fun AsyncInputStream.readU32_be(): Long = readSmallTempExact(4).readU32_be(0)
suspend fun AsyncInputStream.readS16_be(): Int = readSmallTempExact(2).readS16_be(0)
suspend fun AsyncInputStream.readS32_be(): Int = readSmallTempExact(4).readS32_be(0)


private inline fun ByteArray._read8(o: Int): Int = this[o].toInt()

private inline fun ByteArray._read16_le(o: Int): Int = (readU8(o + 0) shl 0) or (readU8(o + 1) shl 8)
private inline fun ByteArray._read24_le(o: Int): Int =
    (readU8(o + 0) shl 0) or (readU8(o + 1) shl 8) or (readU8(o + 2) shl 16)

private inline fun ByteArray._read32_le(o: Int): Int =
    (readU8(o + 0) shl 0) or (readU8(o + 1) shl 8) or (readU8(o + 2) shl 16) or (readU8(o + 3) shl 24)

private inline fun ByteArray._read64_le(o: Int): Long =
    (_read32_le(o + 0).toUnsigned() shl 0) or (_read32_le(o + 4).toUnsigned() shl 32)

private inline fun ByteArray._read16_be(o: Int): Int = (readU8(o + 1) shl 0) or (readU8(o + 0) shl 8)
private inline fun ByteArray._read24_be(o: Int): Int =
    (readU8(o + 2) shl 0) or (readU8(o + 1) shl 8) or (readU8(o + 0) shl 16)

private inline fun ByteArray._read32_be(o: Int): Int =
    (readU8(o + 3) shl 0) or (readU8(o + 2) shl 8) or (readU8(o + 1) shl 16) or (readU8(o + 0) shl 24)

private inline fun ByteArray._read64_be(o: Int): Long =
    (_read32_be(o + 4).toUnsigned() shl 0) or (_read32_be(o + 0).toUnsigned() shl 32)

// Unsigned

fun ByteArray.readU8(o: Int): Int = this[o].toInt() and 0xFF
// LE
fun ByteArray.readU16_le(o: Int): Int = _read16_le(o)

fun ByteArray.readU24_le(o: Int): Int = _read24_le(o)
fun ByteArray.readU32_le(o: Int): Long = _read32_le(o).toUnsigned()
// BE
fun ByteArray.readU16_be(o: Int): Int = _read16_be(o)

fun ByteArray.readU24_be(o: Int): Int = _read24_be(o)
fun ByteArray.readU32_be(o: Int): Long = _read32_be(o).toUnsigned()

// Signed

fun ByteArray.readS8(o: Int): Int = this[o].toInt()
// LE
fun ByteArray.readS16_le(o: Int): Int = _read16_le(o).signExtend(16)

fun ByteArray.readS24_le(o: Int): Int = _read24_le(o).signExtend(24)
fun ByteArray.readS32_le(o: Int): Int = _read32_le(o)
fun ByteArray.readS64_le(o: Int): Long = _read64_le(o)
fun ByteArray.readF32_le(o: Int): Float = Float.fromBits(_read32_le(o))
fun ByteArray.readF64_le(o: Int): Double = Double.fromBits(_read64_le(o))
// BE
fun ByteArray.readS16_be(o: Int): Int = _read16_be(o).signExtend(16)

fun ByteArray.readS24_be(o: Int): Int = _read24_be(o).signExtend(24)
fun ByteArray.readS32_be(o: Int): Int = _read32_be(o)
fun ByteArray.readS64_be(o: Int): Long = _read64_be(o)
fun ByteArray.readF32_be(o: Int): Float = Float.fromBits(_read32_be(o))
fun ByteArray.readF64_be(o: Int): Double = Double.fromBits(_read64_be(o))

fun ByteArray.readS16_LEBE(o: Int, little: Boolean): Int = if (little) readS16_le(o) else readS16_be(o)
fun ByteArray.readS32_LEBE(o: Int, little: Boolean): Int = if (little) readS32_le(o) else readS32_be(o)
fun ByteArray.readS64_LEBE(o: Int, little: Boolean): Long = if (little) readS64_le(o) else readS64_be(o)
fun ByteArray.readF32_LEBE(o: Int, little: Boolean): Float = if (little) readF32_le(o) else readF32_be(o)
fun ByteArray.readF64_LEBE(o: Int, little: Boolean): Double = if (little) readF64_le(o) else readF64_be(o)

suspend fun AsyncOutputStream.writeBytes(data: ByteArray): Unit = write(data, 0, data.size)
suspend fun AsyncOutputStream.write8(v: Int): Unit = write(ByteArray(1).apply { this@apply.write8(0, v) }, 0, 1)
suspend fun AsyncOutputStream.write16_le(v: Int): Unit = write(ByteArray(2).apply { this@apply.write16_le(0, v) }, 0, 2)
suspend fun AsyncOutputStream.write24_le(v: Int): Unit = write(ByteArray(3).apply { this@apply.write24_le(0, v) }, 0, 3)
suspend fun AsyncOutputStream.write32_le(v: Int): Unit = write(ByteArray(4).apply { this@apply.write32_le(0, v) }, 0, 4)

suspend fun AsyncOutputStream.write16_be(v: Int): Unit = write(ByteArray(2).apply { this@apply.write16_be(0, v) }, 0, 2)
suspend fun AsyncOutputStream.write24_be(v: Int): Unit = write(ByteArray(3).apply { this@apply.write24_be(0, v) }, 0, 3)
suspend fun AsyncOutputStream.write32_be(v: Int): Unit = write(ByteArray(4).apply { this@apply.write32_be(0, v) }, 0, 4)
suspend fun AsyncOutputStream.write32_be(v: Long): Unit =
    write(ByteArray(4).apply { this@apply.write32_be(0, v) }, 0, 4)


fun ByteArray.write8(o: Int, v: Int) = run { this[o] = v.toByte() }
fun ByteArray.write8(o: Int, v: Long) = run { this[o] = v.toByte() }

fun ByteArray.write16_LEBE(o: Int, v: Int, little: Boolean) = if (little) write16_le(o, v) else write16_be(o, v)
fun ByteArray.write32_LEBE(o: Int, v: Int, little: Boolean) = if (little) write32_le(o, v) else write32_be(o, v)
fun ByteArray.write64_LEBE(o: Int, v: Long, little: Boolean) = if (little) write64_le(o, v) else write64_be(o, v)

fun ByteArray.writeF32_LEBE(o: Int, v: Float, little: Boolean) = if (little) writeF32_le(o, v) else writeF32_be(o, v)
fun ByteArray.writeF64_LEBE(o: Int, v: Double, little: Boolean) = if (little) writeF64_le(o, v) else writeF64_be(o, v)

fun ByteArray.write16_le(o: Int, v: Int) =
    run { this[o + 0] = v.extract8(0).toByte(); this[o + 1] = v.extract8(8).toByte() }

fun ByteArray.write24_le(o: Int, v: Int) = run {
    this[o + 0] = v.extract8(0).toByte(); this[o + 1] = v.extract8(8).toByte(); this[o + 2] = v.extract8(16).toByte()
}

fun ByteArray.write32_le(o: Int, v: Int) = run {
    this[o + 0] = v.extract8(0).toByte(); this[o + 1] = v.extract8(8).toByte(); this[o + 2] =
        v.extract8(16).toByte(); this[o + 3] = v.extract8(24).toByte()
}

fun ByteArray.write32_le(o: Int, v: Long) = write32_le(o, v.toInt())
fun ByteArray.write64_le(o: Int, v: Long) =
    run { write32_le(o + 0, (v ushr 0).toInt()); write32_le(o + 4, (v ushr 32).toInt()) }

fun ByteArray.writeF32_le(o: Int, v: Float) = run { write32_le(o + 0, v.toRawBits()) }
fun ByteArray.writeF64_le(o: Int, v: Double) = run { write64_le(o + 0, v.toRawBits()) }

fun ByteArray.write16_be(o: Int, v: Int) =
    run { this[o + 1] = v.extract8(0).toByte(); this[o + 0] = v.extract8(8).toByte() }

fun ByteArray.write24_be(o: Int, v: Int) = run {
    this[o + 2] = v.extract8(0).toByte(); this[o + 1] = v.extract8(8).toByte(); this[o + 0] = v.extract8(16).toByte()
}

fun ByteArray.write32_be(o: Int, v: Int) = run {
    this[o + 3] = v.extract8(0).toByte(); this[o + 2] = v.extract8(8).toByte(); this[o + 1] =
        v.extract8(16).toByte(); this[o + 0] = v.extract8(24).toByte()
}

fun ByteArray.write32_be(o: Int, v: Long) = write32_be(o, v.toInt())
fun ByteArray.write64_be(o: Int, v: Long) =
    run { write32_le(o + 0, (v ushr 32).toInt()); write32_le(o + 4, (v ushr 0).toInt()) }

fun ByteArray.writeF32_be(o: Int, v: Float) = run { write32_be(o + 0, v.toRawBits()) }
fun ByteArray.writeF64_be(o: Int, v: Double) = run { write64_be(o + 0, v.toRawBits()) }

class ByteArrayBuilder() {
    private val chunks = arrayListOf<ByteArray>()
    private val small = Small()

    val size get() = chunks.sumBy { it.size } + small.size

    constructor(chunk: ByteArray) : this() {
        append(chunk)
    }

    constructor(chunks: Iterable<ByteArray>) : this() {
        for (chunk in chunks) append(chunk)
    }

    constructor(vararg chunks: ByteArray) : this() {
        for (chunk in chunks) append(chunk)
    }

    private fun flush() {
        if (small.size <= 0) return
        chunks += small.toByteArray()
        small.clear()
    }

    fun clear() {
        chunks.clear()
        small.clear()
    }

    fun append(chunk: ByteArray, offset: Int, length: Int) {
        flush()
        val achunk = chunk.copyOfRange(offset, offset + length)
        chunks += achunk
    }

    fun append(buffer: ByteArrayBuilder) {
        flush()
        chunks += buffer.chunks
    }

    fun append(chunk: ByteArray) = append(chunk, 0, chunk.size)
    fun append(v: Byte) = small.append(v)

    operator fun plusAssign(v: ByteArrayBuilder) = append(v)
    operator fun plusAssign(v: ByteArray) = append(v)
    operator fun plusAssign(v: Byte) = append(v)

    fun toByteArray(): ByteArray {
        flush()
        val out = ByteArray(size)
        var offset = 0
        for (chunk in chunks) {
            for (n in 0 until chunk.size) out[offset + n] = chunk[n]
            //arraycopy(chunk, 0, out, offset, chunk.size)
            offset += chunk.size
        }
        return out
    }

    // @TODO: Optimize this!
    fun toString(charset: Charset): String = toByteArray().toString(charset)

    private class Small(private var bytes: ByteArray, private var len: Int = 0) {
        constructor(capacity: Int = 64) : this(ByteArray(capacity))

        val size: Int get() = len

        fun ensure(size: Int) {
            if (len + size < bytes.size) return
            bytes = bytes.copyOf(max(bytes.size + size, bytes.size * 2))
        }

        fun append(v: Byte) {
            ensure(1)
            bytes[len++] = v
        }

        fun clear() {
            len = 0
        }

        fun toByteArray() = bytes.copyOf(len)

        // @TODO: Optimize this!
        fun toString(charset: Charset): String = toByteArray().toString(charset)
    }
}

class ByteArrayBuffer(var data: ByteArray, size: Int = data.size, val allowGrow: Boolean = true) {
    constructor(initialCapacity: Int = 4096) : this(ByteArray(initialCapacity), 0)

    private var _size: Int = size
    var size: Int
        get() = _size
        set(len) {
            ensure(len)
            _size = len
        }

    fun ensure(expected: Int) {
        if (data.size < expected) {
            if (!allowGrow) throw RuntimeException("ByteArrayBuffer configured to not grow!")
            data = data.copyOf(max(expected, (data.size + 7) * 5))
        }
        _size = max(size, expected)
    }

    fun toByteArray(): ByteArray = data.copyOf(size)
}

interface Closeable {
    fun close(): Unit
}

fun Closeable(callback: () -> Unit) = object : Closeable {
    override fun close() = callback()
}

//java.lang.NoClassDefFoundError: com/soywiz/korio/lang/CloseableKt$Closeable$1 (wrong name: com/soywiz/korio/lang/CloseableKt$closeable$1)
//  at java.lang.ClassLoader.defineClass1(Native Method)
//fun Iterable<Closeable>.closeable(): Closeable = Closeable {
//	for (closeable in this@closeable) closeable.close()
//}

fun <TCloseable : Closeable, T : Any> TCloseable.use(callback: (TCloseable) -> T): T {
    try {
        return callback(this)
    } finally {
        this.close()
    }
}


interface SyncInputStream {
    fun read(buffer: ByteArray, offset: Int, len: Int): Int
}

interface SyncOutputStream {
    fun write(buffer: ByteArray, offset: Int, len: Int): Unit
}

interface SyncLengthStream {
    var length: Long
}

interface SyncRAInputStream {
    fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int
}

interface SyncRAOutputStream {
    fun write(position: Long, buffer: ByteArray, offset: Int, len: Int): Unit
}

open class SyncStreamBase : Closeable, SyncRAInputStream, SyncRAOutputStream, SyncLengthStream {
    val smallTemp = ByteArray(16)
    fun read(position: Long): Int {
        val count = read(position, smallTemp, 0, 1)
        return if (count >= 1) smallTemp[0].toInt() and 0xFF else -1
    }

    override fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int =
        throw UnsupportedOperationException()

    override fun write(position: Long, buffer: ByteArray, offset: Int, len: Int): Unit =
        throw UnsupportedOperationException()

    override var length: Long
        set(value) = throw UnsupportedOperationException()
        get() = throw UnsupportedOperationException()

    override fun close() = Unit
}

class SyncStream(val base: SyncStreamBase, var position: Long = 0L) : Closeable,
    SyncInputStream, SyncOutputStream, SyncLengthStream {
    override fun read(buffer: ByteArray, offset: Int, len: Int): Int {
        val read = base.read(position, buffer, offset, len)
        position += read
        return read
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int): Unit {
        base.write(position, buffer, offset, len)
        position += len
    }

    override var length: Long
        set(value) = run { base.length = value }
        get() = base.length

    val available: Long get() = length - position

    override fun close(): Unit = base.close()

    fun clone() = SyncStream(base, position)

    override fun toString(): String = "SyncStream($base, $position)"
}

fun arraycopy(src: ByteArray, srcPos: Int, dst: ByteArray, dstPos: Int, size: Int) {
    if ((src === dst) && (srcPos >= dstPos)) {
        for (n in 0 until size) dst[dstPos + n] = src[srcPos + n]
    } else {
        for (n in size - 1 downTo 0) dst[dstPos + n] = src[srcPos + n]
    }
}

class MemorySyncStreamBase(var data: ByteArrayBuffer) : SyncStreamBase() {
    constructor(initialCapacity: Int = 4096) : this(ByteArrayBuffer(initialCapacity))

    var ilength: Int
        get() = data.size
        set(value) = run { data.size = value }

    override var length: Long
        get() = data.size.toLong()
        set(value) = run { data.size = value.toInt() }

    fun checkPosition(position: Long) = run { if (position < 0) error("Invalid position $position") }

    override fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int {
        checkPosition(position)
        val ipos = position.toInt()
        if (position !in 0 until ilength) return 0
        val end = min(this.ilength, ipos + len)
        val actualLen = max((end - ipos), 0)
        arraycopy(this.data.data, ipos, buffer, offset, actualLen)
        return actualLen
    }

    override fun write(position: Long, buffer: ByteArray, offset: Int, len: Int) {
        checkPosition(position)
        data.ensure((position + len).toInt())
        arraycopy(buffer, offset, this.data.data, position.toInt(), len)
    }

    override fun close() = Unit

    override fun toString(): String = "MemorySyncStreamBase(${data.size})"
}

val EMPTY_BYTE_ARRAY = ByteArray(0)

fun SyncStreamBase.toSyncStream(position: Long = 0L) = SyncStream(this, position)

fun MemorySyncStream(data: ByteArray = EMPTY_BYTE_ARRAY) = MemorySyncStreamBase(ByteArrayBuffer(data)).toSyncStream()
fun MemorySyncStream(data: ByteArrayBuffer) = MemorySyncStreamBase(data).toSyncStream()
inline fun MemorySyncStreamToByteArray(initialCapacity: Int = 4096, callback: SyncStream.() -> Unit): ByteArray {
    val buffer = ByteArrayBuffer(initialCapacity)
    val s = MemorySyncStream(buffer)
    callback(s)
    return buffer.toByteArray()
}

private fun unhex(c: Char): Int = when (c) {
    in '0'..'9' -> 0 + (c - '0')
    in 'a'..'f' -> 10 + (c - 'a')
    in 'A'..'F' -> 10 + (c - 'A')
    else -> throw RuntimeException("Illegal HEX character $c")
}

internal fun unhex(str: String): ByteArray {
    val out = ByteArray(str.length / 2)
    var m = 0
    for (n in 0 until out.size) {
        out[n] = ((unhex(str[m++]) shl 4) or unhex(str[m++])).toByte()
    }
    return out
}

private val HEX_DIGITS = "0123456789abcdef"

fun Int.toHexString(): String {
    var v = this
    var out = ""
    while (v != 0) {
        val digit = (v and 0xF)
        out += HEX_DIGITS[digit]
        v = v ushr 4
    }
    return out.reversed()
}

val Int.hex32 get() = "0x" + toHexString()

class AsyncStream(val base: AsyncStreamBase, var position: Long = 0L) : AsyncInputStream,
    AsyncInputWithLengthStream, AsyncOutputStream, AsyncPositionLengthStream, AsyncCloseable {

    // FAKE
    override suspend fun read(buffer: ByteArray, offset: Int, len: Int): Int {
        val read = base.read(position, buffer, offset, len)
        if (read >= 0) position += read
        return read
    }

    // FAKE
    override suspend fun write(buffer: ByteArray, offset: Int, len: Int): Unit {
        base.write(position, buffer, offset, len)
        position += len
    }

    override suspend fun setPosition(value: Long): Unit = run { this.position = value }
    override suspend fun getPosition(): Long = this.position
    override suspend fun setLength(value: Long): Unit = base.setLength(value)
    override suspend fun getLength(): Long = base.getLength()
    suspend fun size(): Long = base.getLength()

    suspend fun getAvailable(): Long = getLength() - getPosition()
    suspend fun eof(): Boolean = this.getAvailable() <= 0L

    override suspend fun close(): Unit = base.close()

    @Deprecated("Use synchronous duplicate instead", ReplaceWith("duplicate()"))
    suspend fun clone(): AsyncStream = duplicate()

    fun duplicate(): AsyncStream = AsyncStream(base, position)
}


interface AsyncRAInputStream {
    suspend fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int
}

interface AsyncRAOutputStream {
    suspend fun write(position: Long, buffer: ByteArray, offset: Int, len: Int): Unit
}

interface AsyncLengthStream : AsyncGetLengthStream {
    suspend fun setLength(value: Long): Unit = throw UnsupportedOperationException()
}

open class AsyncStreamBase : AsyncCloseable, AsyncRAInputStream, AsyncRAOutputStream, AsyncLengthStream {
    suspend override fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int =
        throw UnsupportedOperationException()

    suspend override fun write(position: Long, buffer: ByteArray, offset: Int, len: Int): Unit =
        throw UnsupportedOperationException()

    suspend override fun setLength(value: Long): Unit = throw UnsupportedOperationException()
    suspend override fun getLength(): Long = throw UnsupportedOperationException()

    suspend override fun close(): Unit = Unit
}

interface AsyncInputOpenable {
    suspend fun openRead(): AsyncInputStream
}


interface AsyncPositionStream : AsyncGetPositionStream {
    suspend fun setPosition(value: Long): Unit = throw UnsupportedOperationException()
}

interface AsyncPositionLengthStream : AsyncPositionStream, AsyncLengthStream {
}


class MemoryAsyncStreamBase(var data: ByteArrayBuffer) : AsyncStreamBase() {
    constructor(initialCapacity: Int = 4096) : this(ByteArrayBuffer(initialCapacity))

    var ilength: Int
        get() = data.size
        set(value) = run { data.size = value }

    suspend override fun setLength(value: Long) = run { ilength = value.toInt() }
    suspend override fun getLength(): Long = ilength.toLong()

    fun checkPosition(position: Long) = run { if (position < 0) error("Invalid position $position") }

    override suspend fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int {
        checkPosition(position)
        if (position !in 0 until ilength) return 0
        val end = min(this.ilength.toLong(), position + len)
        val actualLen = max((end - position).toInt(), 0)
        arraycopy(this.data.data, position.toInt(), buffer, offset, actualLen)
        return actualLen
    }

    override suspend fun write(position: Long, buffer: ByteArray, offset: Int, len: Int) {
        checkPosition(position)
        data.ensure((position + len).toInt())
        arraycopy(buffer, offset, this.data.data, position.toInt(), len)
    }

    override suspend fun close() = Unit

    override fun toString(): String = "MemoryAsyncStreamBase(${data.size})"
}

fun ByteArray.openAsync(mode: String = "r"): AsyncStream =
    MemoryAsyncStreamBase(ByteArrayBuffer(this, allowGrow = false)).toAsyncStream(0L)

fun AsyncStreamBase.toAsyncStream(position: Long = 0L): AsyncStream = AsyncStream(this, position)

fun SyncStream.toAsync(): AsyncStream = this.base.toAsync().toAsyncStream(this.position)
fun SyncStreamBase.toAsync(): AsyncStreamBase = when (this) {
    is MemorySyncStreamBase -> MemoryAsyncStreamBase(this.data)
    else -> SyncAsyncStreamBase(this)
}

class SyncAsyncStreamBase(val sync: SyncStreamBase) : AsyncStreamBase() {
    suspend override fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int =
        sync.read(position, buffer, offset, len)

    suspend override fun write(position: Long, buffer: ByteArray, offset: Int, len: Int) =
        sync.write(position, buffer, offset, len)

    suspend override fun setLength(value: Long) = run { sync.length = value }
    suspend override fun getLength(): Long = sync.length
}

suspend fun AsyncInputStream.read(data: ByteArray): Int = read(data, 0, data.size)

suspend fun AsyncInputStream.copyTo(target: AsyncOutputStream, chunkSize: Int = 0x10000): Long {
    // Optimization to reduce suspensions
    if (this is AsyncStream && base is MemoryAsyncStreamBase) {
        target.write(base.data.data, position.toInt(), base.ilength - position.toInt())
        return base.ilength.toLong()
    }

    val chunk = ByteArray(chunkSize)
    var totalCount = 0L
    while (true) {
        val count = this.read(chunk)
        if (count <= 0) break
        target.write(chunk, 0, count)
        totalCount += count
    }
    return totalCount
}

fun <T : Any> ioSync(callback: suspend () -> T): T {
    var completed = false
    lateinit var result: T
    var resultEx: Throwable? = null
    callback.startCoroutine(object : Continuation<T> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resume(value: T) = run { result = value; completed = true }
        override fun resumeWithException(exception: Throwable) = run { resultEx = exception; completed = true }
    })
    if (!completed) error("ioSync was not completed synchronously!")
    if (resultEx != null) throw resultEx!!
    return result
}
