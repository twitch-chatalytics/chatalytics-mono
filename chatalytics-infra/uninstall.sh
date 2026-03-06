#!/bin/bash
set -e

echo "=== Tearing down Chatalytics ==="
echo ""
echo "This will delete all pods, services, secrets, and PVCs in the chatalytics namespace."
echo "The PostgreSQL database and user will NOT be affected."
echo ""
read -p "Continue? (y/N) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Aborted."
  exit 0
fi

kubectl delete namespace chatalytics --ignore-not-found

echo ""
echo "=== Chatalytics removed ==="
