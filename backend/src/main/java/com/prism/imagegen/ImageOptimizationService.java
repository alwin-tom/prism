package com.prism.imagegen;

import com.prism.media.MediaService;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ImageOptimizationService {

    private final PrismProperties properties;

    public ImageOptimizationService(PrismProperties properties) {
        this.properties = properties;
    }

    public Optimized optimize(byte[] bytes, String preferredName) throws IOException {
        MediaService.Detected detected = MediaService.detectBytes(bytes, preferredName);
        if (!"IMAGE".equals(detected.type())) {
            throw ApiException.badRequest("Only image files can be generated.");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw ApiException.badRequest("The generated image could not be read.");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        long limit = properties.getMedia().getMaxImageSizeMb() * 1024L * 1024L;
        byte[] output = bytes;
        String mime = detected.mime();
        String extension = detected.extension();
        if (bytes.length > limit || width > 2560 || height > 2560) {
            int targetW = Math.min(width, 1920);
            int targetH = Math.min(height, 1920);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Thumbnails.of(image)
                    .size(targetW, targetH)
                    .outputFormat("jpg")
                    .outputQuality(0.88)
                    .toOutputStream(buffer);
            output = buffer.toByteArray();
            mime = "image/jpeg";
            extension = "jpg";
            BufferedImage resized = ImageIO.read(new ByteArrayInputStream(output));
            if (resized != null) {
                width = resized.getWidth();
                height = resized.getHeight();
            }
        }
        if (output.length > limit) {
            throw ApiException.badRequest("The generated image is too large after optimisation.");
        }
        String aspect = aspectRatio(width, height);
        return new Optimized(output, mime, extension, width, height, aspect);
    }

    static String aspectRatio(int width, int height) {
        if (width <= 0 || height <= 0) {
            return "1:1";
        }
        int g = gcd(width, height);
        return (width / g) + ":" + (height / g);
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.max(1, a);
    }

    public record Optimized(byte[] bytes, String mime, String extension, int width, int height, String aspectRatio) {
    }
}
