package com.example.tool.user.controller;

import com.example.tool.user.request.UpdateUsernameRequest;
import com.example.tool.user.response.AuthResponse;
import com.example.tool.user.service.UserService;
import com.example.tool.user.util.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/username")
public class UserController {
    @Autowired
    private UserService userService;

    @PutMapping("")
    public AuthResponse updateUsername(@Valid @RequestBody UpdateUsernameRequest req,
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        return userService.updateUsername(userDetails.getUserId(), req.getUsername());
    }
}