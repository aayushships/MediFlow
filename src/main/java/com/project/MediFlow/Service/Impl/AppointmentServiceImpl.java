package com.project.MediFlow.Service.Impl;

import com.project.MediFlow.Dtos.AppointmentRequest;
import com.project.MediFlow.Dtos.AppointmentResponse;
import com.project.MediFlow.Dtos.AvailableSlotResponse;
import com.project.MediFlow.Enum.AppointmentEventType;
import com.project.MediFlow.Enum.AppointmentStatus;
import com.project.MediFlow.Enum.NotificationStatus;
import com.project.MediFlow.Exception.DuplicateResourceException;
import com.project.MediFlow.Exception.ResourceNotFoundException;
import com.project.MediFlow.RabbitMQ.Event.AppointmentEvent;
import com.project.MediFlow.RabbitMQ.Producer.AppointmentProducer;
import com.project.MediFlow.Repository.AppointmentRepository;
import com.project.MediFlow.Repository.DoctorAvailabilityRepository;
import com.project.MediFlow.Repository.DoctorRepository;
import com.project.MediFlow.Repository.PatientRepository;
import com.project.MediFlow.Service.AppointmentService;
import com.project.MediFlow.dto.PatientAppointmentRequest;
import com.project.MediFlow.entities.Appointment;
import com.project.MediFlow.entities.DoctorAvailability;
import com.project.MediFlow.entities.Doctor;
import com.project.MediFlow.entities.Patient;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentProducer appointmentProducer;

    public AppointmentServiceImpl(
            AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            DoctorAvailabilityRepository availabilityRepository,
            AppointmentProducer appointmentProducer) {

        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.appointmentProducer = appointmentProducer;
    }

    // =========================================================
    // RECEPTIONIST / ADMIN - CREATE APPOINTMENT
    // =========================================================

    @Override
    @Transactional
    public AppointmentResponse createAppointment(
            AppointmentRequest request) {

        // 1. Check patient
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Patient not found with id: "
                                        + request.getPatientId()
                        )
                );

        // 2. Check doctor
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Doctor not found with id: "
                                        + request.getDoctorId()
                        )
                );

        // Validate the requested 30-minute slot against explicit doctor availability.
        validateBookableSlot(request.getDoctorId(), request.getAppointmentDateTime(), null);

        // 3. Check doctor's availability
        //
        // CANCELLED and REJECTED appointments free the slot.
        // All other statuses block the slot.
        List<AppointmentStatus> freeStatuses = List.of(
                AppointmentStatus.CANCELLED,
                AppointmentStatus.REJECTED
        );

        if (appointmentRepository
                .existsByDoctor_IdAndAppointmentDateTimeAndStatusNotIn(
                        request.getDoctorId(),
                        request.getAppointmentDateTime(),
                        freeStatuses)) {

            throw new DuplicateResourceException(
                    "Doctor already has an appointment at this time"
            );
        }

        // 4. Create appointment
        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .appointmentDateTime(request.getAppointmentDateTime())
                .bookedAt(LocalDateTime.now())
                .reason(request.getReason())
                .status(AppointmentStatus.SCHEDULED)
                .notificationStatus(NotificationStatus.PENDING)
                .build();

        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        // 5. Create event
        AppointmentEvent event = new AppointmentEvent(
                AppointmentEventType.APPOINTMENT_CREATED,
                savedAppointment.getId(),
                patient.getFirstName(),
                patient.getEmail(),
                doctor.getFirstName(),
                savedAppointment.getAppointmentDateTime(),
                null,
                null
        );

        // 6. Publish notification
        //
        // If RabbitMQ is unavailable, the appointment remains
        // successfully saved with notificationStatus = PENDING.
        try {

            appointmentProducer.publishAppointmentEvent(event);

        } catch (Exception e) {

            System.out.println(
                    "RabbitMQ unavailable. Notification remains PENDING for appointment "
                            + savedAppointment.getId()
            );
        }

        return mapToResponse(
                savedAppointment,
                patient,
                doctor
        );
    }

    // =========================================================
    // MAP ENTITY TO RESPONSE
    // =========================================================

    public AppointmentResponse mapToResponse(
            Appointment appointment,
            Patient patient,
            Doctor doctor) {

        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(patient.getId())
                .patientName(
                        patient.getFirstName()
                                + " "
                                + patient.getLastName()
                )
                .doctorId(doctor.getId())
                .doctorName(
                        doctor.getFirstName()
                                + " "
                                + doctor.getLastName()
                )
                .appointmentDateTime(
                        appointment.getAppointmentDateTime()
                )
                .bookedAt(appointment.getBookedAt())
                .reason(appointment.getReason())
                .status(appointment.getStatus())
                .build();
    }

    // =========================================================
    // AVAILABLE SLOTS (30 MINUTES)
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> getAvailableSlots(Long doctorId, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date is required");
        }

        doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor not found with id: " + doctorId));

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<AppointmentStatus> freeStatuses = List.of(
                AppointmentStatus.CANCELLED,
                AppointmentStatus.REJECTED
        );

        List<Appointment> appointments =
                appointmentRepository
                        .findByDoctor_IdAndAppointmentDateTimeGreaterThanEqualAndAppointmentDateTimeLessThanAndStatusNotIn(
                                doctorId, dayStart, dayEnd, freeStatuses);

        List<DoctorAvailability> windows =
                availabilityRepository.findByDoctorIdAndDateOrderByStartTime(
                        doctorId, date);

        LocalDateTime now = LocalDateTime.now();
        List<AvailableSlotResponse> slots = new ArrayList<>();

        for (DoctorAvailability window : windows) {
            LocalDateTime slotStart = LocalDateTime.of(date, window.getStartTime());
            LocalDateTime windowEnd = LocalDateTime.of(date, window.getEndTime());

            while (!slotStart.plusMinutes(30).isAfter(windowEnd)) {
                LocalDateTime slotEnd = slotStart.plusMinutes(30);
                boolean occupied = appointments.stream().anyMatch(appointment -> {
                    LocalDateTime existingStart = appointment.getAppointmentDateTime();
                    LocalDateTime existingEnd = existingStart.plusMinutes(30);
                    return existingStart.isBefore(slotEnd) && existingEnd.isAfter(slotStart);
                });

                if (slotStart.isAfter(now) && !occupied) {
                    slots.add(new AvailableSlotResponse(slotStart, slotEnd));
                }
                slotStart = slotEnd;
            }
        }

        slots.sort(Comparator.comparing(AvailableSlotResponse::startTime));
        return slots;
    }

    private void validateBookableSlot(Long doctorId, LocalDateTime slotStart, Long excludedAppointmentId) {
        if (slotStart == null || !slotStart.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Appointment must be in the future");
        }
        if (slotStart.getSecond() != 0 || slotStart.getNano() != 0
                || slotStart.getMinute() % 30 != 0) {
            throw new IllegalArgumentException(
                    "Appointment must start on a 30-minute boundary (e.g. 09:00 or 09:30)");
        }

        LocalDate date = slotStart.toLocalDate();
        LocalTime start = slotStart.toLocalTime();
        LocalTime end = start.plusMinutes(30);

        boolean withinAvailability = availabilityRepository
                .findByDoctorIdAndDateOrderByStartTime(doctorId, date)
                .stream()
                .anyMatch(window -> !start.isBefore(window.getStartTime())
                        && !end.isAfter(window.getEndTime()));

        if (!withinAvailability) {
            throw new IllegalArgumentException(
                    "Requested 30-minute slot is outside the doctor's availability");
        }

        List<AppointmentStatus> freeStatuses = List.of(
                AppointmentStatus.CANCELLED,
                AppointmentStatus.REJECTED
        );
        boolean occupied = appointmentRepository
                .findByDoctor_IdAndAppointmentDateTimeGreaterThanEqualAndAppointmentDateTimeLessThanAndStatusNotIn(
                        doctorId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), freeStatuses)
                .stream()
                .filter(a -> excludedAppointmentId == null || !a.getId().equals(excludedAppointmentId))
                .anyMatch(a -> {
                    LocalDateTime existingStart = a.getAppointmentDateTime();
                    LocalDateTime existingEnd = existingStart.plusMinutes(30);
                    return existingStart.isBefore(slotStart.plusMinutes(30))
                            && existingEnd.isAfter(slotStart);
                });

        if (occupied) {
            throw new DuplicateResourceException("Doctor already has an appointment overlapping this slot");
        }
    }

    // =========================================================
    // GET ALL APPOINTMENTS
    // =========================================================

    @Override
    @Transactional
    public List<AppointmentResponse> getAllAppointments() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        boolean isDoctor = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_DOCTOR")
                );

        if (isDoctor) {
            return getAppointmentsForDoctor(authentication.getName());
        }

        return appointmentRepository.findAllWithPatientAndDoctor()
                .stream()
                .map(appointment ->
                        mapToResponse(
                                appointment,
                                appointment.getPatient(),
                                appointment.getDoctor()
                        )
                )
                .toList();
    }

    // =========================================================
    // GET DOCTOR APPOINTMENTS
    // =========================================================

    @Override
    public List<AppointmentResponse> getAppointmentsForDoctor(
            String email) {

        return appointmentRepository
                .findAllWithPatientAndDoctorByDoctorEmail(email)
                .stream()
                .map(appointment ->
                        mapToResponse(
                                appointment,
                                appointment.getPatient(),
                                appointment.getDoctor()
                        )
                )
                .toList();
    }

    // =========================================================
    // GET APPOINTMENT BY ID
    // =========================================================

    @Override
    public AppointmentResponse getAppointmentById(Long id) {

        Appointment appointment =
                appointmentRepository
                        .findByIdWithPatientAndDoctor(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Appointment not found with id: "
                                                + id
                                )
                        );

        return mapToResponse(
                appointment,
                appointment.getPatient(),
                appointment.getDoctor()
        );
    }

    // =========================================================
    // CANCEL APPOINTMENT
    // =========================================================

    @Override
    @Transactional
    public AppointmentResponse cancelAppointment(Long id) {

        Appointment appointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Appointment not found with id: " + id
                                )
                        );

        AppointmentStatus status = appointment.getStatus();

        if (status == AppointmentStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Appointment is already cancelled"
            );
        }

        if (status == AppointmentStatus.REJECTED) {

            throw new IllegalStateException(
                    "Rejected appointments cannot be cancelled"
            );
        }

        if (status == AppointmentStatus.COMPLETED) {

            throw new IllegalStateException(
                    "Completed appointments cannot be cancelled"
            );
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);

        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        return mapToResponse(
                savedAppointment,
                savedAppointment.getPatient(),
                savedAppointment.getDoctor()
        );
    }

    // =========================================================
    // CONFIRM APPOINTMENT
    // =========================================================

    @Override
    @Transactional
    public AppointmentResponse confirmAppointment(Long id) {

        Appointment appointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Appointment not found with id: " + id
                                )
                        );

        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {

            throw new IllegalStateException(
                    "Only SCHEDULED appointments can be confirmed"
            );
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);

        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        Patient patient = savedAppointment.getPatient();
        Doctor doctor = savedAppointment.getDoctor();

        AppointmentEvent event = new AppointmentEvent(
                AppointmentEventType.APPOINTMENT_CONFIRMED,
                savedAppointment.getId(),
                patient.getFirstName() + " " + patient.getLastName(),
                patient.getEmail(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                savedAppointment.getAppointmentDateTime(),
                null,
                null
        );

        appointmentProducer.publishAppointmentEvent(event);

        return mapToResponse(
                savedAppointment,
                patient,
                doctor
        );
    }

    // =========================================================
    // COMPLETE APPOINTMENT
    // =========================================================

    @Override
    @Transactional
    public AppointmentResponse completeAppointment(Long id) {

        Appointment appointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Appointment not found with id: " + id
                                )
                        );

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {

            throw new IllegalStateException(
                    "Only CONFIRMED appointments can be completed"
            );
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);

        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        Patient patient = savedAppointment.getPatient();
        Doctor doctor = savedAppointment.getDoctor();

        AppointmentEvent event = new AppointmentEvent(
                AppointmentEventType.APPOINTMENT_COMPLETED,
                savedAppointment.getId(),
                patient.getFirstName() + " " + patient.getLastName(),
                patient.getEmail(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                savedAppointment.getAppointmentDateTime(),
                null,
                null
        );

        appointmentProducer.publishAppointmentEvent(event);

        return mapToResponse(
                savedAppointment,
                patient,
                doctor
        );
    }

    // =========================================================
    // RESCHEDULE APPOINTMENT
    // =========================================================

    @Override
    @Transactional
    public AppointmentResponse rescheduleAppointment(
            Long id,
            LocalDateTime newAppointmentDateTime) {

        Appointment appointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Appointment not found with id: "
                                                + id
                                )
                        );

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Cancelled appointment cannot be rescheduled"
            );
        }

        if (appointment.getStatus() == AppointmentStatus.REJECTED) {

            throw new IllegalStateException(
                    "Rejected appointment cannot be rescheduled"
            );
        }

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {

            throw new IllegalStateException(
                    "Completed appointment cannot be rescheduled"
            );
        }

        if (newAppointmentDateTime == null) {

            throw new IllegalArgumentException(
                    "New appointment date and time cannot be null"
            );
        }

        if (!newAppointmentDateTime.isAfter(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "New appointment date and time must be in the future"
            );
        }

        validateBookableSlot(
                appointment.getDoctor().getId(),
                newAppointmentDateTime,
                id
        );

        // CANCELLED and REJECTED appointments do not block slots.
        List<AppointmentStatus> freeStatuses = List.of(
                AppointmentStatus.CANCELLED,
                AppointmentStatus.REJECTED
        );

        boolean doctorAlreadyBooked =
                appointmentRepository
                        .existsByDoctor_IdAndAppointmentDateTimeAndIdNotAndStatusNotIn(
                                appointment.getDoctor().getId(),
                                newAppointmentDateTime,
                                id,
                                freeStatuses
                        );

        if (doctorAlreadyBooked) {

            throw new DuplicateResourceException(
                    "Doctor is already booked for this time"
            );
        }

        LocalDateTime oldAppointmentDateTime =
                appointment.getAppointmentDateTime();

        appointment.setAppointmentDateTime(
                newAppointmentDateTime
        );

        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        Patient patient = savedAppointment.getPatient();
        Doctor doctor = savedAppointment.getDoctor();

        AppointmentEvent event = new AppointmentEvent(
                AppointmentEventType.APPOINTMENT_RESCHEDULED,
                savedAppointment.getId(),
                patient.getFirstName() + " " + patient.getLastName(),
                patient.getEmail(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                savedAppointment.getAppointmentDateTime(),
                oldAppointmentDateTime,
                newAppointmentDateTime
        );

        appointmentProducer.publishAppointmentEvent(event);

        return mapToResponse(
                savedAppointment,
                patient,
                doctor
        );
    }

    // =========================================================
    // PATIENT - REQUEST APPOINTMENT
    // =========================================================

    @Override
    @Transactional
    public AppointmentResponse requestAppointment(
            PatientAppointmentRequest request,
            String patientEmail) {

        // 1. Find patient using JWT email
        Patient patient =
                patientRepository.findByEmail(patientEmail)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Patient not found"
                                )
                        );

        // 2. Find doctor
        Doctor doctor =
                doctorRepository.findById(request.getDoctorId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Doctor not found"
                                )
                        );

        validateBookableSlot(
                request.getDoctorId(),
                request.getAppointmentDateTime(),
                null
        );

        // 3. Check whether slot is occupied
        //
        // PENDING, SCHEDULED, CONFIRMED and COMPLETED
        // block the slot.
        //
        // CANCELLED and REJECTED free the slot.
        boolean slotTaken =
                appointmentRepository.existsActiveAppointment(
                        request.getDoctorId(),
                        request.getAppointmentDateTime(),
                        List.of(
                                AppointmentStatus.CANCELLED,
                                AppointmentStatus.REJECTED
                        )
                );

        if (slotTaken) {

            throw new DuplicateResourceException(
                    "Doctor already has an appointment at this time"
            );
        }

        // 4. Create PENDING appointment request
        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .appointmentDateTime(
                        request.getAppointmentDateTime()
                )
                .bookedAt(LocalDateTime.now())
                .reason(request.getReason())
                .status(AppointmentStatus.PENDING)
                .notificationStatus(NotificationStatus.PENDING)
                .build();

        // 5. Save
        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        // 6. Return response
        return mapToResponse(
                savedAppointment,
                patient,
                doctor
        );
    }

    // =========================================================
    // GET PENDING APPOINTMENTS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getPendingAppointments() {

        List<Appointment> pendingAppointments =
                appointmentRepository.findByStatusWithPatientAndDoctor(
                        AppointmentStatus.PENDING
                );

        return pendingAppointments.stream()
                .map(pendingAppointment ->
                        mapToResponse(
                                pendingAppointment,
                                pendingAppointment.getPatient(),
                                pendingAppointment.getDoctor()
                        )
                )
                .toList();
    }

    // =========================================================
    // ACCEPT APPOINTMENT
    // =========================================================

    @Override
    @Transactional
    public AppointmentResponse acceptAppointment(Long id) {

        Appointment appointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Appointment not found"
                                )
                        );

        if (appointment.getStatus() != AppointmentStatus.PENDING) {

            throw new IllegalStateException(
                    "Only PENDING appointments can be accepted"
            );
        }

        validateBookableSlot(
                appointment.getDoctor().getId(),
                appointment.getAppointmentDateTime(),
                appointment.getId()
        );

        appointment.setStatus(
                AppointmentStatus.SCHEDULED
        );

        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        return mapToResponse(
                savedAppointment,
                savedAppointment.getPatient(),
                savedAppointment.getDoctor()
        );
    }

    // =========================================================
    // REJECT APPOINTMENT
    // =========================================================

    @Override
    @Transactional
    public AppointmentResponse rejectAppointment(Long id) {

        Appointment appointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Appointment not found"
                                )
                        );

        if (appointment.getStatus() != AppointmentStatus.PENDING) {

            throw new IllegalStateException(
                    "Only PENDING appointments can be rejected"
            );
        }

        appointment.setStatus(
                AppointmentStatus.REJECTED
        );

        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        return mapToResponse(
                savedAppointment,
                savedAppointment.getPatient(),
                savedAppointment.getDoctor()
        );
    }

    // =========================================================
    // GET PATIENT'S OWN APPOINTMENTS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointments(
            String patientEmail) {

        List<Appointment> appointments =
                appointmentRepository.findAllByPatientEmailWithDetails(
                        patientEmail
                );

        return appointments.stream()
                .map(appointment ->
                        mapToResponse(
                                appointment,
                                appointment.getPatient(),
                                appointment.getDoctor()
                        )
                )
                .toList();
    }
}