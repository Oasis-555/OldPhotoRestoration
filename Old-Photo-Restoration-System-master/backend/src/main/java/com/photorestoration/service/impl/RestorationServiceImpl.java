package com.photorestoration.service.impl;

import com.photorestoration.entity.RestorationRecord;
import com.photorestoration.entity.RestorationTask;
import com.photorestoration.repository.RestorationRecordRepository;
import com.photorestoration.repository.RestorationTaskRepository;
import com.photorestoration.service.RestorationService;
import com.photorestoration.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 照片修复服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestorationServiceImpl implements RestorationService {

    private final RestorationRecordRepository restorationRecordRepository;
    private final RestorationTaskRepository restorationTaskRepository;
    private final NotificationService notificationService;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Value("${file.upload.path:/uploads/images/}")
    private String uploadPath;

    @Value("${ai.esrgan.base-url:http://127.0.0.1:8000}")
    private String esrganBaseUrl;

    @Value("${ai.esrgan.enhance-path:/enhance}")
    private String esrganEnhancePath;

    @Value("${ai.esrgan.model-name:RealESRGAN_x4plus}")
    private String esrganModelName;

    @Value("${ai.esrgan.outscale:4}")
    private Integer esrganOutscale;

    @Value("${ai.esrgan.denoise-strength:0.5}")
    private Double esrganDenoiseStrength;

    @Value("${ai.esrgan.tile:0}")
    private Integer esrganTile;

    @Value("${ai.esrgan.tile-pad:10}")
    private Integer esrganTilePad;

    @Value("${ai.esrgan.pre-pad:0}")
    private Integer esrganPrePad;

    @Value("${ai.esrgan.fp32:false}")
    private Boolean esrganFp32;

    @Value("${ai.esrgan.face-enhance:false}")
    private Boolean esrganFaceEnhance;

    @Value("${ai.esrgan.results-dir:}")
    private String esrganResultsDir;

    @Value("${ai.face-restore.base-url:http://127.0.0.1:5001}")
    private String faceRestoreBaseUrl;

    @Value("${ai.face-restore.restore-path:/restore}")
    private String faceRestorePath;

    @Value("${ai.face-restore.weight:0.5}")
    private Double faceRestoreWeight;

    @Value("${ai.inpaint.base-url:http://127.0.0.1:5002}")
    private String inpaintBaseUrl;

    @Value("${ai.inpaint.inpaint-path:/inpaint}")
    private String inpaintPath;

    @Value("${ai.inpaint.radius:3}")
    private Double inpaintRadius;

    @Value("${ai.inpaint.model:auto}")
    private String inpaintModel;

    @Value("${ai.inpaint.prompt:old damaged photo restoration, realistic texture, natural details}")
    private String inpaintPrompt;

    @Override
    @CacheEvict(value = "restorationRecords", allEntries = true)
    public RestorationRecord uploadAndCreateTask(Long userId, MultipartFile file, MultipartFile maskFile, String restorationMode) {
        try {
            // 保存文件
            String fileName = generateFileName(file);
            Path filePath = Paths.get(uploadPath, fileName);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, file.getBytes());

            // Create the absolute image access URL from backend controller path
            String absolutePathForwardSlashes = filePath.toFile().getAbsolutePath().replace("\\", "/");
            String accessUrl = "/api/album/image?path=" + java.net.URLEncoder.encode(absolutePathForwardSlashes, "UTF-8");

            String maskAccessUrl = null;
            String maskAbsolutePath = null;
            if (maskFile != null && !maskFile.isEmpty()) {
                String maskFileName = generateMaskFileName(fileName);
                Path maskPath = Paths.get(uploadPath, "masks", maskFileName);
                Files.createDirectories(maskPath.getParent());
                Files.write(maskPath, maskFile.getBytes());
                maskAbsolutePath = maskPath.toFile().getAbsolutePath().replace("\\", "/");
                maskAccessUrl = "/api/album/image?path=" + java.net.URLEncoder.encode(maskAbsolutePath, "UTF-8");
            }

            // 创建修复记录
            RestorationRecord record = RestorationRecord.builder()
                    .restorationId(System.currentTimeMillis())
                    .userId(userId)
                    .originalImageUrl(accessUrl)
                    .originalAbsolutePath(absolutePathForwardSlashes)
                    .maskImageUrl(maskAccessUrl)
                    .maskAbsolutePath(maskAbsolutePath)
                    .fileSize(file.getSize())
                    .restorationMode(restorationMode)
                    .status(0)  // 待处理
                    .createTime(LocalDateTime.now())
                    .build();

                RestorationRecord savedRecord = restorationRecordRepository.save(record);

            // 创建修复任务
            RestorationTask task = RestorationTask.builder()
                    .taskId(System.currentTimeMillis())
                    .userId(userId)
                    .restorationId(savedRecord.getRestorationId())
                    .status(0)  // 待处理
                    .progress(0)
                    .priority(5)
                    .retryCount(0)
                    .maxRetries(3)
                    .createTime(LocalDateTime.now())
                    .build();

                restorationTaskRepository.save(task);

            // 异步处理修复任务
            CompletableFuture.runAsync(() -> processRestorationTaskAsync(task));

            return savedRecord;
        } catch (IOException e) {
            log.error("File upload failed", e);
            throw new RuntimeException("File upload failed: " + e.getMessage());
        }
    }

    @Override
    public Page<RestorationRecord> getRestorationRecords(Long userId, Pageable pageable) {
        List<RestorationRecord> records = restorationRecordRepository.findByUserIdPaged(
                userId,
                pageable.getPageSize(),
                pageable.getOffset()
        );
        long total = restorationRecordRepository.countByUserId(userId);
        return new PageImpl<>(records, pageable, total);
    }

    @Override
    public Optional<RestorationRecord> getRestorationRecord(Long restorationId) {
        return restorationRecordRepository.findByRestorationId(restorationId);
    }

    @Override
    public List<RestorationRecord> getUserRestorationHistory(Long userId, int limit) {
        List<RestorationRecord> records = restorationRecordRepository.findLatestRecords(userId);
        return records.size() <= limit ? records : records.subList(0, limit);
    }

    @Override
    @Async
    public void processRestorationTask(RestorationTask task) {
        processRestorationTaskAsync(task);
    }

    private void processRestorationTaskAsync(RestorationTask task) {
        try {
            // Give a tiny moment for DB records to be fully available across threads
            Thread.sleep(200);

            // 更新任务状态为处理中
            task.setStatus(1);
            task.setStartTime(LocalDateTime.now());
            restorationTaskRepository.save(task);

            // 获取原始记录
            RestorationRecord record = restorationRecordRepository.findByRestorationId(task.getRestorationId())
                    .orElseThrow(() -> new RuntimeException("Restoration record not found for ID: " + task.getRestorationId()));

            // Use the same logic as AlbumService to get consistent user directory
            String userUploadPath = Paths.get(uploadPath, "user_" + task.getUserId()).toFile().getAbsolutePath();
            File userDir = new File(userUploadPath);
            if (!userDir.exists()) userDir.mkdirs();

            // 模拟修复过程
            long startTime = System.currentTimeMillis();

            RestorationOutput output = restoreImage(record, userUploadPath);
            String restoredUrl = output.accessUrl;

            long endTime = System.currentTimeMillis();

            // 更新记录
            record.setStatus(2);  // 已完成
            record.setRestoredImageUrl(restoredUrl);
            String restoredAbsolutePath = output.absolutePath;
            if (restoredAbsolutePath == null || restoredAbsolutePath.isEmpty()) {
                restoredAbsolutePath = getAbsoluteFromUrl(restoredUrl);
            }
            if ((restoredAbsolutePath == null || restoredAbsolutePath.isEmpty()) && isSuperResolutionMode(record.getRestorationMode())) {
                restoredAbsolutePath = inferAbsolutePathFromResultUrl(restoredUrl);
                // results-dir 不可靠时，直接以服务返回路径(URL)为准
                if (restoredAbsolutePath == null || restoredAbsolutePath.isEmpty()) {
                    restoredAbsolutePath = restoredUrl;
                }
            }
            record.setRestoredAbsolutePath(restoredAbsolutePath);
            record.setQualityScore(output.qualityScore);
            record.setDamageType(output.damageType);
            record.setRestorationTime(endTime - startTime);
            record.setUpdateTime(LocalDateTime.now());
            restorationRecordRepository.save(record);
            restorationRecordRepository.save(record);

            // 更新任务
            task.setStatus(2);  // 已完成
            task.setProgress(100);
            task.setEndTime(LocalDateTime.now());
            task.setExecutionTime(endTime - startTime);
            restorationTaskRepository.save(task);
            restorationTaskRepository.save(task);
            evictRestorationCache();

            notificationService.notifyRestorationCompleted(task.getUserId(), record);
            log.info("Restoration task completed successfully. TaskId: {}, RestorationId: {}", task.getTaskId(), task.getRestorationId());
        } catch (Exception e) {
            log.error("Restoration task error: {}", e.getMessage(), e);

            // 更新失败状态
            task.setStatus(3);  // 失败
            task.setErrorMessage(e.getMessage());
            task.setRetryCount(task.getRetryCount() + 1);
            task.setEndTime(LocalDateTime.now());
            restorationTaskRepository.save(task);
            restorationTaskRepository.save(task);
            evictRestorationCache();

            // 如果未超过最大重试次数，重新尝试处理
            if (task.getRetryCount() < task.getMaxRetries()) {
                log.info("Retrying restoration task... count: {}", task.getRetryCount());
                task.setStatus(0);  // 重新标记为待处理
                restorationTaskRepository.save(task);
                restorationTaskRepository.save(task);
                // 递归或延迟再次触发
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(5000); // Wait 5s before retry
                        processRestorationTaskAsync(task);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }
    }

    private RestorationOutput restoreImage(RestorationRecord record, String userUploadPath) throws IOException {
        String mode = normalizeMode(record.getRestorationMode());
        RestorationOutput current = RestorationOutput.fromPath(record.getOriginalAbsolutePath(), "原图", 0.75f);

        if (isSuperResolutionMode(mode)) {
            return callSuperResolutionService(current.absolutePath, "超分增强");
        }

        if (isFaceRestoreMode(mode)) {
            return callFaceRestoreService(current.absolutePath, "人脸清晰化");
        }

        if (isInpaintMode(mode)) {
            return callInpaintService(current.absolutePath, record.getMaskAbsolutePath(), "划痕/缺损修复");
        }

        if (isAutoMode(mode) || isComprehensiveMode(mode)) {
            boolean hasManualMask = record.getMaskAbsolutePath() != null && !record.getMaskAbsolutePath().trim().isEmpty();
            if (hasManualMask) {
                current = callInpaintService(current.absolutePath, record.getMaskAbsolutePath(), "缺损补全");
            } else {
                log.info("未提供手动破损遮罩，综合修复跳过缺损补全，避免自动检测误修");
            }

            try {
                current = callFaceRestoreService(current.absolutePath, "划痕 + 人脸修复");
            } catch (Exception e) {
                log.info("人脸修复步骤跳过，可能未检测到可修复人脸: {}", e.getMessage());
            }

            current = callSuperResolutionService(current.absolutePath, "综合修复");
            current.damageType = isAutoMode(mode) ? "自动综合修复" : "综合修复";
            current.qualityScore = 0.92f;
            return current;
        }

        log.warn("未知修复模式 {}，使用综合修复链路", record.getRestorationMode());
        current = callInpaintService(current.absolutePath, record.getMaskAbsolutePath(), "划痕/缺损修复");
        current = callSuperResolutionService(current.absolutePath, "综合修复");
        return current;
    }

    private boolean isSuperResolutionMode(String mode) {
        if (mode == null) return false;
        String normalized = mode.trim().toLowerCase();
        return "sr".equals(normalized) || "super".equals(normalized) || "super_resolution".equals(normalized);
    }

    private boolean isFaceRestoreMode(String mode) {
        if (mode == null) return false;
        String normalized = mode.trim().toLowerCase();
        return "face".equals(normalized) || "face_restore".equals(normalized) || "face_enhance".equals(normalized);
    }

    private boolean isInpaintMode(String mode) {
        String normalized = normalizeMode(mode);
        return "inpaint".equals(normalized) || "scratch".equals(normalized) || "damage".equals(normalized);
    }

    private boolean isAutoMode(String mode) {
        return "auto".equals(normalizeMode(mode));
    }

    private boolean isComprehensiveMode(String mode) {
        String normalized = normalizeMode(mode);
        return "comprehensive".equals(normalized) || "full".equals(normalized) || "all".equals(normalized);
    }

    private String normalizeMode(String mode) {
        return mode == null ? "auto" : mode.trim().toLowerCase();
    }

    /**
     * 调用本地超分服务：POST /enhance
     * 请求体示例：
     * {
     *   "image": "/abs/path/to/image.jpg",
     *   "model_name": "RealESRGAN_x4plus",
     *   "outscale": 4,
     *   "face_enhance": false
     * }
     */
    private RestorationOutput callSuperResolutionService(String imageInput, String damageType) {
        if (imageInput == null || imageInput.trim().isEmpty()) {
            throw new RuntimeException("No image path/url available for super-resolution");
        }

        String endpoint = buildEnhanceEndpoint();
        log.info("调用超分服务: {}, image={}", endpoint, imageInput);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("image", imageInput);
        body.put("model_name", esrganModelName);
        body.put("outscale", esrganOutscale);
        body.put("denoise_strength", esrganDenoiseStrength);
        body.put("tile", esrganTile);
        body.put("tile_pad", esrganTilePad);
        body.put("pre_pad", esrganPrePad);
        body.put("fp32", esrganFp32);
        body.put("face_enhance", esrganFaceEnhance);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);
        Map resp = response.getBody();

        if (resp == null || !resp.containsKey("result_url") || resp.get("result_url") == null) {
            throw new RuntimeException("Super-resolution service response missing result_url");
        }

        String resultUrl = String.valueOf(resp.get("result_url"));
        String resultPath = resp.get("result_path") == null ? "" : String.valueOf(resp.get("result_path")).replace("\\", "/");
        resultPath = normalizeEsrganResultPath(resultPath, resultUrl);
        // 服务可能返回相对路径（如 /results/xxx.jpg），补全为绝对URL便于前端直接访问
        if (resultUrl.startsWith("/")) {
            resultUrl = trimTrailingSlash(esrganBaseUrl) + resultUrl;
        }

        log.info("超分完成，result_url={}", resultUrl);
        return new RestorationOutput(resultUrl, resultPath, damageType, 0.9f);
    }

    /**
     * 调用本地人脸修复服务：POST /restore
     * 请求体：{"image_path": "/abs/path/to/image.jpg"}
     * 返回体：{"status":"success","result_path":"/abs/path/to/output.png"}
     */
    private RestorationOutput callFaceRestoreService(String imagePath, String damageType) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            throw new RuntimeException("No absolute image path available for face restore");
        }

        String endpoint = trimTrailingSlash(faceRestoreBaseUrl) + ensureLeadingSlash(faceRestorePath);
        log.info("调用人脸修复服务: {}, image_path={}", endpoint, imagePath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("image_path", imagePath);
        body.put("weight", faceRestoreWeight);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);
        Map resp = response.getBody();

        if (resp == null || resp.get("result_path") == null) {
            throw new RuntimeException("Face-restore service response missing result_path");
        }

        String resultPath = String.valueOf(resp.get("result_path")).replace("\\", "/");
        if (resultPath.trim().isEmpty()) {
            throw new RuntimeException("Face-restore result_path is empty");
        }

        String accessUrl;
        try {
            accessUrl = "/api/album/image?path=" + java.net.URLEncoder.encode(resultPath, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Encode result_path failed: " + resultPath, e);
        }
        log.info("人脸修复完成，result_path={}, accessUrl={}", resultPath, accessUrl);
        return new RestorationOutput(accessUrl, resultPath, damageType, 0.88f);
    }

    private RestorationOutput callInpaintService(String imagePath, String maskPath, String damageType) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            throw new RuntimeException("No absolute image path available for inpaint");
        }

        String endpoint = trimTrailingSlash(inpaintBaseUrl) + ensureLeadingSlash(inpaintPath);
        log.info("调用划痕/缺损修复服务: {}, image_path={}, mask_path={}", endpoint, imagePath, maskPath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("image_path", imagePath);
        body.put("radius", inpaintRadius);
        body.put("model", inpaintModel);
        body.put("prompt", inpaintPrompt);
        if (maskPath != null && !maskPath.trim().isEmpty()) {
            body.put("mask_path", maskPath);
        }

        ResponseEntity<Map> response = new RestTemplate().postForEntity(endpoint, new HttpEntity<>(body, headers), Map.class);
        Map resp = response.getBody();

        if (resp == null || resp.get("result_path") == null) {
            throw new RuntimeException("Inpaint service response missing result_path");
        }
        Object statusObj = resp.get("status");
        if (statusObj != null && "error".equalsIgnoreCase(String.valueOf(statusObj))) {
            throw new RuntimeException("Inpaint service failed: " + resp.get("message"));
        }

        String resultPath = String.valueOf(resp.get("result_path")).replace("\\", "/");
        String accessUrl;
        try {
            accessUrl = "/api/album/image?path=" + java.net.URLEncoder.encode(resultPath, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Encode result_path failed: " + resultPath, e);
        }
        log.info("划痕/缺损修复完成，result_path={}", resultPath);
        return new RestorationOutput(accessUrl, resultPath, damageType, 0.86f);
    }

    private String buildEnhanceEndpoint() {
        return trimTrailingSlash(esrganBaseUrl) + ensureLeadingSlash(esrganEnhancePath);
    }

    private String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String ensureLeadingSlash(String s) {
        if (s == null || s.isEmpty()) return "/enhance";
        return s.startsWith("/") ? s : "/" + s;
    }

    private String inferAbsolutePathFromResultUrl(String resultUrl) {
        if (resultUrl == null || resultUrl.trim().isEmpty()) return "";
        if (esrganResultsDir == null || esrganResultsDir.trim().isEmpty()) return "";
        try {
            URI uri = URI.create(resultUrl);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) return "";
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
            if (fileName.isEmpty()) return "";
            return Paths.get(esrganResultsDir, fileName).toFile().getAbsolutePath().replace("\\", "/");
        } catch (Exception e) {
            log.warn("无法从 result_url 推断本地路径: {}", resultUrl);
            return "";
        }
    }

    private String normalizeEsrganResultPath(String resultPath, String resultUrl) {
        String normalized = resultPath == null ? "" : resultPath.trim().replace("\\", "/");
        if (!normalized.isEmpty() && Paths.get(normalized).isAbsolute()) {
            return normalized;
        }

        String fileName = "";
        if (!normalized.isEmpty()) {
            fileName = normalized.substring(normalized.lastIndexOf("/") + 1);
        }
        if (fileName.isEmpty() && resultUrl != null && !resultUrl.trim().isEmpty()) {
            try {
                URI uri = URI.create(resultUrl);
                String path = uri.getPath();
                if (path != null && !path.isEmpty()) {
                    fileName = path.substring(path.lastIndexOf("/") + 1);
                    fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
                }
            } catch (Exception e) {
                log.warn("无法解析 Real-ESRGAN result_url: {}", resultUrl);
            }
        }
        if (fileName.isEmpty()) {
            return normalized;
        }

        String configured = esrganResultsDir == null ? "" : esrganResultsDir.trim();
        if (!configured.isEmpty()) {
            return Paths.get(configured, fileName).toFile().getAbsolutePath().replace("\\", "/");
        }

        return Paths.get("..", "..", "Real-ESRGAN", "api_results", fileName)
                .toFile()
                .getAbsolutePath()
                .replace("\\", "/");
    }

    private static class RestorationOutput {
        private String accessUrl;
        private String absolutePath;
        private String damageType;
        private float qualityScore;

        private RestorationOutput(String accessUrl, String absolutePath, String damageType, float qualityScore) {
            this.accessUrl = accessUrl;
            this.absolutePath = absolutePath;
            this.damageType = damageType;
            this.qualityScore = qualityScore;
        }

        private static RestorationOutput fromPath(String absolutePath, String damageType, float qualityScore) {
            if (absolutePath == null || absolutePath.trim().isEmpty()) {
                throw new RuntimeException("No absolute image path available");
            }
            try {
                String normalizedPath = absolutePath.replace("\\", "/");
                String accessUrl = "/api/album/image?path=" + java.net.URLEncoder.encode(normalizedPath, "UTF-8");
                return new RestorationOutput(accessUrl, normalizedPath, damageType, qualityScore);
            } catch (Exception e) {
                throw new RuntimeException("Encode image path failed: " + absolutePath, e);
            }
        }
    }

    /**
     * 根据文件名简单推断损伤类型（演示用）
     */
    private String detectDamageType(String imageUrl) {
        String[] types = {"划痕修复", "噪点去除", "色彩恢复", "模糊增强", "综合修复"};
        return types[(int)(Math.random() * types.length)];
    }

    @Override
    @Transactional
    public void deleteRestorationRecord(Long restorationId) {
        RestorationRecord record = restorationRecordRepository.findByRestorationId(restorationId)
                .orElseThrow(() -> new RuntimeException("Record not found"));

        // 删除相关任务
        Optional<RestorationTask> task = restorationTaskRepository.findByRestorationId(restorationId);
        task.ifPresent(restorationTaskRepository::delete);

        // 删除文件
        try {
            if (record.getOriginalImageUrl() != null) {
                Files.deleteIfExists(Paths.get(uploadPath, getFileName(record.getOriginalImageUrl())));
            }
            if (record.getRestoredImageUrl() != null) {
                Files.deleteIfExists(Paths.get(uploadPath, getFileName(record.getRestoredImageUrl())));
            }
        } catch (IOException e) {
            log.warn("Failed to delete image files", e);
        }

        // 删除记录
        restorationRecordRepository.delete(record);
        evictRestorationCache();
    }

    @Override
    public byte[] downloadRestoredImage(Long restorationId) {
        RestorationRecord record = restorationRecordRepository.findByRestorationId(restorationId)
                .orElseThrow(() -> new RuntimeException("Record not found"));

        if ((record.getRestoredAbsolutePath() == null || record.getRestoredAbsolutePath().trim().isEmpty())
                && record.getRestoredImageUrl() == null) {
            throw new RuntimeException("Restored image not available");
        }

        try {
            Path filePath = resolveRestoredFilePath(record);
            if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                throw new IOException("Restored file does not exist: " + filePath);
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("Failed to read image file", e);
            throw new RuntimeException("Failed to download image");
        }
    }

    @Override
    public void batchDownload(Long userId, java.util.List<Long> restorationIds, javax.servlet.http.HttpServletResponse response) throws IOException {
        if (restorationIds == null || restorationIds.isEmpty()) {
            throw new IOException("No restoration records selected");
        }

        int entryCount = 0;
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(buffer)) {
            for (Long restorationId : restorationIds) {
                Optional<RestorationRecord> recordOpt = restorationRecordRepository.findByRestorationId(restorationId);
                if (recordOpt.isEmpty()) {
                    log.warn("Batch download skipped missing record: {}", restorationId);
                    continue;
                }

                RestorationRecord record = recordOpt.get();
                if (!record.getUserId().equals(userId)) {
                    log.warn("Batch download skipped forbidden record: {}", restorationId);
                    continue;
                }

                Path filePath = resolveRestoredFilePath(record);
                if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                    log.warn("Batch download skipped missing file for record {}: {}", restorationId, filePath);
                    continue;
                }

                String fileName = filePath.getFileName().toString();
                java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(restorationId + "_" + fileName);
                zos.putNextEntry(zipEntry);
                Files.copy(filePath, zos);
                zos.closeEntry();
                entryCount++;
            }
            zos.finish();
        }

        if (entryCount == 0) {
            throw new IOException("No downloadable restored images found");
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=restored_photos.zip");
        response.setContentLength(buffer.size());
        response.getOutputStream().write(buffer.toByteArray());
        response.getOutputStream().flush();
    }

    @Override
    public Integer getRestorationProgress(Long taskId) {
        Optional<RestorationTask> task = restorationTaskRepository.findByTaskId(taskId);
        return task.map(RestorationTask::getProgress).orElse(0);
    }

    @Override
    public List<RestorationTask> getPendingTasks() {
        return restorationTaskRepository.findPendingTasks(0);
    }

    @Override
    public Long getSuccessfulRestorationsCount(Long userId) {
        return restorationRecordRepository.countSuccessfulRecords(userId);
    }

    private String generateFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        String extension = originalFileName != null && originalFileName.contains(".")
                ? originalFileName.substring(originalFileName.lastIndexOf("."))
                : ".jpg";
        return UUID.randomUUID() + extension;
    }

    private String generateMaskFileName(String sourceFileName) {
        String baseName = sourceFileName;
        int dotIndex = sourceFileName.lastIndexOf(".");
        if (dotIndex > 0) {
            baseName = sourceFileName.substring(0, dotIndex);
        }
        return baseName + "_mask.png";
    }

    private String getFileName(String fileUrl) {
        if (fileUrl == null) return "";
        try {
            if (fileUrl.contains("path=")) {
                String encodedPath = fileUrl.substring(fileUrl.indexOf("path=") + 5);
                String decodedPath = java.net.URLDecoder.decode(encodedPath, "UTF-8");
                return new java.io.File(decodedPath).getName();
            }
        } catch (Exception e) {
            log.warn("无法解析文件URL: " + fileUrl);
        }
        return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
    }

    private Path resolveRestoredFilePath(RestorationRecord record) {
        String absolutePath = record.getRestoredAbsolutePath();
        if (absolutePath != null && !absolutePath.trim().isEmpty()) {
            Path path = Paths.get(absolutePath);
            if (Files.exists(path)) {
                return path;
            }
        }

        absolutePath = getAbsoluteFromUrl(record.getRestoredImageUrl());
        if (absolutePath != null && !absolutePath.trim().isEmpty()) {
            Path path = Paths.get(absolutePath);
            if (Files.exists(path)) {
                return path;
            }
        }

        String fileName = getFileName(record.getRestoredImageUrl());
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }

        Path uploadFile = Paths.get(uploadPath, fileName);
        if (Files.exists(uploadFile)) {
            return uploadFile;
        }

        if (esrganResultsDir != null && !esrganResultsDir.trim().isEmpty()) {
            Path esrganFile = Paths.get(esrganResultsDir, fileName);
            if (Files.exists(esrganFile)) {
                return esrganFile;
            }
        }

        Path gfpganFile = Paths.get("../../GFPGAN/results/restored_imgs", fileName).toAbsolutePath().normalize();
        if (Files.exists(gfpganFile)) {
            return gfpganFile;
        }

        return uploadFile;
    }

    private String getAbsoluteFromUrl(String fileUrl) {
        if (fileUrl == null) return "";
        try {
            if (fileUrl.contains("path=")) {
                String encodedPath = fileUrl.substring(fileUrl.indexOf("path=") + 5);
                if (encodedPath.contains("&")) {
                   encodedPath = encodedPath.split("&")[0];
                }
                return java.net.URLDecoder.decode(encodedPath, "UTF-8").replace("\\", "/");
            }
        } catch (Exception e) {
            log.warn("无法解析绝对路径: " + fileUrl);
        }
        return "";
    }

    private void evictRestorationCache() {
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache("restorationRecords");
        if (cache != null) {
            cache.clear();
        }
    }
}
