# sql

Runs read-only SQL over the `articles` collection: a `GROUP BY`, a range filter, then the
search table functions `vector_search`, `text_search` and `rrf`. The SDK has no SQL call,
so this talks to `POST /v1/namespaces/{ns}/sql` over the JDK `HttpClient`; the SDK seeds
the data.

```sh
./build.sh run sql
```
