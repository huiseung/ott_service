package com.domain.backend.user.application;

import com.domain.backend.user.domain.AppUser;
import com.domain.backend.user.infrastructure.persistence.AppUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalTestUserInitializer implements ApplicationRunner {

    private static final String LOGIN_ID = "ott_test_user";

    private final UserSecurityProperties properties;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalTestUserInitializer(UserSecurityProperties properties, AppUserRepository userRepository,
                                    PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isLocalTestUserEnabled() || !StringUtils.hasText(properties.getLocalTestUserPassword())) {
            return;
        }
        if (!userRepository.existsByLoginId(LOGIN_ID)) {
            userRepository.save(new AppUser(
                    LOGIN_ID,
                    "OTT 테스트 사용자",
                    passwordEncoder.encode(properties.getLocalTestUserPassword())
            ));
        }
    }
}
