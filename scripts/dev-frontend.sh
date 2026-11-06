#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
exec npm --prefix frontend run dev
