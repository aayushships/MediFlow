
package com.project.MediFlow.RabbitMQ.Consumer;

import com.project.MediFlow.Email.AppointmentEmailService;
import com.project.MediFlow.Enum.AppointmentEventType;
import com.project.MediFlow.RabbitMQ.Event.AppointmentEvent;
import com.project.MediFlow.Service.NotificationStateService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class AppointmentNotificationWorker1Test {

    private AppointmentEmailService emailService;
    private NotificationStateService stateService;
    private Channel channel;
    private Message message;
    private AppointmentNotificationWorker1 worker;
    private AppointmentEvent event;

    @BeforeEach
    void setUp() {
        emailService = mock(AppointmentEmailService.class);
        stateService = mock(NotificationStateService.class);
        channel = mock(Channel.class);
        message = mock(Message.class);

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(123L);
        when(message.getMessageProperties()).thenReturn(properties);

        worker = new AppointmentNotificationWorker1(
                emailService, stateService
        );

        event = new AppointmentEvent(
                AppointmentEventType.APPOINTMENT_CREATED,
                1L,
                "Test Patient",
                "patient@example.com",
                "Dr Test",
                LocalDateTime.now().plusDays(1),
                null,
                null
        );
    }

    @Test
    void sendsEmailAndAcknowledgesSuccessfulNotification()
            throws Exception {

        when(stateService.claimNotification(1L)).thenReturn(true);

        assertDoesNotThrow(() ->
                worker.consumeAppointmentEvent(event, channel, message));

        verify(emailService).sendAppointmentNotification(event);
        verify(stateService).markSent(1L);
        verify(channel).basicAck(123L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void skipsEmailAndAcknowledgesDuplicateNotification()
            throws Exception {

        when(stateService.claimNotification(1L)).thenReturn(false);

        worker.consumeAppointmentEvent(event, channel, message);

        verify(emailService, never()).sendAppointmentNotification(any());
        verify(stateService, never()).markSent(1L);
        verify(channel).basicAck(123L, false);
    }

    @Test
    void marksFailedAndRejectsMessageWhenEmailFails()
            throws Exception {

        when(stateService.claimNotification(1L)).thenReturn(true);

        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService).sendAppointmentNotification(event);

        worker.consumeAppointmentEvent(event, channel, message);

        verify(stateService).markFailed(1L);
        verify(channel).basicNack(123L, false, false);
        verify(channel, never()).basicAck(123L, false);
    }
}
