package com.photorestoration.service;

import com.photorestoration.entity.RestorationRecord;
import com.photorestoration.entity.RestorationTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * 照片修复服务接口
 */
public interface RestorationService {

    /**
     * 上传照片并创建修复任务
     */
    RestorationRecord uploadAndCreateTask(Long userId, MultipartFile file, MultipartFile maskFile, String restorationMode);

    /**
     * 获取修复记录（分页）
     */
    Page<RestorationRecord> getRestorationRecords(Long userId, Pageable pageable);

    /**
     * 获取单个修复记录
     */
    Optional<RestorationRecord> getRestorationRecord(Long restorationId);

    /**
     * 获取用户的修复历史
     */
    List<RestorationRecord> getUserRestorationHistory(Long userId, int limit);

    /**
     * 执行修复任务（调用AI模型）
     */
    void processRestorationTask(RestorationTask task);

    /**
     * 删除修复记录
     */
    void deleteRestorationRecord(Long restorationId);

    /**
     * 下载修复后的图像
     */
    byte[] downloadRestoredImage(Long restorationId);

    /**
     * 获取修复进度
     */
    Integer getRestorationProgress(Long taskId);

    /**
     * 获取待处理的任务列表
     */
    List<RestorationTask> getPendingTasks();

    /**
     * 获取用户统计信息
     */
    Long getSuccessfulRestorationsCount(Long userId);

    void batchDownload(Long userId, java.util.List<Long> restorationIds, javax.servlet.http.HttpServletResponse response) throws java.io.IOException;
}
