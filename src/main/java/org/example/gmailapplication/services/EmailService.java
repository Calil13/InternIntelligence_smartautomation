package org.example.gmailapplication.services;

import lombok.RequiredArgsConstructor;
import org.example.gmailapplication.dto.*;
import org.example.gmailapplication.entity.Email;
import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.enums.EmailFolder;
import org.example.gmailapplication.exceptions.NotFoundException;
import org.example.gmailapplication.mapper.EmailMapper;
import org.example.gmailapplication.repository.EmailRepository;
import org.example.gmailapplication.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final EmailMapper emailMapper;

    @Transactional
    public String sendEmail(EmailSendRequest request) {
        User sender = (request.getSenderId() != null)
                ? userRepository.findById(request.getSenderId())
                .orElseThrow(() -> new RuntimeException("The sending user was not found: ID=" + request.getSenderId()))
                : userRepository.findByEmail(request.getSenderEmail())
                .orElseThrow(() -> new RuntimeException("The sending user was not found: " + request.getSenderEmail()));

        Optional<User> recipientOpt = userRepository.findByEmail(request.getRecipientEmail());

        String subject = (request.getSubject() != null && !request.getSubject().trim().isEmpty())
                ? request.getSubject()
                : "(No Subject)";

        String recipientName = recipientOpt.map(User::getFullName).orElse(null);

        Email sentEmail = Email.builder()
                .user(sender)
                .senderEmail(sender.getEmail())
                .senderName(sender.getFullName())
                .recipientEmail(request.getRecipientEmail())
                .recipientName(recipientName)
                .subject(subject)
                .body(request.getBody())
                .folder(EmailFolder.SENT)
                .isRead(true)
                .isStarred(false)
                .createdAt(LocalDateTime.now())
                .build();

        emailRepository.save(sentEmail);

        if (recipientOpt.isPresent()) {
            User recipient = recipientOpt.get();
            Email inboxEmail = Email.builder()
                    .user(recipient)
                    .senderEmail(sender.getEmail())
                    .senderName(sender.getFullName())
                    .recipientEmail(recipient.getEmail())
                    .recipientName(recipient.getFullName())
                    .subject(subject)
                    .body(request.getBody())
                    .folder(EmailFolder.INBOX)
                    .isRead(false)
                    .isStarred(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            emailRepository.save(inboxEmail);
        }

        return "Email sent. Sender Email: " + sender.getEmail();
    }

    @Transactional
    public String saveDraft(EmailDraftRequest request) {
        User user = (request.getUserId() != null)
                ? userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: ID=" + request.getUserId()))
                : userRepository.findByEmail(request.getUserEmail())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserEmail()));

        Email draft = Email.builder()
                .user(user)
                .senderEmail(user.getEmail())
                .senderName(user.getFullName())
                .recipientEmail(request.getRecipientEmail())
                .recipientName(null)
                .subject(request.getSubject() != null ? request.getSubject() : "(No Subject)")
                .body(request.getBody())
                .folder(EmailFolder.DRAFT)
                .isRead(false)
                .isStarred(false)
                .createdAt(LocalDateTime.now())
                .build();

        emailRepository.save(draft);
        return "Email of the person making the draft: " + request.getUserEmail();
    }

    public EmailResponseDto getEmailMessage(Long emailId) {
        var email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found!"));

        return emailMapper.mapToDto(email);
    }

    public Page<EmailsListResponseDto> getStarredEmails(Long userId, Pageable pageable) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: ID=" + userId));

        Page<Email> starredEmails = emailRepository.findByUserIdAndIsStarredTrue(userId, pageable);
        return starredEmails.map(emailMapper::mapToEmailsListDto);
    }

    public Page<EmailsListResponseDto> getEmailsByFolder(Long userId, EmailFolder folder, Pageable pageable) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: ID=" + userId));

        Page<Email> folderEmails = emailRepository.findByUserIdAndFolder(userId, folder, pageable);
        return folderEmails.map(emailMapper::mapToEmailsListDto);
    }

    @Transactional
    public boolean toggleStar(Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new NotFoundException("Email not found: ID=" + emailId));

        boolean newStarred = email.getIsStarred() == null || !email.getIsStarred();
        email.setIsStarred(newStarred);
        emailRepository.save(email);
        return newStarred;
    }

    public SidebarSummaryDto getSidebarSummary(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: ID=" + userId));

        return SidebarSummaryDto.builder()
                .unreadInboxCount(emailRepository.countByUserIdAndFolderAndIsReadFalse(userId, EmailFolder.INBOX))
                .starredCount(emailRepository.countByUserIdAndIsStarredTrue(userId))
                .sentCount(emailRepository.countByUserIdAndFolder(userId, EmailFolder.SENT))
                .draftCount(emailRepository.countByUserIdAndFolder(userId, EmailFolder.DRAFT))
                .trashCount(emailRepository.countByUserIdAndFolder(userId, EmailFolder.TRASH))
                .build();
    }
}
