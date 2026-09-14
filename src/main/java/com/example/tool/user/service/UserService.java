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
    private final com.example.tool.scripttask.service.ScriptTaskService scriptTaskService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserValidator userValidator,
                       JwtUtils jwtUtils,
                       com.example.tool.scripttask.service.ScriptTaskService scriptTaskService) {
        this.userRepository = userRepository;
        this.userValidator = userValidator;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.scriptTaskService = scriptTaskService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return UserResponse.form(userRepository.findAll());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserResponseById(Integer userId) {
        return new UserResponse(userRepository.findById(userId).orElseThrow(() -> new BusinessException("User not found, id: " + userId)));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserResponseByName(String username) {
        return new UserResponse(userRepository.findByUsername(username).orElseThrow(() -> new BusinessException("User not found, username: " + username)));
    }

    public void deleteById(Integer deleteUserId) {
        User deleteUser = userRepository.findById(deleteUserId).orElseThrow(() -> new BusinessException("User to delete not found, id: " + deleteUserId));
        DeviceScheduledTasks.removeLastPingMap(deleteUser.getDevices());
        detachDevices(deleteUser);
        userRepository.delete(deleteUser);
    }

    public void deleteByUsername(String deleteUsername) {
        User deleteUser = userRepository.findByUsername(deleteUsername).orElseThrow(() -> new BusinessException("User to delete not found, username: " + deleteUsername));
        DeviceScheduledTasks.removeLastPingMap(deleteUser.getDevices());
        detachDevices(deleteUser);
        userRepository.delete(deleteUser);
    }

    private void detachDevices(User user) {
        for (var device : user.getDevices()) {
            scriptTaskService.detachDevice(device.getDeviceId());
        }
    }

    public UserResponse createUser(@Valid CreateUserRequest createUserRequest) {
        User user = new User();
        user.createUser(createUserRequest);
        user.setPassword(passwordEncoder.encode(createUserRequest.getPassword()));
        userValidator.validateBeforeSave(user);

        User saved = userRepository.save(user);

        return new UserResponse(saved);
    }

    public UserResponse updatePassword(Integer operatorId, Integer targetUserId, String operatorPassword, String newPassword) {
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new BusinessException("User not found, id: " + operatorId));
        if (!passwordEncoder.matches(operatorPassword, operator.getPassword())) {
            throw new BusinessException("管理员密码不正确");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException("User not found, id: " + targetUserId));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userRepository.save(user);

        return new UserResponse(user);
    }

    public AuthResponse updateUsername(Integer userId, String newUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found, id: " + userId));

        if (!user.getUsername().equals(newUsername)) {
            if (userRepository.existsByUsername(newUsername)) {
                throw new BusinessException("Username already exists: " + newUsername);
            }
            user.setUsername(newUsername);
            userRepository.save(user);
        }

        // 用更新后的 user 重新构造 UserDetails，再生成新 Token
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newToken = jwtUtils.generateToken(userDetails);

        return new AuthResponse(new UserResponse(user), newToken);
    }

    public AuthResponse changePassword(Integer userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found, id: " + userId));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("当前密码不正确");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userRepository.save(user);

        // tokenVersion 已变化，签发新 Token 保持当前会话有效
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newToken = jwtUtils.generateToken(userDetails);

        return new AuthResponse(new UserResponse(user), newToken);
    }
}
