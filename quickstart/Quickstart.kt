package examples

import dev.loams.GraphModule
import dev.loams.Messages
import dev.loams.ProducerRecord
import dev.loams.SqlModule
import dev.loams.UnimplementedException
import dev.loams.Values
import dev.loams.field

/** SDK tour: namespace, collection, documents, search, vector, graph, SQL and streams. */
fun main(args: Array<String>) {
    val client = newClient()
    val instance = client.getInstance()
    println("connected to ${instance.field("name")} ${instance.field("server_version")}; APIs ${client.apiVersions()}")
    seed(client)
    println("seeded the $NAMESPACE/$COLLECTION collection")

    val nearest = client.vector.search(COLLECTION, embed("refund policy"), "embedding", limit = 3)
    println("direct vector hits: ${toJson(nearest)}")

    println("hybrid search (vector + BM25, RRF):")
    println(toJson(hybridSearch(client, COLLECTION, "fusing vector and keyword rankings")))

    try {
        client.graph.create("examples_graph")
        client.graph.query("examples_graph", "INSERT (:Article {id: \$id})", mapOf("id" to GraphModule.Param.int(1)))
        println("experimental graph: ${toJson(client.graph.query("examples_graph", "MATCH (a:Article) RETURN a.id", readOnly = true))}")
    } catch (_: UnimplementedException) {
        println("graph is not enabled in this engine build")
    }

    val sql = client.sql.query("SELECT category, count(*) AS n FROM $COLLECTION GROUP BY category ORDER BY category")
    println("SQL rows: ${SqlModule.records(sql)}")

    client.streams.create("example_events", partitions = 1)
    val produced = client.streams.produce("example_events", 0,
        listOf(ProducerRecord("article-1", mapOf("kind" to "indexed", "id" to 1))),
        producerId = "kotlin-example", producerSeq = 1)
    val offset = produced.field("base_offset") as Long
    client.streams.subscribe("example_events", 0, fromOffset = offset).use { subscription ->
        val event = subscription.firstOrNull()
        if (event != null) println("stream event at offset ${event.offset}: ${event.text}")
    }

    // Explicit opt-in, read-only, and may require Live directory privileges.
    if (args.contains("--live-admin")) {
        check(client.serves("live.admin")) { "this server does not serve live.admin" }
        val request = Messages.of("loams.live.v1.ListAppsRequest", Values.obj())
        val response = client.modules.liveAdmin.invoke("list_apps", request)
        val appsField = response.descriptorForType.findFieldByName("apps")
        val apps = response.getField(appsField) as List<*>
        println("Live apps visible to this credential: ${apps.size}")
    }
}
