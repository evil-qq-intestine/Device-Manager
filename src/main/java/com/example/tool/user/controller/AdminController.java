package com.example.tool.user.controller;

import com.example.tool.user.request.CreateUserRequest;
import com.example.tool.user.response.UserResponse;
import com.example.tool.user.entity.User;
import com.example.tool.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    @Autowired
    private UserService userService;

    @DeleteMapping("/{idOrName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String idOrName) {
        if (isNumeric(idOrName)) {
            userService.deleteById(Integer.valueOf(idOrName));
        } else {
            userService.deleteByUsername(idOrName);
        }
    }

    @GetMapping("")
    public List<UserResponse> findAllUser() {
        return userService.findAll();
    }

    @GetMapping("/{idOrName}")
    public UserResponse findUser(@PathVariable String idOrName) {
        if (isNumeric(idOrName)) {
            return userService.getUserResponseById(Integer.valueOf(idOrName));
        }
        return userService.getUserResponseByName(idOrName);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse addUser(@Valid @RequestBody CreateUserRequest createUserRequest) {
        return userService.createUser(createUserRequest);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePassword(@PathVariable Integer id, @Valid @RequestBody User user) {
        userService.updatePassword(id, user.getPassword());
    }

    private boolean isNumeric(String value) {
        return value != null && value.matches("\\d+");
    }
}
