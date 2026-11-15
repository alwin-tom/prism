package com.prism.media;

import com.prism.event.ConfigurationSanitizer;
import com.prism.event.EventSite;
import com.prism.hosting.HostingService;
import com.prism.revision.EventRevision;
import com.prism.revision.RevisionRepository;
import com.prism.shared.audit.AuditService;
import com.prism.shared.config.PrismProperties;
import com.prism.shared.exception.ApiException;
import com.prism.shared.security.UserPrincipal;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {

    private final MediaRepository media;
    private final RevisionRepository revisions;
    private final HostingService hosting;
    private final ConfigurationSanitizer sanitizer;
    private final PrismProperties properties;
    private final AuditService audit;

    public MediaService(
            MediaRepository media,
            RevisionRepository revisions,
            HostingService hosting,
            ConfigurationSanitizer sanitizer,
            PrismProperties properties,
            AuditService audit
    ) {
        this.media = media;
        this.revisions = revisions;
        this.hosting = hosting;
        this.sanitizer = sanitizer;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> store(EventSite event, MultipartFile file, UserPrincipal principal, String ip) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a file to upload.");
        }
        EventRevision revision = requireDraftRevision(event);
        Detected detected = detect(file);
        long limit = ("VIDEO".equals(detected.type) ? properties.getMedia().getMaxVideoSizeMb() : properties.getMedia().getMaxImageSizeMb()) * 1024L * 1024L;
        if (file.getSize() > limit) {
            throw ApiException.badRequest("VIDEO".equals(detected.type)
                    ? "Videos must be " + properties.getMedia().getMaxVideoSizeMb() + " MB or smaller."
                    : "Images must be " + properties.getMedia().getMaxImageSizeMb() + " MB or smaller.");
        }
        EventMedia row = writeFile(event, revision, detected, file.getBytes(), cleanName(file.getOriginalFilename()), false);
        audit.record(principal.id(), "MEDIA_UPLOADED", "MEDIA", row.id, Map.of("type", row.mediaType), ip);
        return view(row, false);
    }

    @Transactional
    public EventMedia storeGenerated(EventSite event, byte[] bytes, String preferredName) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("The generated image was empty.");
        }
        EventRevision revision = requireDraftRevision(event);
        Detected detected = detectBytes(bytes, preferredName);
        if (!"IMAGE".equals(detected.type)) {
            throw ApiException.badRequest("Only image files can be stored as generated assets.");
        }
        long limit = properties.getMedia().getMaxImageSizeMb() * 1024L * 1024L;
        if (bytes.length > limit) {
            throw ApiException.badRequest("Images must be " + properties.getMedia().getMaxImageSizeMb() + " MB or smaller.");
        }
        return writeFile(event, revision, detected, bytes, cleanName(preferredName == null ? "generated." + detected.extension : preferredName), true);
    }

    private EventRevision requireDraftRevision(EventSite event) {
        EventRevision revision = revisions.findFirstByEventIdOrderByRevisionNumberDesc(event.id)
                .orElseThrow(() -> ApiException.badRequest("Save the site before uploading."));
        if (!"DRAFT".equals(revision.submissionStatus)) {
            throw ApiException.badRequest("Uploads go onto a draft. Save your changes to open a new draft first.");
        }
        return revision;
    }

    private EventMedia writeFile(EventSite event, EventRevision revision, Detected detected, byte[] bytes, String originalName, boolean generated) throws IOException {
        UUID id = UUID.randomUUID();
        String storedName = id + "." + detected.extension;
        Path root = root();
        Path directory = root.resolve("events").resolve(event.id.toString()).resolve("revisions")
                .resolve(revision.id.toString())
                .resolve("IMAGE".equals(detected.type) ? (generated ? "images/generated" : "images") : "videos");
        Files.createDirectories(directory);
        Path target = directory.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("The file could not be stored.");
        }
        Files.write(target, bytes);
        String thumbnail = null;
        if ("IMAGE".equals(detected.type)) {
            Path thumb = directory.resolve(id + "-thumb.jpg");
            try {
                Thumbnails.of(target.toFile()).size(960, 960).outputFormat("jpg").toFile(thumb.toFile());
                thumbnail = root.relativize(thumb).toString().replace('\\', '/');
            } catch (IOException ignored) {
                thumbnail = null;
            }
        }
        EventMedia row = new EventMedia();
        row.id = id;
        row.eventId = event.id;
        row.revisionId = revision.id;
        row.originalFilename = originalName;
        row.storedFilename = storedName;
        row.mediaType = detected.type;
        row.mimeType = detected.mime;
        row.fileSize = bytes.length;
        row.storagePath = root.relativize(target).toString().replace('\\', '/');
        row.thumbnailPath = thumbnail;
        media.save(row);
        return row;
    }

    public EventMedia requireOwned(EventSite event, UUID mediaId) {
        return media.findByIdAndEventId(mediaId, event.id)
                .orElseThrow(() -> ApiException.notFound("That file was not found."));
    }

    public EventMedia requirePublic(EventSite event, UUID mediaId) {
        if (!"PUBLISHED".equals(event.status) || event.publishedRevisionId == null || !hosting.covers(event.id, Instant.now())) {
            throw ApiException.notFound("That file was not found.");
        }
        EventRevision published = revisions.findById(event.publishedRevisionId)
                .orElseThrow(() -> ApiException.notFound("That file was not found."));
        Set<String> referenced = sanitizer.mediaIds(published.configurationJson);
        if (!referenced.contains(mediaId.toString())) {
            throw ApiException.notFound("That file was not found.");
        }
        return media.findByIdAndEventId(mediaId, event.id)
                .orElseThrow(() -> ApiException.notFound("That file was not found."));
    }

    public Resource resource(EventMedia row, boolean thumb) {
        Path root = root();
        String relative = thumb && row.thumbnailPath != null ? row.thumbnailPath : row.storagePath;
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw ApiException.notFound("That file was not found.");
        }
        return new FileSystemResource(path);
    }

    @Transactional
    public void delete(EventSite event, UUID mediaId) throws IOException {
        EventMedia row = requireOwned(event, mediaId);
        EventRevision revision = row.revisionId == null ? null : revisions.findById(row.revisionId).orElse(null);
        if (revision == null || !"DRAFT".equals(revision.submissionStatus)) {
            throw ApiException.badRequest("Files on a submitted revision stay with that revision.");
        }
        deleteFiles(row);
        media.delete(row);
    }

    public Map<String, Object> view(EventMedia row, boolean published) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", row.id);
        body.put("eventId", row.eventId);
        body.put("revisionId", row.revisionId);
        body.put("originalFilename", row.originalFilename);
        body.put("mediaType", row.mediaType);
        body.put("mimeType", row.mimeType);
        body.put("fileSize", row.fileSize);
        body.put("url", (published ? "/api/v1/public/media/" : "/api/v1/events/" + row.eventId + "/media/") + row.id);
        body.put("createdAt", row.createdAt);
        return body;
    }

    @Transactional
    public int purgeExpired(Instant cutoff) throws IOException {
        return 0;
    }

    public void deleteFiles(EventMedia row) throws IOException {
        Path root = root();
        for (String relative : new String[]{row.storagePath, row.thumbnailPath}) {
            if (relative == null || relative.isBlank() || "purged".equals(relative)) {
                continue;
            }
            Path path = root.resolve(relative).normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        }
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
        return detectHeader(header, read, file.getOriginalFilename());
    }

    public static Detected detectBytes(byte[] bytes, String filename) {
        if (bytes == null || bytes.length < 12) {
            throw ApiException.badRequest("That file type is not supported.");
        }
        byte[] header = new byte[Math.min(32, bytes.length)];
        System.arraycopy(bytes, 0, header, 0, header.length);
        return detectHeader(header, header.length, filename);
    }

    private static Detected detectHeader(byte[] header, int read, String filename) {
        if (read < 12) {
            throw ApiException.badRequest("That file type is not supported.");
        }
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return new Detected("IMAGE", "image/jpeg", "jpg");
        }
        if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return new Detected("IMAGE", "image/png", "png");
        }
        if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) {
            return new Detected("IMAGE", "image/gif", "gif");
        }
        if (header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
            return new Detected("IMAGE", "image/webp", "webp");
        }
        if (header[0] == 0x1A && header[1] == 0x45 && header[2] == (byte) 0xDF && header[3] == (byte) 0xA3) {
            return new Detected("VIDEO", "video/webm", "webm");
        }
        String box = new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
        if ("ftyp".equals(box)) {
            return new Detected("VIDEO", "video/mp4", "mp4");
        }
        String lowered = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lowered.endsWith(".exe") || lowered.endsWith(".js") || lowered.endsWith(".html") || lowered.endsWith(".svg")) {
            throw ApiException.badRequest("That file type is not allowed.");
        }
        throw ApiException.badRequest("Upload a JPEG, PNG, GIF, WebP, MP4 or WebM file.");
    }

    public record Detected(String type, String mime, String extension) {
    }
}
