package mx.itcelaya.hackonlinces.HackOnLinces.controller;

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

@RestController
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /*
     * POST /api/v1/submissions
     * multipart/form-data con:
     *   - file    → archivo obligatorio
     *   - reason  → texto opcional (parte del JSON o form field)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SubmissionResponse>> create(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @RequestPart(value = "reason", required = false) String reason,
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

    /*
     * GET /api/v1/submissions
     * Lista todas las submissions del usuario autenticado.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getMySubmissions(
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        List<SubmissionResponse> submissions = submissionService
                .getMySubmissions(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.ok(submissions, "Submissions obtenidas exitosamente"));
    }

    /*
     * GET /api/v1/submissions/{id}
     * Detalle de una submission — el usuario solo puede ver la suya.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubmissionResponse>> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        SubmissionResponse response = submissionService.getById(
                id, userDetails.getUser().getId(), false
        );
        return ResponseEntity.ok(ApiResponse.ok(response, "Submission obtenida exitosamente"));
    }
}