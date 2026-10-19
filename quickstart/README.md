# quickstart

Connects, creates the `articles` collection in namespace `examples-kotlin` (text, keyword
and integer fields plus a 64-dim vector), upserts six documents and runs two hybrid
searches: a vector retriever and a BM25 retriever fused with reciprocal rank fusion, the
second one filtered on `category`.

```sh
./build.sh run quickstart
```
