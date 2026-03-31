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

| Variable | Descripción |
|---|---|
| `POSTGRES_DB` | Nombre de la base de datos |
| `POSTGRES_USER` | Usuario de PostgreSQL |
| `POSTGRES_PASSWORD` | Contraseña de PostgreSQL |
| `SPRING_PROFILES_ACTIVE` | Usar `prod` |
| `PORT` | Puerto de la app (8080) |
| `JWT_SECRET` | Secreto JWT mínimo 256 bits — genera con `openssl rand -hex 32` |
| `JWT_EXPIRATION` | Expiración en ms (86400000 = 24h) |
| `GOOGLE_CLIENT_ID` | Client ID de Google OAuth2 |
| `GOOGLE_CLIENT_SECRET` | Client Secret de Google OAuth2 |
| `CORS_ALLOWED_ORIGINS` | Dominio del frontend en producción |

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