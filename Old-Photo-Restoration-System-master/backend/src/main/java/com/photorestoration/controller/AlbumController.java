package com.photorestoration.controller;

import com.photorestoration.dto.ApiResponse;
import com.photorestoration.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/album")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;
    private final com.photorestoration.service.UserService userService;

    private Long getUserId(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return userService.getUserIdFromToken(token.substring(7));
        }
        return null; // Handle auth properly based on system design
    }

    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, String>>>>> getGroups(@RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "Unauthorized"));
        return ResponseEntity.ok(ApiResponse.success(albumService.getAlbumGroups(userId)));
    }

    @GetMapping("/image")
    public ResponseEntity<byte[]> getImageByAbsolutePath(@RequestParam String path) {
        byte[] data = albumService.getImage(path);
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        // 简单处理，如果是png则返回png类型，否则默认jpeg
        if (path.toLowerCase().endsWith(".png")) {
            headers.setContentType(MediaType.IMAGE_PNG);
        } else {
            headers.setContentType(MediaType.IMAGE_JPEG);
        }
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    @PostMapping("/classify")
    public ApiResponse<Map<String, Integer>> classify(@RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        if (userId == null) return ApiResponse.error(401, "Unauthorized");
        return ApiResponse.success(albumService.classifyImages(userId));
    }

    @PostMapping("/process")
    public ApiResponse<String> processImage(@RequestHeader("Authorization") String token, @RequestBody Map<String, String> request) {
        Long userId = getUserId(token);
        if (userId == null) return ApiResponse.error(401, "Unauthorized");
        
        String path = request.get("path");
        String mode = request.get("mode");
        if (path == null || mode == null) {
            return ApiResponse.error(400, "Missing path or mode");
        }
        String newPath = albumService.processImage(userId, path, mode);
        if (newPath == null) {
            return ApiResponse.error(500, "Processing failed");
        }
        return ApiResponse.success(newPath);
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<String>> saveImage(@RequestHeader("Authorization") String token, @RequestBody Map<String, String> request) {
        Long userId = getUserId(token);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "Unauthorized"));

        String path = request.get("path");
        String group = request.get("group");
        if (path == null || group == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Missing path or group"));
        }
        boolean success = albumService.saveToGroup(userId, path, group);
        if (!success) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(500, "Save failed"));
        }
        return ResponseEntity.ok(ApiResponse.success("Saved successfully"));
    }

    @PostMapping("/group/add")
    public ApiResponse<String> addGroup(@RequestHeader("Authorization") String token, @RequestBody Map<String, String> request) {
        Long userId = getUserId(token);
        if (userId == null) return ApiResponse.error(401, "Unauthorized");

        String name = request.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ApiResponse.error(400, "Missing group name");
        }
        boolean success = albumService.addGroup(userId, name);
        if (!success) {
            return ApiResponse.error(500, "Add group failed or group exists");
        }
        return ApiResponse.success("Group created");
    }

    @DeleteMapping("/group")
    public ApiResponse<String> deleteGroup(@RequestHeader("Authorization") String token, @RequestParam String name) {
        Long userId = getUserId(token);
        if (userId == null) return ApiResponse.error(401, "Unauthorized");

        if (name == null || name.trim().isEmpty()) {
            return ApiResponse.error(400, "Missing group name");
        }
        boolean success = albumService.deleteGroup(userId, name);
        if (!success) {
            return ApiResponse.error(500, "Delete group failed");
        }
        return ApiResponse.success("Group deleted");
    }

    @DeleteMapping("/image")
    public ApiResponse<String> deleteImage(@RequestHeader("Authorization") String token, @RequestParam String path) {
        Long userId = getUserId(token);
        if (userId == null) return ApiResponse.error(401, "Unauthorized");

        if (path == null || path.trim().isEmpty()) {
            return ApiResponse.error(400, "Missing image path");
        }
        boolean success = albumService.deleteImage(userId, path);
        if (!success) {
            return ApiResponse.error(500, "Delete image failed");
        }
        return ApiResponse.success("Image moved to trash");
    }

    @GetMapping("/trash")
    public ApiResponse<List<Map<String, String>>> getTrash(@RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        if (userId == null) return ApiResponse.error(401, "Unauthorized");
        return ApiResponse.success(albumService.getTrashImages(userId));
    }

    @PostMapping("/trash/restore")
    public ApiResponse<String> restoreImage(@RequestHeader("Authorization") String token, @RequestBody Map<String, String> request) {
        Long userId = getUserId(token);
        if (userId == null) return ApiResponse.error(401, "Unauthorized");
        String path = request.get("path");
        if (path == null || path.trim().isEmpty()) return ApiResponse.error(400, "Missing path");
        if (albumService.restoreImage(userId, path)) {
            return ApiResponse.success("Image restored");
        }
        return ApiResponse.error(500, "Restore failed");
    }

    @DeleteMapping("/trash/hard-delete")
    public ApiResponse<String> hardDeleteImage(@RequestHeader("Authorization") String token, @RequestParam String path) {
        Long userId = getUserId(token);
        if (userId == null) return ApiResponse.error(401, "Unauthorized");
        if (path == null || path.trim().isEmpty()) return ApiResponse.error(400, "Missing path");
        if (albumService.hardDeleteImage(userId, path)) {
            return ApiResponse.success("Image permanently deleted");
        }
        return ApiResponse.error(500, "Delete failed");
    }
}
