package mx.itcelaya.hackonlinces.HackOnLinces.service.strategy;

import mx.itcelaya.hackonlinces.HackOnLinces.dto.request.RegisterRequest;
import mx.itcelaya.hackonlinces.HackOnLinces.entity.User;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType;

/*
 * Contrato para las estrategias de registro.
 *
 * Cada implementación es responsable de:
 *  - Crear el User con sus reglas propias
 *  - Asignar el rol por defecto
 *  - Registrar el AuthProvider correspondiente
 *
 * El AuthService solo conoce esta interfaz — nunca las implementaciones.
 */
public interface RegistrationStrategy {

    /*
     * Ejecuta el registro completo y devuelve el User persistido con sus roles cargados.
     * Debe ser @Transactional en la implementación o heredar la transacción del llamador.
     */
    User register(RegisterRequest request);

    /*
     * Indica si esta estrategia soporta el UserType dado.
     * Usado por RegistrationFactory para seleccionar la estrategia correcta.
     */
    boolean supports(UserType userType);
}