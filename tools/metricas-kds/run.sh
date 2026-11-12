#!/usr/bin/env bash
#
# Ejecucion de la herramienta de medicion de propagacion de eventos del KDS.
#
# Las credenciales NO se guardan en el repositorio: se pasan por variables de
# entorno. Ejemplo:
#
#   KDS_ADMIN_EMAIL=admin@kds.local \
#   KDS_ADMIN_PASSWORD='****' \
#   KDS_NUM_PRUEBAS=20 \
#   ./run.sh
#
set -euo pipefail

cd "$(dirname "$0")"

# --- Java 21 -------------------------------------------------------------
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_CMD="${JAVA_HOME}/bin/java"
else
  JAVA_CMD="$(command -v java || true)"
fi

if [[ -z "${JAVA_CMD}" ]]; then
  echo "[ERROR] No se encuentra java en el PATH. Instala JDK 21 o define JAVA_HOME." >&2
  exit 1
fi

JAVA_MAJOR="$("$JAVA_CMD" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [[ "${JAVA_MAJOR}" -lt 21 ]]; then
  echo "[ERROR] Se requiere Java 21 o superior (detectado: ${JAVA_MAJOR})." >&2
  echo "        Ejemplo: JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./run.sh" >&2
  exit 1
fi

# --- Validacion de la configuracion minima -------------------------------
if [[ -z "${KDS_ADMIN_EMAIL:-}" || -z "${KDS_ADMIN_PASSWORD:-}" ]]; then
  cat >&2 <<'EOF'
[ERROR] Faltan las credenciales de la prueba.
        Define KDS_ADMIN_EMAIL y KDS_ADMIN_PASSWORD con un usuario ADMIN del KDS.

        KDS_ADMIN_EMAIL=... KDS_ADMIN_PASSWORD=... ./run.sh
EOF
  exit 2
fi

# --- Parametros con valores por defecto ----------------------------------
export KDS_BASE_URL="${KDS_BASE_URL:-http://localhost:8080}"
export KDS_NUM_PRUEBAS="${KDS_NUM_PRUEBAS:-20}"
export KDS_TIMEOUT_MS="${KDS_TIMEOUT_MS:-10000}"
export KDS_POLL_INTERVAL_MS="${KDS_POLL_INTERVAL_MS:-10}"
export KDS_DIR_RESULTADOS="${KDS_DIR_RESULTADOS:-resultados}"

export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$JAVA_CMD")")")}"

echo "=================================================="
echo "MEDICION DE PROPAGACION DE EVENTOS KDS"
echo "=================================================="
echo "Gateway          : ${KDS_BASE_URL}"
echo "Pruebas          : ${KDS_NUM_PRUEBAS}"
echo "Timeout          : ${KDS_TIMEOUT_MS} ms"
echo "Intervalo sondeo : ${KDS_POLL_INTERVAL_MS} ms"
echo "Resultados en    : $(pwd)/${KDS_DIR_RESULTADOS}"
echo

exec ./mvnw -q compile exec:java
