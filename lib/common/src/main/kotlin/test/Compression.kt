package test

suspend fun ByteArray.uncompress(method: CompressionMethod): ByteArray = method.uncompress(this)
suspend fun ByteArray.compress(method: CompressionMethod, context: CompressionContext = CompressionContext()): ByteArray = method.compress(this, context)

fun ByteArray.syncUncompress(method: CompressionMethod): ByteArray = ioSync { method.uncompress(this) }
fun ByteArray.syncCompress(method: CompressionMethod, context: CompressionContext = CompressionContext()): ByteArray = ioSync { method.compress(this, context) }

fun ByteArray.syncUncompressTo(method: CompressionMethod, out: ByteArray) {
    ioSync { method.uncompress(this.openAsync(), out.openAsync()) }
}

