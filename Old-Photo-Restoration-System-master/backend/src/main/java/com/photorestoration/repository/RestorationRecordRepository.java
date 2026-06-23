package com.photorestoration.repository;

import com.photorestoration.entity.RestorationRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface RestorationRecordRepository {
    @Select("SELECT * FROM restoration_records WHERE restoration_id = #{restorationId} LIMIT 1")
    Optional<RestorationRecord> findByRestorationId(Long restorationId);

    @Select("SELECT * FROM restoration_records WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<RestorationRecord> findByUserIdPaged(@Param("userId") Long userId, @Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT COUNT(1) FROM restoration_records WHERE user_id = #{userId}")
    long countByUserId(Long userId);

    @Select("SELECT * FROM restoration_records WHERE user_id = #{userId} AND status = #{status} ORDER BY create_time DESC")
    List<RestorationRecord> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);

    @Select("SELECT * FROM restoration_records WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<RestorationRecord> findLatestRecords(Long userId);

    @Select("SELECT COUNT(1) FROM restoration_records WHERE user_id = #{userId} AND status = 2")
    Long countSuccessfulRecords(Long userId);

    @Select("SELECT * FROM restoration_records WHERE create_time BETWEEN #{startTime} AND #{endTime}")
    List<RestorationRecord> findByCreateTimeBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM restoration_records WHERE status = #{status}")
    List<RestorationRecord> findByStatus(Integer status);

    @Insert("INSERT INTO restoration_records (restoration_id, user_id, original_image_url, restored_image_url, original_absolute_path, restored_absolute_path, " +
            "mask_image_url, mask_absolute_path, damage_type, restoration_mode, quality_score, file_size, restoration_time, status, error_message, create_time, update_time, thumbnail_url, remarks) " +
            "VALUES (#{restorationId}, #{userId}, #{originalImageUrl}, #{restoredImageUrl}, #{originalAbsolutePath}, #{restoredAbsolutePath}, " +
            "#{maskImageUrl}, #{maskAbsolutePath}, #{damageType}, #{restorationMode}, #{qualityScore}, #{fileSize}, #{restorationTime}, #{status}, #{errorMessage}, #{createTime}, #{updateTime}, #{thumbnailUrl}, #{remarks})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RestorationRecord record);

    @Update("UPDATE restoration_records SET restoration_id=#{restorationId}, user_id=#{userId}, original_image_url=#{originalImageUrl}, restored_image_url=#{restoredImageUrl}, " +
            "original_absolute_path=#{originalAbsolutePath}, restored_absolute_path=#{restoredAbsolutePath}, mask_image_url=#{maskImageUrl}, mask_absolute_path=#{maskAbsolutePath}, " +
            "damage_type=#{damageType}, restoration_mode=#{restorationMode}, quality_score=#{qualityScore}, file_size=#{fileSize}, restoration_time=#{restorationTime}, " +
            "status=#{status}, error_message=#{errorMessage}, create_time=#{createTime}, update_time=#{updateTime}, thumbnail_url=#{thumbnailUrl}, remarks=#{remarks} WHERE id=#{id}")
    int update(RestorationRecord record);

    @Delete("DELETE FROM restoration_records WHERE id = #{id}")
    int deleteById(Long id);

    default RestorationRecord save(RestorationRecord record) {
        if (record.getId() == null) {
            insert(record);
        } else {
            update(record);
        }
        return record;
    }

    default void delete(RestorationRecord record) {
        if (record != null && record.getId() != null) {
            deleteById(record.getId());
        }
    }
}
