#!/usr/bin/env bash
# Builds the Loams Kotlin SDK from its v0.1.0 tag, then compiles and runs the examples.
#
#   ./build.sh build              # SDK jar + all three examples
#   ./build.sh run quickstart     # also: sql, app
#
# Needs: JDK 11+, git, curl, kotlinc on PATH (or at ~/.local/toolchains/kotlinc/kotlinc/bin).
# The SDK is not on Maven Central yet, so it is built from source (sdks/kotlin at the
# tag); once dev.loams:loams-kotlin:0.1.0 is published you can depend on it instead.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
build="$root/.build"
sdk_tag="${LOAMS_SDK_TAG:-v0.1.0}"
protobuf="protobuf-java-4.35.1.jar"
protobuf_sha256="a4345ba2aa009912ff6f90467fea2d104605256b72c50840d75f13256638a472"

kotlinc="$(command -v kotlinc || true)"
[ -n "$kotlinc" ] || kotlinc="$HOME/.local/toolchains/kotlinc/kotlinc/bin/kotlinc"
[ -x "$kotlinc" ] || { echo "kotlinc not found" >&2; exit 1; }
klib="$(cd "$(dirname "$(readlink -f "$kotlinc")")/../lib" && pwd)"

sdk() {
  [ -f "$build/loams-kotlin.jar" ] && return 0
  mkdir -p "$build/lib" "$build/sdk-classes"
  if [ ! -s "$build/lib/$protobuf" ]; then
    curl -fsSL --retry 3 -o "$build/lib/$protobuf" \
      "https://repo1.maven.org/maven2/com/google/protobuf/protobuf-java/4.35.1/$protobuf"
    echo "$protobuf_sha256  $build/lib/$protobuf" | sha256sum -c - >/dev/null
  fi
  if [ ! -d "$build/src/sdks/kotlin" ]; then
    rm -rf "$build/src" && mkdir -p "$build/src"
    git -C "$build/src" init -q
    git -C "$build/src" remote add origin https://github.com/ostrium-labs/loams.git
    git -C "$build/src" sparse-checkout set sdks/kotlin
    git -C "$build/src" fetch -q --depth 1 --filter=blob:none origin "refs/tags/$sdk_tag"
    git -C "$build/src" checkout -q FETCH_HEAD
  fi
  local s="$build/src/sdks/kotlin"
  cp "$s/gen/loams-descriptor.binpb" "$build/sdk-classes/"
  find "$s/src/main/kotlin" -name '*.kt' | sort >"$build/sdk-sources.txt"
  "$kotlinc" -J-Xmx1g -nowarn -jvm-target 11 -cp "$build/lib/$protobuf" \
    -d "$build/sdk-classes" "@$build/sdk-sources.txt"
  jar --create --file "$build/loams-kotlin.jar" -C "$build/sdk-classes" .
}

classpath() {
  printf '%s' "$build/loams-kotlin.jar:$build/lib/$protobuf:$klib/kotlin-stdlib.jar"
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
