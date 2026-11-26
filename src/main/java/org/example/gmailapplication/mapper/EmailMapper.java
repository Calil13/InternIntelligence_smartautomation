package org.example.gmailapplication.mapper;

import org.example.gmailapplication.dto.EmailResponseDto;
import org.example.gmailapplication.dto.EmailsListResponseDto;
import org.example.gmailapplication.entity.Email;
import org.mapstruct.Mapper;

import java.util.HashSet;

@Mapper(componentModel = "spring")
public interface EmailMapper {

    default EmailResponseDto mapToDto(Email email) {
        if (email == null) {
            return null;
        }
        return EmailResponseDto.builder()
                .id(email.getId())
                .userId(email.getUser() != null ? email.getUser().getId() : null)
                .gmailMessageId(email.getGmailMessageId())
                .gmailThreadId(email.getGmailThreadId())
                .senderEmail(email.getSenderEmail())
                .senderName(email.getSenderName())
                .recipientEmail(email.getRecipientEmail())
                .recipientName(email.getRecipientName())
                .subject(email.getSubject())
                .body(email.getBody())
                .folder(email.getFolder())
                .category(email.getCategory())
                .labels(email.getLabels() != null ? new HashSet<>(email.getLabels()) : new HashSet<>())
                .summary(email.getSummary())
                .isRead(email.getIsRead())
                .isStarred(email.getIsStarred())
                .createdAt(email.getCreatedAt())
                .build();
    }

    default EmailsListResponseDto mapToEmailsListDto(Email email) {
        if (email == null) {
            return null;
        }
        return EmailsListResponseDto.builder()
                .id(email.getId())
                .senderName(email.getSenderName())
                .senderEmail(email.getSenderEmail())
                .subject(email.getSubject())
                .body(email.getBody())
                .category(email.getCategory())
                .labels(email.getLabels() != null ? new HashSet<>(email.getLabels()) : new HashSet<>())
                .summary(email.getSummary())
                .isStarred(email.getIsStarred())
                .isRead(email.getIsRead())
                .createdAt(email.getCreatedAt())
                .build();
    }
}
