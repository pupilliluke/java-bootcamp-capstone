package com.capstone.crm.security;

import com.capstone.crm.entity.AppUser;
import com.capstone.crm.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrmUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public CrmUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        // A federated account has no hash. Spring's User builder rejects a null
        // password, so an empty placeholder is used: it can never match a BCrypt
        // check, which is exactly the desired outcome for a password login.
        String password = user.getPasswordHash() == null ? "" : user.getPasswordHash();

        return User.withUsername(user.getUsername())
                .password(password)
                .roles(user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }
}
