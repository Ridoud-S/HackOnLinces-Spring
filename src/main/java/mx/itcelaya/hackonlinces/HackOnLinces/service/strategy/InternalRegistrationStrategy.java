package mx.itcelaya.hackonlinces.HackOnLinces.service.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.RegisterRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.User;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType;
import mx.itcelaya.hackonlinces.HackOnLinces.exception.ForbiddenActionException;
import org.springframework.stereotype.Component;

/*
 * Estrategia para emails institucionales (@itcelaya.edu.mx).
 *
 * El registro MANUAL de usuarios internos está prohibido.
 * Los usuarios con correo institucional DEBEN autenticarse vía Google OAuth.
 * Esta estrategia existe para interceptar el intento y lanzar una excepción
 * clara en lugar de dejar que el flujo falle silenciosamente.
 *
 * La creación real del User INTERNAL ocurre en OAuth2SuccessHandler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalRegistrationStrategy implements RegistrationStrategy {

    @Override
    public User register(RegisterRequest request) {
        log.warn("Intento de registro manual con email institucional bloqueado: {}", request.email());
        throw new ForbiddenActionException(
                "Los usuarios con correo @itcelaya.edu.mx deben iniciar sesión con Google. " +
                        "El registro manual no está permitido para cuentas institucionales."
        );
    }

    @Override
    public boolean supports(UserType userType) {
        return userType == UserType.INTERNAL;
    }
}