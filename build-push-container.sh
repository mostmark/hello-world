#!/bin/bash

# Stop at the first failed command
set -euo pipefail

IMAGE="quay.io/mostmark/hello-world:latest"

# Run from the project root, wherever the script is called from
cd "$(dirname "$0")"

# Dockerfile.jvm copies the application from target/quarkus-app
mvn clean package

podman manifest rm "$IMAGE" 2>/dev/null || true
podman manifest create "$IMAGE"
podman build --platform linux/amd64,linux/arm64 --manifest "$IMAGE" -f src/main/docker/Dockerfile.jvm .
podman manifest push --all "$IMAGE"

echo ""
echo "=============================================="
echo "✅ Build and push successful!"
echo "Image pushed to: $IMAGE"
echo ""
echo "You can run the container using:"
echo "  podman run --rm -p 8080:8080 $IMAGE"
echo "=============================================="
