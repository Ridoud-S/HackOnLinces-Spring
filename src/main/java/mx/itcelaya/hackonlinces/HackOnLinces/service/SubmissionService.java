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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Regla 1: solo usuarios EXTERNAL requieren submission
        if (user.getUserType() != UserType.EXTERNAL) {
            throw new ForbiddenActionException("Solo los usuarios externos pueden enviar submissions");
        }

        // Regla 2: si ya fue aprobado, no puede reenviar
        if (user.getAccountStatus() == AccountStatus.APPROVED) {
            throw new ConflictException("Tu cuenta ya fue aprobada. No es necesario enviar otra submission");
        }

        // Regla 3: no puede haber dos submissions PENDING al mismo tiempo
        if (submissionRepository.existsByUser_IdAndStatus(userId, SubmissionStatus.PENDING)) {
            throw new ConflictException("Ya tienes una submission pendiente de revisión. Espera la respuesta del administrador");
        }

        // Regla 4: límite de intentos
        int totalAttempts = submissionRepository.countByUser_Id(userId);
        if (totalAttempts >= maxAttempts) {
            throw new ForbiddenActionException(
                    "Has alcanzado el límite de " + maxAttempts + " intentos de submission"
            );
        }

        // Regla 5: archivo obligatorio
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debes adjuntar un archivo a tu submission");
        }

        // Calcular el número de intento
        int attemptNumber = submissionRepository
                .findTopByUser_IdOrderByAttemptNumberDesc(userId)
                .map(s -> s.getAttemptNumber() + 1)
                .orElse(1);

        // Crear la submission
        Submission submission = new Submission();
        submission.setUser(user);
        submission.setAttemptNumber(attemptNumber);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setReason(request.reason());
        submission.setSentAt(LocalDateTime.now());
        submission = submissionRepository.save(submission);

        // Guardar el archivo y crear el Document
        String storedPath = storageService.store(file);
        Document document = new Document();
        document.setSubmission(submission);
        document.setPath(storedPath);
        document.setOriginalName(file.getOriginalFilename());
        document.setMimeType(file.getContentType());
        document.setSize(file.getSize());
        documentRepository.save(document);

        log.info("Submission #{} creada para usuario {} (id={})",
                attemptNumber, user.getEmail(), submission.getId());

        entityManager.flush();
        entityManager.clear();

        Submission saved = submissionRepository.findById(submission.getId())
                .orElseThrow();
        return submissionMapper.toResponse(saved);
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
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission no encontrada"));

        // Un usuario solo puede ver sus propias submissions a menos que sea admin
        if (!isAdmin && !submission.getUser().getId().equals(requestingUserId)) {
            throw new ForbiddenActionException("No tienes acceso a esta submission");
        }

        return submissionMapper.toResponse(submission);
    }
}