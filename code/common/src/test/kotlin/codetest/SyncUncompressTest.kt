package codetest

import test.*
import kotlin.test.*

class SyncUncompressTest {
    fun tryReadFile(name: String): ByteArray {
        for (n in 0 until 6) {
            val parents = "../".repeat(n)
            try {
                return readFile("./$parents$name")
            } catch (e: Throwable) {

            }
        }
        error("Couldn't find $name")
    }

    @Test
    fun uncompressFine() {
        println("[a]")
        val out1 = tryReadFile("compressed1.bin").syncUncompress(Deflate)
        println("[b]")
        val out2 = tryReadFile("compressed2.bin").syncUncompress(Deflate)
        println("[c]")
    }
}
