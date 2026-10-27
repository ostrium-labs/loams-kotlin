package examples

import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import dev.loams.AlreadyExistsException
import dev.loams.ConsistencySession
import dev.loams.Doc
import dev.loams.Filter
import dev.loams.LoamsClient
import dev.loams.Schema
import dev.loams.search
import dev.loams.rrf

const val NAMESPACE = "examples-kotlin"
const val COLLECTION = "articles"
const val DIM = 64

/** Environment-configured published SDK client with a default namespace and read-your-writes. */
fun newClient(): LoamsClient {
    val env = System.getenv() + ("LOAMS_NAMESPACE" to (System.getenv("LOAMS_NAMESPACE") ?: NAMESPACE))
    return LoamsClient.fromEnv(env, session = ConsistencySession())
}

fun embed(text: String): FloatArray {
    val vector = FloatArray(DIM)
    for (word in Regex("[a-z0-9]+").findAll(text.lowercase()).map { it.value }) {
        vector[Math.floorMod(word.hashCode(), DIM)] += 1f
    }
    val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat().takeIf { it > 0f } ?: 1f
    return FloatArray(DIM) { vector[it] / norm }
}

private val articles = listOf(
    Triple("a1", "Reciprocal rank fusion combines vector and keyword rankings", "search"),
    Triple("a2", "BM25 scores documents by term frequency and inverse frequency", "search"),
    Triple("a3", "Approximate nearest neighbour indexes trade recall for speed", "search"),
    Triple("a4", "Object storage keeps durable data cheap while caches keep it fast", "storage"),
    Triple("a5", "A write ahead log makes writes durable before indexing", "storage"),
    Triple("a6", "Agents remember facts across sessions in a memory store", "agents"),
)

/** Create/reuse the dedicated demo namespace and collection, then upsert example documents. */
fun seed(client: LoamsClient, collection: String = COLLECTION) {
    try { client.namespaces.create(client.namespace ?: NAMESPACE) } catch (_: AlreadyExistsException) { }
    try {
        client.collections.create(collection, Schema().text("text").keyword("category").i64("year").vector("embedding", DIM))
    } catch (_: AlreadyExistsException) { }
    client.collection(collection).upsert(articles.mapIndexed { index, article ->
        Doc(article.first, mapOf("text" to article.second, "category" to article.third, "year" to 2022 + index),
            mapOf("embedding" to embed(article.second)))
    })
}

/** SDK app module call: hybrid vector + text search, with an optional category filter. */
fun hybridSearch(client: LoamsClient, collection: String, query: String,
                 category: String? = null, limit: Int = 3): DynamicMessage = client.search.query(search(collection) {
    text(query, "text")
    vector("embedding", embed(query))
    fuse(rrf(60))
    if (category != null) filter(Filter.eq("category", category))
    this.limit(limit)
})

/** Encode SDK protobuf messages for the tiny HTTP app; no engine HTTP calls are made here. */
fun toJson(value: Any?): String = when (value) {
    is DynamicMessage -> when (value.descriptorForType.fullName) {
        "google.protobuf.Struct" -> (value.getField(value.descriptorForType.findFieldByName("fields")) as List<*>)
            .joinToString(",", "{", "}") { entry ->
                entry as DynamicMessage
                val d = entry.descriptorForType
                "${quote(entry.getField(d.findFieldByName("key")) as String)}:${toJson(entry.getField(d.findFieldByName("value")))}"
            }
        "google.protobuf.ListValue" -> (value.getField(value.descriptorForType.findFieldByName("values")) as List<*>)
            .joinToString(",", "[", "]") { toJson(it) }
        "google.protobuf.Value" -> value.descriptorForType.oneofs.firstOrNull()?.let { oneof ->
            value.getOneofFieldDescriptor(oneof)?.let { toJson(value.getField(it)) } ?: "null"
        } ?: "null"
        else -> value.descriptorForType.fields.filter { field ->
            if (field.isRepeated) (value.getField(field) as List<*>).isNotEmpty()
            else if (field.containingOneof != null || field.hasPresence()) value.hasField(field) else true
        }.joinToString(",", "{", "}") { field ->
            val fieldValue = value.getField(field)
            if (field.isMapField) "${quote(field.jsonName)}:" + (fieldValue as List<*>).joinToString(",", "{", "}") { entry ->
                entry as DynamicMessage
                val d = entry.descriptorForType
                "${quote(entry.getField(d.findFieldByName("key")).toString())}:${toJson(entry.getField(d.findFieldByName("value")))}"
            } else "${quote(field.jsonName)}:${toJson(fieldValue)}"
        }
    }
    is List<*> -> value.joinToString(",", "[", "]") { toJson(it) }
    is String -> quote(value)
    is Descriptors.EnumValueDescriptor -> quote(value.name)
    is com.google.protobuf.ByteString -> quote(java.util.Base64.getEncoder().encodeToString(value.toByteArray()))
    is Float, is Double -> if ((value as Number).toDouble().isFinite()) value.toString() else "null"
    null -> "null"
    else -> value.toString()
}

private fun quote(text: String): String = dev.loams.Json.writeCompact(dev.loams.Json.JsonString(text))
