package com.photorestoration.service;

import com.photorestoration.entity.RestorationRecord;
import com.photorestoration.entity.User;
import com.photorestoration.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserService userService;
    private final NotificationWebSocketHandler webSocketHandler;
    private final JavaMailSender mailSender;
    private final Map<Long, Queue<RestorationRecord>> pendingEmails = new ConcurrentHashMap<>();

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Async
    public void notifyRestorationCompleted(Long userId, RestorationRecord record) {
        Optional<User> optionalUser = userService.getUserById(userId);
        if (optionalUser.isEmpty()) {
            return;
        }
        User user = optionalUser.get();

        if (!Boolean.FALSE.equals(user.getWsNotification())) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "RESTORATION_COMPLETE");
            payload.put("title", "照片修复完成");
            payload.put("content", "您的照片已经修复完成，可在当前页面或修复历史中查看。");
            payload.put("restorationId", record.getRestorationId());
            webSocketHandler.sendNotification(userId, payload);
        }

        if (Boolean.TRUE.equals(user.getEmailNotification())
                && user.getEmail() != null
                && !user.getEmail().trim().isEmpty()
                && isMailConfigured()) {
            pendingEmails.computeIfAbsent(userId, ignored -> new ConcurrentLinkedQueue<>()).add(record);
        }
    }

    @Scheduled(fixedDelayString = "${notification.email.flush-delay-ms:30000}")
    public void flushEmailNotifications() {
        if (!isMailConfigured() || pendingEmails.isEmpty()) {
            return;
        }
        for (Long userId : new ArrayList<>(pendingEmails.keySet())) {
            Queue<RestorationRecord> queue = pendingEmails.remove(userId);
            if (queue != null && !queue.isEmpty()) {
                sendCompletionEmail(userId, new ArrayList<>(queue));
            }
        }
    }

    private boolean isMailConfigured() {
        return fromEmail != null && !fromEmail.trim().isEmpty();
    }

    private void sendCompletionEmail(Long userId, List<RestorationRecord> records) {
        Optional<User> optionalUser = userService.getUserById(userId);
        if (optionalUser.isEmpty()) {
            return;
        }
        User user = optionalUser.get();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject("【老照片修复系统】" + records.size() + " 项修复任务已完成");
            helper.setText(buildEmailHtml(user, records), true);
            mailSender.send(message);
            log.info("Sent restoration email to {} with {} records", user.getEmail(), records.size());
        } catch (Exception e) {
            log.error("Restoration email failed for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private String buildEmailHtml(User user, List<RestorationRecord> records) {
        StringBuilder rows = new StringBuilder();
        for (RestorationRecord record : records) {
            rows.append("<tr><td style='padding:8px;border:1px solid #ddd'>")
                    .append(record.getRestorationId())
                    .append("</td><td style='padding:8px;border:1px solid #ddd'>")
                    .append(escapeHtml(record.getRestorationMode()))
                    .append("</td></tr>");
        }
        return "<div style='font-family:Arial,sans-serif;max-width:620px;margin:auto;color:#303133'>"
                + "<h2 style='color:#409eff'>照片修复完成</h2>"
                + "<p>您好，" + escapeHtml(user.getUserName()) + "：</p>"
                + "<p>您提交的 <strong>" + records.size() + "</strong> 项照片修复任务已经完成。</p>"
                + "<table style='width:100%;border-collapse:collapse'>"
                + "<tr><th style='padding:8px;border:1px solid #ddd;text-align:left'>任务 ID</th>"
                + "<th style='padding:8px;border:1px solid #ddd;text-align:left'>修复模式</th></tr>"
                + rows
                + "</table><p>请登录系统，在修复页面或历史记录中查看、保存和下载结果。</p>"
                + "<p style='color:#909399;font-size:12px'>此邮件由系统自动发送，请勿直接回复。</p></div>";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
