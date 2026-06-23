package com.photorestoration.repository;

import com.photorestoration.entity.RestorationTask;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RestorationTaskRepository {
    @Select("SELECT * FROM restoration_tasks WHERE task_id = #{taskId} LIMIT 1")
    Optional<RestorationTask> findByTaskId(Long taskId);

    @Select("SELECT * FROM restoration_tasks WHERE user_id = #{userId} AND status = #{status}")
    List<RestorationTask> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);

    @Select("SELECT * FROM restoration_tasks WHERE status = #{status} ORDER BY priority DESC, create_time ASC")
    List<RestorationTask> findPendingTasks(Integer status);

    @Select("SELECT COUNT(1) FROM restoration_tasks WHERE user_id = #{userId} AND status = 1")
    Long countUserProcessingTasks(Long userId);

    @Select("SELECT * FROM restoration_tasks WHERE restoration_id = #{restorationId} LIMIT 1")
    Optional<RestorationTask> findByRestorationId(Long restorationId);

    @Insert("INSERT INTO restoration_tasks (task_id, user_id, restoration_id, status, progress, priority, error_message, retry_count, max_retries, create_time, update_time, start_time, end_time, execution_time) " +
            "VALUES (#{taskId}, #{userId}, #{restorationId}, #{status}, #{progress}, #{priority}, #{errorMessage}, #{retryCount}, #{maxRetries}, #{createTime}, #{updateTime}, #{startTime}, #{endTime}, #{executionTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RestorationTask task);

    @Update("UPDATE restoration_tasks SET task_id=#{taskId}, user_id=#{userId}, restoration_id=#{restorationId}, status=#{status}, progress=#{progress}, priority=#{priority}, " +
            "error_message=#{errorMessage}, retry_count=#{retryCount}, max_retries=#{maxRetries}, create_time=#{createTime}, update_time=#{updateTime}, " +
            "start_time=#{startTime}, end_time=#{endTime}, execution_time=#{executionTime} WHERE id=#{id}")
    int update(RestorationTask task);

    @Delete("DELETE FROM restoration_tasks WHERE id = #{id}")
    int deleteById(Long id);

    default RestorationTask save(RestorationTask task) {
        if (task.getId() == null) {
            insert(task);
        } else {
            update(task);
        }
        return task;
    }

    default void delete(RestorationTask task) {
        if (task != null && task.getId() != null) {
            deleteById(task.getId());
        }
    }
}
