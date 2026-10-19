package examples

// Connect, create a collection, upsert documents, run a hybrid search
// (vector + BM25, fused with RRF, filtered on `category`) and print the hits.

const val NAMESPACE = "examples-kotlin"
const val COLLECTION = "articles"

fun main() {
    val client = newClient()
    println("connected to $endpoint, api versions: ${client.apiVersions()}")

    client.seed(NAMESPACE, COLLECTION)
    println("upserted ${articles.size} documents into $NAMESPACE/$COLLECTION")

    for ((query, category) in listOf("fusing vector and keyword rankings" to null, "durable writes" to "storage")) {
        println("\nquery: \"$query\"" + (category?.let { "  (category = $it)" } ?: ""))
        val hits = client.hybridSearch(NAMESPACE, COLLECTION, query, category).at("hits")?.array.orEmpty()
        for (hit in hits) {
            val id = hit.at("pk", "string")?.string
            val score = hit.at("score")?.double ?: 0.0
            println("  %-3s %.4f  %s".format(id, score, hit.at("source", "text")?.string))
        }
    }
}
