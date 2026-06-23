package com.photorestoration.repository;

import com.photorestoration.entity.Photo;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PhotoRepository {
    @Select("SELECT * FROM photos WHERE user_id = #{userId}")
    List<Photo> findByUserId(Long userId);

    @Select("SELECT * FROM photos WHERE user_id = #{userId} AND deleted_at IS NULL")
    List<Photo> findByUserIdAndDeletedAtIsNull(Long userId);

    @Select("SELECT * FROM photos WHERE user_id = #{userId} AND group_name = #{groupName}")
    List<Photo> findByUserIdAndGroupName(@Param("userId") Long userId, @Param("groupName") String groupName);

    @Select("SELECT * FROM photos WHERE user_id = #{userId} AND group_name = #{groupName} AND deleted_at IS NULL")
    List<Photo> findByUserIdAndGroupNameAndDeletedAtIsNull(@Param("userId") Long userId, @Param("groupName") String groupName);

    @Select("SELECT * FROM photos WHERE user_id = #{userId} AND deleted_at IS NOT NULL")
    List<Photo> findByUserIdAndDeletedAtIsNotNull(Long userId);

    @Select("SELECT * FROM photos WHERE user_id = #{userId} AND absolute_path = #{absolutePath} LIMIT 1")
    Optional<Photo> findByUserIdAndAbsolutePath(@Param("userId") Long userId, @Param("absolutePath") String absolutePath);

    @Insert("INSERT INTO photos (user_id, group_name, file_name, absolute_path, file_size, upload_time, is_classified, deleted_at) " +
            "VALUES (#{userId}, #{groupName}, #{fileName}, #{absolutePath}, #{fileSize}, #{uploadTime}, #{isClassified}, #{deletedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Photo photo);

    @Update("UPDATE photos SET user_id=#{userId}, group_name=#{groupName}, file_name=#{fileName}, absolute_path=#{absolutePath}, " +
            "file_size=#{fileSize}, upload_time=#{uploadTime}, is_classified=#{isClassified}, deleted_at=#{deletedAt} WHERE id=#{id}")
    int update(Photo photo);

    @Update("UPDATE photos SET group_name = #{newGroup} WHERE user_id = #{userId} AND absolute_path = #{path}")
    int updatePhotoGroup(@Param("userId") Long userId, @Param("path") String path, @Param("newGroup") String newGroup);

    @Delete("DELETE FROM photos WHERE user_id = #{userId} AND absolute_path = #{path}")
    int deleteByUserIdAndAbsolutePath(@Param("userId") Long userId, @Param("path") String path);

    @Delete("DELETE FROM photos WHERE user_id = #{userId} AND group_name = #{groupName}")
    int deleteByUserIdAndGroupName(@Param("userId") Long userId, @Param("groupName") String groupName);

    @Delete("DELETE FROM photos WHERE id = #{id}")
    int deleteById(Long id);

    default Photo save(Photo photo) {
        if (photo.getId() == null) {
            insert(photo);
        } else {
            update(photo);
        }
        return photo;
    }

    default void delete(Photo photo) {
        if (photo != null && photo.getId() != null) {
            deleteById(photo.getId());
        }
    }
}
