package com.project.MediFlow.Service.Impl;

import com.project.MediFlow.Dtos.AppointmentRequest;
import com.project.MediFlow.Exception.ResourceNotFoundException;
import com.project.MediFlow.Repository.AppointmentRepository;
import com.project.MediFlow.Repository.DoctorAvailabilityRepository;
import com.project.MediFlow.Repository.DoctorRepository;
import com.project.MediFlow.Repository.PatientRepository;
import com.project.MediFlow.RabbitMQ.Producer.AppointmentProducer;
import com.project.MediFlow.entities.Doctor;
import com.project.MediFlow.entities.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AppointmentServiceImplFailureTest {
    private AppointmentRepository appointments;
    private PatientRepository patients;
    private DoctorRepository doctors;
    private AppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentRepository.class);
        patients = mock(PatientRepository.class);
        doctors = mock(DoctorRepository.class);
        DoctorAvailabilityRepository availability = mock(DoctorAvailabilityRepository.class);
        AppointmentProducer producer = mock(AppointmentProducer.class);
        service = new AppointmentServiceImpl(
                appointments, patients, doctors, availability, producer);
    }

    @Test
    void rejectsRequestWhenPatientDoesNotExist() {
        AppointmentRequest request = AppointmentRequest.builder()
                .patientId(404L)
                .doctorId(1L)
                .appointmentDateTime(LocalDateTime.now().plusDays(3).withMinute(0).withSecond(0).withNano(0))
                .reason("Consultation")
                .build();
        when(patients.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createAppointment(request));
        verifyNoInteractions(appointments, doctors);
    }

    @Test
    void rejectsRequestWhenDoctorDoesNotExist() {
        AppointmentRequest request = AppointmentRequest.builder()
                .patientId(1L)
                .doctorId(404L)
                .appointmentDateTime(LocalDateTime.now().plusDays(3).withMinute(0).withSecond(0).withNano(0))
                .reason("Consultation")
                .build();
        when(patients.findById(1L)).thenReturn(Optional.of(
                Patient.builder().id(1L).firstName("Test").lastName("Patient")
                        .email("patient@example.com").phone("9876500001")
                        .gender("Other").address("Pune").age(25).build()));
        when(doctors.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createAppointment(request));
        verify(appointments, never()).save(any());
    }
}