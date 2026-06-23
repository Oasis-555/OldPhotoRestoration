package com.photorestoration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    private String userName;

    private String password;

    private String email;

    private String phone;

    private Integer userType;  // 1: 个人, 2: 机构
}
