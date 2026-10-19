# Loams Kotlin examples

Loams is an open-source data engine for AI apps: collections with vector, BM25 and
filtered hybrid search, SQL over the same data, and agent memory, in one Rust binary.
These are runnable examples for the Kotlin SDK (`sdks/kotlin` in
[ostrium-labs/loams](https://github.com/ostrium-labs/loams)).

> Preview software: Loams 0.1.0 is a preview release and its APIs can change. See
> [docs/release/preview.md](https://github.com/ostrium-labs/loams/blob/main/docs/release/preview.md)
> and the docs at <https://loams.dev/docs>.

| Example | What it shows |
|---|---|
| [`quickstart/`](quickstart) | connect, create a collection, upsert documents, hybrid search (vector + BM25 + filter) |
| [`sql/`](sql) | SQL over a collection: aggregates, `vector_search`, `text_search`, `rrf` |
| [`app/`](app) | a small HTTP search service in plain Kotlin (JDK `HttpServer`) |

## Install the SDK

The SDK is `dev.loams:loams-kotlin:0.1.0` (Gradle: `implementation("dev.loams:loams-kotlin:0.1.0")`).
**It is not on Maven Central yet**, so `build.sh` builds it from the monorepo tag `v0.1.0`
(`sdks/kotlin`) and the examples compile against that jar. Once the artifact is
published you can drop that step and use the coordinates above. The SDK depends only on
`protobuf-java` and the Kotlin standard library.

## Run

You need a JDK 11+, `git`, `curl` and `kotlinc` on your `PATH`, and a local engine:

```sh
# in a checkout of ostrium-labs/loams
cargo run -p loams -- dev            # note the address it prints
# or use the compose stack from docs/release/preview.md
```

Then, from this repo (set `LOAMS_URL` if your engine is not on `http://127.0.0.1:18080`):

```sh
export LOAMS_URL=http://127.0.0.1:8080   # whatever address your engine prints
./build.sh build                 # SDK jar + all examples
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
retries and errors all come from the SDK. The SDK has no SQL call, so the `sql` example
sends SQL to the engine's `POST /v1/namespaces/{ns}/sql` endpoint with the JDK `HttpClient`.

Embeddings are a toy hashing embedder (64 dimensions, offline), so the examples need no
model or API key; swap `embed()` for a real model in your own code.

## License

Apache-2.0, see [LICENSE](LICENSE).
