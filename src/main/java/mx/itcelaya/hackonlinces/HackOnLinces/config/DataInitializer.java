package mx.itcelaya.hackonlinces.HackOnLinces.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.AuthProvider;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.Role;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.User;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.UserRole;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.AccountStatus;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.AuthProviderType;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.RoleName;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.AuthProviderRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.RoleRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.UserRepository;
import mx.itcelaya.hackonlinces.HackOnLinces.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthProviderRepository authProviderRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * admin@hackonlinces.mx
     * Admin1234!
     * */
    @Value("${app.seed.admin-email:admin@hackonlinces.mx}")
    private String adminEmail;

    @Value("${app.seed.admin-password:Admin1234!}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedAdminUser();
    }

    /*
     * ApplicationRunner se ejecuta una vez al arrancar Spring Boot,
     * después de que el contexto está completamente inicializado.
     *
     * Insertamos los roles solo si no existen — idempotente,
     * seguro de ejecutar con ddl-auto: update o validate.
     */


    private void seedRoles() {
        Arrays.stream(RoleName.values()).forEach(roleName -> {
            if (roleRepository.findByName(roleName).isEmpty()) {
                roleRepository.save(new Role(roleName));
                log.info("Rol creado: {}", roleName);
            }
        });
        log.info("DataInitializer — roles verificados: {}", RoleName.values().length);
    }

    /*
     * Crea un usuario ADMIN de prueba si no existe.
     * Configurable vía variables de entorno / application.yml:
     *   app.seed.admin-email
     *   app.seed.admin-password
     *
     * ⚠️ Cambia la contraseña antes de desplegar en producción.
     */
    private void seedAdminUser() {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("DataInitializer — usuario admin ya existe: {}", adminEmail);
            return;
        }

        User admin = new User();
        admin.setFullName("Administrador del Sistema");
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setUserType(UserType.INTERNAL);
        admin.setAccountStatus(AccountStatus.APPROVED);
        admin.setInstituteName("TecNM en Celaya");
        admin = userRepository.save(admin);

        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        userRoleRepository.save(new UserRole(admin, adminRole));

        AuthProvider ap = new AuthProvider();
        ap.setUser(admin);
        ap.setProvider(AuthProviderType.LOCAL);
        ap.setProviderEmail(adminEmail);
        authProviderRepository.save(ap);

        log.info("Usuario ADMIN seed creado: {} — ⚠️ cambia la contraseña en producción", adminEmail);
    }
}