package com.example.tool.user.service;

import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.task.DeviceScheduledTasks;
import com.example.tool.user.response.AuthResponse;
import com.example.tool.user.request.CreateUserRequest;
import com.example.tool.user.response.UserResponse;
import com.example.tool.user.entity.User;
import com.example.tool.user.reopsitory.UserRepository;
import com.example.tool.user.util.CustomUserDetails;
import com.example.tool.user.util.JwtUtils;
import com.example.tool.user.validator.UserValidator;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserValidator userValidator,
                       JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.userValidator = userValidator;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return UserResponse.form(userRepository.findAll());
    }

    @Transactional(readOnly = true)
    public User getUserById(Integer userId) {
        return userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户ID:" + userId + "不存在"));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserResponseById(Integer userId) {
        return new UserResponse(userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户ID:" + userId + "不存在")));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserResponseByName(String username) {
        return new UserResponse(userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("用户ID:" + username + "不存在")));
    }

    public void deleteById(Integer deleteUserId) {
        User deleteUser = userRepository.findById(deleteUserId).orElseThrow(() -> new RuntimeException("要删除的用户ID:" + deleteUserId + "不存在"));
        DeviceScheduledTasks.removeLastPingMap(deleteUser.getDevices());
        userRepository.delete(deleteUser);
    }

    public void deleteByUsername(String deleteUsername) {
        User deleteUser = userRepository.findByUsername(deleteUsername).orElseThrow(() -> new RuntimeException("要删除的用户" + deleteUsername + "不存在"));
        DeviceScheduledTasks.removeLastPingMap(deleteUser.getDevices());
        userRepository.delete(deleteUser);
    }

    public UserResponse createUser(@Valid CreateUserRequest createUserRequest) {
        User user = new User();
        user.createUser(createUserRequest);
        userValidator.validateBeforeSave(user);

        User saved = userRepository.save(user);

        return new UserResponse(saved);
    }

    public UserResponse updatePassword(Integer userId, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        return new UserResponse(user);
    }

    public AuthResponse updateUsername(Integer userId, String newUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (!user.getUsername().equals(newUsername)) {
            if (userRepository.existsByUsername(newUsername)) {
                throw new BusinessException("用户名已存在");
            }
            user.setUsername(newUsername);
            userRepository.save(user);
        }

        // 用更新后的 user 重新构造 UserDetails，再生成新 Token
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newToken = jwtUtils.generateToken(userDetails);

        return new AuthResponse(new UserResponse(user), newToken);
    }
}
