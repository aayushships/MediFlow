package com.project.MediFlow.Repository;

import com.project.MediFlow.Enum.AppointmentStatus;
import com.project.MediFlow.Enum.NotificationStatus;
import com.project.MediFlow.entities.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    boolean existsByIdAndPatient_Email(
            Long appointmentId,
            String email
    );

    boolean existsByIdAndDoctor_Email(
            Long appointmentId,
            String email
    );

    boolean existsByDoctor_IdAndAppointmentDateTimeAndStatusNotIn(
            Long doctorId,
            LocalDateTime appointmentDateTime,
            List<AppointmentStatus> freeStatuses
    );

    boolean existsByDoctor_IdAndAppointmentDateTimeAndIdNotAndStatusNotIn(
            Long doctorId,
            LocalDateTime appointmentDateTime,
            Long appointmentId,
            List<AppointmentStatus> freeStatuses
    );

    @Query("""
        SELECT a
        FROM Appointment a
        JOIN FETCH a.patient
        JOIN FETCH a.doctor
        """)
    List<Appointment> findAllWithPatientAndDoctor();

    @Query("""
        SELECT a
        FROM Appointment a
        JOIN FETCH a.patient
        JOIN FETCH a.doctor
        WHERE a.id = :id
        """)
    Optional<Appointment> findByIdWithPatientAndDoctor(
            @Param("id") Long id
    );

    @Query("""
        SELECT a
        FROM Appointment a
        JOIN FETCH a.patient
        JOIN FETCH a.doctor
        WHERE LOWER(a.doctor.email) = LOWER(:email)
        """)
    List<Appointment> findAllWithPatientAndDoctorByDoctorEmail(
            @Param("email") String email
    );

    List<Appointment> findByNotificationStatus(NotificationStatus notificationStatus);

    @Query("""
       SELECT a
       FROM Appointment a
       JOIN FETCH a.patient
       JOIN FETCH a.doctor
       WHERE a.notificationStatus = :status
       """)
    List<Appointment> findPendingNotificationsWithDetails(
            @Param("status") NotificationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT a
       FROM Appointment a
       WHERE a.id = :id
       """)
    Optional<Appointment> findByIdForNotificationUpdate(
            @Param("id") Long id
    );

    @Query("""
    SELECT COUNT(a) > 0
    FROM Appointment a
    WHERE a.doctor.id = :doctorId
      AND a.appointmentDateTime = :appointmentDateTime
      AND a.status NOT IN :freeStatuses
    """)
    boolean existsActiveAppointment(
            @Param("doctorId") Long doctorId,
            @Param("appointmentDateTime") LocalDateTime appointmentDateTime,
            @Param("freeStatuses") List<AppointmentStatus> freeStatuses
    );

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByDoctor_IdAndAppointmentDateTimeGreaterThanEqualAndAppointmentDateTimeLessThanAndStatusNotIn(
            Long doctorId,
            LocalDateTime from,
            LocalDateTime to,
            List<AppointmentStatus> excludedStatuses
    );

    @Query("""
       SELECT a
       FROM Appointment a
       JOIN FETCH a.patient
       JOIN FETCH a.doctor
       WHERE a.status = :status
       """)
    List<Appointment> findByStatusWithPatientAndDoctor(
            @Param("status") AppointmentStatus status
    );

    @Query("""
       SELECT a
       FROM Appointment a
       JOIN FETCH a.patient
       JOIN FETCH a.doctor
       WHERE LOWER(a.patient.email) = LOWER(:email)
       ORDER BY a.appointmentDateTime DESC
       """)
    List<Appointment> findAllByPatientEmailWithDetails(
            @Param("email") String email
    );
}
