package mx.itcelaya.hackonlinces.HackOnLinces.repository;

import mx.itcelaya.hackonlinces.HackOnLinces.entity.UserRole;
import mx.itcelaya.hackonlinces.HackOnLinces.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;


public interface UserRoleRepository extends JpaRepository<UserRole, UserRole.UserRoleId> {

    boolean existsByUser_IdAndRole_Name(Long userId, RoleName roleName);
}