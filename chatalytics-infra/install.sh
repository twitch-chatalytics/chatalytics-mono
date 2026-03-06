#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Chatalytics K8s Deployment ==="
echo ""
echo "Prerequisites:"
echo "  1. PostgreSQL 'chatalytics' database and user must already exist"
echo "  2. secrets/ must have real values filled in"
echo ""

echo "Step 1: Namespace + Secrets"
kubectl apply -f base/
kubectl apply -f secrets/

echo ""
echo "Step 2: Infrastructure (Kafka + Redis)"
kubectl apply -f infrastructure/
kubectl -n chatalytics rollout status statefulset/redis --timeout=120s
kubectl -n chatalytics rollout status statefulset/kafka --timeout=120s

echo ""
echo "Step 3: API (runs Liquibase migrations)"
kubectl apply -f services/api.yaml
kubectl -n chatalytics rollout status deployment/api --timeout=300s

echo ""
echo "Step 4: Session Manager"
kubectl apply -f services/session-manager.yaml
kubectl -n chatalytics rollout status deployment/session-manager --timeout=300s

echo ""
echo "Step 5: IRC Producer + Consumer"
kubectl apply -f services/irc-producer.yaml
kubectl apply -f services/irc-consumer.yaml
kubectl -n chatalytics rollout status deployment/irc-producer --timeout=300s
kubectl -n chatalytics rollout status deployment/irc-consumer --timeout=300s

echo ""
echo "=== All services deployed ==="
echo ""
kubectl get pods -n chatalytics
