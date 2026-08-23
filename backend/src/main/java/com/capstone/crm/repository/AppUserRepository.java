package com.capstone.crm.repository;

import com.capstone.crm.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    // Google sign-in resolves an account by the verified email on the ID token.
    Optional<AppUser> findByEmailIgnoreCase(String email);
}
