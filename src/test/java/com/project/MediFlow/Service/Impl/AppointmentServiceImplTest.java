package com.project.MediFlow.Service.Impl;

import com.project.MediFlow.Repository.AppointmentRepository;
import com.project.MediFlow.Repository.DoctorAvailabilityRepository;
import com.project.MediFlow.Repository.DoctorRepository;
import com.project.MediFlow.Repository.PatientRepository;
import com.project.MediFlow.RabbitMQ.Producer.AppointmentProducer;
import com.project.MediFlow.entities.Appointment;
import com.project.MediFlow.entities.Doctor;
import com.project.MediFlow.entities.DoctorAvailability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppointmentServiceImplTest {

    private AppointmentRepository appointmentRepository;
    private DoctorRepository doctorRepository;
    private DoctorAvailabilityRepository availabilityRepository;
    private AppointmentServiceImpl service;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(AppointmentRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        doctorRepository = mock(DoctorRepository.class);
        availabilityRepository = mock(DoctorAvailabilityRepository.class);
        AppointmentProducer producer = mock(AppointmentProducer.class);

        service = new AppointmentServiceImpl(
                appointmentRepository,
                patientRepository,
                doctorRepository,
                availabilityRepository,
                producer
        );

        doctor = mock(Doctor.class);
        when(doctor.getId()).thenReturn(1L);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
    }

    @Test
    void generatesFourSlotsForTwoHourWindow() {
        LocalDate date = LocalDate.now().plusDays(10);
        when(availabilityRepository.findByDoctorIdAndDateOrderByStartTime(1L, date))
                .thenReturn(List.of(window(date, "09:00", "11:00")));
        when(appointmentRepository
                .findByDoctor_IdAndAppointmentDateTimeGreaterThanEqualAndAppointmentDateTimeLessThanAndStatusNotIn(
                        eq(1L), any(LocalDateTime.class), any(LocalDateTime.class), anyList()))
                .thenReturn(List.of());

        var slots = service.getAvailableSlots(1L, date);

        assertEquals(4, slots.size());
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 0)), slots.get(0).startTime());
        assertEquals(LocalDateTime.of(date, LocalTime.of(10, 30)), slots.get(3).startTime());
        assertEquals(LocalDateTime.of(date, LocalTime.of(11, 0)), slots.get(3).endTime());
    }

    @Test
    void doesNotGenerateSlotWhenWindowIsShorterThanThirtyMinutes() {
        LocalDate date = LocalDate.now().plusDays(10);
        when(availabilityRepository.findByDoctorIdAndDateOrderByStartTime(1L, date))
                .thenReturn(List.of(window(date, "09:00", "09:15")));
        when(appointmentRepository
                .findByDoctor_IdAndAppointmentDateTimeGreaterThanEqualAndAppointmentDateTimeLessThanAndStatusNotIn(
                        eq(1L), any(LocalDateTime.class), any(LocalDateTime.class), anyList()))
                .thenReturn(List.of());

        assertEquals(0, service.getAvailableSlots(1L, date).size());
    }

    @Test
    void excludesSlotOverlappingExistingActiveAppointment() {
        LocalDate date = LocalDate.now().plusDays(10);
        LocalDateTime occupiedStart = LocalDateTime.of(date, LocalTime.of(9, 30));
        Appointment appointment = mock(Appointment.class);
        when(appointment.getAppointmentDateTime()).thenReturn(occupiedStart);
        when(availabilityRepository.findByDoctorIdAndDateOrderByStartTime(1L, date))
                .thenReturn(List.of(window(date, "09:00", "11:00")));
        when(appointmentRepository
                .findByDoctor_IdAndAppointmentDateTimeGreaterThanEqualAndAppointmentDateTimeLessThanAndStatusNotIn(
                        eq(1L), any(LocalDateTime.class), any(LocalDateTime.class), anyList()))
                .thenReturn(List.of(appointment));

        var slots = service.getAvailableSlots(1L, date);

        assertEquals(3, slots.size());
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 0)), slots.get(0).startTime());
        assertEquals(LocalDateTime.of(date, LocalTime.of(10, 0)), slots.get(1).startTime());
    }

    private DoctorAvailability window(LocalDate date, String start, String end) {
        return DoctorAvailability.builder()
                .doctor(doctor)
                .date(date)
                .startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end))
                .build();
    }
}
