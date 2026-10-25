#!/usr/bin/env bash
# Compiles and runs the examples against dev.loams:loams-kotlin:0.1.2.
#   ./build.sh build
#   ./build.sh run quickstart   # also: sql, app
# Needs JDK 11+, curl and kotlinc. SDK_JAR may point to an explicit local SDK jar.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
build="$root/.build"
sdk_version="0.1.2"
sdk_jar="${SDK_JAR:-$build/lib/loams-kotlin-$sdk_version.jar}"
protobuf="protobuf-java-4.35.1.jar"
protobuf_sha256="a4345ba2aa009912ff6f90467fea2d104605256b72c50840d75f13256638a472"

kotlinc="$(command -v kotlinc || true)"
[ -n "$kotlinc" ] || kotlinc="$HOME/.local/toolchains/kotlinc/kotlinc/bin/kotlinc"
[ -x "$kotlinc" ] || { echo "kotlinc not found" >&2; exit 1; }
klib="$(cd "$(dirname "$(readlink -f "$kotlinc")")/../lib" && pwd)"

sdk() {
  mkdir -p "$build/lib"
  if [ ! -s "$build/lib/$protobuf" ]; then
    curl -fsSL --retry 3 -o "$build/lib/$protobuf.tmp" \
      "https://repo1.maven.org/maven2/com/google/protobuf/protobuf-java/4.35.1/$protobuf"
    echo "$protobuf_sha256  $build/lib/$protobuf.tmp" | sha256sum -c - >/dev/null
    mv "$build/lib/$protobuf.tmp" "$build/lib/$protobuf"
  fi
  if [ -n "${SDK_JAR:-}" ]; then
    [ -s "$sdk_jar" ] || { echo "SDK_JAR does not exist: $sdk_jar" >&2; exit 1; }
  elif [ ! -s "$sdk_jar" ]; then
    curl -fsSL --retry 3 -o "$sdk_jar.tmp" \
      "https://repo1.maven.org/maven2/dev/loams/loams-kotlin/$sdk_version/loams-kotlin-$sdk_version.jar"
    mv "$sdk_jar.tmp" "$sdk_jar"
  fi
}

classpath() {
  printf '%s' "$sdk_jar:$build/lib/$protobuf:$klib/kotlin-stdlib.jar"
}

example() {
  local name="$1"
  mkdir -p "$build/$name"
  "$kotlinc" -J-Xmx1g -nowarn -jvm-target 11 -cp "$(classpath)" \
    -d "$build/$name" "$root/shared/Loams.kt" "$root/$name"/*.kt
}

case "${1:-build}" in
  build) sdk; for e in quickstart sql app; do example "$e"; done ;;
  run)
    sdk; example "${2:?quickstart|sql|app}"
    main="$(tr '[:lower:]' '[:upper:]' <<<"${2:0:1}")${2:1}"
    exec java -cp "$build/$2:$(classpath)" "examples.${main}Kt" "${@:3}" ;;
  clean) rm -rf "$build" ;;
  *) echo "usage: ./build.sh build | run quickstart|sql|app | clean" >&2; exit 2 ;;
esac
