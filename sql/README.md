# sql

Runs read-only SQL over the `articles` collection: a `GROUP BY`, a range filter, then the
search table functions `vector_search`, `text_search` and `rrf`. This example demonstrates the native SQL HTTP route; the SDK also provides a SQL module.

```sh
./build.sh run sql
```
