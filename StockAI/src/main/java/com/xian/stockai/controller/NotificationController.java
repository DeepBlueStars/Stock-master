package com.xian.stockai.controller;

import com.xian.stockai.po.Notification;
import com.xian.stockai.service.NotificationService;
import com.xian.stockai.vo.NotificationReadRequest;
import com.xian.stockai.vo.Result;
import com.xian.stockai.vo.UserRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/notification")
@Slf4j
public class NotificationController {
    private final NotificationService notificationService;

    @PostMapping("/list")
    public Result<List<Notification>> list(@RequestBody UserRequest req) {
        return notificationService.list(req.getUserId());
    }

    @PostMapping("/read")
    public Result<Void> read(@RequestBody NotificationReadRequest req) {
        return notificationService.markRead(req.getUserId(), req.getIds());
    }
}