package com.xian.stockai.service.notify;

import com.xian.stockai.po.Notification;

public interface NotificationSender {
    void send(Notification notification);
}