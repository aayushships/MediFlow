package com.project.MediFlow.Dtos;

import java.time.LocalDateTime;

public record AvailableSlotResponse(
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
