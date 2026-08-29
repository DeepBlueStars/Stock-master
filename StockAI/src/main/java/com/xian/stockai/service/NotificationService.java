package com.xian.stockai.service;

import com.xian.stockai.dao.NotificationRepository;
import com.xian.stockai.po.Notification;
import com.xian.stockai.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Result<List<Notification>> list(String userId) {
        return Result.success(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    public Result<Void> markRead(String userId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.fail("请选择要标记的提醒");
        }
        List<Notification> notifications = notificationRepository.findByUserIdAndIdIn(userId, ids);
        for (Notification notification : notifications) {
            notification.setRead(true);
        }
        notificationRepository.saveAll(notifications);
        return Result.success();
    }
}