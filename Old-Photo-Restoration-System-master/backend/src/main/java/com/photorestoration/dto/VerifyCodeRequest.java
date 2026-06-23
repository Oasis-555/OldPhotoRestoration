package com.photorestoration.dto;
import lombok.Data;
@Data // 关键注解：必须加上
public class VerifyCodeRequest {
    private String email;
    private String code;
    // getters and setters
}