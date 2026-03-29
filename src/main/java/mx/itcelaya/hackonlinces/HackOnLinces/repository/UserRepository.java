package mx.itcelaya.hackonlinces.HackOnLinces.repository;

import mx.itcelaya.hackonlinces.HackOnLinces.entity.User;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.AccountStatus;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.RoleName;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /*
     * Carga el usuario con sus roles en una sola query (JOIN FETCH).
     * Evita N+1 al construir el UserDetails para Spring Security.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    /*
     * Waitlist: usuarios externos pendientes de aprobación.
     */
    List<User> findByUserTypeAndAccountStatus(UserType userType, AccountStatus accountStatus);

    // ── Métricas para dashboard ──────────────────────────────────────────────

    long countByAccountStatus(AccountStatus accountStatus);

    long countByUserType(UserType userType);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from")
    long countRegisteredSince(@Param("from") LocalDateTime from);

    @Query("SELECT COUNT(u) FROM User u JOIN u.userRoles ur WHERE ur.role.name = :roleName")
    long countByRoleName(@Param("roleName") RoleName roleName);

    // ── Listado con filtros opcionales para panel admin ──────────────────────

    /*
     * Todos los parámetros son opcionales.
     * Si son null la condición se omite — equivale a "sin filtro".
     * DISTINCT es necesario porque el JOIN FETCH puede duplicar filas.
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.userRoles ur
        LEFT JOIN FETCH ur.role
        WHERE (:status   IS NULL OR u.accountStatus = :status)
          AND (:userType IS NULL OR u.userType      = :userType)
          AND (:search   IS NULL
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY u.createdAt DESC
        """)
    List<User> findAllWithFilters(
            @Param("status")   AccountStatus status,
            @Param("userType") UserType userType,
            @Param("search")   String search
    );

    /*
     * Devuelve [User, submissionCount] sin JOIN FETCH para evitar duplicados
     * en la proyección Object[]. Los roles se cargan en AdminService con
     * findByEmailWithRoles si fuera necesario, o simplemente con el lazy
     * loading dentro de la transacción activa.
     *
     * La subquery COUNT es segura frente a null — COUNT siempre devuelve 0
     * cuando no hay filas, nunca null.
     */
    @Query("""
    SELECT u, (SELECT COUNT(s) FROM Submission s WHERE s.user.id = u.id)
    FROM User u
    WHERE (:status   IS NULL OR u.accountStatus = :status)
      AND (:userType IS NULL OR u.userType      = :userType)
      AND (:search   IS NULL
           OR LOWER(u.fullName) LIKE :search
           OR LOWER(u.email)    LIKE :search)
    ORDER BY u.createdAt DESC
    """)
    List<Object[]> findAllWithSubmissionCounts(
            @Param("status")   AccountStatus status,
            @Param("userType") UserType userType,
            @Param("search")   String search
    );



    /*
     * Stats para el dashboard.
     *
     * Problema original: el LEFT JOIN de userRoles multiplicaba filas,
     * haciendo que los SUM de accountStatus contaran duplicados cuando
     * un usuario tenía más de un rol asignado.
     *
     * Solución: cada métrica es una subquery escalar independiente.
     * De esta forma no hay JOIN entre dimensiones y las cardinalidades
     * no se mezclan. El FROM User u / GROUP BY u.id es un alias técnico
     * de JPQL para poder escribir múltiples subqueries en el SELECT —
     * JPQL no tiene SELECT sin FROM como SQL nativo.
     *
     * Si la base de datos estuviera vacía, la query no devolvería filas;
     * AdminService lo maneja con getLongOrZero().
     */
    @Query("""
        SELECT
            COUNT(DISTINCT u.id),
            SUM(CASE WHEN u.accountStatus = mx.itcelaya.hackonlinces.HackOnLinces.enums.AccountStatus.PENDING   THEN 1 ELSE 0 END),
            SUM(CASE WHEN u.accountStatus = mx.itcelaya.hackonlinces.HackOnLinces.enums.AccountStatus.APPROVED  THEN 1 ELSE 0 END),
            SUM(CASE WHEN u.accountStatus = mx.itcelaya.hackonlinces.HackOnLinces.enums.AccountStatus.REJECTED  THEN 1 ELSE 0 END),
            SUM(CASE WHEN u.userType = mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType.INTERNAL THEN 1 ELSE 0 END),
            SUM(CASE WHEN u.userType = mx.itcelaya.hackonlinces.HackOnLinces.enums.UserType.EXTERNAL THEN 1 ELSE 0 END),
            (SELECT COUNT(ur) FROM UserRole ur WHERE ur.role.name = mx.itcelaya.hackonlinces.HackOnLinces.enums.RoleName.ADMIN),
            (SELECT COUNT(ur) FROM UserRole ur WHERE ur.role.name = mx.itcelaya.hackonlinces.HackOnLinces.enums.RoleName.JUDGE),
            (SELECT COUNT(ur) FROM UserRole ur WHERE ur.role.name = mx.itcelaya.hackonlinces.HackOnLinces.enums.RoleName.SPEAKER),
            (SELECT COUNT(ur) FROM UserRole ur WHERE ur.role.name = mx.itcelaya.hackonlinces.HackOnLinces.enums.RoleName.PARTICIPANT),
            (SELECT COUNT(ur) FROM UserRole ur WHERE ur.role.name = mx.itcelaya.hackonlinces.HackOnLinces.enums.RoleName.GUEST),
            SUM(CASE WHEN u.createdAt >= :startOfToday THEN 1 ELSE 0 END),
            SUM(CASE WHEN u.createdAt >= :startOfWeek  THEN 1 ELSE 0 END)
        FROM User u
        """)
    Object[] getUserStats(
            @Param("startOfToday") LocalDateTime startOfToday,
            @Param("startOfWeek")  LocalDateTime startOfWeek
    );


}