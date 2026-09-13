package com.example.tool.user.response;

import com.example.tool.user.entity.Role;
import com.example.tool.user.entity.User;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class UserResponse {
    private Integer userId;
    private String name;
    private Role role;

    public UserResponse(User user) {
        this.userId = user.getId();
        this.name = user.getUsername();
        this.role = user.getRole();
    }

    public static List<UserResponse> form(List<User> users) {
        return users.stream().map(UserResponse::new).toList();
    }
}
