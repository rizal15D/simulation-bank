package com.example.ledgerbank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ledgerbank.common.exception.BusinessException;
import com.example.ledgerbank.customer.Customer;
import com.example.ledgerbank.customer.CustomerRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private AppUserRepository users;
    @Mock private CustomerRepository customers;
    @Mock private PasswordEncoder passwords;
    @Mock private AuthSessionService sessions;
    private AuthService service;

    @BeforeEach
    void setUp() {
        when(passwords.encode(anyString())).thenAnswer(invocation -> "HASH:" + invocation.getArgument(0));
        service = new AuthService(users, customers, passwords, sessions);
    }

    @Test
    void registrationCreatesLinkedCustomerAndStoresOnlyPasswordHash() {
        UUID customerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            ReflectionTestUtils.setField(customer, "id", customerId);
            return customer;
        });
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", userId);
            return user;
        });

        UserResponse response = service.register(new RegisterRequest(
                "Budi Santoso", "BUDI@example.com", "correct horse battery"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.email()).isEqualTo("budi@example.com");
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("HASH:correct horse battery");
        assertThat(saved.getValue().getPasswordHash()).doesNotMatch("correct horse battery");
    }

    @Test
    void loginUsesHashAndCreatesRedisSession() {
        AppUser user = new AppUser(UUID.randomUUID(), "budi@example.com", "stored-hash");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        TokenResponse token = new TokenResponse("token", "Bearer", Instant.now(), UserResponse.from(user));
        when(users.findByEmail("budi@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("the-password", "stored-hash")).thenReturn(true);
        when(sessions.create(user)).thenReturn(token);

        assertThat(service.login(new LoginRequest("BUDI@example.com", "the-password"))).isSameAs(token);
        verify(sessions).create(user);
    }

    @Test
    void loginDoesNotRevealWhetherEmailOrPasswordWasWrong() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(passwords.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("missing@example.com", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email or password is invalid")
                .extracting("code").isEqualTo("INVALID_CREDENTIALS");
    }
}
