#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="chatalytics"
DOCKER_ORG="peavers"

# All deployable services
JAVA_SERVICES=(api irc-consumer irc-producer session-manager)
ALL_SERVICES=("${JAVA_SERVICES[@]}" react-ui)

# If arguments provided, deploy only those; otherwise deploy everything
if [[ $# -gt 0 ]]; then
  SERVICES=("$@")
else
  SERVICES=("${ALL_SERVICES[@]}")
fi

# Validate requested services
for svc in "${SERVICES[@]}"; do
  found=false
  for valid in "${ALL_SERVICES[@]}"; do
    [[ "$svc" == "$valid" ]] && found=true && break
  done
  if ! $found; then
    echo "Unknown service: $svc"
    echo "Valid services: ${ALL_SERVICES[*]}"
    exit 1
  fi
done

echo "==> Deploying: ${SERVICES[*]}"

# Collect which Java services need building
JAVA_TO_BUILD=()
for svc in "${SERVICES[@]}"; do
  for java_svc in "${JAVA_SERVICES[@]}"; do
    [[ "$svc" == "$java_svc" ]] && JAVA_TO_BUILD+=("$svc")
  done
done

# Build and push Java services with Jib
if [[ ${#JAVA_TO_BUILD[@]} -gt 0 ]]; then
  # If deploying all Java services, just run jib from root
  if [[ ${#JAVA_TO_BUILD[@]} -eq ${#JAVA_SERVICES[@]} ]]; then
    echo "==> Building and pushing all Java services..."
    ./gradlew jib
  else
    for svc in "${JAVA_TO_BUILD[@]}"; do
      echo "==> Building and pushing chatalytics-${svc}..."
      ./gradlew ":chatalytics-${svc}:jib"
    done
  fi
fi

# Build and push React UI
for svc in "${SERVICES[@]}"; do
  if [[ "$svc" == "react-ui" ]]; then
    echo "==> Building and pushing react-ui..."
    (cd chatalytics-react-ui && docker buildx build --platform linux/amd64 -t "${DOCKER_ORG}/chatalytics-react-ui:latest" --push .)
  fi
done

# Restart deployments
for svc in "${SERVICES[@]}"; do
  echo "==> Restarting ${svc}..."
  kubectl rollout restart deployment/"$svc" -n "$NAMESPACE"
done

# Wait for rollouts
for svc in "${SERVICES[@]}"; do
  echo "==> Waiting for ${svc}..."
  kubectl rollout status deployment/"$svc" -n "$NAMESPACE" --timeout=120s
done

echo "==> Deploy complete!"
