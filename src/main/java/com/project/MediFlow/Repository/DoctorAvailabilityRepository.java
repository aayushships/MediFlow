package com.project.MediFlow.Repository;

import com.project.MediFlow.entities.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {

    List<DoctorAvailability> findByDoctorIdAndDateOrderByStartTime(
            Long doctorId,
            LocalDate date
    );

    List<DoctorAvailability> findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(
            Long doctorId,
            LocalDate from,
            LocalDate to
    );

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM DoctorAvailability a
            WHERE a.doctor.id = :doctorId
              AND a.date = :date
              AND (:excludeId IS NULL OR a.id <> :excludeId)
              AND a.startTime < :endTime
              AND a.endTime > :startTime
            """)
    boolean existsOverlappingAvailability(
            @Param("doctorId") Long doctorId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId
    );
}
