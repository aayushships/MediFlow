package com.project.MediFlow.Controller;

import com.project.MediFlow.Dtos.DoctorAvailabilityRequest;
import com.project.MediFlow.Dtos.DoctorAvailabilityResponse;
import com.project.MediFlow.Service.DoctorAvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/doctors/me/availability")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorAvailabilityController {

    private final DoctorAvailabilityService availabilityService;

    @PostMapping
    public ResponseEntity<DoctorAvailabilityResponse> createAvailability(
            Principal principal,
            @Valid @RequestBody DoctorAvailabilityRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(availabilityService.createAvailability(principal.getName(), request));
    }

    @GetMapping
    public ResponseEntity<List<DoctorAvailabilityResponse>> getMyAvailability(
            Principal principal,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        return ResponseEntity.ok(
                availabilityService.getMyAvailability(principal.getName(), from, to));
    }

    @PatchMapping("/{availabilityId}")
    public ResponseEntity<DoctorAvailabilityResponse> updateAvailability(
            Principal principal,
            @PathVariable Long availabilityId,
            @Valid @RequestBody DoctorAvailabilityRequest request) {

        return ResponseEntity.ok(
                availabilityService.updateAvailability(principal.getName(), availabilityId, request));
    }

    @DeleteMapping("/{availabilityId}")
    public ResponseEntity<Void> deleteAvailability(
            Principal principal,
            @PathVariable Long availabilityId) {

        availabilityService.deleteAvailability(principal.getName(), availabilityId);
        return ResponseEntity.noContent().build();
    }
}
