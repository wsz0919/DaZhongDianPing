package com.hmdp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.util.Date;

/**
 * Spring Boot邮件发送工具类
 * 支持发送文本邮件、HTML邮件和带附件的邮件
 */
@Component
public class MailConfig {

    @Autowired
    private JavaMailSender javaMailSender;

    /**
     * 发件人邮箱地址，从配置文件读取
     */
    @Value("${spring.mail.username}")
    private String from;

    /**
     * 发送简单文本邮件
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @throws MessagingException 邮件发送异常
     */
    public void sendSimpleMail(String to, String subject, String content) throws MessagingException {
        sendMail(to, null, null, subject, content, false, null);
    }

    /**
     * 发送HTML格式邮件
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param htmlContent HTML内容
     * @throws MessagingException 邮件发送异常
     */
    public void sendHtmlMail(String to, String subject, String htmlContent) throws MessagingException {
        sendMail(to, null, null, subject, htmlContent, true, null);
    }

    /**
     * 发送带附件的邮件
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @param attachments 附件文件数组
     * @throws MessagingException 邮件发送异常
     */
    public void sendAttachmentsMail(String to, String subject, String content, File[] attachments) throws MessagingException {
        sendMail(to, null, null, subject, content, false, attachments);
    }

    /**
     * 发送带抄送的邮件
     * @param to 收件人邮箱
     * @param cc 抄送人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @throws MessagingException 邮件发送异常
     */
    public void sendCcMail(String to, String[] cc, String subject, String content) throws MessagingException {
        sendMail(to, cc, null, subject, content, false, null);
    }

    /**
     * 通用邮件发送方法
     * @param to 收件人邮箱
     * @param cc 抄送人邮箱数组
     * @param bcc 密送人邮箱数组
     * @param subject 邮件主题
     * @param content 邮件内容
     * @param isHtml 是否为HTML格式
     * @param attachments 附件文件数组
     * @throws MessagingException 邮件发送异常
     */
    private void sendMail(String to, String[] cc, String[] bcc, String subject,
                          String content, boolean isHtml, File[] attachments) throws MessagingException {
        // 创建mime类型邮件
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        // 创建邮件帮助类，第二个参数true表示支持多部分内容（附件等）
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        // 设置发件人
        helper.setFrom(from);
        // 设置收件人
        helper.setTo(to);
        // 设置抄送
        if (cc != null && cc.length > 0) {
            helper.setCc(cc);
        }
        // 设置密送
        if (bcc != null && bcc.length > 0) {
            helper.setBcc(bcc);
        }
        // 设置邮件主题
        helper.setSubject(subject);
        // 设置邮件内容，第二个参数表示是否为HTML格式
        helper.setText(content, isHtml);
        // 设置发送时间
        helper.setSentDate(new Date());

        // 添加附件
        if (attachments != null && attachments.length > 0) {
            for (File file : attachments) {
                if (file.exists() && file.isFile()) {
                    FileSystemResource resource = new FileSystemResource(file);
                    String fileName = file.getName();
                    helper.addAttachment(fileName, resource);
                }
            }
        }

        // 发送邮件
        javaMailSender.send(mimeMessage);
    }
}
    