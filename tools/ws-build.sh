#!/bin/bash
# Build de bajo consumo para trabajar en paralelo (varios worktrees a la vez en 16 GB).
# Uso: tools/ws-build.sh [tareas…]   (por defecto compila Kotlin). Sale con el código de Gradle
# e imprime BUILD OK / BUILD FAILED al final.
cd "$(dirname "$0")/.." || exit 1
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
[ $# -eq 0 ] && set -- :app:compileDebugKotlin
set -o pipefail
./gradlew --no-daemon --console=plain -q \
  -Dorg.gradle.jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" \
  -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process \
  "$@" 2>&1 | { grep -v '^w: ' | grep -v 'Daemon will be stopped\|single-use Daemon\|To honour the JVM settings' || true; }
status=${PIPESTATUS[0]}
[ "$status" -eq 0 ] && echo "BUILD OK" || echo "BUILD FAILED ($status)"
exit "$status"
