# Docker — HackOnLinces

Instrucciones para levantar el proyecto con Docker, tanto en local como en Dokploy.

---

## Estructura

```
HackOnLinces/
├── Dockerfile          ← build de la aplicación
├── .env.example        ← plantilla de variables (copia y renombra a .env)
├── .env                ← tus credenciales reales (nunca subir al repo)
└── docker/
    ├── compose.yaml    ← orquestación de servicios
    └── README.md       ← este archivo
```

---

## Desarrollo local

### 1. Copiar las variables de entorno

```bash
cp .env.example .env
```

Edita `.env` con tus credenciales reales.

### 2. Levantar los servicios

Desde la raíz del proyecto:

```bash
docker compose --env-file .env -f docker/compose.yaml up --build
```

La aplicación queda disponible en `http://localhost:8080/api/v1`.

Para verificar que todo está corriendo:

```bash
docker compose --env-file .env -f docker/compose.yaml ps
```

### 3. Ver logs

```bash
# Logs en tiempo real
docker compose --env-file .env -f docker/compose.yaml logs -f app

# Solo la BD
docker compose --env-file .env -f docker/compose.yaml logs -f db
```

### 4. Detener los servicios

```bash
docker compose --env-file .env -f docker/compose.yaml down
```

Para eliminar también los volúmenes (borra los datos de la BD):

```bash
docker compose --env-file .env -f docker/compose.yaml down -v
```

---

## Cómo se conecta la app a PostgreSQL

Dentro de Docker los contenedores se comunican por nombre de servicio en la red `hackonlinces-net`. Por eso la URL de conexión es:

```
jdbc:postgresql://db:5432/HackOnLinces
```

El nombre `db` resuelve al contenedor de PostgreSQL — `localhost` no funciona dentro de Docker porque cada contenedor tiene su propia red interna.

---

## Despliegue en Dokploy

Dokploy no usa el archivo `.env` local. Las variables se configuran directamente en su panel bajo **Environment Variables**.

Variables que debes configurar en Dokploy:

### Base de datos (requeridas)

| Variable | Descripción | Requerida |
|---|---|---|
| `POSTGRES_DB` | Nombre de la base de datos | ✅ Sí |
| `POSTGRES_USER` | Usuario de PostgreSQL | ✅ Sí |
| `POSTGRES_PASSWORD` | Contraseña de PostgreSQL | ✅ Sí |
| `DB_URL` | URL de conexión JDBC: `jdbc:postgresql://db:5432/NOMBRE_BD` | ✅ Sí |
| `DB_USERNAME` | Usuario de la aplicación para conectar a BD (igual a `POSTGRES_USER`) | ✅ Sí |
| `DB_PASSWORD` | Contraseña para la app (igual a `POSTGRES_PASSWORD`) | ✅ Sí |

### Autenticación y seguridad (requeridas)

| Variable | Descripción | Requerida |
|---|---|---|
| `JWT_SECRET` | Secreto JWT mínimo 256 bits — genera con `openssl rand -hex 32` | ✅ Sí |
| `GOOGLE_CLIENT_ID` | Client ID de Google OAuth2 (para usuarios internos @itcelaya.edu.mx) | ✅ Sí |
| `GOOGLE_CLIENT_SECRET` | Client Secret de Google OAuth2 | ✅ Sí |
| `CORS_ALLOWED_ORIGINS` | Dominio del frontend en producción (ej: `https://app.tudominio.com`) | ✅ Sí |
| `INTERNAL_EMAIL_DOMAIN` | Dominio de email institucional (default: `itcelaya.edu.mx`) | ✅ Sí |

### Spring Boot y servidor (opcionales)

| Variable | Descripción | Requerida |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil de Spring: `dev` o `prod` (por defecto `prod`) | ❌ No |
| `PORT` | Puerto de la app (por defecto `8080`) | ❌ No |
| `JWT_EXPIRATION` | Expiración en ms (por defecto `86400000` = 24h) | ❌ No |

### Uploads y límites de archivos (opcionales)

| Variable | Descripción | Requerida |
|---|---|---|
| `MAX_FILE_SIZE` | Tamaño máximo de archivo individual (por defecto `10MB`) | ❌ No |
| `MAX_REQUEST_SIZE` | Tamaño máximo de request completo (por defecto `30MB`) | ❌ No |
| `UPLOAD_DIR` | Directorio de uploads (por defecto `/app/uploads`) | ❌ No |

### Configuración de negocio (opcionales)

| Variable | Descripción | Requerida |
|---|---|---|
| `SUBMISSION_MAX_ATTEMPTS` | Máximo de intentos para hacer submission (por defecto `5`) | ❌ No |

### Correspondencia de variables de base de datos

Las variables de base de datos tienen dos "conjuntos" por una razón técnica:

- **`POSTGRES_*`** → Se usan para inicializar el contenedor PostgreSQL
- **`DB_*`** → Se usan por la aplicación Spring para conectarse

Deben tener los mismos valores:

```
POSTGRES_USER = DB_USERNAME
POSTGRES_PASSWORD = DB_PASSWORD
```

La `DB_URL` debe construirse así:
```
DB_URL = jdbc:postgresql://db:5432/{valor_de_POSTGRES_DB}
```

**Ejemplo correcto para Dokploy:**
```
POSTGRES_DB=HackOnLinces
POSTGRES_USER=hackonlinces_user
POSTGRES_PASSWORD=mysecurepassword123!@#
DB_USERNAME=hackonlinces_user
DB_PASSWORD=mysecurepassword123!@#
DB_URL=jdbc:postgresql://db:5432/HackOnLinces
```

⚠️ **ANTI-FALLOS para Dokploy:**
- No uses `localhost` en `DB_URL` — usa `db` (el nombre del servicio en Docker Compose)
- Si copias la contraseña desde el `.env.example` de ejemplo, es obvio que fallará en producción
- Las 3 variables `DB_*` DEBEN coincidir con `POSTGRES_*` — no pueden estar desincronizadas
- Usa caracteres especiales seguros en contraseña: `!@#$%^&*` (evita comillas y espacios)

### Google OAuth2 — Usuarios internos (@itcelaya.edu.mx)

El sistema solo permite login con Google para usuarios cuyo email termina en `@itcelaya.edu.mx`. Esto se valida en el backend automáticamente.

Si intentas hacer login con `@gmail.com` u otro dominio, serás rechazado con un mensaje claro. Esto está validado en la estrategia de autenticación OAuth2.

**Pasos para obtener credenciales:**
1. Ir a https://console.cloud.google.com
2. Crear un nuevo proyecto (o usar uno existente)
3. Habilitar "Google+ API"
4. Crear "OAuth 2.0 Client ID" de tipo "Web application"
5. Agregar URL autorizadas:
   - En dev: `http://localhost:8080/login/oauth2/code/google`
   - En prod: `https://tudominio.com/login/oauth2/code/google`
6. Copiar `Client ID` y `Client Secret` a las variables

### CORS — Configuración para producción

`CORS_ALLOWED_ORIGINS` especifica qué dominios pueden hacer requests al backend:

```
# En desarrollo (permite varios puertos)
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:8080

# En producción (IMPORTANTE: HTTPS)
CORS_ALLOWED_ORIGINS=https://app.tudominio.com
```

⚠️ **Anti-fallos CORS:**
- En producción, SIEMPRE usa `https://` (nunca `http://`)
- Especifica SOLO el dominio del frontend real
- Si el frontend está en subdomain: `https://app.tudominio.com` (sin trailing slash)
- Si tienes múltiples frontends (ej: app principal + admin): separa por comas sin espacios

### INTERNAL_EMAIL_DOMAIN — Validación de usuarios internos

Esta variable controla qué dominio de email se considera "interno" (con acceso a OAuth2 de Google):

```
INTERNAL_EMAIL_DOMAIN=itcelaya.edu.mx
```

Solo usuarios con email `algo@itcelaya.edu.mx` pueden hacer login con OAuth2. Los demás pueden:
- Registrarse manualmente (EXTERNAL)
- Pero NO pueden hacer OAuth2

Esto está hardcodeado en `application.yaml` pero configurable vía variable de entorno.

### Pasos en Dokploy

1. Conecta tu repositorio
2. Selecciona **Docker Compose** como tipo de despliegue
3. Apunta al archivo `docker/compose.yaml`
4. Configura todas las variables de la tabla anterior
5. Despliega

---

## Notas

- El perfil `prod` desactiva Swagger y reduce los logs.
- Los archivos subidos por usuarios se persisten en el volumen `uploads_data`.
- El usuario admin seed se crea automáticamente al primer arranque — cambia su contraseña en producción.

---

### 📦 Docker Compose

El archivo `docker/compose.yaml` orquesta dos servicios:

**Servicio `db` (PostgreSQL 16):**
- Imagen: `postgres:16-alpine` (ligera y actualizada)
- Variables: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (inyectadas desde `.env`)
- Healthcheck: Valida cada 10s que la BD está lista antes de que la app intente conectar
- Volumen: `postgres_data` persiste la BD entre reinicios
- Red: `hackonlinces-net` (comunicación interna)
- `expose: 5432` (no publica el puerto al host — solo internamente)

**Servicio `app` (Spring Boot):**
- Build: Compila desde Dockerfile en la raíz
- Dependencia: Espera a que `db` pase el healthcheck
- Env: Recibe todas las variables configuradas en Dokploy
- Volumen: `uploads_data` persiste los archivos subidos por usuarios
- Puerto: Expone `${PORT:-8080}:8080` (configurable, default 8080)
- Restart: `unless-stopped` (reinicia automáticamente si cae)

**Volúmenes nombrados:**
- `postgres_data`: Datos de PostgreSQL
- `uploads_data`: Archivos PDF/documentos subidos

**Red bridge `hackonlinces-net`:**
- Aislamiento de red entre contenedores
- Los contenedores se comunican por nombre (`db`, `app`)
- No es accesible desde el host (seguridad)

**Cloud-ready:** ✅
- Healthcheck evita race conditions (app intenta conectar a BD sin estar lista)
- Volúmenes nombrados persisten datos correctamente
- Variables inyectadas (no hardcodeadas)
- `depends_on` + `condition: service_healthy` garantiza orden de inicio
- Red aislada (seguridad)
- Puertos configurables

### 🔐 Consideraciones de seguridad

1. **Usuario no-root en Dockerfile** — Reduce riesgos si el contenedor es comprometido
2. **Base de datos no expuesta** — Solo la app puede conectar (port 5432 expuesto internamente)
3. **Variables secretas** — No están en código, se inyectan en Dokploy
4. **HTTPS en CORS** — En prod, el frontend debe estar en HTTPS
5. **Healthcheck** — Evita que la app intente conectar antes de que la BD esté lista

### ⚙️ Escalabilidad futura

Si necesitas escalar en el futuro:
- **Redis para caché:** Agregar un servicio `redis` en Compose
- **Migrations:** Cambiar `ddl-auto: update` a `validate` + Flyway/Liquibase
- **Logs centralizados:** ELK Stack o similar
- **Certificados SSL:** En Dokploy, usar Let's Encrypt automáticamente
- **Auto-scaling:** Dokploy soporta replicas de contenedores
