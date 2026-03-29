package mx.itcelaya.hackonlinces.HackOnLinces.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthProviderRepository authProviderRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.internal-domain}")
    private String internalDomain;

    /*
     * Flujo completo del callback OAuth2 de Google:
     *
     * 1. Extraemos los atributos del usuario de Google (email, name, sub, picture)
     * 2. Buscamos si ya existe un AuthProvider GOOGLE con ese providerUserId
     *    a) Si existe → recuperamos el User vinculado
     *    b) Si no existe → buscamos por email o creamos un User nuevo
     * 3. Actualizamos datos auxiliares del proveedor (avatar, etc.)
     * 4. Generamos JWT y redirigimos al frontend con el token en query param
     */
    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        String googleSub   = oauth2User.getAttribute("sub");          // ID único de Google
        String email       = oauth2User.getAttribute("email");
        String name        = oauth2User.getAttribute("name");
        String avatarUrl   = oauth2User.getAttribute("picture");

        if (email == null || googleSub == null) {
            log.error("OAuth2: Google no devolvió email o sub. Abortando.");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Datos de Google incompletos");
            return;
        }

        // 1. Buscar por providerUserId (más robusto que buscar por email)
        // La variable 'user' ahora es "effectively final" porque no la reasignaremos.
        User user = authProviderRepository
                .findByProviderAndProviderUserId(AuthProviderType.GOOGLE, googleSub)
                .map(AuthProvider::getUser)
                .orElseGet(() -> findOrCreateUserForGoogle(email, name, avatarUrl, googleSub));

        // 2. Actualizar o crear el AuthProvider con datos frescos de Google
        authProviderRepository
                .findByUser_IdAndProvider(user.getId(), AuthProviderType.GOOGLE)
                .ifPresentOrElse(
                        ap -> ap.setAvatarUrl(avatarUrl),
                        () -> createGoogleAuthProvider(user, googleSub, email, avatarUrl)
                );

        // 3. Recargar con roles para el token USANDO UNA VARIABLE NUEVA
        User userWithRoles = userRepository.findByEmailWithRoles(user.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado al recargar roles"));

        // 4. Generar el JWT
        String token = jwtUtil.generateToken(userWithRoles);

        log.info("OAuth2 Google login exitoso: {} (id={}, type={})",
                userWithRoles.getEmail(), userWithRoles.getId(), userWithRoles.getUserType());

        /*
         * Redirigimos al frontend con el token como query param.
         * El frontend debe leer ?token=... y guardarlo en memoria/localStorage.
         */
        String redirectUrl = UriComponentsBuilder
                .fromUriString(getFrontendRedirectUrl())
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private User findOrCreateUserForGoogle(String email, String name, String avatarUrl, String googleSub) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> createUserFromGoogle(email, name));
    }

    private User createUserFromGoogle(String email, String name) {
        boolean isInternal = email.endsWith("@" + internalDomain);

        User user = new User();
        user.setFullName(name != null ? name : email);
        user.setEmail(email);
        user.setUserType(isInternal ? UserType.INTERNAL : UserType.EXTERNAL);

        /*
         * Los usuarios INTERNAL se aprueban automáticamente.
         * Los EXTERNAL vía Google quedan PENDING igual que los de registro manual.
         */
        user.setAccountStatus(isInternal ? AccountStatus.APPROVED : AccountStatus.PENDING);

        if (isInternal) {
            // Extraemos el dominio institucional como instituto
            user.setInstituteName("IT Celaya");
        }

        user = userRepository.save(user);

        // Rol por defecto según tipo
        RoleName defaultRole = isInternal ? RoleName.PARTICIPANT : RoleName.GUEST;
        Role role = roleRepository.findByName(defaultRole).orElseThrow();
        userRoleRepository.save(new UserRole(user, role));

        log.info("Nuevo usuario creado vía Google OAuth: {} (internal={})", email, isInternal);
        return user;
    }

    private void createGoogleAuthProvider(User user, String googleSub, String email, String avatarUrl) {
        AuthProvider ap = new AuthProvider();
        ap.setUser(user);
        ap.setProvider(AuthProviderType.GOOGLE);
        ap.setProviderUserId(googleSub);
        ap.setProviderEmail(email);
        ap.setAvatarUrl(avatarUrl);
        authProviderRepository.save(ap);
    }

    /*
     * URL de redirección post-OAuth hacia el frontend.
     * En una siguiente iteración esto puede venir de una propiedad configurable.
     */
    private String getFrontendRedirectUrl() {
        return "http://localhost:3000/oauth2/callback";
    }
}