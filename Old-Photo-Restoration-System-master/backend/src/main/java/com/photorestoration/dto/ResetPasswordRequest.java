package com.photorestoration.dto;
import lombok.Data;
@Data // 关键注解：必须加上
public class ResetPasswordRequest {
    private String email;
    private String code;
    private String newPassword;
    // getters and setters
}