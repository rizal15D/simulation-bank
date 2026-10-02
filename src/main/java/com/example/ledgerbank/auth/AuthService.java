package com.example.ledgerbank.auth;

import com.example.ledgerbank.common.exception.BusinessException;
import com.example.ledgerbank.customer.Customer;
import com.example.ledgerbank.customer.CustomerRepository;
import com.example.ledgerbank.event.BankingEvent;
import com.example.ledgerbank.event.BankingEventPublisher;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder passwords;
    private final AuthSessionService sessions;
    private final BankingEventPublisher events;
    private final String dummyPasswordHash;

    public AuthService(AppUserRepository users, CustomerRepository customers,
                       PasswordEncoder passwords, AuthSessionService sessions, BankingEventPublisher events) {
        this.users = users;
        this.customers = customers;
        this.passwords = passwords;
        this.sessions = sessions;
        this.events = events;
        this.dummyPasswordHash = passwords.encode("timing-only-password-value");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email) || customers.existsByEmail(email)) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "Email is already registered", HttpStatus.CONFLICT);
        }
        Customer customer = customers.saveAndFlush(new Customer(request.fullName(), email));
        AppUser user = users.saveAndFlush(new AppUser(customer.getId(), email, passwords.encode(request.password())));
        return UserResponse.from(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = normalize(request.email());
        AppUser user = users.findByEmail(email).orElse(null);
        String storedHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwords.matches(request.password(), storedHash);
        if (user == null || !passwordMatches || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("INVALID_CREDENTIALS", "Email or password is invalid", HttpStatus.UNAUTHORIZED);
        }
        try {
            TokenResponse token = sessions.create(user);
            events.publish(BankingEvent.userLogin(user.getId(), user.getCustomerId()));
            return token;
        } catch (DataAccessException unavailable) {
            throw new BusinessException("AUTH_SERVICE_UNAVAILABLE", "Authentication session storage is unavailable",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
