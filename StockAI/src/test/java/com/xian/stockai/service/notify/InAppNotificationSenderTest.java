package com.xian.stockai.service.notify;

import com.xian.stockai.dao.NotificationRepository;
import com.xian.stockai.po.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InAppNotificationSenderTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private InAppNotificationSender sender;

    @Test
    void sendPersistsNotification() {
        Notification n = new Notification();
        n.setRead(false);

        sender.send(n);

        verify(notificationRepository).save(n);
    }
}