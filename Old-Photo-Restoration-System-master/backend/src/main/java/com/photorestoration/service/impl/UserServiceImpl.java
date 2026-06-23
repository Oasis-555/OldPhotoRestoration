package com.photorestoration.service.impl;

import com.photorestoration.dto.LoginRequest;
import com.photorestoration.dto.LoginResponse;
import com.photorestoration.dto.RegisterRequest;
import com.photorestoration.entity.User;
import com.photorestoration.repository.UserRepository;
import com.photorestoration.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Key;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Override
    @Transactional
    public User register(RegisterRequest request) {
        String userName = request.getUserName() == null ? "" : request.getUserName().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String password = request.getPassword() == null ? "" : request.getPassword();

        if (userName.isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (email.isEmpty()) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (password.length() < 6) {
            throw new RuntimeException("密码长度不能少于6位");
        }
        // 验证用户名是否已存在
        if (userRepository.existsByUserName(userName)) {
            throw new RuntimeException("用户名已存在");
        }

        // 验证邮箱是否已存在
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new RuntimeException("该邮箱已被注册");
        }
        // 创建新用户
        User user = User.builder()
                .userId(System.currentTimeMillis())  // 生成唯一用户ID
                .userName(userName)
                .password(passwordEncoder.encode(password))
                .email(email)
                .phone(request.getPhone())
                .userType(request.getUserType())
                .isActive(true)
                .createTime(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String account = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword() == null ? "" : request.getPassword();

        if (account.isEmpty()) {
            throw new RuntimeException("请输入用户名或邮箱");
        }
        if (password.isEmpty()) {
            throw new RuntimeException("请输入密码");
        }

        Optional<User> userOpt = userRepository.findByUserNameOrEmail(account);

        if (userOpt.isEmpty()) {
            throw new RuntimeException("账号未注册");
        }

        User user = userOpt.get();

        if (!verifyPassword(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 更新最后登录时间
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // 生成JWT Token
        String token = generateToken(user);

        return LoginResponse.builder()
                .userId(user.getUserId())  // Changed from getId() to getUserId()
                .userName(user.getUserName())
                .email(user.getEmail())
                .token(token)
                .expiresIn(jwtExpiration)
                .userType(user.getUserType())
                .build();
    }

    @Override
    public Optional<User> getUserById(Long userId) {
        return userRepository.findByUserId(userId); // Changed from findById
    }

    @Override
    public Optional<User> getUserByUserName(String userName) {
        return userRepository.findByUserName(userName);
    }

    @Override
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    @Transactional
    public User updateUserInfo(Long userId, User user) {
        User existing = userRepository.findByUserId(userId) // Changed from findById
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getEmail() != null) {
            existing.setEmail(user.getEmail());
        }
        if (user.getPhone() != null) {
            existing.setPhone(user.getPhone());
        }
        if (user.getBio() != null) {
            existing.setBio(user.getBio());
        }
        if (user.getAvatarUrl() != null) {
            existing.setAvatarUrl(user.getAvatarUrl());
        }

        existing.setUpdateTime(LocalDateTime.now());
        return userRepository.save(existing);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findByUserId(userId) // Changed from findById
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!verifyPassword(oldPassword, user.getPassword())) {
            throw new RuntimeException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
    }

    @Override
    public String generateToken(User user) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .setSubject(user.getUserId().toString()) // Changed from getId()
                .claim("userId", user.getUserId()) // Changed from getId()
                .claim("userName", user.getUserName())
                .claim("email", user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.error("Token validation failed", e);
            return false;
        }
    }

    @Override
    public Long getUserIdFromToken(String token) {
        try {
            Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            var claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return ((Number) claims.get("userId")).longValue();
        } catch (Exception e) {
            log.error("Failed to extract userId from token", e);
            return null;
        }
    }

    @Override
    public void sendResetCode(String email) throws Exception {
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("邮箱不能为空");
        }

        Optional<User> userOpt = userRepository.findByEmail(email.trim());
        if (userOpt.isEmpty()) {
            throw new RuntimeException("该邮箱未注册，请检查邮箱地址");
        }

        String code = String.format("%06d", new Random().nextInt(999999));
        redisTemplate.opsForValue().set(buildResetCodeKey(email), code, Duration.ofMinutes(5));
        log.info("发送重置验证码到邮箱：{}", email);

        if (mailFrom == null || mailFrom.trim().isEmpty()) {
            throw new RuntimeException("邮件服务未配置，请先在 application.yml 中配置 spring.mail.username");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("老照片修复系统 - 密码重置验证码");
        message.setText(String.format("您好！您的密码重置验证码是：%s\n该验证码有效期5分钟，请尽快使用。", code));

        try {
            mailSender.send(message);
        } catch (Exception e) {
            redisTemplate.delete(buildResetCodeKey(email));
            log.error("发送验证码邮件失败", e);
            throw new RuntimeException("邮件发送失败，请稍后重试");
        }
    }

    @Override
    public boolean verifyResetCode(String email, String code) {
        if (email == null || code == null) {
            return false;
        }

        Object storedCode = redisTemplate.opsForValue().get(buildResetCodeKey(email));
        return code.equals(String.valueOf(storedCode));
    }

    @Override
    @Transactional
    public void resetPassword(String email, String code, String newPassword) throws Exception {
        if (!verifyResetCode(email, code)) {
            throw new RuntimeException("验证码错误或已过期，请重新获取");
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("密码长度不能少于6位");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        log.info("邮箱{}的密码重置成功", email);

        redisTemplate.delete(buildResetCodeKey(email));
    }

    @Override
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    private String buildResetCodeKey(String email) {
        return "password-reset:code:" + email;
    }
}

