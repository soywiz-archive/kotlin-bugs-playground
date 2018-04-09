package test

import java.io.*

actual fun readFile(name: String): ByteArray {
    return File(name).readBytes()
}