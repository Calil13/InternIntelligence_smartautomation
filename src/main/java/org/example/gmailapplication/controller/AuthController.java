package org.example.gmailapplication.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.gmailapplication.dto.AuthUserDto;
import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.exceptions.BadRequestException;
import org.example.gmailapplication.services.CurrentUserResolver;
import org.example.gmailapplication.services.DemoDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication & OAuth 2.0", description = "OAuth 2.0 session inspection and portfolio demo login endpoints")
public class AuthController {

    private final CurrentUserResolver currentUserResolver;
    private final DemoDataService demoDataService;

    @Value("${app.demo-mode.enabled:true}")
    private boolean demoModeEnabled;

    @Operation(summary = "Get current authenticated user info (OAuth 2.0 or Demo)")
    @GetMapping("/me")
    public AuthUserDto getCurrentUser(Authentication authentication) {
        Optional<User> userOpt = currentUserResolver.resolveOptional(authentication);
        if (userOpt.isEmpty()) {
            return AuthUserDto.builder()
                    .authenticated(false)
                    .demoModeEnabled(demoModeEnabled)
                    .build();
        }
        return toDto(userOpt.get());
    }

    @Operation(summary = "Launch portfolio demo workspace with seeded sample emails")
    @PostMapping("/demo-login")
    public AuthUserDto demoLogin(HttpServletRequest request, HttpServletResponse response) {
        if (!demoModeEnabled) {
            throw new BadRequestException("Demo mode is disabled. Please authenticate with Google OAuth 2.0.");
        }

        User demoUser = demoDataService.getOrCreateDemoUser();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                demoUser.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
        repo.saveContext(context, request, response);

        return toDto(demoUser);
    }

    @Operation(summary = "Log out current user and clear session")
    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private AuthUserDto toDto(User user) {
        return AuthUserDto.builder()
                .authenticated(true)
                .demoModeEnabled(demoModeEnabled)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .oauthConnected(Boolean.TRUE.equals(user.getOauthConnected()))
                .gmailHistoryId(user.getGmailHistoryId())
                .lastSyncedAt(user.getLastSyncedAt())
                .build();
    }
}
