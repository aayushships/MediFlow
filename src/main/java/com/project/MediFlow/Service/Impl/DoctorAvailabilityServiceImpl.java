package com.project.MediFlow.Service.Impl;

import com.project.MediFlow.Dtos.DoctorAvailabilityRequest;
import com.project.MediFlow.Dtos.DoctorAvailabilityResponse;
import com.project.MediFlow.Exception.ResourceNotFoundException;
import com.project.MediFlow.Repository.DoctorAvailabilityRepository;
import com.project.MediFlow.Repository.AppointmentRepository;
import com.project.MediFlow.Enum.AppointmentStatus;
import com.project.MediFlow.Repository.DoctorRepository;
import com.project.MediFlow.Service.DoctorAvailabilityService;
import com.project.MediFlow.entities.Doctor;
import com.project.MediFlow.entities.DoctorAvailability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorAvailabilityServiceImpl implements DoctorAvailabilityService {

    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;

    @Override
    @Transactional
    public DoctorAvailabilityResponse createAvailability(
            String doctorEmail,
            DoctorAvailabilityRequest request) {

        Doctor doctor = getDoctorByEmail(doctorEmail);
        validateRequest(request);
        validateNoOverlap(doctor.getId(), request.date(), request.startTime(), request.endTime(), null);

        DoctorAvailability availability = DoctorAvailability.builder()
                .doctor(doctor)
                .date(request.date())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();

        return toResponse(availabilityRepository.save(availability));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> getMyAvailability(
            String doctorEmail,
            LocalDate from,
            LocalDate to) {

        Doctor doctor = getDoctorByEmail(doctorEmail);

        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("Both from and to dates are required together");
        }

        if (from != null && to.isBefore(from)) {
            throw new IllegalArgumentException("The to date cannot be before the from date");
        }

        List<DoctorAvailability> availability;
        if (from == null) {
            availability = availabilityRepository.findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(
                    doctor.getId(), LocalDate.now(), LocalDate.now().plusYears(1));
        } else {
            availability = availabilityRepository.findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(
                    doctor.getId(), from, to);
        }

        return availability.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public DoctorAvailabilityResponse updateAvailability(
            String doctorEmail,
            Long availabilityId,
            DoctorAvailabilityRequest request) {

        Doctor doctor = getDoctorByEmail(doctorEmail);
        DoctorAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor availability not found with id: " + availabilityId));

        if (!availability.getDoctor().getId().equals(doctor.getId())) {
            throw new IllegalArgumentException("You can only modify your own availability");
        }

        validateRequest(request);
        validateNoOverlap(doctor.getId(), request.date(), request.startTime(), request.endTime(), availabilityId);
        if (hasActiveAppointmentInWindow(doctor.getId(), availability.getDate(), availability.getStartTime(), availability.getEndTime())) {
            throw new IllegalStateException("Availability cannot be changed while it contains active appointments");
        }

        availability.setDate(request.date());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());

        return toResponse(availabilityRepository.save(availability));
    }

    @Override
    @Transactional
    public void deleteAvailability(String doctorEmail, Long availabilityId) {

        Doctor doctor = getDoctorByEmail(doctorEmail);
        DoctorAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor availability not found with id: " + availabilityId));

        if (!availability.getDoctor().getId().equals(doctor.getId())) {
            throw new IllegalArgumentException("You can only delete your own availability");
        }

        if (hasActiveAppointmentInWindow(doctor.getId(), availability.getDate(), availability.getStartTime(), availability.getEndTime())) {
            throw new IllegalStateException("Availability cannot be deleted while it contains active appointments");
        }

        availabilityRepository.delete(availability);
    }

    private Doctor getDoctorByEmail(String email) {
        return doctorRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor profile not found for email: " + email));
    }

    private boolean hasActiveAppointmentInWindow(
            Long doctorId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime) {

        // Appointments are 30 minutes long; include a preceding half-hour
        // to detect any existing appointment that overlaps the window start.
        LocalDateTime from = LocalDateTime.of(date, startTime).minusMinutes(30);
        LocalDateTime to = LocalDateTime.of(date, endTime);

        return appointmentRepository
                .findByDoctor_IdAndAppointmentDateTimeGreaterThanEqualAndAppointmentDateTimeLessThanAndStatusNotIn(
                        doctorId,
                        from,
                        to,
                        List.of(AppointmentStatus.CANCELLED, AppointmentStatus.REJECTED))
                .stream()
                .anyMatch(appointment -> {
                    LocalDateTime appointmentStart = appointment.getAppointmentDateTime();
                    LocalDateTime appointmentEnd = appointmentStart.plusMinutes(30);
                    LocalDateTime windowStart = LocalDateTime.of(date, startTime);
                    LocalDateTime windowEnd = LocalDateTime.of(date, endTime);
                    return appointmentStart.isBefore(windowEnd)
                            && appointmentEnd.isAfter(windowStart);
                });
    }

    private void validateRequest(DoctorAvailabilityRequest request) {
        if (request.date().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Availability date cannot be in the past");
        }

        LocalTime start = request.startTime();
        LocalTime end = request.endTime();

        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
    }

    private void validateNoOverlap(
            Long doctorId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            Long excludeId) {

        if (availabilityRepository.existsOverlappingAvailability(
                doctorId, date, startTime, endTime, excludeId)) {
            throw new IllegalArgumentException("Availability window overlaps an existing window");
        }
    }

    private DoctorAvailabilityResponse toResponse(DoctorAvailability availability) {
        return new DoctorAvailabilityResponse(
                availability.getId(),
                availability.getDoctor().getId(),
                availability.getDate(),
                availability.getStartTime(),
                availability.getEndTime()
        );
    }
}
