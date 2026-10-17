package com.project.MediFlow.security;

import com.project.MediFlow.Repository.AppointmentRepository;
import com.project.MediFlow.Repository.DoctorRepository;
import com.project.MediFlow.Repository.PatientRepository;
import com.project.MediFlow.entities.Doctor;
import com.project.MediFlow.entities.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizationServiceTest {
    private PatientRepository patients;
    private AppointmentRepository appointments;
    private DoctorRepository doctors;
    private PatientAuthorizationService patientAuthorization;
    private DoctorAuthorizationService doctorAuthorization;

    @BeforeEach
    void setUp() {
        patients = mock(PatientRepository.class);
        appointments = mock(AppointmentRepository.class);
        doctors = mock(DoctorRepository.class);
        patientAuthorization = new PatientAuthorizationService(patients, appointments);
        doctorAuthorization = new DoctorAuthorizationService(doctors, appointments);
    }

    @Test
    void patientCanAccessOwnProfileCaseInsensitively() {
        when(patients.findById(7L)).thenReturn(Optional.of(
                Patient.builder().id(7L).email("patient@example.com").build()));

        assertTrue(patientAuthorization.isOwnPatient(7L, "PATIENT@example.com"));
        assertFalse(patientAuthorization.isOwnPatient(7L, "other@example.com"));
    }

    @Test
    void missingPatientCannotPassOwnershipCheck() {
        when(patients.findById(99L)).thenReturn(Optional.empty());
        assertFalse(patientAuthorization.isOwnPatient(99L, "patient@example.com"));
    }

    @Test
    void patientAppointmentOwnershipUsesRepositoryCheck() {
        when(appointments.existsByIdAndPatient_Email(12L, "patient@example.com"))
                .thenReturn(true);
        when(appointments.existsByIdAndPatient_Email(13L, "patient@example.com"))
                .thenReturn(false);

        assertTrue(patientAuthorization.isOwnAppointment(12L, "patient@example.com"));
        assertFalse(patientAuthorization.isOwnAppointment(13L, "patient@example.com"));
    }

    @Test
    void doctorCanAccessOwnProfileCaseInsensitively() {
        when(doctors.findById(4L)).thenReturn(Optional.of(
                Doctor.builder().id(4L).email("doctor@example.com").build()));

        assertTrue(doctorAuthorization.isOwnDoctor(4L, "DOCTOR@example.com"));
        assertFalse(doctorAuthorization.isOwnDoctor(4L, "other@example.com"));
    }

    @Test
    void doctorAppointmentOwnershipUsesRepositoryCheck() {
        when(appointments.existsByIdAndDoctor_Email(22L, "doctor@example.com"))
                .thenReturn(true);
        assertTrue(doctorAuthorization.isOwnAppointment(22L, "doctor@example.com"));
    }
}