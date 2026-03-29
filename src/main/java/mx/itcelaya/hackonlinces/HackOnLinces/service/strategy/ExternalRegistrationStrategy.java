package mx.itcelaya.hackonlinces.HackOnLinces.service.strategy;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.RegisterRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.AuthProvider;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.Role;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.User;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.UserRole;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.AccountStatus;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.AuthProviderType;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.RoleName;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType;
import mx.itcelaya.hackonlinces.HackOnLinces.exception.ConflictException;
import mx.itcelaya.hackonlinces.HackOnLinces.exception.ResourceNotFoundException;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.AuthProviderRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.RoleRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.UserRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalRegistrationStrategy implements RegistrationStrategy {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthProviderRepository authProviderRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public User register(RegisterRequest request) {

        // 1. Email único
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Ya existe una cuenta con el email: " + request.email());
        }

        // 2. instituteName obligatorio para externos — validado aquí además de en el DTO
        //    porque la validación Jakarta solo corre en el controller, no en llamadas internas
        if (request.instituteName() == null || request.instituteName().isBlank()) {
            throw new IllegalArgumentException("El nombre del instituto es obligatorio para usuarios externos");
        }

        // 3. Construir el User
        User user = new User();
        user.setFullName(request.fullName());
        user.setInstituteName(request.instituteName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUserType(UserType.EXTERNAL);
        user.setAccountStatus(AccountStatus.PENDING);
        user = userRepository.save(user);

        // 4. Rol GUEST por defecto para externos
        Role role = roleRepository.findByName(RoleName.GUEST)
                .orElseThrow(() -> new ResourceNotFoundException("Rol GUEST no encontrado"));
        userRoleRepository.save(new UserRole(user, role));

        // 5. AuthProvider LOCAL
        AuthProvider ap = new AuthProvider();
        ap.setUser(user);
        ap.setProvider(AuthProviderType.LOCAL);
        ap.setProviderEmail(user.getEmail());
        authProviderRepository.save(ap);

        log.info("Registro EXTERNAL completado: {} (id={})", user.getEmail(), user.getId());

        /*
         * flush() → escribe todos los inserts pendientes a la BD dentro de la transacción.
         * clear() → limpia el primer nivel de caché del EntityManager.
         * Sin esto, findByEmailWithRoles puede devolver el User sin el UserRole
         * recién guardado porque Hibernate lo tiene en caché sin el join.
         */
        entityManager.flush();
        entityManager.clear();

        // 6. Retornar con roles cargados para que JwtUtil pueda generar el token
        return userRepository.findByEmailWithRoles(user.getEmail()).orElseThrow();
    }

    @Override
    public boolean supports(UserType userType) {
        return userType == UserType.EXTERNAL;
    }
}