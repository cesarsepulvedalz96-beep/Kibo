package com.stockflow.storage.application;

import com.stockflow.shared.domain.BusinessException;
import java.io.*;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class ImageSanitizer {
    /** Read dimensions before decoding, and re-encode to remove scripts and metadata. */
    public byte[] sanitize(byte[] input) {
        if (input.length > 5 * 1024 * 1024) throw new BusinessException("INVALID_IMAGE", 400);
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new BusinessException("INVALID_IMAGE", 400);
            var reader = readers.next();
            try {
                if (!Set.of("JPEG", "PNG")
                        .contains(reader.getFormatName().toUpperCase(java.util.Locale.ROOT))) {
                    throw new BusinessException("INVALID_IMAGE", 400);
                }
                reader.setInput(stream);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 16000000)
                    throw new BusinessException("INVALID_IMAGE", 400);
                var image = reader.read(0);
                int width = Math.min(1000, image.getWidth());
                int height = Math.max(1, image.getHeight() * width / image.getWidth());
                var resized =
                        new java.awt.image.BufferedImage(
                                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics = resized.createGraphics();
                try {
                    graphics.setRenderingHint(
                            java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(image, 0, 0, width, height, null);
                } finally {
                    graphics.dispose();
                }
                var output = new ByteArrayOutputStream();
                ImageIO.write(resized, "png", output);
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new BusinessException("INVALID_IMAGE", 400);
        }
    }
}
