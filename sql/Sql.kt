package examples

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Demonstrate SQL through the native HTTP route with the JDK HttpClient.
// The SDK also provides a SQL module; collection seeding here uses SDK RPCs.

const val NAMESPACE = "examples-kotlin"
const val COLLECTION = "articles"

fun sql(statement: String): String {
    val request = HttpRequest.newBuilder(URI("$endpoint/v1/namespaces/$NAMESPACE/sql"))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("""{"query":${quote(statement)}}"""))
        .build()
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()
}

fun main() {
    newClient().seed(NAMESPACE, COLLECTION)

    val statements = listOf(
        "SELECT category, count(*) AS n FROM $COLLECTION GROUP BY category ORDER BY n DESC",
        "SELECT _id, year FROM $COLLECTION WHERE year >= 2025 ORDER BY _id",
        "SELECT _id, _score FROM vector_search('$COLLECTION', ${embed("vector search").json()}, 'embedding', 3)",
        "SELECT _id, _score FROM text_search('$COLLECTION', 'durable index', 'text', 3)",
        "SELECT _id, _score FROM rrf(" +
            "vector_search('$COLLECTION', ${embed("durable writes").json()}, 'embedding', 10), " +
            "text_search('$COLLECTION', 'durable writes', 'text', 10), 60, 3)",
    )
    for (statement in statements) {
        println("> ${statement.take(110)}${if (statement.length > 110) "..." else ""}")
        println(sql(statement))
        println()
    }
}
