package com.photorestoration.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestorationRecord {
    private Long id;
    private Long restorationId;
    private Long userId;
    private String originalImageUrl;
    private String restoredImageUrl;
    private String originalAbsolutePath;
    private String restoredAbsolutePath;
    private String maskImageUrl;
    private String maskAbsolutePath;
    private String damageType;
    private String restorationMode;
    private Float qualityScore;
    private Long fileSize;
    private Long restorationTime;
    private Integer status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String thumbnailUrl;
    private String remarks;
}
