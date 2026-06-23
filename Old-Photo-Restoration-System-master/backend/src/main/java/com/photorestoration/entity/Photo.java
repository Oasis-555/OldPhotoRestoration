package com.photorestoration.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Photo {
    private Long id;
    private Long userId;
    private String groupName;
    private String fileName;
    private String absolutePath;
    private Long fileSize;
    private LocalDateTime uploadTime;
    private Boolean isClassified;
    private LocalDateTime deletedAt;
}
