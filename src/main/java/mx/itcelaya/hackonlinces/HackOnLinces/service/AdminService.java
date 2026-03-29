package mx.itcelaya.hackonlinces.HackOnLinces.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.ChangeRoleRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.CreateUserRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.ReviewSubmissionRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.ReviewUserRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.AdminUserResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.DashboardResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.SubmissionResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.response.WaitlistUserResponse;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.AuthProvider;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.Role;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.Submission;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.User;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.UserRole;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.AccountStatus;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.AuthProviderType;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.RoleName;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.SubmissionStatus;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType;
import mx.itcelaya.hackonlinces.HackOnLinces.exception.ConflictException;
import mx.itcelaya.hackonlinces.HackOnLinces.exception.ForbiddenActionException;
import mx.itcelaya.hackonlinces.HackOnLinces.exception.ResourceNotFoundException;
import mx.itcelaya.hackonlinces.HackOnLinces.mapper.SubmissionMapper;
import mx.itcelaya.hackonlinces.HackOnLinces.mapper.UserMapper;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.AuthProviderRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.RoleRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.SubmissionRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.UserRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthProviderRepository authProviderRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    // Seguro para uso concurrente; java.util.Random NO lo es.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";

    // ── Dashboard ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        LocalDateTime startOfToday = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime startOfWeek  = LocalDateTime.now().minusDays(7);

        Object[] userStatsArray = userRepository.getUserStats(startOfToday, startOfWeek);

        DashboardResponse.UserStats userStats = new DashboardResponse.UserStats(
                toLong(userStatsArray, 0),   // total
                toLong(userStatsArray, 1),   // pending
                toLong(userStatsArray, 2),   // approved
                toLong(userStatsArray, 3),   // rejected
                toLong(userStatsArray, 4),   // internal
                toLong(userStatsArray, 5),   // external
                toLong(userStatsArray, 6),   // admins
                toLong(userStatsArray, 7),   // judges
                toLong(userStatsArray, 8),   // speakers
                toLong(userStatsArray, 9),   // participants
                toLong(userStatsArray, 10),  // guests
                toLong(userStatsArray, 11),  // registeredToday
                toLong(userStatsArray, 12)   // registeredThisWeek
        );

        Object[] submissionStatsArray = submissionRepository.getSubmissionStats();

        DashboardResponse.SubmissionStats submissionStats = new DashboardResponse.SubmissionStats(
                toLong(submissionStatsArray, 0),  // total
                toLong(submissionStatsArray, 1),  // pending
                toLong(submissionStatsArray, 2),  // approved
                toLong(submissionStatsArray, 3),  // rejected
                toLong(submissionStatsArray, 4)   // resubmit_required
        );

        return new DashboardResponse(userStats, submissionStats);
    }

    // ── Listado de usuarios ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getAllUsers(AccountStatus status, UserType userType, String search) {
        String normalizedSearch = (search == null || search.isBlank())
                ? null
                : "%" + search.toLowerCase() + "%";  // ← wildcards + lowercase aquí

        List<Object[]> results = userRepository.findAllWithSubmissionCounts(
                status, userType, normalizedSearch
        );

        return results.stream()
                .map(row -> {
                    User u = (User) row[0];
                    /*
                     * COUNT() en JPQL NUNCA devuelve null, siempre Long.
                     * El cast a Number + longValue() es defensivo por si
                     * algún dialecto devuelve BigInteger o Integer.
                     */
                    long submissionCount = row[1] != null
                            ? ((Number) row[1]).longValue()
                            : 0L;

                    /*
                     * La query no hace JOIN FETCH, así que userRoles está lazy.
                     * Forzamos la carga aquí dentro de la transacción activa
                     * para que el mapper no encuentre una colección no inicializada.
                     */
                    User withRoles = userRepository.findByEmailWithRoles(u.getEmail())
                            .orElse(u);

                    return userMapper.toAdminUserResponse(withRoles, (int) submissionCount);
                })
                .toList();
    }

    // ── Crear usuario manualmente ────────────────────────────────────────────

    @Transactional
    public AdminUserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Ya existe una cuenta con el email: " + request.email());
        }

        String rawPassword = (request.password() != null && !request.password().isBlank())
                ? request.password()
                : generateTemporaryPassword();

        User user = new User();
        user.setFullName(request.fullName());
        user.setInstituteName(request.instituteName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setUserType(request.userType());
        user.setAccountStatus(request.accountStatus());
        user = userRepository.save(user);

        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado: " + request.role()));
        userRoleRepository.save(new UserRole(user, role));

        AuthProvider ap = new AuthProvider();
        ap.setUser(user);
        ap.setProvider(AuthProviderType.LOCAL);
        ap.setProviderEmail(user.getEmail());
        authProviderRepository.save(ap);

        log.info("Usuario creado manualmente por admin: {} con rol {}", user.getEmail(), request.role());

        User saved = userRepository.findByEmailWithRoles(user.getEmail()).orElseThrow();
        return userMapper.toAdminUserResponse(saved, 0);
    }

    // ── Waitlist ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WaitlistUserResponse> getWaitlist() {
        return userRepository
                .findByUserTypeAndAccountStatus(UserType.EXTERNAL, AccountStatus.PENDING)
                .stream()
                .map(userMapper::toWaitlistResponse)
                .toList();
    }

    // ── Revisión de usuario ──────────────────────────────────────────────────

    @Transactional
    public AdminUserResponse reviewUser(Long userId, ReviewUserRequest request) {
        if (request.decision() == AccountStatus.PENDING) {
            throw new ForbiddenActionException("No se puede asignar el estado PENDING manualmente");
        }

        // Carga directamente con roles para no hacer dos queries innecesarias
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user = userRepository.findByEmailWithRoles(user.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user.setAccountStatus(request.decision());

        if (request.decision() == AccountStatus.APPROVED) {
            promoteToParticipant(user);
        }

        userRepository.save(user);
        log.info("Usuario {} actualizado a {} por admin", user.getEmail(), request.decision());

        User updated = userRepository.findByEmailWithRoles(user.getEmail()).orElseThrow();
        return userMapper.toAdminUserResponse(updated,
                (int) submissionRepository.countByUser_Id(userId));
    }

    // ── Cambio de rol ────────────────────────────────────────────────────────

    @Transactional
    public AdminUserResponse changeRole(Long userId, ChangeRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user = userRepository.findByEmailWithRoles(user.getEmail()).orElseThrow();

        Role newRole = roleRepository.findByName(request.role())
                .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado: " + request.role()));

        userRoleRepository.deleteAll(user.getUserRoles());
        userRoleRepository.flush(); // garantiza que el DELETE llega a BD antes del INSERT
        userRoleRepository.save(new UserRole(user, newRole));

        log.info("Rol de usuario {} cambiado a {}", user.getEmail(), request.role());

        User updated = userRepository.findByEmailWithRoles(user.getEmail()).orElseThrow();
        return userMapper.toAdminUserResponse(updated,
                (int) submissionRepository.countByUser_Id(userId));
    }

    // ── Submissions ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getPendingSubmissions() {
        return submissionRepository
                .findByStatusOrderByCreatedAtAsc(SubmissionStatus.PENDING)
                .stream()
                .map(submissionMapper::toResponse)
                .toList();
    }

    @Transactional
    public SubmissionResponse reviewSubmission(UUID submissionId, Long adminId, ReviewSubmissionRequest request) {
        if (request.decision() == SubmissionStatus.PENDING) {
            throw new ForbiddenActionException("No se puede asignar el estado PENDING manualmente");
        }

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission no encontrada"));

        if (submission.getStatus() != SubmissionStatus.PENDING) {
            throw new ConflictException(
                    "Esta submission ya fue revisada con estado: " + submission.getStatus());
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin no encontrado"));

        submission.setStatus(request.decision());
        submission.setReason(request.comment());
        submission.setReviewedBy(admin);
        submission.setReviewedAt(LocalDateTime.now());
        submissionRepository.save(submission);

        if (request.decision() == SubmissionStatus.APPROVED) {
            User user = userRepository.findByEmailWithRoles(
                    submission.getUser().getEmail()
            ).orElseThrow();

            user.setAccountStatus(AccountStatus.APPROVED);
            promoteToParticipant(user);
            userRepository.save(user);

            log.info("Submission {} aprobada — usuario {} promovido a PARTICIPANT",
                    submissionId, user.getEmail());
        }

        // Recarga para reflejar el estado persistido, incluidos los documentos
        return submissionMapper.toResponse(
                submissionRepository.findById(submissionId).orElseThrow()
        );
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private void promoteToParticipant(User user) {
        boolean alreadyParticipant = user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getName() == RoleName.PARTICIPANT);
        if (alreadyParticipant) return;

        Role participantRole = roleRepository.findByName(RoleName.PARTICIPANT)
                .orElseThrow(() -> new ResourceNotFoundException("Rol PARTICIPANT no encontrado"));

        user.getUserRoles().stream()
                .filter(ur -> ur.getRole().getName() == RoleName.GUEST)
                .findFirst()
                .ifPresent(guestRole -> {
                    userRoleRepository.delete(guestRole);
                    user.getUserRoles().remove(guestRole);
                });

        userRoleRepository.flush(); // forzar el DELETE antes del INSERT

        UserRole newRole = new UserRole(user, participantRole);
        userRoleRepository.save(newRole);
        user.getUserRoles().add(newRole);
    }

    /**
     * Genera una contraseña temporal de 12 caracteres con SecureRandom.
     * java.util.Random es predecible; nunca usarlo para credenciales.
     */
    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            password.append(PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    /**
     * Extrae un valor numérico del Object[] devuelto por las queries de estadísticas.
     * Hibernate puede retornar Long, Integer o BigInteger según el dialecto —
     * castear a Number es la única forma segura de cubrirlos todos.
     */
    private long toLong(Object[] array, int index) {
        if (array == null || index >= array.length || array[index] == null) {
            return 0L;
        }
        return ((Number) array[index]).longValue();
    }
}