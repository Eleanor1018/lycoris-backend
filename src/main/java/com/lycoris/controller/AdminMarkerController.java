package com.lycoris.controller;

import com.lycoris.dto.MarkerUpdateRequest;
import com.lycoris.entity.MapMarker;
import com.lycoris.service.MapMarkerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/markers")
public class AdminMarkerController {

    private final MapMarkerService markerService;
    private static final long SECOND_FACTOR_TTL_MS = 30 * 60 * 1000L;
    @Value("${app.upload-dir}")
    private String uploadDir;

    public AdminMarkerController(MapMarkerService markerService) {
        this.markerService = markerService;
    }

    @GetMapping("/pending")
    public List<MapMarker> pendingList() {
        return markerService.listPendingReview();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable("id") Long id) {
        return markerService.findById(id)
                .<ResponseEntity<?>>map(marker -> {
                    marker.setReviewStatus("APPROVED");
                    MapMarker updated = markerService.save(marker);
                    return ResponseEntity.ok(updated);
                })
                .orElseGet(() -> ResponseEntity.status(404).body("点位不存在"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable("id") Long id) {
        return markerService.findById(id)
                .<ResponseEntity<?>>map(marker -> {
                    marker.setReviewStatus("REJECTED");
                    MapMarker updated = markerService.save(marker);
                    return ResponseEntity.ok(updated);
                })
                .orElseGet(() -> ResponseEntity.status(404).body("点位不存在"));
    }

    @GetMapping("/all")
    public ResponseEntity<?> listAll(HttpSession session) {
        ResponseEntity<?> blocked = requireSecondFactor(session);
        if (blocked != null) return blocked;
        return ResponseEntity.ok(markerService.listAll());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> adminUpdate(
            @PathVariable("id") Long id,
            @RequestBody MarkerUpdateRequest req,
            HttpSession session
    ) {
        ResponseEntity<?> blocked = requireSecondFactor(session);
        if (blocked != null) return blocked;

        return markerService.findById(id)
                .<ResponseEntity<?>>map(marker -> {
                    if (req.getCategory() != null) {
                        marker.setCategory(markerService.normalizeCategoryForWrite(req.getCategory()));
                    }
                    if (req.getTitle() != null) marker.setTitle(req.getTitle());
                    if (req.getDescription() != null) marker.setDescription(req.getDescription());
                    if (req.getIsPublic() != null) marker.setIsPublic(req.getIsPublic());
                    if (req.getOpenTimeStart() != null || req.getOpenTimeEnd() != null) {
                        markerService.applyOpenTimeWindow(marker, req.getOpenTimeStart(), req.getOpenTimeEnd());
                    }
                    if (req.getIsActive() != null) marker.setIsActive(req.getIsActive());
                    marker.setReviewStatus("APPROVED");

                    MapMarker updated = markerService.save(marker);
                    return ResponseEntity.ok(updated);
                })
                .orElseGet(() -> ResponseEntity.status(404).body("点位不存在"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> adminDelete(@PathVariable("id") Long id, HttpSession session) {
        ResponseEntity<?> blocked = requireSecondFactor(session);
        if (blocked != null) return blocked;

        return markerService.findById(id)
                .<ResponseEntity<?>>map(marker -> {
                    markerService.delete(marker);
                    return ResponseEntity.ok().build();
                })
                .orElseGet(() -> ResponseEntity.status(404).body("点位不存在"));
    }

    @PostMapping("/cleanup-missing-images")
    public ResponseEntity<?> cleanupMissingImages(HttpSession session) {
        ResponseEntity<?> blocked = requireSecondFactor(session);
        if (blocked != null) return blocked;

        int totalChecked = 0;
        int cleared = 0;

        for (MapMarker marker : markerService.listAll()) {
            String markImage = marker.getMarkImage();
            if (markImage == null || markImage.isBlank()) continue;
            if (!markImage.startsWith("/uploads/markers/")) continue;

            totalChecked++;
            String filename = markImage.substring("/uploads/markers/".length());
            if (filename.isBlank()) continue;

            Path path = Paths.get(uploadDir, "markers", filename);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                marker.setMarkImage(null);
                markerService.save(marker);
                cleared++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checked", totalChecked);
        result.put("cleared", cleared);
        result.put("message", "失效图片链接清理完成");
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<?> requireSecondFactor(HttpSession session) {
        Object ok = session.getAttribute("adminSecondVerified");
        Object at = session.getAttribute("adminSecondVerifiedAt");
        if (!(ok instanceof Boolean) || !((Boolean) ok)) {
            return ResponseEntity.status(403).body("需要二级密码");
        }
        if (at instanceof Long) {
            long elapsed = System.currentTimeMillis() - (Long) at;
            if (elapsed > SECOND_FACTOR_TTL_MS) {
                session.removeAttribute("adminSecondVerified");
                session.removeAttribute("adminSecondVerifiedAt");
                return ResponseEntity.status(403).body("二级密码已过期，请重新验证");
            }
        }
        return null;
    }
}
