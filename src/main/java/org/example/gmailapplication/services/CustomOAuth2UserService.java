package org.example.gmailapplication.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.gmailapplication.entity.User;
import org.example.gmailapplication.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        upsertGoogleUser(oauth2User.getAttributes(), userRequest.getClientRegistration().getRegistrationId());
        return oauth2User;
    }

    public User upsertGoogleUser(Map<String, Object> attributes, String registrationId) {
        String sub = asString(attributes.get("sub"));
        String email = asString(attributes.get("email"));
        String name = asString(attributes.get("name"));
        String picture = asString(attributes.get("picture"));

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("OAuth2 provider did not return an email address");
        }

        User user = (sub != null ? userRepository.findByGoogleSub(sub) : userRepository.findByEmail(email))
                .or(() -> userRepository.findByEmail(email))
                .orElseGet(() -> User.builder()
                        .email(email)
                        .fullName(name != null ? name : email)
                        .build());

        user.setEmail(email);
        if (name != null && !name.isBlank()) {
            user.setFullName(name);
        }
        if (picture != null && !picture.isBlank()) {
            user.setAvatarUrl(picture);
        }
        if (sub != null && !sub.isBlank()) {
            user.setGoogleSub(sub);
        }
        user.setOauthProvider(registrationId != null ? registrationId.toUpperCase() : "GOOGLE");
        user.setOauthConnected(true);

        User saved = userRepository.save(user);
        log.info("Provisioned/updated OAuth2 user {} (id={})", saved.getEmail(), saved.getId());
        return saved;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
