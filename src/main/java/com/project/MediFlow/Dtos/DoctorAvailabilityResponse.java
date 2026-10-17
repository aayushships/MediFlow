package com.project.MediFlow.Dtos;

import java.time.LocalDate;
import java.time.LocalTime;

public record DoctorAvailabilityResponse(
        Long id,
        Long doctorId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
}
