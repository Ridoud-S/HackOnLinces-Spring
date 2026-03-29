package mx.itcelaya.hackonlinces.HackOnLinces.repository;

import mx.itcelaya.hackonlinces.HackOnLinces.entity.Submission;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findByUser_IdOrderByAttemptNumberAsc(Long userId);

    boolean existsByUser_IdAndStatus(Long userId, SubmissionStatus status);

    int countByUser_Id(Long userId);

    Optional<Submission> findTopByUser_IdOrderByAttemptNumberDesc(Long userId);

    List<Submission> findByStatusOrderByCreatedAtAsc(SubmissionStatus status);

    // ── Métrica para dashboard ───────────────────────────────────────────────

    long countByStatus(SubmissionStatus status);

    @Query("SELECT COUNT(s) FROM Submission s")
    long countTotal();

    @Query("""
    SELECT s FROM Submission s
    LEFT JOIN FETCH s.documents d
    LEFT JOIN FETCH s.user u
    WHERE s.id = :id
""")
    Optional<Submission> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
    SELECT
        COUNT(s),
        SUM(CASE WHEN s.status = 'PENDING' THEN 1 ELSE 0 END),
        SUM(CASE WHEN s.status = 'APPROVED' THEN 1 ELSE 0 END),
        SUM(CASE WHEN s.status = 'REJECTED' THEN 1 ELSE 0 END),
        SUM(CASE WHEN s.status = 'RESUBMIT_REQUIRED' THEN 1 ELSE 0 END)
    FROM Submission s
    """)
    Object[] getSubmissionStats();
}