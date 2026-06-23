package com.photorestoration.service.impl;

import com.photorestoration.entity.Photo;
import com.photorestoration.repository.PhotoRepository;
import com.photorestoration.service.AlbumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumServiceImpl implements AlbumService {

	private final PhotoRepository photoRepository;

	@Value("${file.upload.path}")
	private String basePathStr;

	@Value("${ai.esrgan.results-dir:}")
	private String esrganResultsDir;

	@Value("${ai.classifier.base-url:http://127.0.0.1:5000}")
	private String classifierBaseUrl;

	@Value("${ai.classifier.generate-path:/generate}")
	private String classifierGeneratePath;

	private File getUserBasePath(Long userId) {
		File baseDir = new File(basePathStr, "user_" + userId).getAbsoluteFile();
		if (!baseDir.exists()) {
			baseDir.mkdirs();
		}
		return baseDir;
	}

	@Override
	public Map<String, List<Map<String, String>>> getAlbumGroups(Long userId) {
		Map<String, List<Map<String, String>>> albumMap = new LinkedHashMap<>();
		File userBaseDir = getUserBasePath(userId);
		File[] groupDirs = userBaseDir.listFiles(File::isDirectory);

		if (groupDirs != null) {
			for (File dir : groupDirs) {
				albumMap.put(dir.getName(), new ArrayList<>());
			}
		}

		albumMap.putIfAbsent("未分类", new ArrayList<>());

		List<Photo> userPhotos = photoRepository.findByUserIdAndDeletedAtIsNull(userId);
		for (Photo photo : userPhotos) {
			String group = photo.getGroupName();
			albumMap.putIfAbsent(group, new ArrayList<>());

			Map<String, String> photoInfo = new HashMap<>();
			photoInfo.put("fileName", photo.getFileName());
			photoInfo.put("absolutePath", photo.getAbsolutePath());
			albumMap.get(group).add(photoInfo);
		}

		return albumMap;
	}

	@Override
	public byte[] getImage(String absolutePath) {
		if (absolutePath == null || absolutePath.trim().isEmpty()) {
			return new byte[0];
		}
		try {
			return Files.readAllBytes(new File(absolutePath).toPath());
		} catch (IOException e) {
			log.error("Read image failed: {}", absolutePath, e);
			return new byte[0];
		}
	}

	@Override
	@Transactional
	public Map<String, Integer> classifyImages(Long userId) {
		List<Photo> unclassifiedPhotos = photoRepository.findByUserIdAndGroupNameAndDeletedAtIsNull(userId, "未分类");
		int classifiedCount = 0;
		File userBaseDir = getUserBasePath(userId);

		for (Photo photo : unclassifiedPhotos) {
			String group;
			try {
				group = classifyByAi(photo.getAbsolutePath());
			} catch (Exception e) {
				log.warn("AI分类调用失败，使用兜底分组。path={}", photo.getAbsolutePath(), e);
				group = "智能分类";
			}

			File groupDir = new File(userBaseDir, group);
			if (!groupDir.exists()) {
				groupDir.mkdirs();
			}

			File source = new File(photo.getAbsolutePath());
			File target = new File(groupDir, source.getName());
			try {
				if (!source.getAbsolutePath().equals(target.getAbsolutePath())) {
					Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
				photo.setGroupName(group);
				photo.setAbsolutePath(target.getAbsolutePath().replace("\\", "/"));
				photo.setIsClassified(true);
				photoRepository.save(photo);
				classifiedCount++;
			} catch (Exception e) {
				log.error("Move classified image failed: {}", photo.getAbsolutePath(), e);
			}
		}

		return Collections.singletonMap("classifiedCount", classifiedCount);
	}

	private String classifyByAi(String imagePath) {
		if (imagePath == null || imagePath.trim().isEmpty()) {
			return "未分类";
		}

		File imageFile = new File(imagePath);
		if (!imageFile.exists()) {
			throw new RuntimeException("分类图片不存在: " + imagePath);
		}

		String endpoint = trimTrailingSlash(classifierBaseUrl) + ensureLeadingSlash(classifierGeneratePath);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		Map<String, Object> body = new HashMap<>();
		body.put("image_path", imageFile.getAbsolutePath().replace("\\", "/"));

		ResponseEntity<Map> response = new RestTemplate().postForEntity(endpoint, new HttpEntity<>(body, headers), Map.class);
		Map responseBody = response.getBody();
		if (responseBody == null) {
			throw new RuntimeException("分类服务返回空响应");
		}

		Object statusObj = responseBody.get("status");
		if (statusObj != null && "error".equalsIgnoreCase(String.valueOf(statusObj))) {
			String errorMessage = firstNonBlank(
					responseBody.get("error"),
					responseBody.get("message"),
					responseBody.get("msg")
			);
			throw new RuntimeException("分类服务执行失败: " + errorMessage);
		}

		Object resultObj = firstNonNull(
				responseBody.get("result"),
				responseBody.get("label"),
				responseBody.get("category"),
				responseBody.get("class"),
				responseBody.get("prediction"),
				responseBody.get("data")
		);
		String resultText = extractResultText(resultObj);
		if (resultText.isEmpty()) {
			throw new RuntimeException("分类服务未返回有效分类结果: " + responseBody);
		}
		log.info("AI分类成功: path={}, result={}", imagePath, resultText);
		return normalizeGroupName(resultText);
	}

	private Object firstNonNull(Object... candidates) {
		for (Object candidate : candidates) {
			if (candidate != null) {
				return candidate;
			}
		}
		return null;
	}

	private String firstNonBlank(Object... candidates) {
		for (Object candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			String text = String.valueOf(candidate).trim();
			if (!text.isEmpty()) {
				return text;
			}
		}
		return "未知错误";
	}

	private String extractResultText(Object resultObj) {
		if (resultObj == null) {
			return "";
		}
		if (resultObj instanceof String) {
			return ((String) resultObj).trim();
		}
		if (resultObj instanceof List) {
			List<?> resultList = (List<?>) resultObj;
			if (!resultList.isEmpty() && resultList.get(0) != null) {
				return String.valueOf(resultList.get(0)).trim();
			}
		}
		return String.valueOf(resultObj).trim();
	}

	private String normalizeGroupName(String aiResult) {
		if (aiResult == null || aiResult.trim().isEmpty()) {
			return "未分类";
		}

		String text = aiResult.trim();
		String lower = text.toLowerCase();
		if (containsAny(lower, "动物", "宠物", "猫", "狗", "animal", "pet", "cat", "dog", "bird")) {
			return "动物";
		}
		if (containsAny(lower, "风景", "自然", "山", "海", "河", "湖", "landscape", "nature", "scenery")) {
			return "风景";
		}
		if (containsAny(lower, "建筑", "房子", "城市", "building", "architecture", "city")) {
			return "建筑";
		}
		if (containsAny(lower, "交通", "汽车", "火车", "飞机", "船", "vehicle", "car", "train", "plane", "ship")) {
			return "交通工具";
		}
		if (containsAny(lower, "美食", "食物", "餐", "food", "meal", "dining")) {
			return "美食";
		}
		if (containsAny(lower, "儿童", "孩子", "小孩", "baby", "child", "kid")) {
			return "儿童";
		}
		if (containsAny(lower, "证件", "证件照", "id photo", "passport")) {
			return "证件照";
		}
		if (containsAny(lower, "合影", "全家福", "多人", "group", "family")) {
			return "合影";
		}
		if (containsAny(lower, "活动", "婚礼", "聚会", "运动", "event", "party", "wedding", "sport")) {
			return "活动";
		}
		if (containsAny(lower, "人物", "人像", "人脸", "portrait", "person", "people", "face", "man", "woman")) {
			return "人物";
		}

		if (isAllowedGroup(text)) {
			return text;
		}
		return "未分类";
	}

	private boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private boolean isAllowedGroup(String groupName) {
		if (groupName == null) {
			return false;
		}
		String group = groupName.trim();
		return "人物".equals(group)
				|| "动物".equals(group)
				|| "风景".equals(group)
				|| "建筑".equals(group)
				|| "交通工具".equals(group)
				|| "美食".equals(group)
				|| "合影".equals(group)
				|| "儿童".equals(group)
				|| "证件照".equals(group)
				|| "活动".equals(group)
				|| "未分类".equals(group);
	}

	private boolean isSmartClassifyGroup(String groupName) {
		if (groupName == null) {
			return false;
		}
		String group = groupName.trim();
		return group.isEmpty()
				|| "智能分类".equals(group)
				|| "自动分类".equals(group)
				|| "AI分类".equalsIgnoreCase(group)
				|| "auto".equalsIgnoreCase(group);
	}

	@Override
	@Transactional
	public String processImage(Long userId, String absolutePath, String mode) {
		try {
			File srcFile = new File(absolutePath);
			if (!srcFile.exists()) {
				log.error("Source file does not exist: {}", absolutePath);
				return null;
			}

			String ext = srcFile.getName().contains(".")
					? srcFile.getName().substring(srcFile.getName().lastIndexOf('.'))
					: ".jpg";
			String fileName = srcFile.getName().replace(ext, "_" + mode + "_" + UUID.randomUUID().toString().substring(0, 8) + ext);
			File destFile = new File(srcFile.getParentFile(), fileName);
			Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

			String newAbsolutePath = destFile.getAbsolutePath().replace("\\", "/");
			Optional<Photo> originalPhoto = photoRepository.findByUserIdAndAbsolutePath(userId, absolutePath);
			String groupName = originalPhoto.map(Photo::getGroupName).orElse("未分类");

			Photo newPhoto = Photo.builder()
					.userId(userId)
					.groupName(groupName)
					.fileName(destFile.getName())
					.absolutePath(newAbsolutePath)
					.fileSize(destFile.length())
					.uploadTime(LocalDateTime.now())
					.isClassified(!"未分类".equals(groupName))
					.build();
			photoRepository.save(newPhoto);

			return newAbsolutePath;
		} catch (Exception e) {
			log.error("Process image failed: userId={}, path={}, mode={}", userId, absolutePath, mode, e);
			return null;
		}
	}

	@Override
	@Transactional
	public boolean saveToGroup(Long userId, String absolutePath, String groupName) {
		try {
			String targetGroup = groupName == null ? "" : groupName.trim();
			if (isSmartClassifyGroup(targetGroup)) {
				try {
					targetGroup = classifyByAi(absolutePath);
				} catch (Exception e) {
					log.warn("AI classify failed when saving to album, fallback to 未分类. path={}", absolutePath, e);
					targetGroup = "未分类";
				}
			} else {
				targetGroup = normalizeGroupName(targetGroup);
			}

			File userBaseDir = getUserBasePath(userId);
			File groupDir = new File(userBaseDir, targetGroup);
			if (!groupDir.exists()) {
				groupDir.mkdirs();
			}

			File destFile;
			if (isHttpUrl(absolutePath)) {
				destFile = downloadRemoteImageToGroup(absolutePath, groupDir);
				if (destFile == null || !destFile.exists()) {
					return false;
				}
			} else {
				File srcFile = new File(absolutePath);
				if (!srcFile.exists()) {
					log.error("Source file does not exist: {}", absolutePath);
					return false;
				}
				destFile = new File(groupDir, srcFile.getName());
				if (!srcFile.getAbsolutePath().equals(destFile.getAbsolutePath())) {
					Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			}

			String newAbsolutePath = destFile.getAbsolutePath().replace("\\", "/");
			Optional<Photo> photoOpt = photoRepository.findByUserIdAndAbsolutePath(userId, absolutePath);
			if (photoOpt.isPresent()) {
				Photo photo = photoOpt.get();
				photo.setAbsolutePath(newAbsolutePath);
				photo.setGroupName(targetGroup);
				photo.setIsClassified(!"未分类".equals(targetGroup));
				photoRepository.save(photo);
			} else {
				Photo newPhoto = Photo.builder()
						.userId(userId)
						.groupName(targetGroup)
						.fileName(destFile.getName())
						.absolutePath(newAbsolutePath)
						.fileSize(destFile.length())
						.uploadTime(LocalDateTime.now())
						.isClassified(!"未分类".equals(targetGroup))
						.build();
				photoRepository.save(newPhoto);
			}
			return true;
		} catch (Exception e) {
			log.error("Save to group failed: userId={}, path={}, group={}", userId, absolutePath, groupName, e);
			return false;
		}
	}

	private boolean isHttpUrl(String path) {
		if (path == null) {
			return false;
		}
		String normalized = path.trim().toLowerCase();
		return normalized.startsWith("http://") || normalized.startsWith("https://");
	}

	private File downloadRemoteImageToGroup(String imageUrl, File groupDir) {
		try {
			byte[] body = new RestTemplate().getForEntity(imageUrl, byte[].class).getBody();
			if (body == null || body.length == 0) {
				return null;
			}
			File destFile = new File(groupDir, extractFileNameFromUrl(imageUrl));
			Files.write(destFile.toPath(), body);
			return destFile;
		} catch (Exception e) {
			log.error("Download remote image failed: {}", imageUrl, e);
			return null;
		}
	}

	private String extractFileNameFromUrl(String imageUrl) {
		try {
			java.net.URI uri = java.net.URI.create(imageUrl);
			String path = uri.getPath();
			if (path != null && !path.isEmpty()) {
				String name = java.net.URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), "UTF-8");
				if (!name.isEmpty()) {
					return name.contains(".") ? name : name + ".jpg";
				}
			}
		} catch (Exception ignored) {
		}
		return "saved_" + System.currentTimeMillis() + ".jpg";
	}

	@Override
	public boolean addGroup(Long userId, String groupName) {
		if (groupName == null || groupName.trim().isEmpty()) {
			return false;
		}
		File groupDir = new File(getUserBasePath(userId), groupName.trim());
		return groupDir.exists() || groupDir.mkdirs();
	}

	@Override
	@Transactional
	public boolean deleteGroup(Long userId, String groupName) {
		if (groupName == null || groupName.trim().isEmpty()) {
			return false;
		}
		String normalizedGroup = groupName.trim();
		if ("未分类".equals(normalizedGroup)) {
			return false;
		}

		try {
			List<Photo> photos = photoRepository.findByUserIdAndGroupName(userId, normalizedGroup);
			for (Photo photo : photos) {
				try {
					if (photo.getAbsolutePath() != null && !photo.getAbsolutePath().trim().isEmpty()) {
						Files.deleteIfExists(new File(photo.getAbsolutePath()).toPath());
					}
				} catch (Exception e) {
					log.warn("Delete group file failed: {}", photo.getAbsolutePath(), e);
				}
			}

			photoRepository.deleteByUserIdAndGroupName(userId, normalizedGroup);
			deleteDirectoryRecursively(new File(getUserBasePath(userId), normalizedGroup));
			return true;
		} catch (Exception e) {
			log.error("Delete group failed. userId={}, group={}", userId, normalizedGroup, e);
			return false;
		}
	}

	private void deleteDirectoryRecursively(File dir) {
		if (dir == null || !dir.exists()) {
			return;
		}
		File[] children = dir.listFiles();
		if (children != null) {
			for (File child : children) {
				if (child.isDirectory()) {
					deleteDirectoryRecursively(child);
				} else {
					try {
						Files.deleteIfExists(child.toPath());
					} catch (Exception e) {
						log.warn("Delete file failed: {}", child.getAbsolutePath(), e);
					}
				}
			}
		}
		try {
			Files.deleteIfExists(dir.toPath());
		} catch (Exception e) {
			log.warn("Delete dir failed: {}", dir.getAbsolutePath(), e);
		}
	}

	@Override
	@Transactional
	public boolean deleteImage(Long userId, String absolutePath) {
		if (absolutePath == null || absolutePath.trim().isEmpty()) {
			return false;
		}
		Optional<Photo> photoOpt = photoRepository.findByUserIdAndAbsolutePath(userId, absolutePath);
		if (photoOpt.isPresent()) {
			Photo photo = photoOpt.get();
			photo.setDeletedAt(LocalDateTime.now());
			photoRepository.save(photo);
			return true;
		}
		return false;
	}

	@Override
	public List<Map<String, String>> getTrashImages(Long userId) {
		List<Photo> trashPhotos = photoRepository.findByUserIdAndDeletedAtIsNotNull(userId);
		List<Map<String, String>> result = new ArrayList<>();
		for (Photo photo : trashPhotos) {
			Map<String, String> photoInfo = new HashMap<>();
			photoInfo.put("fileName", photo.getFileName());
			photoInfo.put("name", photo.getFileName());
			photoInfo.put("absolutePath", photo.getAbsolutePath());
			photoInfo.put("deletedAt", photo.getDeletedAt() == null ? "" : photo.getDeletedAt().toString());
			result.add(photoInfo);
		}
		return result;
	}

	@Override
	@Transactional
	public boolean restoreImage(Long userId, String absolutePath) {
		if (absolutePath == null || absolutePath.trim().isEmpty()) {
			return false;
		}
		Optional<Photo> photoOpt = photoRepository.findByUserIdAndAbsolutePath(userId, absolutePath);
		if (photoOpt.isEmpty()) {
			return false;
		}
		Photo photo = photoOpt.get();
		photo.setDeletedAt(null);
		photoRepository.save(photo);
		return true;
	}

	@Override
	@Transactional
	public boolean hardDeleteImage(Long userId, String absolutePath) {
		if (absolutePath == null || absolutePath.trim().isEmpty()) {
			return false;
		}
		Optional<Photo> photoOpt = photoRepository.findByUserIdAndAbsolutePath(userId, absolutePath);
		if (photoOpt.isEmpty()) {
			return false;
		}
		photoRepository.delete(photoOpt.get());
		try {
			Files.deleteIfExists(new File(absolutePath).toPath());
		} catch (Exception e) {
			log.warn("Delete trash file failed: {}", absolutePath, e);
		}
		return true;
	}

	private String trimTrailingSlash(String value) {
		if (value == null) {
			return "";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private String ensureLeadingSlash(String value) {
		if (value == null || value.isEmpty()) {
			return "/generate";
		}
		return value.startsWith("/") ? value : "/" + value;
	}
}
