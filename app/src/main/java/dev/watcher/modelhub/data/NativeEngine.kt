package dev.watcher.modelhub.data

object NativeEngine {
    init { System.loadLibrary("modelhub") }
    external fun load(path: String): Long
    external fun release(handle: Long)
    external fun prepare(handle: Long, roles: Array<String>, messages: Array<ByteArray>, temperature: Float, threads: Int)
    external fun next(handle: Long): ByteArray?
    external fun stop()
    external fun transform(input: String, output: String, quant: String, layers: IntArray, threads: Int)
}
