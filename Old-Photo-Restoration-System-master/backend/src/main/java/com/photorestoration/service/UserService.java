package com.photorestoration.service;

import com.photorestoration.dto.LoginRequest;
import com.photorestoration.dto.LoginResponse;
import com.photorestoration.dto.RegisterRequest;
import com.photorestoration.entity.User;

import java.util.Optional;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     */
    User register(RegisterRequest request);

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 根据用户ID获取用户信息
     */
    Optional<User> getUserById(Long userId);

    /**
     * 根据用户名获取用户信息
     */
    Optional<User> getUserByUserName(String userName);

    /**
     * 验证密码
     */
    boolean verifyPassword(String rawPassword, String encodedPassword);

    /**
     * 修改用户信息
     */
    User updateUserInfo(Long userId, User user);

    /**
     * 修改密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 生成JWT Token
     */
    String generateToken(User user);

    /**
     * 验证JWT Token
     */
    boolean validateToken(String token);

    /**
     * 从Token中提取用户ID
     */
    Long getUserIdFromToken(String token);

    void sendResetCode(String email) throws Exception;

    boolean verifyResetCode(String email, String code);

    void resetPassword(String email, String code, String newPassword) throws Exception;

    Optional<User> getUserByEmail(String email);
}
