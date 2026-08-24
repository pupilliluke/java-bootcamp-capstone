package com.capstone.crm.repository;

import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    long countByRoleAndEnabledTrue(UserRole role);

    // Google sign-in resolves an account by the verified email on the ID token.
    Optional<AppUser> findByEmailIgnoreCase(String email);
}
