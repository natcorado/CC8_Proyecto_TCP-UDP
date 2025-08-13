#!/usr/bin/env bash
set -euo pipefail
PORT="${1:-}"

if [ -z "$PORT" ]; then
  echo "Uso: $0 <puerto>"
  exit 1
fi

OS="$(uname -s)"

echo "[INFO] Liberando puerto $PORT..."

if command -v fuser >/dev/null 2>&1; then
  fuser -k -n tcp "$PORT" 2>/dev/null && echo "[OK] fuser mató procesos en $PORT" || echo "[INFO] fuser no encontró nada"
fi

if command -v lsof >/dev/null 2>&1; then
  PIDS="$(lsof -nP -iTCP:$PORT -sTCP:LISTEN -t || true)"
  if [ -n "$PIDS" ]; then
    echo "$PIDS" | xargs -r kill -9
    echo "[OK] lsof/kill liberó $PORT"
  fi
fi

# Si estás en WSL/Git Bash o mac y nada funcionó, intenta netstat
if command -v netstat >/dev/null 2>&1; then
  PIDS=$(netstat -ano 2>/dev/null | grep ":$PORT" | awk '{print $5}' | sort -u || true)
  if [ -n "$PIDS" ]; then
    if command -v taskkill >/dev/null 2>&1; then
      for p in $PIDS; do taskkill /PID "$p" /F 2>/dev/null || true; done
      echo "[OK] taskkill liberó $PORT"
    fi
  fi
fi

echo "[DONE] Revisión completa."