package com.project.MediFlow.Service.Impl;

import com.project.MediFlow.Dtos.LoginRequest;
import com.project.MediFlow.Dtos.RegisterRequest;
import com.project.MediFlow.Repository.PatientRepository;
import com.project.MediFlow.Repository.UserRepository;
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
    private PatientRepository patients;
    private PasswordEncoder encoder;
    private JwtService jwt;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        patients = mock(PatientRepository.class);
        encoder = mock(PasswordEncoder.class);
        jwt = mock(JwtService.class);
        service = new AuthServiceImpl(users, patients, encoder, jwt);
    }

    private RegisterRequest validRegistration() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("Secret123!");
        request.setFirstName("Aarav");
        request.setLastName("Patil");
        request.setAge(21);
        request.setGender("Male");
        request.setPhone("9876501234");
        request.setAddress("Pune");
        return request;
    }

    @Test
    void registerCreatesUserAndPatientProfile() {
        RegisterRequest request = validRegistration();
        when(users.existsByEmail(request.getEmail())).thenReturn(false);
        when(patients.existsByEmail(request.getEmail())).thenReturn(false);
        when(patients.existsByPhone(request.getPhone())).thenReturn(false);
        when(encoder.encode("Secret123!")).thenReturn("encoded-password");

        service.register(request);

        verify(users).save(argThat(user ->
                user.getEmail().equals("new@example.com")
                        && user.getPassword().equals("encoded-password")
                        && user.isEnabled()));
        verify(patients).save(argThat(patient ->
                patient.getEmail().equals("new@example.com")
                        && patient.getFirstName().equals("Aarav")
                        && patient.getPhone().equals("9876501234")));
    }

    @Test
    void registerRejectsDuplicateUserEmail() {
        RegisterRequest request = validRegistration();
        when(users.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verify(users, never()).save(any());
        verify(patients, never()).save(any());
    }

    @Test
    void registerRejectsDuplicatePatientPhone() {
        RegisterRequest request = validRegistration();
        when(users.existsByEmail(request.getEmail())).thenReturn(false);
        when(patients.existsByEmail(request.getEmail())).thenReturn(false);
        when(patients.existsByPhone(request.getPhone())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verify(users, never()).save(any());
        verify(patients, never()).save(any());
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