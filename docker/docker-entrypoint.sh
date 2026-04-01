#!/usr/bin/env sh
set -eu

RUNTIME_ENV_FILE="/app/.runtime-generated.env"

# Reutilizar valores generados anteriormente para no cambiarlos en cada redeploy
if [ -f "$RUNTIME_ENV_FILE" ]; then
  set -a
  . "$RUNTIME_ENV_FILE"
  set +a
fi

generated_any=0

# Generar JWT_SECRET si no está definido o es el valor por defecto
if [ -z "${JWT_SECRET:-}" ] || [ "$JWT_SECRET" = "cambia-este-secreto-en-produccion-minimo-256-bits" ]; then
  JWT_SECRET="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 64)"
  export JWT_SECRET
  generated_any=1
fi

# Generar password de BD si no está definida
if [ -z "${DB_PASSWORD:-}" ] || [ "$DB_PASSWORD" = "hackonlinces_pass_2024" ]; then
  DB_PASSWORD="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
  export DB_PASSWORD
  generated_any=1
fi

if [ "$generated_any" -eq 1 ]; then
  {
    printf 'JWT_SECRET=%s\n' "$JWT_SECRET"
    printf 'DB_PASSWORD=%s\n' "$DB_PASSWORD"
  } > "$RUNTIME_ENV_FILE"
  chmod 600 "$RUNTIME_ENV_FILE"

  echo '=== GENERATED ENV (PEGA ESTO EN DOKPLOY ENV VARS) ==='
  echo "JWT_SECRET=$JWT_SECRET"
  echo "DB_PASSWORD=$DB_PASSWORD"
  echo 'Pega estos valores en Dokploy para que no cambien en cada redeploy.'
  echo '======================================================'
fi

exec sh -c "java ${JAVA_OPTS:-} -jar /app/app.jar"