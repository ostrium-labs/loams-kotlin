# Loams Kotlin examples

These examples target the published **Loams SDK 0.1.2** release.

| Example | What it shows |
|---|---|
| [`quickstart/`](quickstart) | instance/API discovery; namespaces, collections and documents; text/vector/hybrid search; experimental graph; SQL; streams |
| [`sql/`](sql) | SDK SQL module with parameters, aggregates and hybrid-search table functions |
| [`app/`](app) | JDK HTTP search service whose Loams calls use SDK modules |

The SDK artifact is `dev.loams:loams-kotlin:0.1.2`. `build.sh` downloads that exact jar
and protobuf-java from Maven Central. To use a local SDK build, set
`SDK_JAR=/path/to/loams-kotlin-0.1.2.jar`.

## Run

You need a JDK 11+, `curl` and `kotlinc` on your `PATH`, plus a local engine:

```sh
cargo run -p loams -- dev  # from an ostrium-labs/loams checkout
```

Then, from this repository:

```sh
export LOAMS_ENDPOINT=http://127.0.0.1:8080
export LOAMS_NAMESPACE=examples-kotlin  # optional; use a disposable namespace
./build.sh build
./build.sh run quickstart
./build.sh run quickstart --live-admin  # optional, read-only; requires a served Live admin API
./build.sh run sql
./build.sh run app 8081
```

Set `LOAMS_API_KEY` or `LOAMS_TOKEN` if engine authentication is enabled. Examples create
collections and append stream records in the configured namespace.

## SDK module coverage

Examples use the published high-level app modules `namespaces`, `collections`, `documents`,
`search`, `vector`, `graph`, `sql` and `streams`. The shared helper configures the client,
seeds records through the modules, and serializes SDK protobuf replies for the inbound HTTP
demo. The optional Live admin list uses the SDK's descriptor-backed module facade because
that surface has no dedicated typed builder. Graph is experimental and the quickstart reports
it unavailable when the server build does not include it.

Embeddings are deterministic toy hashes (64 dimensions), not model output. Replace `embed()`
with your model and use the same vector dimension as the collection schema.

| Module family | Example coverage in SDK 0.1.2 |
|---|---|
| `instance`, system/version discovery | Covered by quickstart (`getInstance`, `apiVersions`). |
| `namespaces`, `collections`, `documents`, `search`, `vector`, `graph`, `sql`, `streams` | Covered by quickstart and SQL; graph is optional when the server enables the experimental package. |
| `live`, `live.admin`, `tables` | The quickstart has an opt-in, read-only Live admin listing. Live watch and tables query/mutate/deploy calls are not exercised because they need application-specific sessions or privileges. |
| Authentication | Optional client configuration with `LOAMS_API_KEY` / `LOAMS_TOKEN`; no identity provisioning or sign-in flow is shown. |
| House | Unavailable as a typed module in SDK 0.1.2. |

Loams 0.1.2 is preview software; APIs may change. See the [tagged release notes](https://github.com/ostrium-labs/loams/blob/v0.1.2/docs/release/preview.md).
Apache-2.0, see [LICENSE](LICENSE).
