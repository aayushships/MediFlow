
package com.project.MediFlow.Email;

import com.project.MediFlow.Enum.AppointmentEventType;
import com.project.MediFlow.RabbitMQ.Event.AppointmentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppointmentEmailServiceTest {

    private JavaMailSender mailSender;
    private AppointmentEmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailService = new AppointmentEmailService(mailSender);
    }

    @Test
    void sendsEmailThroughMailSender() {
        AppointmentEvent event = new AppointmentEvent(
                AppointmentEventType.APPOINTMENT_CREATED,
                1L,
                "Test Patient",
                "patient@example.com",
                "Dr Test",
                LocalDateTime.now().plusDays(1),
                null,
                null
        );

        emailService.sendAppointmentNotification(event);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void propagatesEmailFailureWhenMailSenderFails() {
        AppointmentEvent event = new AppointmentEvent(
                AppointmentEventType.APPOINTMENT_CREATED,
                1L,
                "Test Patient",
                "patient@example.com",
                "Dr Test",
                LocalDateTime.now().plusDays(1),
                null,
                null
        );

        doThrow(new RuntimeException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(
                RuntimeException.class,
                () -> emailService.sendAppointmentNotification(event)
        );
    }
}
