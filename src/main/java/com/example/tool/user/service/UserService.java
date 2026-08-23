package com.example.tool.user.service;

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

//    public List<User> findAll(User user){
//        log.info("查询用户列表");
//        List<User> users = List.of();
//        if (user == null) {
//            log.warn("未授权的访问");
//        }
//        else if (user.getRole() == Role.SUPER_ADMIN) {
//            users = userRepository.findAll();
//            log.info("管理员 {} 查询了用户列表，共 {} 条", user.getUsername(), users.size());
//        } else {
//            log.warn("未授权的访问{}", user.getUserId());
//        }
//        return users;
//    }

}
