# quickstart

Runs an SDK tour in the configured example namespace: instance discovery/API versions,
namespace and collection creation, document upserts, vector and hybrid search, experimental
graph, SQL, and stream produce/subscribe. Embeddings are deterministic toy hashes.

```sh
./build.sh run quickstart
./build.sh run quickstart --live-admin  # optional, read-only; requires Live directory access
```

The optional directory listing only runs when requested and when the server serves
`live.admin`; it requires a credential authorized to list apps.
