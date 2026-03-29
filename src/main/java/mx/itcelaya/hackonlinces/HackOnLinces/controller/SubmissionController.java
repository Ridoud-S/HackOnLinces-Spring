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
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.CreateSubmissionRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.ApiResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.SubmissionResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.security.AppUserDetails;
import mx.itcelaya.hackonlinces.HackOnLinces.service.SubmissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "2. Admisión",
        description = """
                Endpoints para que los usuarios **externos** gestionen su carta de intención.
                
                Flujo esperado:
                1. El usuario externo se registra → queda como `GUEST / PENDING`
                2. Sube su carta de intención (PDF)
                3. El administrador la revisa desde el panel de administración
                4. Si es aprobada → el usuario pasa automáticamente a `PARTICIPANT / APPROVED`
                
                > Todos los endpoints requieren autenticación JWT.
                """
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @Operation(
            summary = "Enviar carta de intención",
            description = """
                    Sube la carta de intención del usuario externo para solicitar acceso al hackathon.
                    
                    **Restricciones:**
                    - Solo usuarios con `userType = EXTERNAL` pueden usar este endpoint.
                    - No se puede enviar si ya existe una submission con estado `PENDING`.
                    - No se puede enviar si la cuenta ya fue `APPROVED`.
                    - Existe un límite máximo de intentos configurado en el sistema.
                    - El archivo es **obligatorio**.
                    
                    **Formato:** `multipart/form-data`
                    - `file` → archivo PDF de la carta (obligatorio)
                    - `reason` → comentario adicional del usuario (opcional)
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Carta de intención enviada exitosamente.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "Submission enviada exitosamente",
                                      "data": {
                                        "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                        "userId": 5,
                                        "attemptNumber": 1,
                                        "status": "PENDING",
                                        "reason": null,
                                        "reviewedById": null,
                                        "reviewedAt": null,
                                        "sentAt": "2025-06-01T10:30:00",
                                        "createdAt": "2025-06-01T10:30:00",
                                        "documents": [
                                          {
                                            "id": "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
                                            "originalName": "carta_intencion.pdf",
                                            "mimeType": "application/pdf",
                                            "size": 204800,
                                            "uploadedAt": "2025-06-01T10:30:00"
                                          }
                                        ]
                                      },
                                      "timestamp": "2025-06-01T10:30:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "El usuario no es externo o alcanzó el límite de intentos.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "message": "Solo los usuarios externos pueden enviar submissions",
                                      "timestamp": "2025-06-01T10:30:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Ya existe una submission pendiente o la cuenta ya fue aprobada.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "message": "Ya tienes una submission pendiente de revisión. Espera la respuesta del administrador",
                                      "timestamp": "2025-06-01T10:30:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Token JWT ausente o inválido.",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SubmissionResponse>> create(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Parameter(description = "Comentario opcional del usuario")
            @RequestPart(value = "reason", required = false) String reason,
            @Parameter(description = "Archivo PDF de la carta de intención (obligatorio)", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        CreateSubmissionRequest request = new CreateSubmissionRequest(reason);
        SubmissionResponse response = submissionService.create(
                userDetails.getUser().getId(), request, file
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response, "Submission enviada exitosamente"));
    }

    @Operation(
            summary = "Mis cartas de intención",
            description = "Lista todas las cartas de intención enviadas por el usuario autenticado, ordenadas por número de intento."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de submissions del usuario.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "Submissions obtenidas exitosamente",
                                      "data": [
                                        {
                                          "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                          "userId": 5,
                                          "attemptNumber": 1,
                                          "status": "REJECTED",
                                          "reason": "El documento estaba ilegible.",
                                          "reviewedById": 1,
                                          "reviewedAt": "2025-06-02T09:00:00",
                                          "sentAt": "2025-06-01T10:30:00",
                                          "createdAt": "2025-06-01T10:30:00",
                                          "documents": []
                                        },
                                        {
                                          "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                                          "userId": 5,
                                          "attemptNumber": 2,
                                          "status": "PENDING",
                                          "reason": null,
                                          "reviewedById": null,
                                          "reviewedAt": null,
                                          "sentAt": "2025-06-03T08:00:00",
                                          "createdAt": "2025-06-03T08:00:00",
                                          "documents": []
                                        }
                                      ],
                                      "timestamp": "2025-06-03T08:00:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Token JWT ausente o inválido.",
                    content = @Content(mediaType = "application/json")
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getMySubmissions(
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        List<SubmissionResponse> submissions = submissionService
                .getMySubmissions(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.ok(submissions, "Submissions obtenidas exitosamente"));
    }

    @Operation(
            summary = "Detalle de una carta de intención",
            description = "Obtiene el detalle de una carta de intención por su ID. El usuario solo puede consultar las suyas propias."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Detalle de la submission.",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "La submission no pertenece al usuario autenticado.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "message": "No tienes acceso a esta submission",
                                      "timestamp": "2025-06-01T10:30:00"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Submission no encontrada.",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Token JWT ausente o inválido.",
                    content = @Content(mediaType = "application/json")
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubmissionResponse>> getById(
            @Parameter(description = "UUID de la carta de intención", required = true)
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        SubmissionResponse response = submissionService.getById(
                id, userDetails.getUser().getId(), false
        );
        return ResponseEntity.ok(ApiResponse.ok(response, "Submission obtenida exitosamente"));
    }
}