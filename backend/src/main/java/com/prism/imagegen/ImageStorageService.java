package com.prism.imagegen;

import com.prism.event.EventSite;
import com.prism.media.EventMedia;
import com.prism.media.MediaService;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

@Service
public class ImageStorageService {

    private final MediaService media;
    private final ImageOptimizationService optimization;
    private final PrismProperties properties;

    public ImageStorageService(MediaService media, ImageOptimizationService optimization, PrismProperties properties) {
        this.media = media;
        this.optimization = optimization;
        this.properties = properties;
    }

    @Transactional
    public Stored store(EventSite event, GeneratedAsset asset, byte[] rawBytes) throws IOException {
        ImageOptimizationService.Optimized optimized = optimization.optimize(rawBytes, asset.assetKey + ".png");
        String name = safeName(asset.assetKey) + "." + optimized.extension();
        EventMedia row = media.storeGenerated(event, optimized.bytes(), name);
        asset.mediaId = row.id;
        asset.originalFilename = row.originalFilename;
        asset.storedFilename = row.storedFilename;
        asset.mimeType = optimized.mime();
        asset.fileSize = (long) optimized.bytes().length;
        asset.width = optimized.width();
        asset.height = optimized.height();
        asset.aspectRatio = optimized.aspectRatio();
        if (properties.getImage().isBase64Enabled()) {
            int max = Math.max(1, properties.getImage().getBase64MaxSizeKb()) * 1024;
            if (optimized.bytes().length <= max) {
                asset.base64Content = Base64.getEncoder().encodeToString(optimized.bytes());
            } else {
                asset.base64Content = null;
            }
        } else {
            asset.base64Content = null;
        }
        return new Stored(row, optimized);
    }

    private static String safeName(String assetKey) {
        String cleaned = assetKey == null ? "generated" : assetKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        if (cleaned.isBlank()) {
            cleaned = "generated";
        }
        if (cleaned.contains("..") || cleaned.contains("/") || cleaned.contains("\\")) {
            throw ApiException.badRequest("The asset key is not valid.");
        }
        byte[] utf8 = cleaned.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > 80) {
            cleaned = cleaned.substring(0, 60);
        }
        return cleaned;
    }

    public record Stored(EventMedia media, ImageOptimizationService.Optimized optimized) {
    }
}