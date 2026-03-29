package mx.itcelaya.hackonlinces.HackOnLinces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.LoginRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.RegisterRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.ApiResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.AuthResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "1. Autenticación",
        description = """
                Endpoints públicos para registro y login local.
                El token JWT devuelto debe enviarse en el header `Authorization: Bearer <token>`
                en todos los endpoints protegidos.
                """
)
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Registrar usuario externo",
            description = """
                    Crea una nueva cuenta para un usuario externo (fuera de @itcelaya.edu.mx).
                    
                    La cuenta se crea automáticamente con:
                    - `accountStatus`: **PENDING**
                    - `role`: **GUEST**
                    
                    El usuario deberá enviar su carta de intención para ser revisado por el administrador.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Usuario registrado. Cuenta pendiente de aprobación.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "Usuario registrado exitosamente. Tu cuenta está pendiente de aprobación.",
                                      "data": {
                                        "token": "eyJhbGciOiJIUzI1NiJ9...",
                                        "tokenType": "Bearer",
                                        "userId": 5,
                                        "fullName": "Juan Pérez",
                                        "email": "juan@gmail.com",
                                        "userType": "EXTERNAL",
                                        "accountStatus": "PENDING",
                                        "roles": ["GUEST"]
                                      },
                                      "timestamp": "2025-06-01T10:00:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Ya existe una cuenta con ese email.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "message": "Ya existe una cuenta con el email: juan@gmail.com",
                                      "timestamp": "2025-06-01T10:00:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "message": "Error de validación en los campos enviados",
                                      "errors": {
                                        "email": "El email no tiene un formato válido",
                                        "password": "La contraseña debe tener entre 8 y 100 caracteres"
                                      },
                                      "timestamp": "2025-06-01T10:00:00"
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse data = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(data, "Usuario registrado exitosamente. Tu cuenta está pendiente de aprobación."));
    }

    @Operation(
            summary = "Login local",
            description = """
                    Autentica un usuario con email y contraseña.
                    
                    Devuelve un token JWT válido para usar en los endpoints protegidos.
                    Copia el valor de `token` y pégalo en el botón **Authorize 🔒** como:
                    ```
                    Bearer eyJhbGci...
                    ```
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Login exitoso. Token JWT devuelto.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "Inicio de sesión exitoso",
                                      "data": {
                                        "token": "eyJhbGciOiJIUzI1NiJ9...",
                                        "tokenType": "Bearer",
                                        "userId": 1,
                                        "fullName": "Administrador",
                                        "email": "admin@itcelaya.edu.mx",
                                        "userType": "INTERNAL",
                                        "accountStatus": "APPROVED",
                                        "roles": ["ADMIN"]
                                      },
                                      "timestamp": "2025-06-01T10:00:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Credenciales incorrectas.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "message": "Credenciales inválidas",
                                      "timestamp": "2025-06-01T10:00:00"
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse data = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(data, "Inicio de sesión exitoso"));
    }
}