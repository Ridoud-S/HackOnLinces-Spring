    package mx.itcelaya.hackonlinces.HackOnLinces.entity;


    import jakarta.persistence.*;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;
    import mx.itcelaya.hackonlinces.HackOnLinces.enums.SubmissionStatus;
    import org.hibernate.annotations.CreationTimestamp;
    import org.hibernate.annotations.UpdateTimestamp;

    import java.time.LocalDateTime;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.UUID;

    @Entity
    @Table(
            name = "submissions",
            uniqueConstraints = {
                    /*
                     * Un usuario no puede tener dos submissions con el mismo número de intento.
                     * Esto garantiza que attemptNumber sea secuencial y no se repita por usuario.
                     */
                    @UniqueConstraint(name = "uq_submissions_user_attempt", columnNames = {"user_id", "attempt_number"})
            },
            indexes = {
                    @Index(name = "idx_submissions_user_id", columnList = "user_id"),
                    @Index(name = "idx_submissions_status", columnList = "status"),
                    @Index(name = "idx_submissions_reviewed_by", columnList = "reviewed_by")
            }
    )
    @Getter
    @Setter
    @NoArgsConstructor
    public class Submission {

        /*
         * UUID como PK para este recurso porque se expondrá en URLs públicas.
         * Evita que alguien enumere submissions por ID secuencial.
         */
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(name = "id", updatable = false, nullable = false)
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        @Column(name = "attempt_number", nullable = false)
        private Integer attemptNumber;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 25)
        private SubmissionStatus status = SubmissionStatus.PENDING;

        /*
         * reason: comentario del admin al aprobar, rechazar o pedir reenvío.
         * También puede usarse por el usuario para describir su solicitud.
         */
        @Column(name = "reason", columnDefinition = "TEXT")
        private String reason;

        /*
         * reviewedBy apunta al User que revisó esta submission (debe ser ADMIN).
         * Es nullable porque al crearse no ha sido revisada aún.
         */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "reviewed_by")
        private User reviewedBy;

        @Column(name = "reviewed_at")
        private LocalDateTime reviewedAt;

        @Column(name = "sent_at")
        private LocalDateTime sentAt;

        @CreationTimestamp
        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;

        @UpdateTimestamp
        @Column(name = "updated_at", nullable = false)
        private LocalDateTime updatedAt;

        @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        private List<Document> documents = new ArrayList<>();
    }
