#!/usr/bin/env bash
# build.sh — builds the full strata project in the correct order.
#
# WHY TWO INVOCATIONS:
#   fabric-loom resolves modImplementation jars at Gradle *configuration time*.
#   settings.gradle only includes strata-world when strata-core's remapped jar
#   already exists on disk, so:
#     Invocation 1: strata-world is NOT in the project graph → strata-core builds freely
#     Invocation 2: jar now exists → strata-world is included and configures OK
#
# Usage:
#   ./build.sh           # incremental build
#   ./build.sh --clean   # wipe both build/ dirs, then full rebuild

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ "${1:-}" == "--clean" ]]; then
    echo "==> Cleaning..."
    rm -rf strata-core/build strata-world/build
fi

echo "==> Building strata-core..."
./gradlew :strata-core:build

echo "==> Building strata-world..."
./gradlew :strata-world:build

echo "==> Done."
