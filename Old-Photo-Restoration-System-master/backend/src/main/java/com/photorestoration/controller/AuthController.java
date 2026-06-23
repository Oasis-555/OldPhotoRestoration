package com.photorestoration.controller;

import com.photorestoration.dto.ApiResponse;
import com.photorestoration.dto.LoginRequest;
import com.photorestoration.dto.LoginResponse;
import com.photorestoration.dto.RegisterRequest;
import com.photorestoration.dto.ResetPasswordRequest;
import com.photorestoration.dto.SendCodeRequest;
import com.photorestoration.dto.VerifyCodeRequest;
import com.photorestoration.entity.User;
import com.photorestoration.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ApiResponse<User> register(@RequestBody RegisterRequest request) {
        log.info("User registration: {}", request.getUserName());
        try {
            User user = userService.register(request);
            return ApiResponse.success(user);
        } catch (RuntimeException e) {
            log.error("Registration failed: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Registration failed", e);
            return ApiResponse.error(500, "注册失败，请稍后重试");
        }
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("User login: {}", request.getUsername());
        try {
            LoginResponse response = userService.login(request);
            return ApiResponse.success(response);
        } catch (RuntimeException e) {
            log.error("Login failed: {}", e.getMessage());
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("Login failed", e);
            return ApiResponse.error(401, "登录失败，请稍后重试");
        }
    }

    /**
     * 验证Token
     */
    @GetMapping("/validate-token")
    public ApiResponse<Boolean> validateToken(@RequestHeader("Authorization") String token) {
        try {
            String actualToken = token.replace("Bearer ", "");
            boolean isValid = userService.validateToken(actualToken);
            return ApiResponse.success(isValid);
        } catch (Exception e) {
            return ApiResponse.error(401, "Invalid token");
        }
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<User> getCurrentUser(@RequestHeader("Authorization") String token) {
        try {
            String actualToken = token.replace("Bearer ", "");
            Long userId = userService.getUserIdFromToken(actualToken);
            User user = userService.getUserById(userId).orElse(null);
            if (user == null) {
                return ApiResponse.error(404, "User not found");
            }
            return ApiResponse.success(user);
        } catch (Exception e) {
            return ApiResponse.error(401, "Invalid token");
        }
    }

    @PostMapping("/forgot-password/send-code")
    public ApiResponse<?> sendCode(@RequestBody SendCodeRequest request) {
        try {
            userService.sendResetCode(request.getEmail());
            return ApiResponse.success("验证码已发送");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/forgot-password/verify-code")
    public ApiResponse<?> verifyCode(@RequestBody VerifyCodeRequest request) {
        boolean valid = userService.verifyResetCode(request.getEmail(), request.getCode());
        if (valid) {
            return ApiResponse.success("验证成功");
        } else {
            return ApiResponse.error("验证码错误或已过期");
        }
    }

    @PostMapping("/forgot-password/reset")
    public ApiResponse<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            userService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
            return ApiResponse.success("密码重置成功");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}

