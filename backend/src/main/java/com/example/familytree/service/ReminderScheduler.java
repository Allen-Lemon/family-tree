package com.example.familytree.service;

import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {
 private final JdbcClient jdbc; public ReminderScheduler(JdbcClient j){jdbc=j;}
 @Scheduled(fixedDelayString="${app.reminder-poll-ms:60000}") @Transactional
 public void sendDue(){jdbc.sql("insert into notifications(user_id,tree_id,type,message) select created_by,tree_id,'EVENT_REMINDER',concat('事件提醒：',title) from family_events where remind_at is not null and remind_at<=current_timestamp(6)").update();jdbc.sql("update family_events set remind_at=null where remind_at is not null and remind_at<=current_timestamp(6)").update();}
}
