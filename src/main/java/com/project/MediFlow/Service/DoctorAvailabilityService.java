package com.project.MediFlow.Service;

import com.project.MediFlow.Dtos.DoctorAvailabilityRequest;
import com.project.MediFlow.Dtos.DoctorAvailabilityResponse;

import java.time.LocalDate;
import java.util.List;

public interface DoctorAvailabilityService {

    DoctorAvailabilityResponse createAvailability(
            String doctorEmail,
            DoctorAvailabilityRequest request
    );

    List<DoctorAvailabilityResponse> getMyAvailability(
            String doctorEmail,
            LocalDate from,
            LocalDate to
    );

    DoctorAvailabilityResponse updateAvailability(
            String doctorEmail,
            Long availabilityId,
            DoctorAvailabilityRequest request
    );

    void deleteAvailability(String doctorEmail, Long availabilityId);
}
