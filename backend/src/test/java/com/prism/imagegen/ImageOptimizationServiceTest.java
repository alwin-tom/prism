package com.prism.imagegen;

import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageOptimizationServiceTest {

    @Test
    void optimisesValidPng() throws Exception {
        PrismProperties properties = new PrismProperties();
        properties.getMedia().setMaxImageSizeMb(10);
        ImageOptimizationService service = new ImageOptimizationService(properties);
        byte[] png = solidPng(64, 48);
        ImageOptimizationService.Optimized optimized = service.optimize(png, "hero.png");
        assertEquals("image/png", optimized.mime());
        assertTrue(optimized.width() > 0);
        assertTrue(optimized.height() > 0);
        assertEquals("4:3", optimized.aspectRatio());
    }

    @Test
    void rejectsNonImage() {
        PrismProperties properties = new PrismProperties();
        ImageOptimizationService service = new ImageOptimizationService(properties);
        assertThrows(ApiException.class, () -> service.optimize(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, "bad.bin"));
    }

    private static byte[] solidPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, Color.LIGHT_GRAY.getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
