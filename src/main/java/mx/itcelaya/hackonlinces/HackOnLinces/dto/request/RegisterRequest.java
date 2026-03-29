package mx.itcelaya.hackonlinces.HackOnLinces.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
        String fullName,

        @NotBlank(message = "El nombre del instituto es obligatorio")
        @Size(max = 120, message = "El nombre del instituto no puede exceder 120 caracteres")
        String instituteName,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
        String password
) {}