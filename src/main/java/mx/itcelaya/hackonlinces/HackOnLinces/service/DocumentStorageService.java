package mx.itcelaya.hackonlinces.HackOnLinces.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class DocumentStorageService {

    private final Path rootLocation;

    public DocumentStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /*
     * Crea el directorio de uploads al arrancar si no existe.
     * Falla rápido en lugar de fallar en el primer upload.
     */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("Directorio de uploads listo: {}", rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio de uploads: " + rootLocation, e);
        }
    }

    /*
     * Guarda el archivo con un nombre UUID para evitar colisiones y
     * para no exponer el nombre original en el filesystem.
     *
     * Devuelve la ruta relativa al directorio raíz de uploads —
     * portable entre entornos (local, Docker, cloud).
     */
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No se puede guardar un archivo vacío");
        }

        String extension = extractExtension(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + extension;
        Path destination = rootLocation.resolve(storedName).normalize();

        // Prevención de path traversal — el destino debe estar dentro de rootLocation
        if (!destination.startsWith(rootLocation)) {
            throw new IllegalArgumentException("No se puede guardar el archivo fuera del directorio permitido");
        }

        try {
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Archivo guardado: {}", storedName);
            return storedName;
        } catch (IOException e) {
            throw new IllegalStateException("Error al guardar el archivo: " + storedName, e);
        }
    }

    /*
     * Elimina un archivo por su ruta relativa guardada en BD.
     * No lanza excepción si el archivo ya no existe — idempotente.
     */
    public void delete(String relativePath) {
        try {
            Path target = rootLocation.resolve(relativePath).normalize();
            Files.deleteIfExists(target);
            log.debug("Archivo eliminado: {}", relativePath);
        } catch (IOException e) {
            log.warn("No se pudo eliminar el archivo {}: {}", relativePath, e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return "." + originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
    }
}