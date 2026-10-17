package com.project.MediFlow.Service.Impl;

import com.project.MediFlow.Dtos.LoginRequest;
import com.project.MediFlow.Dtos.RegisterRequest;
import com.project.MediFlow.Repository.UserRepository;
import com.project.MediFlow.Repository.PatientRepository;
import com.project.MediFlow.Service.Impl.AuthServiceImpl;
import com.project.MediFlow.security.JwtService;
import com.project.MediFlow.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {
    private UserRepository users;
    private PasswordEncoder encoder;
    private JwtService jwt;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        jwt = mock(JwtService.class);
        service = new AuthServiceImpl(users, encoder, jwt);
    }

    @Test
    void registerSavesEncodedPasswordWhenEmailIsNew() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("Secret123!");

        when(users.existsByEmail(request.getEmail())).thenReturn(false);
        when(encoder.encode("Secret123!")).thenReturn("encoded-password");

        service.register(request);

        verify(users).save(argThat(user ->
                user.getEmail().equals("new@example.com")
                        && user.getPassword().equals("encoded-password")
                        && user.isEnabled()));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("Secret123!");
        when(users.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verify(users, never()).save(any());
    }

    @Test
    void loginReturnsJwtForValidCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("patient@example.com");
        request.setPassword("Secret123!");
        User user = User.builder().email(request.getEmail())
                .password("encoded-password").enabled(true).build();
        when(users.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("Secret123!", "encoded-password")).thenReturn(true);
        when(jwt.generateToken(user)).thenReturn("test-jwt");

        assertEquals("test-jwt", service.login(request).getToken());
    }

    @Test
    void loginRejectsUnknownEmail() {
        LoginRequest request = new LoginRequest();
        request.setEmail("missing@example.com");
        request.setPassword("Secret123!");
        when(users.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.login(request));
    }

    @Test
    void loginRejectsWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setEmail("patient@example.com");
        request.setPassword("wrong");
        User user = User.builder().email(request.getEmail())
                .password("encoded-password").enabled(true).build();
        when(users.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "encoded-password")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.login(request));
    }

    @Test
    void loginRejectsDisabledAccount() {
        LoginRequest request = new LoginRequest();
        request.setEmail("disabled@example.com");
        request.setPassword("Secret123!");
        User user = User.builder().email(request.getEmail())
                .password("encoded-password").enabled(false).build();
        when(users.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("Secret123!", "encoded-password")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.login(request));
    }
}