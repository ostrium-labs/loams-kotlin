package examples

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder

// A tiny search service on the JDK's built-in HTTP server:
//   GET /search?q=durable+writes&category=storage&limit=3  ->  the engine's hybrid-search answer
// It seeds the sample articles on start-up. Port: first argument, default 8080.

const val NAMESPACE = "examples-kotlin"
const val COLLECTION = "service-articles"

fun main(args: Array<String>) {
    val client = newClient()
    client.seed(NAMESPACE, COLLECTION)

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", args.firstOrNull()?.toInt() ?: 8080), 0)
    server.createContext("/search") { exchange ->
        val params = (exchange.requestURI.rawQuery ?: "").split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
        val (status, body) = try {
            val q = params["q"]?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("missing ?q=")
            val answer = client.hybridSearch(NAMESPACE, COLLECTION, q, params["category"], params["limit"]?.toInt() ?: 3)
            200 to answer.toString()
        } catch (e: IllegalArgumentException) {
            400 to """{"error":${quote(e.message ?: "bad request")}}"""
        } catch (e: dev.loams.LoamsException) {
            502 to """{"error":${quote(e.message ?: "engine error")}}"""
        }
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("content-type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    println("listening on http://${server.address.hostString}:${server.address.port}/search?q=...")
}
