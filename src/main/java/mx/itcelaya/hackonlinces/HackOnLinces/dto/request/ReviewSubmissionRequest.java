package mx.itcelaya.hackonlinces.HackOnLinces.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.SubmissionStatus;

public record ReviewSubmissionRequest(

        /*
         * Solo se permiten APPROVED, REJECTED o RESUBMIT_REQUIRED.
         * PENDING no es una decisión válida del admin — se valida en el service.
         */
        @NotNull(message = "La decisión es obligatoria")
        SubmissionStatus decision,

        @Size(max = 1000, message = "El comentario no puede exceder 1000 caracteres")
        String comment
) {}