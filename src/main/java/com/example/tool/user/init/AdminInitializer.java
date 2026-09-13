package com.example.tool.user.init;

import com.example.tool.user.entity.Role;
import com.example.tool.user.entity.User;
import com.example.tool.user.reopsitory.UserRepository;
import com.example.tool.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@Slf4j
public class AdminInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args){

        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Admin already exists, skip initialization");
            return;
        }

        String rawPassword = generateRandomPassword();

        User admin = new User();
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(admin);

        log.warn("============================================");
        log.warn("Admin has been created");
        log.warn("Username: admin");
        log.warn("Password: {}", rawPassword);
        log.warn("Please log in and change your password immediately");
        log.warn("============================================");
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!?@#$%&*<>{}[]()\"\\/'";
        SecureRandom random = new SecureRandom();
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            stringBuilder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return stringBuilder.toString();
    }
}
