package com.example.tool.user.validator;

import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.user.entity.User;
import com.example.tool.user.reopsitory.UserRepository;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserValidator {
    @Autowired
    private UserRepository userRepository;

    public void validateBeforeSave(User user){
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ValidationException("Username is already in use");
        }
    }
}
