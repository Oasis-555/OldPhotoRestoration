package com.photorestoration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private Long userId;

    private String userName;

    private String email;

    private String token;

    private Long expiresIn;  // Token过期时间（毫秒）

    private Integer userType;
}
