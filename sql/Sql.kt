package examples

import dev.loams.SqlModule
/** SQL queries go through the SDK's sql module; no hand-built HTTP request is needed. */
fun main() {
    val client = newClient()
    seed(client)

    runSql(client, "group by", "SELECT category, count(*) AS n FROM $COLLECTION GROUP BY category ORDER BY n DESC")
    runSql(client, "parameterized query", "SELECT text, year FROM $COLLECTION WHERE year >= \$1 ORDER BY year", listOf(2024))
    runSql(client, "hybrid search in SQL",
        "SELECT _id, _score FROM rrf(vector_search('$COLLECTION', \$1, 'embedding', 10), " +
                "text_search('$COLLECTION', 'durable writes', 'text', 10)) LIMIT 3",
        listOf(embed("durable writes").toList()))
}

private fun runSql(client: dev.loams.LoamsClient, label: String, query: String, params: List<Any?> = emptyList()) {
    println("-- $label")
    println(SqlModule.records(client.sql.query(query, params)))
}
