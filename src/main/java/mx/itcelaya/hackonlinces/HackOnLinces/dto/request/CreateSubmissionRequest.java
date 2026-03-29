package mx.itcelaya.hackonlinces.HackOnLinces.dto.request;

import jakarta.validation.constraints.Size;

/*
 * Este DTO llega como parte de un multipart/form-data.
 * El archivo (MultipartFile) se recibe directamente en el controller
 * como parámetro separado — no forma parte de este record.
 *
 * reason es opcional: el usuario puede describir su solicitud
 * o dejarlo vacío y que el archivo hable por sí solo.
 */
public record CreateSubmissionRequest(

        @Size(max = 1000, message = "La descripción no puede exceder 1000 caracteres")
        String reason
) {}