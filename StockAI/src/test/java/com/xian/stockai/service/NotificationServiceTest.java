package com.xian.stockai.service;

import com.xian.stockai.dao.NotificationRepository;
import com.xian.stockai.po.Notification;
import com.xian.stockai.vo.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void listReturnsNotifications() {
        List<Notification> list = List.of(new Notification());
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("u1")).thenReturn(list);

        Result<List<Notification>> result = notificationService.list("u1");

        assertTrue(result.checkSuccess());
        assertEquals(list, result.getData());
    }

    @Test
    void markReadUpdatesReadFlag() {
        Notification n = new Notification();
        n.setId("n1");
        n.setRead(false);
        when(notificationRepository.findByUserIdAndIdIn("u1", List.of("n1"))).thenReturn(List.of(n));

        Result<Void> result = notificationService.markRead("u1", List.of("n1"));

        assertTrue(result.checkSuccess());
        assertTrue(n.getRead());
        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void markReadRejectsEmptyIds() {
        Result<Void> result = notificationService.markRead("u1", List.of());

        assertFalse(result.checkSuccess());
    }
}