# app

A search service on the JDK's built-in `HttpServer`. `GET /search?q=...&category=...&limit=...`
returns the engine's hybrid-search answer as JSON. It seeds the sample articles on start-up.

```sh
./build.sh run app 8081
curl 'http://127.0.0.1:8081/search?q=memory+for+agents&limit=2'
curl 'http://127.0.0.1:8081/search?q=durable+writes&category=storage'
```
