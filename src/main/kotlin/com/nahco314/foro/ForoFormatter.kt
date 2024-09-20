package com.nahco314.foro

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.file.Path

class ForoUnexpectedErrorException(message: String): Exception(message)

data class FormatArgs(val targetPath: Path, val targetContent: String, val currentDir: Path, val foroExecutable: Path, val configFile: Path, val cacheDir: Path, val socketDir: Path)
sealed class FormatResult {
    data class Success(val formattedContent: String): FormatResult()
    data object Ignored: FormatResult()
    data class Error(val error: String): FormatResult()
}

class ForoFormatter {
    private fun parseInfo(infoStr: String): Pair<Int, Long>? {
        val parts = infoStr.split(',')

        if (parts.size != 2) {
            return null
        }

        val pid = parts[0].toIntOrNull() ?: return null
        val startTime = parts[1].toLongOrNull() ?: return null

        return Pair(pid, startTime)
    }

    private fun getProcessInfo(pid: Long): Long? {
        val process = ProcessHandle.of(pid)

        if (process.isEmpty) {
            return null
        }

        val start = process.get().info().startInstant()

        return start.get().epochSecond
    }

    fun daemonIsAlive(socketDir: Path): Boolean {
        val infoPath = socketDir.resolve("daemon-cmd.sock.info").toFile()
        if (!infoPath.exists()) {
            return false
        }
        val content = infoPath.readText()

        val (pid, startTime) = parseInfo(content) ?: return false

        val realStartTime = getProcessInfo(pid.toLong()) ?: return false

        return realStartTime == startTime
    }

    fun start(foroExecutable: Path) {
        val parts = arrayOf(foroExecutable.toString(), "daemon", "start")
        val proc = ProcessBuilder(*parts)
            .start()
        proc.waitFor()

        if (proc.exitValue() != 0) {
            throw ForoUnexpectedErrorException("Failed to start daemon")
        }
    }

    private fun escape(raw: String): String {
        var escaped = raw
        escaped = escaped.replace("\\", "\\\\")
        escaped = escaped.replace("\"", "\\\"")
        escaped = escaped.replace("\b", "\\b")
        escaped = escaped.replace("\u000c", "\\f")
        escaped = escaped.replace("\n", "\\n")
        escaped = escaped.replace("\r", "\\r")
        escaped = escaped.replace("\t", "\\t")
        // TODO: escape other non-printing characters using uXXXX notation
        return escaped
    }

    private fun formatInner(args: FormatArgs): FormatResult {
        val commandJsonString = """
            {
                "command": {
                    "PureFormat": {
                        "path": "${args.targetPath}",
                        "content": "${escape(args.targetContent)}"
                    }
                },
                "current_dir": "${args.currentDir}",
                "global_options": {
                    "config_file": "${args.configFile}",
                    "cache_dir": "${args.cacheDir}",
                    "socket_dir": "${args.socketDir}",
                    "no_cache": false
                }
            }
        """

        // println(commandJsonString)

        val socketPath = args.socketDir.resolve("daemon-cmd.sock")
        val address = UnixDomainSocketAddress.of(socketPath)

        val result: FormatResult

        SocketChannel.open(StandardProtocolFamily.UNIX).use { sc ->
            sc.connect(address)

            val byteBuffer = ByteBuffer.wrap(commandJsonString.toByteArray())
            sc.write(byteBuffer)
            sc.shutdownOutput()

            val buffer = ByteBuffer.allocate(1024)
            val decoder: CharsetDecoder = Charset.forName("UTF-8").newDecoder()

            val responseBuilder = StringBuilder()
            val remainderBuffer = ByteBuffer.allocate(256)

            while (true) {
                val bytesRead = sc.read(buffer)

                if (bytesRead == -1) {
                    break
                }

                buffer.flip()

                remainderBuffer.flip()
                val combinedBuffer = ByteBuffer.allocate(remainderBuffer.remaining() + buffer.remaining())
                combinedBuffer.put(remainderBuffer)
                combinedBuffer.put(buffer)
                combinedBuffer.flip()

                val decoded = decoder.decode(combinedBuffer).toString()
                responseBuilder.append(decoded)

                remainderBuffer.clear()
                if (combinedBuffer.hasRemaining()) {
                    remainderBuffer.put(combinedBuffer)
                }

                buffer.clear()
            }

            val response = responseBuilder.toString()
            // println(response)
            val responseJson = Json.decodeFromString<JsonObject>(response)
            val content = responseJson["PureFormat"]!!.jsonObject
            if (content.containsKey("Success")) {
                result = FormatResult.Success(content["Success"]!!.jsonPrimitive.content)
            } else if (content.containsKey("Ignored")) {
                result = FormatResult.Ignored
            } else {
                result = FormatResult.Error(content["Error"]!!.jsonPrimitive.toString())
            }
        }

        return result
    }

    fun format(args: FormatArgs): FormatResult {
        if (!daemonIsAlive(args.socketDir)) {
            start(args.foroExecutable)
        }

        return formatInner(args)
    }
}
