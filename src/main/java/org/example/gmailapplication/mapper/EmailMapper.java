package org.example.gmailapplication.mapper;

import org.example.gmailapplication.dto.EmailResponseDto;
import org.example.gmailapplication.dto.EmailsListResponseDto;
import org.example.gmailapplication.entity.Email;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EmailMapper {

    default EmailResponseDto mapToDto(Email email) {
        return EmailResponseDto.builder()
                .id(email.getId())
                .userId(email.getUser() != null ? email.getUser().getId() : null)
                .senderEmail(email.getSenderEmail())
                .senderName(email.getSenderName())
                .recipientEmail(email.getRecipientEmail())
                .recipientName(email.getRecipientName())
                .subject(email.getSubject())
                .body(email.getBody())
                .folder(email.getFolder())
                .isRead(email.getIsRead())
                .isStarred(email.getIsStarred())
                .createdAt(email.getCreatedAt())
                .build();
    }

    default EmailsListResponseDto mapToEmailsListDto(Email email) {
        return EmailsListResponseDto.builder()
                .id(email.getId())
                .senderName(email.getSenderName())
                .senderEmail(email.getSenderEmail())
                .subject(email.getSubject())
                .body(email.getBody())
                .isStarred(email.getIsStarred())
                .isRead(email.getIsRead())
                .createdAt(email.getCreatedAt())
                .build();
    }
}
