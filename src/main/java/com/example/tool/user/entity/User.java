package com.example.tool.user.entity;

import com.example.tool.device.entity.Device;
import com.example.tool.user.request.CreateUserRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @JsonIgnore
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Device> devices = new ArrayList<>();

    private String username;
    private String password;
    private Integer tokenVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    public void addDevice(Device device) {
        devices.add(device);
        device.setUser(this);
    }

    public void removeDevice(Device device) {
        devices.remove(device);
        device.setUser(null);
    }

    public void createUser(CreateUserRequest createUserRequest) {
        this.setUsername(createUserRequest.getUsername());
        this.setPassword(createUserRequest.getPassword());
        this.setRole(createUserRequest.getRole());
    }
}