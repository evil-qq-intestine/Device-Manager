package com.example.tool.user.controller;

import com.example.tool.user.dto.LoginRequest;
import com.example.tool.user.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping
    public Map<String, String> login(@RequestBody LoginRequest loginRequst) {
        //认证用户
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequst.getUsername(), loginRequst.getPassword())
        );

        //认证成功生成JWT
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();//内部调用了 createToken，把用户名放进 subject，调用了 .signWith(getSigningKey())
        String jwt = jwtUtils.generateToken(userDetails);

        Map<String, String> response = new HashMap<>();
        response.put("token", jwt);

        return response;
    }

}