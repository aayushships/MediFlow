package com.project.MediFlow.Service;

import com.project.MediFlow.Dtos.AppointmentRequest;
import com.project.MediFlow.Dtos.AppointmentResponse;
import com.project.MediFlow.Dtos.AvailableSlotResponse;
import com.project.MediFlow.dto.PatientAppointmentRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentService {

    AppointmentResponse createAppointment(AppointmentRequest request);

    List<AppointmentResponse> getAllAppointments();

    List<AvailableSlotResponse> getAvailableSlots(Long doctorId, LocalDate date);

    List<AppointmentResponse> getAppointmentsForDoctor(String email);

    AppointmentResponse getAppointmentById(Long id);

    AppointmentResponse cancelAppointment(Long id);

    AppointmentResponse confirmAppointment(Long id);

    AppointmentResponse completeAppointment(Long id);

    AppointmentResponse rescheduleAppointment(
            Long id,
            LocalDateTime newAppointmentDateTime
    );

    AppointmentResponse requestAppointment(
            PatientAppointmentRequest request,
            String patientEmail
    );

    List<AppointmentResponse> getPendingAppointments();

    AppointmentResponse acceptAppointment(Long id);

    AppointmentResponse rejectAppointment(Long id);

    List<AppointmentResponse> getMyAppointments(String patientEmail);
}