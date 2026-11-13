package com.prism.designlibrary;

import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DesignReferenceMediaService {

    private static final Set<String> KINDS = Set.of("DESKTOP", "MOBILE", "SECTION");

    private final DesignReferenceMediaRepository media;
    private final PrismProperties properties;

    public DesignReferenceMediaService(DesignReferenceMediaRepository media, PrismProperties properties) {
        this.media = media;
        this.properties = properties;
    }

    @Transactional
    public DesignReferenceMedia store(MultipartFile file, String kind) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a file to upload.");
        }
        String normalisedKind = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(normalisedKind)) {
            throw ApiException.badRequest("Screenshot kind must be DESKTOP, MOBILE, or SECTION.");
        }
        Detected detected = detect(file);
        long limit = properties.getMedia().getMaxImageSizeMb() * 1024L * 1024L;
        if (file.getSize() > limit) {
            throw ApiException.badRequest("Images must be " + properties.getMedia().getMaxImageSizeMb() + " MB or smaller.");
        }
        UUID id = UUID.randomUUID();
        String storedName = id + "." + detected.extension;
        Path root = root();
        Path directory = root.resolve("design-references");
        Files.createDirectories(directory);
        Path target = directory.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("That upload path is not allowed.");
        }
        file.transferTo(target);
        DesignReferenceMedia row = new DesignReferenceMedia();
        row.id = id;
        row.originalFilename = cleanName(file.getOriginalFilename());
        row.storedFilename = storedName;
        row.mimeType = detected.mime;
        row.fileSize = file.getSize();
        row.storagePath = root.relativize(target).toString().replace('\\', '/');
        row.kind = normalisedKind;
        return media.save(row);
    }

    public Resource resource(DesignReferenceMedia row) {
        Path root = root();
        Path path = root.resolve(row.storagePath).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw ApiException.notFound("That file was not found.");
        }
        return new FileSystemResource(path);
    }

    public Map<String, Object> view(DesignReferenceMedia row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", row.id);
        body.put("kind", row.kind);
        body.put("mimeType", row.mimeType);
        body.put("fileSize", row.fileSize);
        body.put("width", row.width);
        body.put("height", row.height);
        body.put("url", "/api/v1/admin/design-references/media/" + row.id);
        body.put("createdAt", row.createdAt);
        return body;
    }

    public DesignReferenceMedia require(UUID id) {
        return media.findById(id).orElseThrow(() -> ApiException.notFound("That screenshot was not found."));
    }

    private Path root() {
        try {
            Path path = Path.of(properties.getMedia().getStoragePath()).toAbsolutePath().normalize();
            Files.createDirectories(path);
            return path;
        } catch (IOException exception) {
            throw new IllegalStateException("Media storage is not available.", exception);
        }
    }

    private static String cleanName(String original) {
        String name = original == null ? "upload" : Path.of(original).getFileName().toString();
        name = name.replaceAll("[\\p{Cntrl}]", "").replace("\"", "");
        if (name.isBlank()) {
            name = "upload";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static Detected detect(MultipartFile file) throws IOException {
        byte[] header = new byte[32];
        int read;
        try (InputStream input = file.getInputStream()) {
            read = input.read(header);
        }
        if (read < 12) {
            throw ApiException.badRequest("That file type is not supported.");
        }
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return new Detected("image/jpeg", "jpg");
        }
        if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return new Detected("image/png", "png");
        }
        if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) {
            return new Detected("image/gif", "gif");
        }
        if (header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
            return new Detected("image/webp", "webp");
        }
        throw ApiException.badRequest("Upload a JPEG, PNG, GIF or WebP screenshot.");
    }

    private record Detected(String mime, String extension) {
    }
}
