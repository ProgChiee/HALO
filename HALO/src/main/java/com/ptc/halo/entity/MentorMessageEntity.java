package com.ptc.halo.entity;

import com.ptc.halo.enums.MessageSender;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mentor_messages", uniqueConstraints = @UniqueConstraint(name = "uk_mentor_request_sender", columnNames = {"session_id", "request_id", "sender"}))
public class MentorMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", length = 36)
    private String requestId;
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private MentorSessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageSender sender;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public MentorMessageEntity() {
    }

    public Long getId() {
        return id;
    }

    public MentorSessionEntity getSession() {
        return session;
    }

    public void setSession(MentorSessionEntity session) {
        this.session = session;
    }

    public MessageSender getSender() {
        return sender;
    }

    public void setSender(MessageSender sender) {
        this.sender = sender;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
