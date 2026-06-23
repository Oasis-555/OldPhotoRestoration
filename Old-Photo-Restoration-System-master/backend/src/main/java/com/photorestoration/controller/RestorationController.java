package com.photorestoration.controller;

import com.photorestoration.dto.ApiResponse;
import com.photorestoration.entity.RestorationRecord;
import com.photorestoration.service.RestorationService;
import com.photorestoration.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 照片修复控制器
 */
@Slf4j
@RestController
@RequestMapping("/restoration")
@RequiredArgsConstructor
public class RestorationController {

    private final RestorationService restorationService;
    private final UserService userService;

    /**
     * 上传照片并创建修复任务
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<RestorationRecord>> uploadImage(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mask", required = false) MultipartFile maskFile,
            @RequestParam(value = "mode", defaultValue = "auto") String mode) {
        try {
            String actualToken = token.replace("Bearer ", "");
            Long userId = userService.getUserIdFromToken(actualToken);

            if (!userService.getUserById(userId).isPresent()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "User not found or session expired"));
            }

            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "File is empty"));
            }

            RestorationRecord record = restorationService.uploadAndCreateTask(userId, file, maskFile, mode);
            return ResponseEntity.ok(ApiResponse.success(record));
        } catch (Exception e) {
            log.error("File upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "File upload failed: " + e.getMessage()));
        }
    }

    /**
     * 获取用户的修复记录（分页）
     */
    @GetMapping("/records")
    public ApiResponse<Page<RestorationRecord>> getRecords(
            @RequestHeader("Authorization") String token,
            Pageable pageable) {
        try {
            String actualToken = token.replace("Bearer ", "");
            Long userId = userService.getUserIdFromToken(actualToken);

            Page<RestorationRecord> records = restorationService.getRestorationRecords(userId, pageable);
            return ApiResponse.success(records);
        } catch (Exception e) {
            log.error("Get records failed", e);
            return ApiResponse.error(500, "Failed to get records");
        }
    }

    /**
     * 获取单个修复记录的详细信息
     */
    @GetMapping("/records/{restorationId}")
    public ApiResponse<RestorationRecord> getRecord(
            @PathVariable Long restorationId) {
        try {
            RestorationRecord record = restorationService.getRestorationRecord(restorationId)
                    .orElse(null);
            if (record == null) {
                return ApiResponse.error(404, "Record not found");
            }
            return ApiResponse.success(record);
        } catch (Exception e) {
            return ApiResponse.error(500, "Failed to get record");
        }
    }

    /**
     * 获取修复进度
     */
    @GetMapping("/progress/{taskId}")
    public ApiResponse<Integer> getProgress(@PathVariable Long taskId) {
        try {
            Integer progress = restorationService.getRestorationProgress(taskId);
            return ApiResponse.success(progress);
        } catch (Exception e) {
            return ApiResponse.error(500, "Failed to get progress");
        }
    }

    /**
     * 下载修复后的图像
     */
    @GetMapping("/download/{restorationId}")
    public ResponseEntity<byte[]> downloadImage(@PathVariable Long restorationId) {
        try {
            byte[] imageData = restorationService.downloadRestoredImage(restorationId);
            if (imageData == null || imageData.length == 0) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=restored_image.jpg")
                    .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                    .body(imageData);
        } catch (Exception e) {
            log.error("Download failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/batch-download")
    public void batchDownload(
            @RequestHeader("Authorization") String token,
            @RequestBody List<Long> restorationIds,
            HttpServletResponse response) {
        try {
            String actualToken = token.replace("Bearer ", "");
            Long userId = userService.getUserIdFromToken(actualToken);
            restorationService.batchDownload(userId, restorationIds, response);
        } catch (Exception e) {
            log.error("Batch download failed", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * 删除修复记录
     */
    @DeleteMapping("/records/{restorationId}")
    public ApiResponse<String> deleteRecord(
            @PathVariable Long restorationId,
            @RequestHeader("Authorization") String token) {
        try {
            String actualToken = token.replace("Bearer ", "");
            Long userId = userService.getUserIdFromToken(actualToken);

            RestorationRecord record = restorationService.getRestorationRecord(restorationId)
                    .orElse(null);
            if (record == null || !record.getUserId().equals(userId)) {
                return ApiResponse.error(403, "Forbidden");
            }

            restorationService.deleteRestorationRecord(restorationId);
            return ApiResponse.success("Record deleted successfully");
        } catch (Exception e) {
            return ApiResponse.error(500, "Failed to delete record");
        }
    }

    /**
     * 获取用户统计信息
     */
    @GetMapping("/stats")
    public ApiResponse<java.util.Map<String, Object>> getStats(
            @RequestHeader("Authorization") String token) {
        try {
            String actualToken = token.replace("Bearer ", "");
            Long userId = userService.getUserIdFromToken(actualToken);

            Long successCount = restorationService.getSuccessfulRestorationsCount(userId);
            return ApiResponse.success(java.util.Collections.singletonMap("successCount", successCount));
        } catch (Exception e) {
            return ApiResponse.error(500, "Failed to get stats");
        }
    }
}
