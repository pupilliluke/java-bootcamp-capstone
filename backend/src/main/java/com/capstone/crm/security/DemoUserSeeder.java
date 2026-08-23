package com.capstone.crm.security;

import com.capstone.crm.entity.AppUser;
import com.capstone.crm.entity.UserRole;
import com.capstone.crm.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

//TODO: demo accounts only. Replace with real account provisioning before any
// deployment that is not a training environment.
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public DemoUserSeeder(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    // Hashes are generated here rather than written into the migration so no
    // credential material sits in version control, and so the cost factor
    // follows whatever PasswordEncoder the application is configured with.
    @Override
    public void run(ApplicationArguments args) {
        seed("agent1", "agent1@example.test", "agent1", UserRole.AGENT);
        seed("admin1", "admin1@example.test", "admin1", UserRole.ADMIN);
    }

    private void seed(String username, String email, String rawPassword, UserRole role) {
        if (users.findByUsername(username).isPresent()) {
            return;
        }
        users.save(new AppUser(username, email, encoder.encode(rawPassword), role));
        log.info("Seeded demo account {} with role {}", username, role);
    }
}
