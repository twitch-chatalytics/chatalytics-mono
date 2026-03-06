#!/usr/bin/env bash
set -euo pipefail

IMAGE="peavers/chatalytics-react-ui:latest"
NAMESPACE="chatalytics"
DEPLOYMENT="react-ui"

echo "==> Building and pushing Docker image (linux/amd64)..."
docker buildx build --platform linux/amd64 -t "$IMAGE" --push .

echo "==> Restarting deployment..."
kubectl rollout restart deployment/"$DEPLOYMENT" -n "$NAMESPACE"

echo "==> Waiting for rollout..."
kubectl rollout status deployment/"$DEPLOYMENT" -n "$NAMESPACE" --timeout=120s

echo "==> Deploy complete!"
