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

import java.io.IOException;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

class AppointmentNotificationWorker1Test {
    private AppointmentEmailService emailService;
    private NotificationStateService stateService;
    private Channel channel;
    private AppointmentNotificationWorker1 worker;
    private Message message;
    private AppointmentEvent event;

    @BeforeEach
    void setUp() {
        emailService = mock(AppointmentEmailService.class);
        stateService = mock(NotificationStateService.class);
        channel = mock(Channel.class);
        worker = new AppointmentNotificationWorker1(emailService, stateService);

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(42L);
        message = new Message(new byte[0], properties);
        event = new AppointmentEvent(AppointmentEventType.APPOINTMENT_CREATED,
                15L, "Test Patient", "patient@example.com", "Dr Test",
                LocalDateTime.now().plusDays(2), null, null);
    }

    @Test
    void sendsEmailMarksSentAndAcknowledgesMessage() throws IOException {
        when(stateService.claimNotification(15L)).thenReturn(true);

        worker.consumeAppointmentEvent(event, channel, message);

        verify(emailService).sendAppointmentNotification(event);
        verify(stateService).markSent(15L);
        verify(channel).basicAck(42L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void acknowledgesAlreadyClaimedNotificationWithoutSendingDuplicateEmail() throws IOException {
        when(stateService.claimNotification(15L)).thenReturn(false);

        worker.consumeAppointmentEvent(event, channel, message);

        verifyNoInteractions(emailService);
        verify(channel).basicAck(42L, false);
        verify(stateService, never()).markSent(15L);
    }

    @Test
    void marksFailedAndRejectsMessageWhenEmailFails() throws IOException {
        when(stateService.claimNotification(15L)).thenReturn(true);
        doThrow(new RuntimeException("mail unavailable"))
                .when(emailService).sendAppointmentNotification(event);

        worker.consumeAppointmentEvent(event, channel, message);

        verify(stateService).markFailed(15L);
        verify(channel).basicNack(42L, false, false);
        verify(channel, never()).basicAck(42L, false);
    }

    @Test
    void rejectsMessageWhenClaimFails() throws IOException {
        when(stateService.claimNotification(15L))
                .thenThrow(new RuntimeException("database unavailable"));

        worker.consumeAppointmentEvent(event, channel, message);

        verify(channel).basicNack(42L, false, false);
        verify(stateService).markFailed(15L);
    }
}