# Loams Kotlin examples

These examples target **Loams SDK 0.1.2**. Installation and CI require the matching
0.1.2 packages and release tags to be published.

Loams is an open-source data engine for AI apps: collections with vector, BM25 and
filtered hybrid search, SQL over the same data, and agent memory, in one Rust binary.
These are runnable examples for the Kotlin SDK (`sdks/kotlin` in
[ostrium-labs/loams](https://github.com/ostrium-labs/loams)).

> Preview software: Loams 0.1.2 is a preview release and its APIs can change. See
> [docs/release/preview.md](https://github.com/ostrium-labs/loams/blob/main/docs/release/preview.md)
> and the docs at <https://loams.dev/docs>.

| Example | What it shows |
|---|---|
| [`quickstart/`](quickstart) | connect, create a collection, upsert documents, hybrid search (vector + BM25 + filter) |
| [`sql/`](sql) | SQL over a collection: aggregates, `vector_search`, `text_search`, `rrf` |
| [`app/`](app) | a small HTTP search service in plain Kotlin (JDK `HttpServer`) |

## Install the SDK

The SDK is `dev.loams:loams-kotlin:0.1.2` (Gradle: `implementation("dev.loams:loams-kotlin:0.1.2")`).
`build.sh` downloads that exact jar and protobuf-java from Maven Central. To test
an explicit local SDK build, set `SDK_JAR=/path/to/loams-kotlin-0.1.2.jar`.
The SDK depends on protobuf-java and the Kotlin standard library.

## Run

You need a JDK 11+, `curl` and `kotlinc` on your `PATH`, and a local engine:

```sh
# in a checkout of ostrium-labs/loams
cargo run -p loams -- dev            # note the address it prints
# or use the compose stack from docs/release/preview.md
```

Then, from this repo (set `LOAMS_URL` if your engine is not on `http://127.0.0.1:8080`):

```sh
export LOAMS_URL=http://127.0.0.1:8080   # whatever address your engine prints
./build.sh build                 # download SDK jar + compile all examples
./build.sh run quickstart
./build.sh run sql
./build.sh run app 8081          # then: curl 'http://127.0.0.1:8081/search?q=durable+writes'
```

The examples write only to the namespace `examples-kotlin` and assume auth is off. To use a key, pass `tokenSource = StaticTokenSource(...)` to `LoamsClient` in
[`shared/Loams.kt`](shared/Loams.kt).

## How the examples call the SDK

The Kotlin SDK's typed surface is a descriptor-driven facade over protobuf
`DynamicMessage`, with `client.invoke("<package>.<Service>/<Method>", message)` as the
generic call. [`shared/Loams.kt`](shared/Loams.kt) is a small helper shared by all three
examples: it builds a request from JSON text and prints responses as JSON. Transport,
retries and errors all come from the SDK. The SDK also provides app modules, builders, collection handles, SQL and streams.
This `sql` example demonstrates the native HTTP route and sends SQL to the engine's `POST /v1/namespaces/{ns}/sql` endpoint with the JDK `HttpClient`.

Embeddings are a toy hashing embedder (64 dimensions, offline), so the examples need no
model or API key; swap `embed()` for a real model in your own code.

## License

Apache-2.0, see [LICENSE](LICENSE).
