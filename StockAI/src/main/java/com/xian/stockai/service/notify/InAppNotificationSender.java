package com.xian.stockai.service.notify;

import com.xian.stockai.dao.NotificationRepository;
import com.xian.stockai.po.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InAppNotificationSender implements NotificationSender {
    private final NotificationRepository notificationRepository;

    public InAppNotificationSender(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void send(Notification notification) {
        notificationRepository.save(notification);
        log.debug("生成站内提醒: userId={}, type={}, symbol={}",
                notification.getUserId(), notification.getType(), notification.getSymbol());
    }
}