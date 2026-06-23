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
public class RestorationTask {
    private Long id;
    private Long taskId;
    private Long userId;
    private Long restorationId;
    private Integer status;
    private Integer progress;
    private Integer priority;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long executionTime;
}
