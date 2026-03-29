package mx.itcelaya.hackonlinces.HackOnLinces.service.strategy;

import lombok.RequiredArgsConstructor;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/*
 * Factory que selecciona la RegistrationStrategy correcta.
 *
 * Spring inyecta automáticamente TODAS las implementaciones de
 * RegistrationStrategy — no hay que registrarlas manualmente.
 * Cuando añadamos una nueva estrategia (ej. SPONSOR), solo hay que
 * crear el @Component y la factory la descubrirá sola.
 *
 * La selección se hace en dos pasos:
 *   1. Determinar el UserType a partir del dominio del email
 *   2. Delegar en la estrategia que soporta ese UserType
 */
@Component
@RequiredArgsConstructor
public class RegistrationFactory {

    private final List<RegistrationStrategy> strategies;

    @Value("${app.internal-domain}")
    private String internalDomain;

    /*
     * Resuelve la estrategia a partir del email.
     * Si el email termina en @itcelaya.edu.mx → INTERNAL → InternalRegistrationStrategy
     * Cualquier otro dominio → EXTERNAL → ExternalRegistrationStrategy
     */
    public RegistrationStrategy resolve(String email) {
        UserType userType = resolveUserType(email);
        return strategies.stream()
                .filter(s -> s.supports(userType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró estrategia de registro para UserType: " + userType
                ));
    }

    public UserType resolveUserType(String email) {
        return email.endsWith("@" + internalDomain)
                ? UserType.INTERNAL
                : UserType.EXTERNAL;
    }
}