package examples

import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import dev.loams.LoamsClient
import dev.loams.Json
import dev.loams.JsonValue

/** The engine the examples talk to; override with LOAMS_URL. */
val endpoint: String = System.getenv("LOAMS_URL") ?: "http://127.0.0.1:8080"

fun newClient(): LoamsClient = LoamsClient(endpoint = endpoint)

/**
 * One unary RPC with a proto3-JSON request body, answered as proto3 JSON.
 *
 * The Kotlin SDK's typed surface is a descriptor-driven facade over `DynamicMessage`, so
 * these examples build requests from JSON text and let the SDK parse them against the
 * committed descriptor set. Everything else (transport, retries, errors) is the SDK's.
 */
fun LoamsClient.call(rpc: String, requestJson: String): JsonValue {
    val request = buildMessage(binding(rpc).request, Json.parseOrThrow(requestJson))
    val response = invoke(rpc, request)
    return Json.parseOrThrow(toJson(response))
}

/**
 * A response as JSON text. The SDK's own writer refuses `google.protobuf.Struct` (it only
 * writes requests), and collection schemas and document sources are Structs, so this walks
 * the message itself.
 */
fun toJson(value: Any?): String = when (value) {
    is DynamicMessage -> when (value.descriptorForType.fullName) {
        "google.protobuf.Struct" -> (value.getField(value.descriptorForType.findFieldByName("fields")) as List<*>)
            .joinToString(",", "{", "}") { e ->
                e as DynamicMessage
                val d = e.descriptorForType
                "${quote(e.getField(d.findFieldByName("key")) as String)}:${toJson(e.getField(d.findFieldByName("value")))}"
            }
        "google.protobuf.ListValue" ->
            (value.getField(value.descriptorForType.findFieldByName("values")) as List<*>).joinToString(",", "[", "]") { toJson(it) }
        "google.protobuf.Value" -> value.oneofFieldOrNull()?.let { toJson(value.getField(it)) } ?: "null"
        else -> value.descriptorForType.fields.filter { f ->
            if (f.isRepeated) (value.getField(f) as List<*>).isNotEmpty() else if (f.containingOneof != null || f.hasPresence()) value.hasField(f) else true
        }.joinToString(",", "{", "}") { f ->
            val v = value.getField(f)
            // Proto3 map fields are repeated entry messages.
            if (f.isMapField) {
                "${quote(f.jsonName)}:" + (v as List<*>).joinToString(",", "{", "}") { e ->
                    e as DynamicMessage
                    val d = e.descriptorForType
                    "${quote(e.getField(d.findFieldByName("key")).toString())}:${toJson(e.getField(d.findFieldByName("value")))}"
                }
            } else "${quote(f.jsonName)}:${toJson(v)}"
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

private fun DynamicMessage.oneofFieldOrNull(): Descriptors.FieldDescriptor? =
    descriptorForType.oneofs.firstOrNull()?.let { getOneofFieldDescriptor(it) }

/**
 * A proto3-JSON request body as a `DynamicMessage`. The SDK's own JSON reader handles
 * `google.protobuf.Struct` but not a bare `google.protobuf.Value`, which document vectors
 * and filters use, so requests are built here.
 */
fun buildMessage(type: Descriptors.Descriptor, json: JsonValue): DynamicMessage {
    when (type.fullName) {
        "google.protobuf.Empty" -> return DynamicMessage.getDefaultInstance(type)
        "google.protobuf.Value" -> return buildValue(type, json)
        "google.protobuf.ListValue" -> {
            val values = type.findFieldByName("values")
            return DynamicMessage.newBuilder(type).also { b ->
                json.array!!.forEach { b.addRepeatedField(values, buildValue(values.messageType, it)) }
            }.build()
        }
    }
    val builder = DynamicMessage.newBuilder(type)
    val entries = json.obj!!.entries
    if (type.fullName == "google.protobuf.Struct") {
        val fields = type.findFieldByName("fields")
        for ((key, member) in entries) {
            builder.addRepeatedField(fields, mapEntry(fields, key, buildValue(fields.messageType.findFieldByName("value").messageType, member)))
        }
        return builder.build()
    }
    for ((name, member) in entries) {
        val field = type.findFieldByName(name) ?: type.fields.firstOrNull { it.jsonName == name }
            ?: throw IllegalArgumentException("${type.fullName} has no field $name")
        when {
            field.isMapField -> for ((key, v) in member.obj!!.entries) {
                val valueField = field.messageType.findFieldByName("value")
                builder.addRepeatedField(field, mapEntry(field, key, single(valueField, v)))
            }
            field.isRepeated -> member.array!!.forEach { builder.addRepeatedField(field, single(field, it)) }
            else -> builder.setField(field, single(field, member))
        }
    }
    return builder.build()
}

private fun mapEntry(field: Descriptors.FieldDescriptor, key: String, value: Any): DynamicMessage {
    val entry = field.messageType
    return DynamicMessage.newBuilder(entry).setField(entry.findFieldByName("key"), key)
        .setField(entry.findFieldByName("value"), value).build()
}

private fun single(field: Descriptors.FieldDescriptor, v: JsonValue): Any = when (field.type) {
    Descriptors.FieldDescriptor.Type.MESSAGE -> buildMessage(field.messageType, v)
    Descriptors.FieldDescriptor.Type.STRING -> v.string!!
    Descriptors.FieldDescriptor.Type.BOOL -> v.boolean!!
    Descriptors.FieldDescriptor.Type.INT32, Descriptors.FieldDescriptor.Type.UINT32 -> v.long!!.toInt()
    Descriptors.FieldDescriptor.Type.INT64, Descriptors.FieldDescriptor.Type.UINT64 -> v.long!!
    Descriptors.FieldDescriptor.Type.FLOAT -> v.double!!.toFloat()
    Descriptors.FieldDescriptor.Type.DOUBLE -> v.double!!
    Descriptors.FieldDescriptor.Type.ENUM -> field.enumType.findValueByName(v.string!!)
    else -> throw IllegalArgumentException("unsupported field type ${field.type} for ${field.fullName}")
}

private fun buildValue(type: Descriptors.Descriptor, v: JsonValue): DynamicMessage {
    fun f(name: String) = type.findFieldByName(name)
    val b = DynamicMessage.newBuilder(type)
    when (v) {
        is Json.JsonNull -> b.setField(f("null_value"), f("null_value").enumType.findValueByNumber(0))
        is Json.JsonString -> b.setField(f("string_value"), v.value)
        is Json.JsonBoolean -> b.setField(f("bool_value"), v.value)
        is Json.JsonNumber -> b.setField(f("number_value"), v.raw.toDouble())
        is Json.JsonArray -> b.setField(f("list_value"), buildMessage(f("list_value").messageType, v))
        is Json.JsonObject -> b.setField(f("struct_value"), buildMessage(f("struct_value").messageType, v))
    }
    return b.build()
}

/** Walks `a`, `b`, `c` through nested JSON objects; null when any step is missing. */
fun JsonValue.at(vararg path: String): JsonValue? =
    path.fold(this as JsonValue?) { node, key -> node?.obj?.get(key) }

/** JSON string literal for embedding user text in a request body. */
fun quote(text: String): String = Json.writeCompact(Json.JsonString(text))

/** A toy bag-of-words hashing embedder: deterministic, offline, and good enough to demo. */
const val DIM = 64

fun embed(text: String): List<Float> {
    val v = FloatArray(DIM)
    for (word in Regex("[a-z0-9]+").findAll(text.lowercase()).map { it.value }) {
        v[Math.floorMod(word.hashCode(), DIM)] += 1f
    }
    val norm = Math.sqrt(v.sumOf { (it * it).toDouble() }).toFloat().takeIf { it > 0f } ?: 1f
    return v.map { it / norm }
}

fun List<Float>.json(): String = joinToString(",", "[", "]")

/** The schema every example uses: `text` (BM25), `category` and `year` (filterable), `embedding`. */
val articleSchema = """
{"fields":[
  {"name":"text","source_path":"text","kind":"text","indexed":true},
  {"name":"category","source_path":"category","kind":"keyword","indexed":true,"fast":true},
  {"name":"year","source_path":"year","kind":"i64","indexed":true,"fast":true}],
 "vectors":[{"name":"embedding","dim":$DIM,"distance":"cosine"}],
 "sparse_vectors":[],"dynamic":"ignore","max_fields":100}
""".trimIndent()

data class Article(val id: String, val text: String, val category: String, val year: Int)

val articles = listOf(
    Article("a1", "Reciprocal rank fusion merges vector and keyword rankings into one list", "search", 2024),
    Article("a2", "BM25 scores documents by term frequency and inverse document frequency", "search", 2023),
    Article("a3", "Approximate nearest neighbour indexes trade recall for speed on large vector sets", "search", 2025),
    Article("a4", "Object storage keeps durable data cheap while caches keep hot data fast", "storage", 2024),
    Article("a5", "A write-ahead log makes writes durable before they reach the index", "storage", 2022),
    Article("a6", "Agents remember facts across sessions by writing them to a memory store", "agents", 2025),
)

/** Creates the collection (idempotent) and upserts the sample articles; returns the write token. */
fun LoamsClient.seed(namespace: String, collection: String) {
    runCatching { call("loams.collection.v1.NamespaceService/CreateNamespace", """{"namespace":${quote(namespace)}}""") }
    call(
        "loams.collection.v1.CollectionService/CreateCollection",
        """{"namespace":${quote(namespace)},"name":${quote(collection)},"schema":$articleSchema,"partitions":1}""",
    )
    val ops = articles.joinToString(",") {
        """{"upsert":{"id":{"string":${quote(it.id)}},
            "source":{"text":${quote(it.text)},"category":${quote(it.category)},"year":${it.year}},
            "vectors":{"embedding":${embed(it.text).json()}}}}"""
    }
    call(
        "loams.collection.v1.DocumentService/WriteDocuments",
        """{"namespace":${quote(namespace)},"collection":${quote(collection)},"ops":[$ops]}""",
    )
}

/** Hybrid search: vector + BM25 fused with RRF, optionally filtered on `category`. */
fun LoamsClient.hybridSearch(namespace: String, collection: String, query: String, category: String? = null, limit: Int = 3): JsonValue {
    val filter = category?.let { ""","filter":{"term":{"field":"category","value":${quote(it)}}}""" } ?: ""
    return call(
        "loams.collection.v1.QueryService/Search",
        """{"namespace":${quote(namespace)},"collection":${quote(collection)},
           "retrievers":[
             {"vector":{"field":"embedding","query":${embed(query).json()},"k":20,"params":{"exact":true}}},
             {"text":{"query":{"match":{"field":"text","text":${quote(query)}}},"k":20}}],
           "fusion":{"rrf":{}},"limit":$limit,
           "select":{"source":"all","vectors":[],"fields":[]}$filter}""",
    )
}
