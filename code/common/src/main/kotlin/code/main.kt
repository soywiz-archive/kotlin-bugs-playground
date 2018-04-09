package code

import test.*

fun main(args: Array<String>) {
    println("[a]")
    val out1 = readFile("compressed1.bin").syncUncompress(Deflate)
    println("[b]")
    val out2 = readFile("compressed2.bin").syncUncompress(Deflate)
    println("[c]")
}