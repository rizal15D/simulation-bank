package com.example.ledgerbank.auth;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwords,
                          @Value("${ledgerbank.bootstrap-admin.email:}") String email,
                          @Value("${ledgerbank.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || !password.matches("[\\x20-\\x7E]{12,72}")) {
            throw new IllegalStateException("APP_ADMIN_EMAIL and a 12-72 character ASCII APP_ADMIN_PASSWORD are required together");
        }
        if (!users.existsByEmail(email)) {
            users.saveAndFlush(AppUser.admin(email, passwords.encode(password)));
        }
    }
}
