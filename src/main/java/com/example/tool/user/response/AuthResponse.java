package com.example.tool.user.response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AuthResponse {
    private UserResponse user;
    private String token;

    public AuthResponse(UserResponse user, String token) {
        this.user = user;
        this.token = token;
    }
}