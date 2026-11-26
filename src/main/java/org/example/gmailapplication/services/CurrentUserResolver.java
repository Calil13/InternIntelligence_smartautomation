package org.example.gmailapplication.services;

import lombok.RequiredArgsConstructor;
import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.exceptions.NotFoundException;
import org.example.gmailapplication.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public Optional<User> resolveOptional(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2User principal = oauthToken.getPrincipal();
            String sub = principal.getAttribute("sub");
            String email = principal.getAttribute("email");
            if (sub != null) {
                Optional<User> bySub = userRepository.findByGoogleSub(sub);
                if (bySub.isPresent()) {
                    return bySub;
                }
            }
            if (email != null) {
                return userRepository.findByEmail(email);
            }
        }

        return userRepository.findByEmail(authentication.getName());
    }

    public User resolveRequired(Authentication authentication) {
        return resolveOptional(authentication)
                .orElseThrow(() -> new NotFoundException("Authenticated user record not found"));
    }
}
