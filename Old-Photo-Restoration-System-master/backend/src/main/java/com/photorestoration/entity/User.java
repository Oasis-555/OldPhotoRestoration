package com.photorestoration.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private Long id;
    private Long userId;
    private String userName;

    @JsonIgnore
    private String password;

    private String email;
    private Integer userType;
    private String phone;
    private String avatarUrl;
    private String bio;
    private Boolean isActive;
    private Boolean emailNotification;
    private Boolean wsNotification;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime lastLogin;
}
