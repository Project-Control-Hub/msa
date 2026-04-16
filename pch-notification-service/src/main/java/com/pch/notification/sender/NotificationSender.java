package com.pch.notification.sender;

import com.pch.notification.domain.Channel;
import com.pch.notification.service.NotificationMessage;

public interface NotificationSender {
    Channel channel();
    void send(NotificationMessage message);
}
