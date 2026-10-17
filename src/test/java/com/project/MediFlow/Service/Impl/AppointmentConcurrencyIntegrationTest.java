
package com.project.MediFlow.Service.Impl;

import com.project.MediFlow.Dtos.AppointmentRequest;
import com.project.MediFlow.Dtos.AppointmentResponse;
import com.project.MediFlow.Enum.AppointmentStatus;
import com.project.MediFlow.RabbitMQ.Producer.AppointmentProducer;
import com.project.MediFlow.Repository.AppointmentRepository;
import com.project.MediFlow.Repository.DoctorAvailabilityRepository;
import com.project.MediFlow.Repository.DoctorRepository;
import com.project.MediFlow.Repository.PatientRepository;
import com.project.MediFlow.entities.Appointment;
import com.project.MediFlow.entities.Doctor;
import com.project.MediFlow.entities.DoctorAvailability;
import com.project.MediFlow.entities.Patient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class AppointmentConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("mediflow_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
                postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    AppointmentServiceImpl appointmentService;

    @Autowired
    PatientRepository patientRepository;

    @Autowired
    DoctorRepository doctorRepository;

    @Autowired
    DoctorAvailabilityRepository availabilityRepository;

    @Autowired
    AppointmentRepository appointmentRepository;

    @MockitoBean
    AppointmentProducer appointmentProducer;

    @Test
    void onlyOnePatientCanBookTheSameSlotConcurrently()
            throws Exception {

        LocalDate date = LocalDate.now().plusDays(7);
        LocalDateTime slot = LocalDateTime.of(
                date, LocalTime.of(10, 0));

        Doctor doctor = doctorRepository.save(Doctor.builder()
                .firstName("Test")
                .lastName("Doctor")
                .gender("Male")
                .specialization("General Medicine")
                .phone("9000000001")
                .email("doctor@test.com")
                .build());

        Patient patient1 = patientRepository.save(Patient.builder()
                .firstName("Patient")
                .lastName("One")
                .age(25)
                .gender("Male")
                .phone("9000000002")
                .email("patient1@test.com")
                .address("Pune")
                .build());

        Patient patient2 = patientRepository.save(Patient.builder()
                .firstName("Patient")
                .lastName("Two")
                .age(26)
                .gender("Male")
                .phone("9000000003")
                .email("patient2@test.com")
                .address("Pune")
                .build());

        availabilityRepository.save(DoctorAvailability.builder()
                .doctor(doctor)
                .date(date)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Boolean> booking1 = () -> attemptBooking(
                    patient1.getId(), doctor.getId(),
                    slot, ready, start);

            Callable<Boolean> booking2 = () -> attemptBooking(
                    patient2.getId(), doctor.getId(),
                    slot, ready, start);

            Future<Boolean> result1 = executor.submit(booking1);
            Future<Boolean> result2 = executor.submit(booking2);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            boolean success1 = result1.get(30, TimeUnit.SECONDS);
            boolean success2 = result2.get(30, TimeUnit.SECONDS);

            assertNotEquals(success1, success2,
                    "Exactly one booking must succeed");

            List<Appointment> appointments =
                    appointmentRepository.findAll();

            long activeBookings = appointments.stream()
                    .filter(a -> a.getDoctor().getId().equals(doctor.getId()))
                    .filter(a -> a.getAppointmentDateTime().equals(slot))
                    .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED
                            && a.getStatus() != AppointmentStatus.REJECTED)
                    .count();

            assertEquals(1, activeBookings,
                    "The slot must have exactly one active booking");

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private boolean attemptBooking(
            Long patientId,
            Long doctorId,
            LocalDateTime slot,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start");
        }

        try {
            AppointmentRequest request = AppointmentRequest.builder()
                    .patientId(patientId)
                    .doctorId(doctorId)
                    .appointmentDateTime(slot)
                    .reason("Concurrency test")
                    .build();

            AppointmentResponse response =
                    appointmentService.createAppointment(request);

            return response != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }




}
