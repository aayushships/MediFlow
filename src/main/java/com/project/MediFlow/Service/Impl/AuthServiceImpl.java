package com.project.MediFlow.Service.Impl;

import com.project.MediFlow.Dtos.AuthResponse;
import com.project.MediFlow.Dtos.LoginRequest;
import com.project.MediFlow.Dtos.RegisterRequest;
import com.project.MediFlow.Enum.Role;
import com.project.MediFlow.entities.Patient;
import com.project.MediFlow.entities.User;
import com.project.MediFlow.Repository.PatientRepository;
import com.project.MediFlow.Repository.UserRepository;
import com.project.MediFlow.Service.AuthService;
import com.project.MediFlow.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User with this email already exists");
        }
        if (patientRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Patient profile with this email already exists");
        }
        if (patientRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("Patient with this phone number already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.PATIENT)
                .enabled(true)
                .build();
        userRepository.save(user);

        Patient patient = Patient.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .age(request.getAge())
                .gender(request.getGender())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .build();
        patientRepository.save(patient);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new IllegalStateException("User account is disabled");
        }

        return new AuthResponse(jwtService.generateToken(user));
    }
}