-- 老照片修复系统数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS photo_restoration
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE photo_restoration;

-- 用户信息表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增ID',
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    user_name VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密）',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    user_type TINYINT NOT NULL DEFAULT 1 COMMENT '用户类型（1个人，2机构）',
    phone VARCHAR(20) COMMENT '电话号码',
    avatar_url VARCHAR(255) COMMENT '头像URL',
    bio VARCHAR(500) COMMENT '个人简介',
    is_active BOOLEAN NOT NULL DEFAULT true COMMENT '是否激活',
    email_notification BOOLEAN NOT NULL DEFAULT true COMMENT '是否接收邮件通知',
    ws_notification BOOLEAN NOT NULL DEFAULT true COMMENT '是否接收站内通知',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    last_login DATETIME COMMENT '最后登录时间',
    INDEX idx_user_name (user_name),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- 照片修复记录表
CREATE TABLE IF NOT EXISTS restoration_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增ID',
    restoration_id BIGINT NOT NULL UNIQUE COMMENT '修复记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    original_image_url VARCHAR(255) NOT NULL COMMENT '原始照片URL',
    restored_image_url VARCHAR(255) COMMENT '修复后照片URL',
    original_absolute_path VARCHAR(500) COMMENT '原始照片绝对路径',
    restored_absolute_path VARCHAR(500) COMMENT '修复后照片绝对路径',
    mask_image_url VARCHAR(255) COMMENT '破损区域遮罩URL',
    mask_absolute_path VARCHAR(500) COMMENT '破损区域遮罩绝对路径',
    damage_type VARCHAR(100) COMMENT '损伤类型（划痕/噪点/褪色）',
    restoration_mode VARCHAR(50) COMMENT '修复模式（自动/手动）',
    quality_score FLOAT COMMENT '修复质量评分',
    file_size BIGINT COMMENT '原始文件大小',
    restoration_time BIGINT COMMENT '修复耗时（毫秒）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态（0待处理，1处理中，2已完成，3失败）',
    error_message VARCHAR(500) COMMENT '错误信息',
    thumbnail_url VARCHAR(255) COMMENT '缩略图URL',
    remarks VARCHAR(500) COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    INDEX idx_restoration_id (restoration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='照片修复记录表';

-- 照片表（相册）
CREATE TABLE IF NOT EXISTS photos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    group_name VARCHAR(100) NOT NULL COMMENT '分组名称',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    absolute_path VARCHAR(1000) NOT NULL COMMENT '绝对路径',
    file_size BIGINT NOT NULL COMMENT '文件大小',
    upload_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传/保存时间',
    is_classified BOOLEAN NOT NULL DEFAULT false COMMENT '是否已分类',
    deleted_at DATETIME COMMENT '软删除时间',
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_user_id (user_id),
    INDEX idx_group_name (group_name),
    INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='照片表（相册）';

-- 修复任务队列表
CREATE TABLE IF NOT EXISTS restoration_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增ID',
    task_id BIGINT NOT NULL UNIQUE COMMENT '任务ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    restoration_id BIGINT NOT NULL COMMENT '修复记录ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态（0待处理，1处理中，2已完成，3失败）',
    progress INT DEFAULT 0 COMMENT '处理进度（0-100）',
    priority INT DEFAULT 5 COMMENT '优先级',
    error_message VARCHAR(255) COMMENT '错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retries INT DEFAULT 3 COMMENT '最大重试次数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    start_time DATETIME COMMENT '开始执行时间',
    end_time DATETIME COMMENT '结束执行时间',
    execution_time BIGINT COMMENT '执行耗时（毫秒）',
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (restoration_id) REFERENCES restoration_records(restoration_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_task_id (task_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='修复任务队列表';

-- 添加索引优化查询
CREATE INDEX idx_users_active ON users(is_active);
CREATE INDEX idx_restoration_records_user_status ON restoration_records(user_id, status);
CREATE INDEX idx_restoration_tasks_status_priority ON restoration_tasks(status, priority);

-- 创建视图：用户修复统计
CREATE OR REPLACE VIEW user_restoration_stats AS
SELECT
    u.user_id,
    u.user_name,
    COUNT(CASE WHEN rr.status = 2 THEN 1 END) as success_count,
    COUNT(CASE WHEN rr.status = 3 THEN 1 END) as failed_count,
    COUNT(CASE WHEN rr.status IN (0, 1) THEN 1 END) as pending_count,
    AVG(rr.quality_score) as avg_quality_score,
    SUM(rr.file_size) as total_file_size
FROM users u
LEFT JOIN restoration_records rr ON u.user_id = rr.user_id
GROUP BY u.user_id, u.user_name;

-- 测试数据：请通过应用程序注册接口（POST /api/auth/register）创建账号
-- 后端使用 BCrypt 加密密码，不能直接在 SQL 中插入明文或 SHA2 哈希
-- 示例：
-- curl -X POST http://localhost:8080/api/auth/register \
--   -H "Content-Type: application/json" \
--   -d '{"userName":"testuser","email":"test@example.com","password":"password123","userType":1}'

