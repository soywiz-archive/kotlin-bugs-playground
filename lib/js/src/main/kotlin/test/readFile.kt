package test

import org.khronos.webgl.*

external internal fun require(name: String): dynamic

typealias NodeJsBuffer = Uint8Array

fun ByteArray.asInt8Array(): Int8Array = this.unsafeCast<Int8Array>()

fun ByteArray.asUint8Array(): Uint8Array {
    val i = this.asInt8Array()
    return Uint8Array(i.buffer, i.byteOffset, i.length)
}
fun ByteArray.toNodeJsBuffer(): NodeJsBuffer = this.asUint8Array().unsafeCast<NodeJsBuffer>()
fun NodeJsBuffer.toByteArray() = Int8Array(this.unsafeCast<Int8Array>()).unsafeCast<ByteArray>()

actual fun readFile(name: String): ByteArray {
    val fs = require("fs")
    return fs.readFileSync(name).unsafeCast<NodeJsBuffer>().toByteArray()
}
