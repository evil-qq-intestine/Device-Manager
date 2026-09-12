package com.example.tool.user.service;

import com.example.tool.device.entity.Device;
import com.example.tool.user.entity.Role;
import com.example.tool.user.entity.User;
import com.example.tool.user.reopsitory.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findByid(Integer userId) {
        return userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户" + userId + "不存在"));
    }
}
