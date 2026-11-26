package org.example.gmailapplication.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = true)
    private String password;

    private String avatarUrl;

    @Column(unique = true)
    private String googleSub;

    private String oauthProvider;

    @Column(nullable = false)
    @Builder.Default
    private Boolean oauthConnected = false;

    private String gmailHistoryId;

    private LocalDateTime lastSyncedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (oauthConnected == null) {
            oauthConnected = false;
        }
    }
}
