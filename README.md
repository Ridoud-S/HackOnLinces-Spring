# HackOnLinces — Backend API

**Backend REST API** del sistema de gestión de OnLinces, el club de programación del TecNM en Celaya.

---

## Tabla de contenidos

- [Descripción general](#descripción-general)
- [Contexto funcional](#contexto-funcional)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Requisitos previos](#requisitos-previos)
- [Configuración del entorno](#configuración-del-entorno)
- [Ejecución con Docker](#ejecución-con-docker)
- [Ejecución local con Maven](#ejecución-local-con-maven)
- [Para el equipo de frontend](#para-el-equipo-de-frontend)
- [API Endpoints](#api-endpoints)
- [Modelos de datos](#modelos-de-datos)
- [Roles y permisos](#roles-y-permisos)
- [Troubleshooting](#troubleshooting)

---

## Descripción general

**HackOnLinces** es el backend API de gestión para **OnLinces**, el club de programación del Instituto Tecnológico de Celaya (ITcelaya). El sistema centraliza la administración de participantes, eventos y competencias del club.

Funcionalidades principales:

- Registro y autenticación de miembros internos (Google OAuth2) y externos (email/password)
- Validación automática de dominio `@itcelaya.edu.mx` para usuarios internos
- Gestión de roles y permisos para diferentes tipos de usuarios
- Sistema de admisión con cartas de intención y carga de archivos
- Panel administrativo con métricas, dashboards y revisión de candidatos
- API REST documentada con Swagger/OpenAPI
- Almacenamiento de archivos para documentos y evidencias

---

## Contexto funcional

### Tipos de usuarios

| Tipo | Descripción |
|------|-------------|
| **INTERNAL** | Miembros del ITcelaya (`@itcelaya.edu.mx`) — estudiantes, profesores, staff |
| **EXTERNAL** | Participantes externos de otras instituciones |

### Estados de cuenta

| Estado | Significado |
|--------|-------------|
| **PENDING** | Esperando revisión |
| **APPROVED** | Aprobado para participar |
| **REJECTED** | Rechazado, sin acceso |

### Flujo de admisión

```
1. REGISTRO           → Estado: PENDING, Rol: GUEST
2. CARTA DE INTENCIÓN → Usuario externo sube PDF
3. REVISIÓN ADMIN
   ├─ APROBADA        → Estado: APPROVED, Rol: PARTICIPANT
   ├─ RECHAZADA       → Estado: REJECTED
   └─ REENVÍO         → Usuario puede volver a intentar
4. PARTICIPACIÓN      → Acceso a hackathons y eventos
```

### Roles

| Rol | Descripción |
|-----|-------------|
| **ADMIN** | Crear usuarios, revisar admisiones, ver dashboard |
| **JUDGE** | Acceso a info de participantes, calificación |
| **SPEAKER** | Acceso a recursos del evento |
| **PARTICIPANT** | Acceso completo a eventos del club |
| **GUEST** | Puede enviar carta de intención |

---

## Stack tecnológico

| Componente | Versión |
|------------|---------|
| Java | 21 |
| Spring Boot | 3.5.13 |
| PostgreSQL | 16 |
| JWT (JJWT) | 0.12.6 |
| Springdoc OpenAPI | 2.8.5 |
| Maven | 3.9+ |

---

## Estructura del proyecto

```
HackOnLinces/
├── Dockerfile
├── .env.example
├── pom.xml
├── mvnw
├── docker/
│   ├── compose.yaml
│   └── README.md               ← instrucciones de Docker y Dokploy
└── src/main/
    ├── java/.../HackOnLinces/
    │   ├── config/             # SecurityConfig, OpenApiConfig, DataInitializer
    │   ├── controller/         # AuthController, AdminController, SubmissionController
    │   ├── service/            # AuthService, AdminService, SubmissionService
    │   │   └── strategy/       # RegistrationFactory + estrategias de registro
    │   ├── repository/
    │   ├── entity/             # User, Role, UserRole, Submission, Document
    │   ├── dto/
    │   │   ├── request/
    │   │   └── response/
    │   ├── mapper/
    │   ├── security/           # JwtUtil, JwtAuthFilter, OAuth2SuccessHandler
    │   ├── enums/
    │   └── exception/
    └── resources/
        ├── application.yaml
        ├── application-dev.yaml
        └── application-prod.yaml
```

---

## Requisitos previos

### Con Docker (recomendado)
- Docker 20.10+
- Docker Compose v2+

### Sin Docker
- Java 21+
- Maven 3.9+
- PostgreSQL 16+

---

## Configuración del entorno

Las variables de entorno necesarias están documentadas en `.env.example` en la raíz del proyecto. Copia ese archivo y renómbralo a `.env`:

```bash
cp .env.example .env
```

Edita `.env` con tus credenciales reales. Este archivo nunca debe subirse al repositorio — ya está incluido en `.gitignore`.

---

## Ejecución con Docker

> Para instrucciones detalladas de Docker y despliegue en Dokploy, consulta [`docker/README.md`](docker/README.md).

Comando rápido desde la raíz del proyecto:

```bash
docker compose --env-file .env -f docker/compose.yaml up --build
```

La aplicación queda disponible en `http://localhost:8080/api/v1`.

---

## Ejecución local con Maven

### 1. Preparar la base de datos

```bash
psql -U postgres -c 'CREATE DATABASE "HackOnLinces";'
```

### 2. Exportar variables de entorno

```bash
export SPRING_PROFILES_ACTIVE=dev
# ... resto de variables del .env
```

### 3. Compilar y ejecutar

```bash
# Compilar
mvn clean package -DskipTests

# Ejecutar
java -jar target/HackOnLinces-0.0.1-SNAPSHOT.jar

# O directamente con Maven
mvn spring-boot:run
```

---

## Para el equipo de frontend

**Base URL:** `http://localhost:8080/api/v1`

**Swagger UI:** `http://localhost:8080/api/v1/swagger-ui.html`
Disponible solo con perfil `dev`. Usa el botón **Authorize 🔒** con tu token JWT para probar los endpoints.

---

### Autenticación

**Usuarios externos — email + password:**

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "estudiante@unam.mx",
  "password": "Password123!"
}
```

La respuesta incluye un campo `token`. Envíalo en cada request protegido:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Usuarios internos — Google OAuth2 (`@itcelaya.edu.mx`):**

Redirige al usuario a:

```
GET /oauth2/authorization/google
```

Google autentica, el backend valida el dominio institucional y redirige al frontend con el JWT.

---

### Estructura de respuesta estándar

```json
{
  "success": true,
  "message": "Descripción del resultado",
  "data": { },
  "timestamp": "2025-06-01T10:00:00"
}
```

En caso de error:

```json
{
  "success": false,
  "message": "Descripción del error",
  "timestamp": "2025-06-01T10:00:00"
}
```

Para errores de validación (`400`):

```json
{
  "success": false,
  "message": "Error de validación en los campos enviados",
  "errors": {
    "email": "El email no tiene un formato válido"
  },
  "timestamp": "2025-06-01T10:00:00"
}
```

---

### Códigos HTTP a manejar

| Código | Cuándo ocurre |
|--------|---------------|
| `200` | Operación exitosa |
| `201` | Recurso creado |
| `400` | Datos de entrada inválidos |
| `401` | Token JWT ausente, expirado o inválido |
| `403` | Sin permisos para esta acción |
| `404` | Recurso no encontrado |
| `409` | Conflicto — recurso ya existe o estado no lo permite |

---

### Enviar carta de intención

```http
POST /api/v1/submissions
Authorization: Bearer <token>
Content-Type: multipart/form-data

file:   (PDF obligatorio)
reason: "Texto opcional de motivación"
```

Restricciones a validar en el frontend antes de llamar al endpoint:

- Solo usuarios con `userType: EXTERNAL` pueden enviarla
- No se puede enviar si ya hay una submission en estado `PENDING`
- No se puede enviar si la cuenta ya está `APPROVED`
- Hay un límite máximo de intentos — si se alcanza el backend responde `403`

---

## API Endpoints

**Base URL:** `http://localhost:8080/api/v1`

### Autenticación (`/auth`)

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `POST` | `/auth/register` | Registro de usuario externo | No |
| `POST` | `/auth/login` | Login con email y password | No |
| `GET` | `/oauth2/authorization/google` | Inicio de flujo OAuth2 Google | No |

### Submissions (`/submissions`)

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `POST` | `/submissions` | Enviar carta de intención (multipart) | JWT |
| `GET` | `/submissions` | Listar mis submissions | JWT |
| `GET` | `/submissions/{id}` | Detalle de una submission | JWT |

### Administración (`/admin`) — requiere rol `ADMIN`

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `GET` | `/admin/dashboard` | Métricas del sistema | JWT + ADMIN |
| `GET` | `/admin/users` | Listar usuarios con filtros opcionales | JWT + ADMIN |
| `POST` | `/admin/users` | Crear usuario manualmente | JWT + ADMIN |
| `PATCH` | `/admin/users/{id}/review` | Aprobar o rechazar usuario | JWT + ADMIN |
| `PATCH` | `/admin/users/{id}/role` | Cambiar rol de usuario | JWT + ADMIN |
| `GET` | `/admin/waitlist` | Usuarios externos pendientes | JWT + ADMIN |
| `GET` | `/admin/submissions` | Cartas pendientes de revisión | JWT + ADMIN |
| `GET` | `/admin/submissions/{id}` | Detalle de cualquier carta | JWT + ADMIN |
| `PATCH` | `/admin/submissions/{id}/review` | Revisar carta de intención | JWT + ADMIN |

---

## Modelos de datos

### User

```json
{
  "id": 1,
  "fullName": "Juan Pérez",
  "email": "juan@gmail.com",
  "instituteName": "UNAM",
  "userType": "EXTERNAL",
  "accountStatus": "PENDING",
  "roles": ["GUEST"],
  "createdAt": "2025-06-01T10:00:00"
}
```

### Submission

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userId": 5,
  "attemptNumber": 1,
  "status": "PENDING",
  "reason": null,
  "reviewedById": null,
  "reviewedAt": null,
  "sentAt": "2025-06-01T10:30:00",
  "documents": [
    {
      "id": "doc-uuid",
      "originalName": "carta.pdf",
      "mimeType": "application/pdf",
      "size": 204800,
      "uploadedAt": "2025-06-01T10:30:00"
    }
  ]
}
```

### Dashboard (solo ADMIN)

```json
{
  "users": {
    "total": 42,
    "pending": 10,
    "approved": 28,
    "rejected": 4,
    "internal": 15,
    "external": 27
  },
  "submissions": {
    "total": 35,
    "pending": 8,
    "approved": 20,
    "rejected": 5,
    "resubmitRequired": 2
  }
}
```

---

## Roles y permisos

| Acción | ADMIN | JUDGE | SPEAKER | PARTICIPANT | GUEST |
|--------|:-----:|:-----:|:-------:|:-----------:|:-----:|
| Ver dashboard | ✅ | ❌ | ❌ | ❌ | ❌ |
| Gestionar usuarios | ✅ | ❌ | ❌ | ❌ | ❌ |
| Revisar submissions | ✅ | ❌ | ❌ | ❌ | ❌ |
| Enviar submission | ✅ | ✅ | ✅ | ✅ | ✅ |
| Ver perfil propio | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## Troubleshooting

### La app no conecta a la BD en Docker

```bash
docker compose --env-file .env -f docker/compose.yaml ps
docker compose --env-file .env -f docker/compose.yaml logs db
```

### Puerto 8080 en uso

```bash
# Linux/Mac
lsof -i :8080

# Windows
netstat -ano | findstr :8080
```

Cambia el puerto en `.env`:
```
PORT=8081
```

### Error CORS desde el frontend

Verifica que `CORS_ALLOWED_ORIGINS` en `.env` incluye exactamente el origen del frontend con protocolo y puerto.

### Reconstruir imagen después de cambios en código

```bash
docker compose --env-file .env -f docker/compose.yaml up --build --force-recreate app
```

---

**Versión:** `0.0.2-SNAPSHOT` · **Última actualización:** Marzo 2026  
**OnLinces** — Club de Programación del TecNM en Celaya