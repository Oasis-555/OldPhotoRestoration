package com.photorestoration.service;

import java.util.List;
import java.util.Map;

/**
 * 个人相册与分类服务接口
 */
public interface AlbumService {

    /**
     * 获取所有带有分组的相片绝对路径
     * @param userId 所属用户ID
     * @return Map key: 分组名, value: 该组下的图片绝对路径列表
     */
    Map<String, List<Map<String, String>>> getAlbumGroups(Long userId);

    /**
     * 读取图片文件为字节数组
     * @param absolutePath 图片绝对路径
     * @return 字节数组
     */
    byte[] getImage(String absolutePath);

    /**
     * 对未分类图片进行一键分类（模拟）
     * @param userId 所属用户ID
     * @return 分类统计信息
     */
    Map<String, Integer> classifyImages(Long userId);

    /**
     * 对图片进行处理（超分、人脸修复、图像修复等）
     * @param userId 所属用户ID
     * @param absolutePath 图片绝对路径
     * @param mode 处理模式 (sr, face, inpaint, backup)
     * @return 处理后图片的新绝对路径
     */
    String processImage(Long userId, String absolutePath, String mode);

    /**
     * 保存处理后的图片到指定分组
     * @param userId 所属用户ID
     * @param absolutePath 现在的绝对路径
     * @param groupName 目标分组名
     * @return 是否成功
     */
    boolean saveToGroup(Long userId, String absolutePath, String groupName);

    /**
     * 创建一个新分组
     * @param userId 所属用户ID
     * @param groupName 分组名
     * @return 是否成功
     */
    boolean addGroup(Long userId, String groupName);

    /**
     * 删除一个分组
     * @param userId 所属用户ID
     * @param groupName 分组名
     * @return 是否成功
     */
    boolean deleteGroup(Long userId, String groupName);

    /**
     * 删除一张相片
     * @param userId 所属用户ID
     * @param absolutePath 相片绝对路径
     * @return 是否成功
     */
    boolean deleteImage(Long userId, String absolutePath);

    List<Map<String, String>> getTrashImages(Long userId);

    boolean restoreImage(Long userId, String absolutePath);

    boolean hardDeleteImage(Long userId, String absolutePath);
}
