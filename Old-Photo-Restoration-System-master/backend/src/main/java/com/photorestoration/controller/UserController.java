package com.photorestoration.controller;

import com.photorestoration.dto.ApiResponse;
import com.photorestoration.entity.User;
import com.photorestoration.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 更新用户信息
     */
    @PutMapping("/{userId}")
    public ApiResponse<User> updateUser(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String token,
            @RequestBody User userInfo) {
        try {
            String actualToken = token.replace("Bearer ", "");
            Long tokenUserId = userService.getUserIdFromToken(actualToken);
            if (tokenUserId == null || !tokenUserId.equals(userId)) {
                return ApiResponse.error(403, "无权修改此用户信息");
            }
            User updated = userService.updateUserInfo(userId, userInfo);
            return ApiResponse.success(updated);
        } catch (Exception e) {
            log.error("更新用户信息失败", e);
            return ApiResponse.error(500, "更新用户信息失败：" + e.getMessage());
        }
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public ApiResponse<String> changePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> request) {
        try {
            String actualToken = token.replace("Bearer ", "");
            Long userId = userService.getUserIdFromToken(actualToken);
            if (userId == null) {
                return ApiResponse.error(401, "未授权");
            }
            String oldPassword = request.get("oldPassword");
            String newPassword = request.get("newPassword");
            if (oldPassword == null || newPassword == null) {
                return ApiResponse.error(400, "请提供旧密码和新密码");
            }
            userService.changePassword(userId, oldPassword, newPassword);
            return ApiResponse.success("密码修改成功");
        } catch (Exception e) {
            log.error("修改密码失败", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }
}
