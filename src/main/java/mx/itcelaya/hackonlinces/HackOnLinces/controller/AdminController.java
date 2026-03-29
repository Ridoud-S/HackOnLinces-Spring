package mx.itcelaya.hackonlinces.HackOnLinces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.ChangeRoleRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.CreateUserRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.ReviewSubmissionRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.ReviewUserRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.AdminUserResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.ApiResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.DashboardResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.SubmissionResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.WaitlistUserResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.AccountStatus;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType;
import mx.itcelaya.hackonlinces.HackOnLinces.security.AppUserDetails;
import mx.itcelaya.hackonlinces.HackOnLinces.service.AdminService;
import mx.itcelaya.hackonlinces.HackOnLinces.service.SubmissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "3. Administración",
        description = """
                Panel de control exclusivo para administradores (`role = ADMIN`).
                
                Permite gestionar usuarios, revisar la lista de espera y aprobar o rechazar
                las cartas de intención enviadas por usuarios externos.
                
                > Todos los endpoints requieren autenticación JWT con rol **ADMIN**.
                """
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final SubmissionService submissionService;

    // ── Dashboard ────────────────────────────────────────────────────────────

    @Operation(
            summary = "Dashboard",
            description = """
                    Devuelve todas las métricas del sistema en una sola llamada.
                    
                    Incluye conteos de usuarios por estado, tipo y rol,
                    así como estadísticas de cartas de intención por estado.
                    Ideal para pintar las tarjetas del panel de administración.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Métricas del sistema.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "Dashboard obtenido exitosamente",
                                      "data": {
                                        "users": {
                                          "total": 42,
                                          "pending": 10,
                                          "approved": 28,
                                          "rejected": 4,
                                          "internal": 15,
                                          "external": 27,
                                          "admins": 2,
                                          "judges": 3,
                                          "speakers": 5,
                                          "participants": 20,
                                          "guests": 12,
                                          "registeredToday": 3,
                                          "registeredThisWeek": 11
                                        },
                                        "submissions": {
                                          "total": 35,
                                          "pending": 8,
                                          "approved": 20,
                                          "rejected": 5,
                                          "resubmitRequired": 2
                                        }
                                      },
                                      "timestamp": "2025-06-01T10:00:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "El usuario autenticado no tiene rol ADMIN.",
                    content = @Content(mediaType = "application/json")
            )
    })
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.getDashboard(),
                "Dashboard obtenido exitosamente"
        ));
    }

    // ── Gestión de usuarios ──────────────────────────────────────────────────

    @Operation(
            summary = "Listar usuarios",
            description = """
                    Lista todos los usuarios del sistema con filtros opcionales combinables.
                    
                    **Ejemplos de uso:**
                    - `GET /admin/users` → todos los usuarios
                    - `GET /admin/users?status=PENDING` → solo pendientes de aprobación
                    - `GET /admin/users?userType=EXTERNAL` → solo usuarios externos
                    - `GET /admin/users?search=juan` → busca por nombre o email
                    - `GET /admin/users?status=APPROVED&userType=EXTERNAL` → combinados
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de usuarios.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "Usuarios obtenidos exitosamente",
                                      "data": [
                                        {
                                          "id": 5,
                                          "fullName": "Juan Pérez",
                                          "instituteName": "UNAM",
                                          "email": "juan@gmail.com",
                                          "userType": "EXTERNAL",
                                          "accountStatus": "PENDING",
                                          "roles": ["GUEST"],
                                          "totalSubmissions": 1,
                                          "createdAt": "2025-06-01T10:00:00",
                                          "updatedAt": "2025-06-01T10:00:00"
                                        }
                                      ],
                                      "timestamp": "2025-06-01T10:00:00"
                                    }
                                    """)
                    )
            )
    })
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> getAllUsers(
            @Parameter(description = "Filtrar por estado de cuenta: `PENDING`, `APPROVED`, `REJECTED`")
            @RequestParam(required = false) AccountStatus status,
            @Parameter(description = "Filtrar por tipo de usuario: `INTERNAL`, `EXTERNAL`")
            @RequestParam(required = false) UserType userType,
            @Parameter(description = "Buscar por nombre completo o email (parcial, sin distinción de mayúsculas)")
            @RequestParam(required = false) String search
    ) {
        List<AdminUserResponse> users = adminService.getAllUsers(status, userType, search);
        return ResponseEntity.ok(ApiResponse.ok(users, "Usuarios obtenidos exitosamente"));
    }

    @Operation(
            summary = "Crear usuario manualmente",
            description = """
                    Crea un usuario directamente desde el panel de administración.
                    
                    Útil para registrar jueces, ponentes u otros roles internos
                    sin pasar por el flujo de registro público.
                    
                    Si no se proporciona `password`, el sistema genera una contraseña temporal.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Usuario creado exitosamente.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "Usuario creado exitosamente",
                                      "data": {
                                        "id": 10,
                                        "fullName": "María Juez",
                                        "instituteName": "ITC",
                                        "email": "maria@itcelaya.edu.mx",
                                        "userType": "INTERNAL",
                                        "accountStatus": "APPROVED",
                                        "roles": ["JUDGE"],
                                        "totalSubmissions": 0,
                                        "createdAt": "2025-06-01T11:00:00",
                                        "updatedAt": "2025-06-01T11:00:00"
                                      },
                                      "timestamp": "2025-06-01T11:00:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Ya existe una cuenta con ese email.",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<AdminUserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        AdminUserResponse response = adminService.createUser(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response, "Usuario creado exitosamente"));
    }

    @Operation(
            summary = "Aprobar o rechazar usuario",
            description = """
                    Cambia el `accountStatus` de un usuario directamente, sin revisar su carta de intención.
                    
                    - `APPROVED` → también promueve automáticamente el rol a **PARTICIPANT** si era **GUEST**.
                    - `REJECTED` → la cuenta queda bloqueada.
                    - `PENDING` **no es una decisión válida** — el sistema lo rechazará.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Estado del usuario actualizado.",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Se intentó asignar el estado PENDING manualmente.",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado.",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PatchMapping("/users/{id}/review")
    public ResponseEntity<ApiResponse<AdminUserResponse>> reviewUser(
            @Parameter(description = "ID del usuario", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ReviewUserRequest request
    ) {
        AdminUserResponse response = adminService.reviewUser(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Usuario actualizado exitosamente"));
    }

    @Operation(
            summary = "Cambiar rol de usuario",
            description = """
                    Reemplaza el rol actual del usuario por el nuevo rol indicado.
                    
                    Roles disponibles: `ADMIN`, `JUDGE`, `SPEAKER`, `PARTICIPANT`, `GUEST`
                    
                    > ⚠️ Esta acción elimina el rol anterior y asigna el nuevo. No acumula roles.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Rol actualizado exitosamente.",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Usuario o rol no encontrado.",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PatchMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeRole(
            @Parameter(description = "ID del usuario", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request
    ) {
        AdminUserResponse response = adminService.changeRole(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Rol actualizado exitosamente"));
    }

    // ── Waitlist ─────────────────────────────────────────────────────────────

    @Operation(
            summary = "Lista de espera",
            description = """
                    Atajo rápido para ver todos los usuarios externos con estado `PENDING`.
                    
                    Equivale a `GET /admin/users?status=PENDING&userType=EXTERNAL`.
                    Son los usuarios que están esperando revisión de su carta de intención.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de usuarios en espera.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "Waitlist obtenida exitosamente",
                                      "data": [
                                        {
                                          "id": 5,
                                          "fullName": "Juan Pérez",
                                          "instituteName": "UNAM",
                                          "email": "juan@gmail.com",
                                          "userType": "EXTERNAL",
                                          "accountStatus": "PENDING",
                                          "roles": ["GUEST"],
                                          "createdAt": "2025-06-01T10:00:00"
                                        }
                                      ],
                                      "timestamp": "2025-06-01T10:00:00"
                                    }
                                    """)
                    )
            )
    })
    @GetMapping("/waitlist")
    public ResponseEntity<ApiResponse<List<WaitlistUserResponse>>> getWaitlist() {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.getWaitlist(),
                "Waitlist obtenida exitosamente"
        ));
    }

    // ── Submissions ──────────────────────────────────────────────────────────

    @Operation(
            summary = "Cartas de intención pendientes",
            description = """
                    Lista todas las cartas de intención con estado `PENDING`, ordenadas por fecha de envío ascendente.
                    
                    Estas son las cartas que el administrador debe revisar para aprobar o rechazar el acceso de usuarios externos.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de cartas de intención pendientes.",
                    content = @Content(mediaType = "application/json")
            )
    })
    @GetMapping("/submissions")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getPendingSubmissions() {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.getPendingSubmissions(),
                "Submissions pendientes obtenidas exitosamente"
        ));
    }

    @Operation(
            summary = "Detalle de una carta de intención",
            description = "Obtiene el detalle completo de cualquier carta de intención. El administrador puede ver todas, sin restricción de propietario."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Detalle de la carta de intención.",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Carta de intención no encontrada.",
                    content = @Content(mediaType = "application/json")
            )
    })
    @GetMapping("/submissions/{id}")
    public ResponseEntity<ApiResponse<SubmissionResponse>> getSubmissionById(
            @Parameter(description = "UUID de la carta de intención", required = true)
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                submissionService.getById(id, userDetails.getUser().getId(), true),
                "Submission obtenida exitosamente"
        ));
    }

    @Operation(
            summary = "Revisar carta de intención",
            description = """
                    Aprueba, rechaza o solicita el reenvío de una carta de intención.
                    
                    **Decisiones posibles:**
                    - `APPROVED` → el usuario pasa automáticamente a `accountStatus: APPROVED` y rol `PARTICIPANT`
                    - `REJECTED` → la solicitud se deniega definitivamente
                    - `RESUBMIT_REQUIRED` → se solicita una nueva carta al usuario
                    
                    **Reglas:**
                    - Solo se pueden revisar cartas con estado `PENDING`.
                    - `PENDING` no es una decisión válida — el sistema lo rechazará.
                    - El campo `adminComment` es opcional pero recomendado para `REJECTED` y `RESUBMIT_REQUIRED`.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Carta de intención revisada.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "Submission revisada exitosamente",
                                      "data": {
                                        "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                        "userId": 5,
                                        "attemptNumber": 1,
                                        "status": "APPROVED",
                                        "reason": "Carta de intención aceptada.",
                                        "reviewedById": 1,
                                        "reviewedAt": "2025-06-02T09:00:00",
                                        "sentAt": "2025-06-01T10:30:00",
                                        "createdAt": "2025-06-01T10:30:00",
                                        "documents": []
                                      },
                                      "timestamp": "2025-06-02T09:00:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "La carta de intención ya fue revisada anteriormente.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "message": "Esta submission ya fue revisada con estado: APPROVED",
                                      "timestamp": "2025-06-02T09:00:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Carta de intención no encontrada.",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PatchMapping("/submissions/{id}/review")
    public ResponseEntity<ApiResponse<SubmissionResponse>> reviewSubmission(
            @Parameter(description = "UUID de la carta de intención", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody ReviewSubmissionRequest request,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        SubmissionResponse response = adminService.reviewSubmission(
                id, userDetails.getUser().getId(), request
        );
        return ResponseEntity.ok(ApiResponse.ok(response, "Submission revisada exitosamente"));
    }
}