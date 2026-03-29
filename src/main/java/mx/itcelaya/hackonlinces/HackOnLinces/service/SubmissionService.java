package mx.itcelaya.hackonlinces.HackOnLinces.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.CreateSubmissionRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.SubmissionResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.Document;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.Submission;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.User;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.AccountStatus;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.SubmissionStatus;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType;
import mx.itcelaya.hackonlinces.HackOnLinces.exception.AppException;
import mx.itcelaya.hackonlinces.HackOnLinces.exception.ConflictException;
import mx.itcelaya.hackonlinces.HackOnLinces.exception.ForbiddenActionException;
import mx.itcelaya.hackonlinces.HackOnLinces.exception.ResourceNotFoundException;
import mx.itcelaya.hackonlinces.HackOnLinces.mapper.SubmissionMapper;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.DocumentRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.SubmissionRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService storageService;
    private final SubmissionMapper submissionMapper;
    private final EntityManager entityManager;

    @Value("${app.submission.max-attempts}")
    private int maxAttempts;

    // ── Crear submission ─────────────────────────────────────────────────────

    @Transactional
    public SubmissionResponse create(Long userId, CreateSubmissionRequest request, MultipartFile file) {
        // 1. Cargar el usuario y validar existencia
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // 2. REGLA DE NEGOCIO: Solo usuarios EXTERNAL pasan por este proceso de admisión
        if (user.getUserType() != UserType.EXTERNAL) {
            throw new ForbiddenActionException("Solo los usuarios externos requieren enviar una carta de intención.");
        }

        // 3. REGLA DE SEGURIDAD: Si ya fue aprobado, su ciclo de admisión terminó
        if (user.getAccountStatus() == AccountStatus.APPROVED) {
            throw new ConflictException("Tu cuenta ya ha sido aprobada. No es necesario enviar más documentos.");
        }

        // 4. REGLA DE CONTROL: Solo una solicitud PENDING a la vez para evitar spam al admin
        if (submissionRepository.existsByUser_IdAndStatus(userId, SubmissionStatus.PENDING)) {
            throw new ConflictException("Ya tienes una solicitud en revisión. Por favor, espera la respuesta del administrador.");
        }

        // 5. REGLA DE LÍMITE: Validar intentos máximos (configurable)
        int totalAttempts = submissionRepository.countByUser_Id(userId);
        if (totalAttempts >= maxAttempts) {
            throw new ForbiddenActionException("Has alcanzado el límite máximo de " + maxAttempts + " intentos permitidos.");
        }

        // 6. VALIDACIÓN DE ARCHIVO: La carta de intención es obligatoria
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Es obligatorio adjuntar el documento de tu carta de intención.");
        }

        // 7. CÁLCULO DE ATTEMPT NUMBER: Buscamos el último y sumamos 1, o empezamos en 1
        int nextAttempt = submissionRepository
                .findTopByUser_IdOrderByAttemptNumberDesc(userId)
                .map(s -> s.getAttemptNumber() + 1)
                .orElse(1);

        // 8. CREACIÓN DE LA SUBMISSION (Trámite de Admisión)
        Submission submission = new Submission();
        submission.setUser(user);
        submission.setAttemptNumber(nextAttempt);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setReason(request.reason());
        submission.setSentAt(LocalDateTime.now());

        // Guardamos primero la submission para tener el ID para el documento
        submission = submissionRepository.save(submission);

        // 9. GESTIÓN DE ARCHIVO Y DOCUMENTO
        // El storageService se encarga de la persistencia física (FileSystem/S3/Azure)
        String storedPath = storageService.store(file);

        Document document = new Document();
        document.setSubmission(submission);
        document.setPath(storedPath);
        document.setOriginalName(file.getOriginalFilename());
        document.setMimeType(file.getContentType());
        document.setSize(file.getSize());

        documentRepository.save(document);

        log.info("Nueva solicitud de admisión (Intento #{}) creada para: {}", nextAttempt, user.getEmail());

        // 10. SINCRONIZACIÓN: Forzamos el volcado para que el Mapper vea los datos frescos
        entityManager.flush();
        entityManager.clear();

        Submission savedSubmission = submissionRepository.findById(submission.getId())
                .orElseThrow(() -> new AppException("Error al recuperar la submission guardada"));

        return submissionMapper.toResponse(savedSubmission);
    }

    // ── Consultas del usuario ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getMySubmissions(Long userId) {
        return submissionRepository.findByUser_IdOrderByAttemptNumberAsc(userId)
                .stream()
                .map(submissionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getById(UUID submissionId, Long requestingUserId, boolean isAdmin) {
        Submission submission = submissionRepository.findByIdWithDetails(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission no encontrada"));
        if (!isAdmin && !submission.getUser().getId().equals(requestingUserId)) {
            throw new ForbiddenActionException("No tienes acceso a esta submission");
        }

        return submissionMapper.toResponse(submission);
    }
}