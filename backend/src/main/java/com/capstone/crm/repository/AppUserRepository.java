package com.capstone.crm.repository;

import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    long countByRoleAndEnabledTrue(UserRole role);

    // The approval queue: self-registered accounts start disabled, oldest first.
    // Scoped to a role on purpose. "Pending" means a self-service sign-up waiting
    // for approval, and self-service sign-up only ever creates agents. Without the
    // role in the query an ADMIN that somebody deliberately suspended is also
    // disabled, so it would appear in the approval queue and the Approve button
    // would quietly restore it.
    List<AppUser> findByEnabledFalseAndRoleOrderByCreatedAtAsc(UserRole role);

    // Google sign-in resolves an account by the verified email on the ID token.
    Optional<AppUser> findByEmailIgnoreCase(String email);
}
