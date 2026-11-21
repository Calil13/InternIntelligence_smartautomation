package org.example.gmailapplication.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.gmailapplication.enums.EmailCategory;
import org.example.gmailapplication.enums.EmailFolder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "emails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String senderEmail;

    private String senderName;

    @Column(nullable = false)
    private String recipientEmail;

    private String recipientName;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailFolder folder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmailCategory category = EmailCategory.PRIMARY;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "email_labels", joinColumns = @JoinColumn(name = "email_id"))
    @Column(name = "label")
    @Builder.Default
    private Set<String> labels = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isStarred = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isRead == null) {
            isRead = false;
        }
        if (isStarred == null) {
            isStarred = false;
        }
        if (folder == null) {
            folder = EmailFolder.INBOX;
        }
        if (category == null) {
            category = EmailCategory.PRIMARY;
        }
        if (labels == null) {
            labels = new HashSet<>();
        }
    }
}
