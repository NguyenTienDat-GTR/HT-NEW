package phucuong.ht.modules.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import phucuong.ht.modules.leader.model.enums.LeaderGender;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.request.LeaderUpdateRequest;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.leader.service.LeaderService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.media.config.MediaProperties;
import phucuong.ht.modules.media.job.MediaLegacyMigrationService;
import phucuong.ht.modules.media.job.MediaMaintenanceService;
import phucuong.ht.modules.media.job.MediaProviderMigrationService;
import phucuong.ht.modules.media.model.entity.MediaAsset;
import phucuong.ht.modules.media.model.enums.MediaStatus;
import phucuong.ht.modules.media.repository.MediaAssetRepository;
import phucuong.ht.modules.media.service.MediaFolderService;
import phucuong.ht.modules.media.service.MediaUrlSigner;
import phucuong.ht.modules.media.storage.MediaStorageProvider;
import phucuong.ht.modules.media.storage.MediaStorageException;
import phucuong.ht.modules.media.storage.MediaStorageRegistry;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra xuyên suốt trên Postgres thật: migration V110, tải ảnh lên → gắn vào huynh trưởng → thư mục theo giáo phận
 * và loại ảnh, phát ảnh chỉ khi đã đăng nhập + chữ ký đúng, chuyển ảnh base64 cũ, chuyển giữa hai nhà cung cấp, dọn dẹp.
 */
@Testcontainers
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "app.security.swagger-public-enabled=false",
        "app.rate-limit.enabled=false",
        "app.security.jwt.access-secret=isolated-test-access-secret-with-at-least-32-bytes",
        "app.security.jwt.refresh-secret=isolated-test-refresh-secret-with-at-least-32-bytes",
        "app.security.jwt.access-token-expiration-ms=900000",
        "app.security.jwt.refresh-token-expiration-ms=604800000",
        "app.security.jwt.issuer=ht-management-media-test",
        "app.security.jwt.audience=ht-management-media-client",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "management.health.mail.enabled=false",
        "app.media.active-provider=local",
        "app.media.root-prefix=ht/test",
        "app.media.signing-secret=integration-test-signing-secret",
        "app.media.local.enabled=true",
        "app.media.legacy-migration.enabled=false",
        "app.media.provider-migration.enabled=false",
        "app.media.cleanup.enabled=false"
})
class MediaStorageIntegrationTest {

    private static final Path LOCAL_DIR = createTempDir();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withCommand("redis-server", "--requirepass", "media-redis-password").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "media-rabbit").withEnv("RABBITMQ_DEFAULT_PASS", "media-rabbit-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "media-redis-password");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "media-rabbit");
        registry.add("spring.rabbitmq.password", () -> "media-rabbit-password");
        registry.add("app.media.local.dir", LOCAL_DIR::toString);
    }

    private static Path createTempDir() {
        try {
            Path dir = Files.createTempDirectory("ht-media-it");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Nhà cung cấp thứ hai (trong bộ nhớ) để kiểm thử chuyển dữ liệu giữa các nhà cung cấp. */
    static class InMemoryProvider implements MediaStorageProvider {
        final Map<String, byte[]> files = new ConcurrentHashMap<>();

        @Override
        public String name() {
            return "memory";
        }

        @Override
        public void put(String key, byte[] data, String contentType) {
            files.put(key, data.clone());
        }

        @Override
        public byte[] get(String key) {
            byte[] data = files.get(key);
            if (data == null) {
                throw new MediaStorageException("Không có " + key, true);
            }
            return data.clone();
        }

        @Override
        public void delete(String key) {
            files.remove(key);
        }
    }

    @TestConfiguration
    static class ExtraProviderConfig {
        @Bean
        InMemoryProvider memoryProvider() {
            return new InMemoryProvider();
        }
    }

    private static final String USERNAME = "admin.phucuong";

    private static UUID dioceseId;
    private static UUID otherDioceseId;
    private static UUID parishId;
    private static UUID deaneryIdForAlbum;
    private static UUID otherParishId;
    private static UUID uploadedAssetId;
    private static String uploadedUrl;
    private static UUID leaderId;
    private static UUID attachedAssetId;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LeaderService leaderService;
    @Autowired private LeaderLevelService leaderLevelService;
    @Autowired private MediaAssetRepository assetRepository;
    @Autowired private MediaFolderService folderService;
    @Autowired private MediaUrlSigner signer;
    @Autowired private MediaProperties properties;
    @Autowired private MediaLegacyMigrationService legacyMigration;
    @Autowired private MediaMaintenanceService maintenance;
    @Autowired private MediaStorageRegistry registry;
    @Autowired private InMemoryProvider memory;
    @Autowired private TransactionTemplate tx;

    private static Authentication auth(String... authorities) {
        return new UsernamePasswordAuthenticationToken(USERNAME, "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static Authentication leaderAdmin() {
        return auth("organization.leader.create.diocese", "organization.leader.read.diocese");
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int i = 0; i < width; i += 6) {
            g.setColor(new Color((i * 5) % 256, (i * 11) % 256, (i * 23) % 256));
            g.fillRect(i, 0, 6, height);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static String dataUrl(byte[] bytes) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private Path fileOf(String key) {
        return LOCAL_DIR.resolve(key);
    }

    private byte[] readLocal(String key) {
        try {
            return Files.readAllBytes(fileOf(key));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @Order(1)
    void seedOrganizationAndMigrationCreatesDioceseFolderLazily() {
        dioceseId = UUID.randomUUID();
        otherDioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        deaneryIdForAlbum = deaneryId;
        UUID otherDeaneryId = UUID.randomUUID();
        parishId = UUID.randomUUID();
        otherParishId = UUID.randomUUID();
        jdbc.update("insert into diocese (id, name, union_name, created_at) values (?, 'Phú Cường', 'Liên đoàn PC', now())", dioceseId);
        jdbc.update("insert into diocese (id, name, union_name, created_at) values (?, 'Xuân Lộc', 'Liên đoàn XL', now())", otherDioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Thủ Dầu Một', 'HĐ', ?, now())", deaneryId, dioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Long Khánh', 'HĐ', ?, now())", otherDeaneryId, otherDioceseId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (?, 'Bà Lụa', 'CĐ', ?, now())", parishId, deaneryId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (?, 'Gia Kiệm', 'CĐ', ?, now())", otherParishId, otherDeaneryId);
        jdbc.update("insert into account (username, created_at, diocese_id) values (?, now(), ?)", USERNAME, dioceseId);
        leaderLevelService.seedDefaults(dioceseId, "test");
        leaderLevelService.seedDefaults(otherDioceseId, "test");

        String folder = folderService.dioceseFolder(dioceseId);
        assertEquals("phu-cuong-" + dioceseId.toString().replace("-", "").substring(0, 8), folder);
        assertEquals(folder, jdbc.queryForObject("select storage_folder from diocese where id = ?", String.class, dioceseId));
        assertEquals("ht/test/dioceses/" + folder + "/avatars/x",
                folderService.baseKey(dioceseId, phucuong.ht.modules.media.model.enums.MediaCategory.LEADER_AVATAR, UUID.nameUUIDFromBytes(new byte[0])).replaceAll("/[0-9a-f-]{36}$", "/x"));
    }

    @Test
    @Order(2)
    void uploadRequiresPermissionAndStoresPendingAsset() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", png(900, 700));

        mockMvc.perform(multipart("/api/media").file(file).param("category", "LEADER_AVATAR")
                        .with(authentication(auth("training.course.create_past.diocese"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/media").file(file).param("category", "LEADER_AVATAR"))
                .andExpect(status().isUnauthorized());

        MvcResult result = mockMvc.perform(multipart("/api/media").file(file).param("category", "LEADER_AVATAR")
                        .with(authentication(leaderAdmin())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        uploadedAssetId = UUID.fromString(data.get("assetId").asText());
        uploadedUrl = data.get("url").asText();
        assertTrue(uploadedUrl.startsWith("/api/media/" + uploadedAssetId + "/medium?sig="));

        MediaAsset asset = assetRepository.findById(uploadedAssetId).orElseThrow();
        assertEquals(MediaStatus.PENDING, asset.getStatus());
        assertEquals("local", asset.getProvider());
        assertTrue(asset.getBaseKey().startsWith("ht/test/pending/avatars/"));
        assertEquals(2, asset.getVariants().size());
        asset.getVariants().forEach(v -> assertTrue(Files.exists(fileOf(v.key())), v.key()));
    }

    @Test
    @Order(3)
    void servesImageOnlyToLoggedInUsersWithValidSignature() throws Exception {
        // Đăng nhập + chữ ký đúng → 200, ảnh JPEG nhỏ, cache riêng tư.
        MvcResult ok = mockMvc.perform(get(uploadedUrl).with(authentication(leaderAdmin())))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("image/jpeg", ok.getResponse().getContentType());
        assertTrue(ok.getResponse().getHeader("Cache-Control").contains("private"));
        assertEquals("nosniff", ok.getResponse().getHeader("X-Content-Type-Options"));
        BufferedImage served = ImageIO.read(new java.io.ByteArrayInputStream(ok.getResponse().getContentAsByteArray()));
        // Ảnh tải lên 900x700 được cắt khổ dọc 4x6 (2:3) và không phóng to.
        assertEquals(2.0 / 3.0, (double) served.getWidth() / served.getHeight(), 0.01);
        assertTrue(served.getHeight() <= 720);
        String etag = ok.getResponse().getHeader("ETag");
        assertNotNull(etag);

        // Gửi lại với If-None-Match → 304, không tải lại.
        mockMvc.perform(get(uploadedUrl).header("If-None-Match", etag).with(authentication(leaderAdmin())))
                .andExpect(status().isNotModified());

        // Người ngoài có link nhưng chưa đăng nhập → 401.
        mockMvc.perform(get(uploadedUrl)).andExpect(status().isUnauthorized());

        // Đã đăng nhập nhưng chữ ký sai / thiếu / biến thể bị đổi → 403.
        mockMvc.perform(get(uploadedUrl.replaceAll("sig=.*$", "sig=forged")).with(authentication(leaderAdmin())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/media/" + uploadedAssetId + "/medium").with(authentication(leaderAdmin())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(uploadedUrl.replace("/medium?", "/thumb?")).with(authentication(leaderAdmin())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(uploadedUrl.replace("/medium?", "/master?")).with(authentication(leaderAdmin())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void creatingLeaderMovesAvatarIntoDioceseAvatarFolder() throws Exception {
        LeaderCreateRequest request = new LeaderCreateRequest();
        request.setHolyName("Phêrô");
        request.setFullName("Nguyễn Văn An");
        request.setBirthDate(LocalDate.of(1995, 3, 7));
        request.setEmail("an@example.com");
        request.setGender(LeaderGender.NAM);
        request.setLeaderLevel("NONE");
        request.setParishId(parishId);
        request.setImageAssetId(uploadedAssetId);

        LeaderResponse created = leaderService.create(request, leaderAdmin());
        leaderId = created.getId();
        attachedAssetId = created.getImageAssetId();

        assertEquals(uploadedAssetId, attachedAssetId);
        assertNotNull(created.getImageUrl());
        assertTrue(created.getImageUrl().contains("/medium?"));
        assertTrue(created.getImageThumbUrl().contains("/thumb?"));

        MediaAsset asset = assetRepository.findById(attachedAssetId).orElseThrow();
        assertEquals(MediaStatus.ACTIVE, asset.getStatus());
        assertEquals(dioceseId, asset.getDioceseId());
        String folder = folderService.dioceseFolder(dioceseId);
        assertEquals("ht/test/dioceses/" + folder + "/avatars/" + attachedAssetId, asset.getBaseKey());
        asset.getVariants().forEach(v -> {
            assertTrue(v.key().startsWith(asset.getBaseKey() + "/"), v.key());
            assertTrue(Files.exists(fileOf(v.key())), "phải có ở thư mục giáo phận: " + v.key());
        });
        // Thư mục tạm không còn tệp.
        assertFalse(Files.exists(fileOf("ht/test/pending/avatars/" + attachedAssetId + "/thumb.jpg")));
    }

    @Test
    @Order(5)
    void otherUserCannotAttachSomeoneElsesPendingUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", png(300, 300));
        MvcResult result = mockMvc.perform(multipart("/api/media").file(file).param("category", "LEADER_AVATAR")
                        .with(authentication(leaderAdmin())))
                .andExpect(status().isOk()).andReturn();
        UUID pending = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("assetId").asText());

        LeaderCreateRequest request = new LeaderCreateRequest();
        request.setHolyName("Maria");
        request.setFullName("Trần Thị Bình");
        request.setBirthDate(LocalDate.of(1998, 11, 21));
        request.setEmail("binh@example.com");
        request.setGender(LeaderGender.NU);
        request.setLeaderLevel("NONE");
        request.setParishId(parishId);
        request.setImageAssetId(pending);

        Authentication intruder = new UsernamePasswordAuthenticationToken("intruder", "n/a",
                List.of(new SimpleGrantedAuthority("organization.leader.create.diocese")));
        jdbc.update("insert into account (username, created_at, diocese_id) values ('intruder', now(), ?)", dioceseId);
        org.junit.jupiter.api.Assertions.assertThrows(phucuong.ht.modules.auth.exception.ApiException.class,
                () -> leaderService.create(request, intruder));
        assertEquals(MediaStatus.PENDING, assetRepository.findById(pending).orElseThrow().getStatus());
    }

    @Test
    @Order(6)
    void replacingAvatarReleasesOldAssetAndRemovalIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "b.png", "image/png", png(500, 500));
        MvcResult result = mockMvc.perform(multipart("/api/media").file(file).param("category", "LEADER_AVATAR")
                        .with(authentication(leaderAdmin())))
                .andExpect(status().isOk()).andReturn();
        UUID replacement = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("assetId").asText());

        Authentication updater = auth("organization.leader.update.parish", "organization.leader.read.diocese",
                "organization.leader.create.diocese");
        LeaderUpdateRequest update = new LeaderUpdateRequest();
        update.setImageAssetId(replacement);
        LeaderResponse updated = leaderService.update(leaderId, update, updater);
        assertEquals(replacement, updated.getImageAssetId());
        assertEquals(MediaStatus.DELETED, assetRepository.findById(attachedAssetId).orElseThrow().getStatus());
        String folder = folderService.dioceseFolder(dioceseId);
        assertEquals("ht/test/dioceses/" + folder + "/avatars/" + replacement,
                assetRepository.findById(replacement).orElseThrow().getBaseKey());

        // Ảnh đại diện không được xóa, chỉ được thay thế: yêu cầu gỡ ảnh bị từ chối và ảnh hiện tại giữ nguyên.
        LeaderUpdateRequest clear = new LeaderUpdateRequest();
        clear.setClearFields(List.of("imageUrl"));
        org.junit.jupiter.api.Assertions.assertThrows(phucuong.ht.modules.auth.exception.ApiException.class,
                () -> leaderService.update(leaderId, clear, updater));
        assertEquals(MediaStatus.ACTIVE, assetRepository.findById(replacement).orElseThrow().getStatus());
        assertEquals(replacement, jdbc.queryForObject("select image_asset_id from leader where id = ?", UUID.class, leaderId));
    }

    @Test
    @Order(7)
    void legacyBase64ImagesAreMovedToCloudFolderAndColumnCleared() throws Exception {
        UUID legacyLeader = UUID.randomUUID();
        byte[] original = png(640, 480);
        jdbc.update("insert into leader (id, created_at, status, holy_name, first_name, last_name, birth_date, email, gender, leader_level, parish_id, image_url) "
                + "values (?, now(), true, 'Giuse', 'Cường', 'Lê', '1990-02-02', 'cuong@example.com', 'NAM', 'NONE', ?, ?)", legacyLeader, otherParishId, dataUrl(original));
        jdbc.update("insert into leader (id, created_at, status, holy_name, first_name, last_name, birth_date, email, gender, leader_level, parish_id, image_url) "
                + "values (?, now(), true, 'Anna', 'Hoa', 'Võ', '1991-02-02', 'hoa@example.com', 'NU', 'NONE', ?, 'data:image/png;base64,@@not-an-image@@')", UUID.randomUUID(), otherParishId);

        UUID configId = UUID.randomUUID();
        jdbc.update("insert into certificate_config (id, name, is_default, scope_key, paper_size, orientation, background_image, field_layout, created_at, status) "
                + "values (?, 'Mẫu cũ', true, ?, 'A4', 'LANDSCAPE', ?, '[]'::jsonb, now(), true)", configId, "DIOCESE:" + otherDioceseId, dataUrl(png(1600, 1100)));

        int migrated = legacyMigration.migrateBatch();
        assertEquals(2, migrated, "1 avatar + 1 nền chứng nhận; ảnh hỏng bị bỏ qua");

        UUID assetId = jdbc.queryForObject("select image_asset_id from leader where id = ?", UUID.class, legacyLeader);
        assertNotNull(assetId);
        assertNull(jdbc.queryForObject("select image_url from leader where id = ?", String.class, legacyLeader));
        MediaAsset avatar = assetRepository.findById(assetId).orElseThrow();
        String otherFolder = folderService.dioceseFolder(otherDioceseId);
        assertEquals("ht/test/dioceses/" + otherFolder + "/avatars/" + assetId, avatar.getBaseKey());
        assertEquals(otherDioceseId, avatar.getDioceseId());

        UUID bgAsset = jdbc.queryForObject("select background_asset_id from certificate_config where id = ?", UUID.class, configId);
        assertNotNull(bgAsset);
        assertNull(jdbc.queryForObject("select background_image from certificate_config where id = ?", String.class, configId));
        MediaAsset bg = assetRepository.findById(bgAsset).orElseThrow();
        assertEquals("ht/test/dioceses/" + otherFolder + "/certificate-backgrounds/" + bgAsset, bg.getBaseKey());
        assertEquals(List.of("master", "preview"), bg.getVariants().stream().map(v -> v.name()).sorted().toList());

        // Ảnh hỏng vẫn còn nguyên dữ liệu gốc và các lần chạy sau không lặp lại việc chuyển.
        assertEquals(1, jdbc.queryForObject("select count(*) from leader where image_url like 'data:%'", Integer.class));
        assertEquals(0, legacyMigration.migrateBatch());
    }

    @Test
    @Order(8)
    void relocationMovesAvatarWhenLeaderChangesDiocese() {
        UUID assetId = jdbc.queryForObject("select image_asset_id from leader where email = 'cuong@example.com'", UUID.class);
        jdbc.update("update leader set parish_id = ? where email = 'cuong@example.com'", parishId);

        assertEquals(1, maintenance.relocateLeaderAvatars());

        MediaAsset asset = assetRepository.findById(assetId).orElseThrow();
        String folder = folderService.dioceseFolder(dioceseId);
        assertEquals(dioceseId, asset.getDioceseId());
        assertEquals("ht/test/dioceses/" + folder + "/avatars/" + assetId, asset.getBaseKey());
        asset.getVariants().forEach(v -> assertTrue(Files.exists(fileOf(v.key())), v.key()));
        assertFalse(Files.exists(fileOf("ht/test/dioceses/" + folderService.dioceseFolder(otherDioceseId) + "/avatars/" + assetId + "/thumb.jpg")));
    }

    @Test
    @Order(9)
    void switchingProviderCopiesEveryVariantAndKeepsContent() {
        List<MediaAsset> before = assetRepository.findAll().stream()
                .filter(a -> a.getStatus() == MediaStatus.ACTIVE).toList();
        assertFalse(before.isEmpty());
        assertTrue(before.stream().allMatch(a -> a.getProvider().equals("local")));

        // Giả lập đổi cấu hình: nhà cung cấp mới là "memory", "local" vẫn được giữ cấu hình để đọc dữ liệu cũ.
        MediaProperties switched = new MediaProperties();
        switched.setActiveProvider("memory");
        switched.getProviderMigration().setBatchSize(100);
        MediaStorageRegistry newRegistry = new MediaStorageRegistry(
                List.of(registry.require("local"), memory), switched);
        MediaProviderMigrationService migration = new MediaProviderMigrationService(assetRepository, newRegistry, switched);

        int migrated = tx.execute(status -> migration.migrateBatch());
        assertEquals(before.size(), migrated);

        for (MediaAsset original : before) {
            MediaAsset after = assetRepository.findById(original.getId()).orElseThrow();
            assertEquals("memory", after.getProvider());
            after.getVariants().forEach(v -> {
                assertArrayEquals(memory.get(v.key()), java.util.Objects.requireNonNull(readLocal(v.key())),
                        "nội dung phải giống hệt bản gốc: " + v.key());
            });
        }
        // Hệ thống vẫn đọc được ảnh (qua provider ghi trong asset) mà không cần chạy lại gì.
        assertEquals(0, (int) tx.execute(status -> migration.migrateBatch()), "chạy lại không còn gì để chuyển");
        // Bản nguồn mặc định được giữ lại (an toàn), chỉ xóa khi bật delete-source.
        MediaAsset sample = assetRepository.findById(before.get(0).getId()).orElseThrow();
        assertTrue(Files.exists(fileOf(sample.getVariants().get(0).key())));
    }

    @Test
    @Order(10)
    void sourceIsDeletedOnlyWhenDeleteSourceIsEnabledAndRecordsFailuresWithBackoff() {
        // Asset mới ở "local" rồi chuyển sang "memory" với delete-source bật → bản nguồn bị xóa.
        MediaProperties switched = new MediaProperties();
        switched.setActiveProvider("memory");
        switched.getProviderMigration().setDeleteSource(true);
        MediaStorageRegistry newRegistry = new MediaStorageRegistry(List.of(registry.require("local"), memory), switched);
        MediaProviderMigrationService migration = new MediaProviderMigrationService(assetRepository, newRegistry, switched);

        MediaAsset fresh = tx.execute(status -> {
            MediaAsset asset = new MediaAsset();
            asset.setId(UUID.randomUUID());
            asset.setCategory(phucuong.ht.modules.media.model.enums.MediaCategory.LEADER_AVATAR);
            asset.setStatus(MediaStatus.ACTIVE);
            asset.setProvider("local");
            asset.setBaseKey("ht/test/system/avatars/" + asset.getId());
            String key = asset.getBaseKey() + "/thumb.jpg";
            byte[] bytes = new byte[]{1, 2, 3};
            registry.require("local").put(key, bytes, "image/jpeg");
            asset.setVariants(List.of(new phucuong.ht.modules.media.model.entity.MediaVariantInfo(
                    "thumb", key, "image/jpeg", 3, 1, 1, phucuong.ht.modules.media.service.MediaImageProcessor.sha256(bytes))));
            asset.setCreatedAt(OffsetDateTime.now());
            asset.setUpdatedAt(OffsetDateTime.now());
            return assetRepository.save(asset);
        });
        // Asset có sha256 sai → lỗi, tăng bộ đếm, hẹn thử lại.
        MediaAsset broken = tx.execute(status -> {
            MediaAsset asset = new MediaAsset();
            asset.setId(UUID.randomUUID());
            asset.setCategory(phucuong.ht.modules.media.model.enums.MediaCategory.LEADER_AVATAR);
            asset.setStatus(MediaStatus.ACTIVE);
            asset.setProvider("local");
            asset.setBaseKey("ht/test/system/avatars/" + asset.getId());
            String key = asset.getBaseKey() + "/thumb.jpg";
            registry.require("local").put(key, new byte[]{9, 9}, "image/jpeg");
            asset.setVariants(List.of(new phucuong.ht.modules.media.model.entity.MediaVariantInfo(
                    "thumb", key, "image/jpeg", 2, 1, 1, "0".repeat(64))));
            asset.setCreatedAt(OffsetDateTime.now());
            asset.setUpdatedAt(OffsetDateTime.now());
            return assetRepository.save(asset);
        });

        int migrated = tx.execute(status -> migration.migrateBatch());
        assertEquals(1, migrated);
        String movedKey = fresh.getBaseKey() + "/thumb.jpg";
        assertFalse(Files.exists(fileOf(movedKey)), "bản nguồn phải bị xóa khi bật delete-source");
        assertNotNull(memory.get(movedKey));

        MediaAsset failed = assetRepository.findById(broken.getId()).orElseThrow();
        assertEquals("local", failed.getProvider());
        assertEquals(1, failed.getMigrationRetries());
        assertNotNull(failed.getMigrationError());
        assertNotNull(failed.getNextAttemptAt());
        assertTrue(failed.getNextAttemptAt().isAfter(OffsetDateTime.now()));
    }

    @Test
    @Order(11)
    void cleanupDeletesStalePendingOrphanAndExpiredDeletedAssets() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "p.png", "image/png", png(200, 200));
        MvcResult result = mockMvc.perform(multipart("/api/media").file(file).param("category", "LEADER_AVATAR")
                        .with(authentication(leaderAdmin())))
                .andExpect(status().isOk()).andReturn();
        UUID stale = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("assetId").asText());
        List<String> staleKeys = assetRepository.findById(stale).orElseThrow().getVariants().stream().map(v -> v.key()).toList();

        jdbc.update("update media_asset set created_at = now() - interval '3 days' where id = ?", stale);
        maintenance.cleanup();
        assertEquals(MediaStatus.DELETED, assetRepository.findById(stale).orElseThrow().getStatus(), "PENDING quá hạn bị gỡ");

        jdbc.update("update media_asset set deleted_at = now() - interval '30 days' where id = ?", stale);
        maintenance.cleanup();
        assertTrue(assetRepository.findById(stale).isEmpty(), "DELETED quá thời gian lưu bị xóa hẳn");
        staleKeys.forEach(key -> assertFalse(Files.exists(fileOf(key)), "tệp trên cloud phải bị xóa: " + key));
        assertNotEquals(0, assetRepository.count(), "asset đang dùng không bị đụng tới");
    }

    // ---------------- Album ảnh nền ----------------

    private static Authentication dioceseCertAdmin() {
        return new UsernamePasswordAuthenticationToken(USERNAME, "n/a", List.of(
                new SimpleGrantedAuthority("training.certificate_config.read.diocese"),
                new SimpleGrantedAuthority("training.certificate_config.create.diocese"),
                new SimpleGrantedAuthority("training.certificate_config.update.diocese"),
                new SimpleGrantedAuthority("training.badge_config.read.diocese"),
                new SimpleGrantedAuthority("training.badge_config.create.diocese")));
    }

    private static Authentication deaneryCertAdmin() {
        return new UsernamePasswordAuthenticationToken("admin.thudaumot", "n/a", List.of(
                new SimpleGrantedAuthority("training.certificate_config.read.deanery"),
                new SimpleGrantedAuthority("training.certificate_config.create.deanery"),
                new SimpleGrantedAuthority("training.certificate_config.update.deanery")));
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private MvcResult addToAlbum(String kind, Authentication auth) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "nen.png", "image/png", png(900, 600));
        return mockMvc.perform(multipart("/api/media/albums/" + kind).file(file).with(authentication(auth))).andReturn();
    }

    @Test
    @Order(12)
    void albumHoldsAtMostThreeBackgroundsPerUnitAndKind() throws Exception {
        jdbc.update("insert into account (username, created_at, deanery_id) values ('admin.thudaumot', now(), ?)", deaneryIdForAlbum);

        MvcResult empty = mockMvc.perform(get("/api/media/albums/CERTIFICATE").with(authentication(dioceseCertAdmin())))
                .andExpect(status().isOk()).andReturn();
        assertEquals(3, data(empty).get("maxItems").asInt());
        assertEquals(0, data(empty).get("items").size());

        for (int i = 0; i < 3; i++) {
            assertEquals(200, addToAlbum("CERTIFICATE", dioceseCertAdmin()).getResponse().getStatus());
        }
        MvcResult fourth = addToAlbum("CERTIFICATE", dioceseCertAdmin());
        assertEquals(409, fourth.getResponse().getStatus());
        assertTrue(fourth.getResponse().getContentAsString().contains("xóa bớt một ảnh"));
        assertEquals(3, jdbc.queryForObject("select count(*) from background_album_item where scope_key = ? and category = 'CERTIFICATE_BACKGROUND'",
                Integer.class, "DIOCESE:" + dioceseId));

        // Mỗi loại có album riêng: mặt trước và mặt sau của thẻ mỗi mặt tối đa 3 ảnh, độc lập nhau và với chứng nhận.
        for (int i = 0; i < 3; i++) {
            assertEquals(200, addToAlbum("BADGE_FRONT", dioceseCertAdmin()).getResponse().getStatus());
        }
        MvcResult fourthFront = addToAlbum("BADGE_FRONT", dioceseCertAdmin());
        assertEquals(409, fourthFront.getResponse().getStatus());
        assertTrue(fourthFront.getResponse().getContentAsString().contains("mặt trước thẻ"));
        for (int i = 0; i < 3; i++) {
            assertEquals(200, addToAlbum("BADGE_BACK", dioceseCertAdmin()).getResponse().getStatus());
        }
        MvcResult fourthBack = addToAlbum("BADGE_BACK", dioceseCertAdmin());
        assertEquals(409, fourthBack.getResponse().getStatus());
        assertTrue(fourthBack.getResponse().getContentAsString().contains("mặt sau thẻ"));
        assertEquals(3, data(mockMvc.perform(get("/api/media/albums/BADGE_BACK").with(authentication(dioceseCertAdmin())))
                .andExpect(status().isOk()).andReturn()).get("items").size());

        // Ảnh nằm trong thư mục giáo phận, đúng loại ảnh.
        String folder = folderService.dioceseFolder(dioceseId);
        String baseKey = jdbc.queryForObject("""
                select a.base_key from background_album_item i join media_asset a on a.id = i.asset_id
                where i.scope_key = ? and i.category = 'CERTIFICATE_BACKGROUND' limit 1""", String.class, "DIOCESE:" + dioceseId);
        assertTrue(baseKey.startsWith("ht/test/dioceses/" + folder + "/certificate-backgrounds/"), baseKey);
    }

    @Test
    @Order(13)
    void unitsNeverSeeEachOthersAlbums() throws Exception {
        // Giáo hạt không thấy album của giáo phận.
        MvcResult deaneryView = mockMvc.perform(get("/api/media/albums/CERTIFICATE").with(authentication(deaneryCertAdmin())))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, data(deaneryView).get("items").size());

        // Giáo hạt có album riêng đủ 3 ảnh độc lập với giáo phận.
        for (int i = 0; i < 3; i++) {
            assertEquals(200, addToAlbum("CERTIFICATE", deaneryCertAdmin()).getResponse().getStatus());
        }
        assertEquals(409, addToAlbum("CERTIFICATE", deaneryCertAdmin()).getResponse().getStatus());

        // Giáo phận vẫn chỉ thấy 3 ảnh của mình, không thấy ảnh của giáo hạt.
        MvcResult dioceseView = mockMvc.perform(get("/api/media/albums/CERTIFICATE").with(authentication(dioceseCertAdmin())))
                .andExpect(status().isOk()).andReturn();
        JsonNode dioceseItems = data(dioceseView).get("items");
        assertEquals(3, dioceseItems.size());
        List<String> deaneryAssets = jdbc.queryForList("select asset_id::text from background_album_item where scope_key = ?", String.class, "DEANERY:" + deaneryIdForAlbum);
        for (JsonNode item : dioceseItems) {
            assertFalse(deaneryAssets.contains(item.get("assetId").asText()));
        }

        // Giáo phận không xóa được ảnh của giáo hạt (báo không tìm thấy), ảnh vẫn còn.
        UUID deaneryItem = jdbc.queryForObject("select id from background_album_item where scope_key = ? limit 1", UUID.class, "DEANERY:" + deaneryIdForAlbum);
        mockMvc.perform(delete("/api/media/albums/CERTIFICATE/" + deaneryItem).with(authentication(dioceseCertAdmin())))
                .andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("select count(*) from background_album_item where id = ?", Integer.class, deaneryItem));

        // Không có quyền cấu hình thì không đụng được album.
        mockMvc.perform(get("/api/media/albums/CERTIFICATE").with(authentication(leaderAdmin()))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/media/albums/CERTIFICATE")).andExpect(status().isUnauthorized());
    }

    @Autowired private phucuong.ht.modules.certificate.config.service.CertificateConfigService certificateConfigService;

    @Test
    @Order(14)
    void configUsesACopyOfAnAlbumImageAndOtherUnitsCannotUseIt() throws Exception {
        UUID dioceseAsset = jdbc.queryForObject("select asset_id from background_album_item where scope_key = ? and category = 'CERTIFICATE_BACKGROUND' limit 1",
                UUID.class, "DIOCESE:" + dioceseId);

        phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest request =
                new phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest();
        request.setName("Mẫu từ album");
        request.setBackgroundAssetId(dioceseAsset);
        var created = certificateConfigService.create(request, dioceseCertAdmin());

        // Cấu hình giữ bản sao riêng, ảnh trong album vẫn còn nguyên.
        assertNotNull(created.getBackgroundAssetId());
        assertNotEquals(dioceseAsset, created.getBackgroundAssetId());
        assertEquals(1, jdbc.queryForObject("select count(*) from background_album_item where asset_id = ?", Integer.class, dioceseAsset));

        // Xóa ảnh khỏi album không làm mất ảnh của cấu hình.
        UUID itemId = jdbc.queryForObject("select id from background_album_item where asset_id = ?", UUID.class, dioceseAsset);
        mockMvc.perform(delete("/api/media/albums/CERTIFICATE/" + itemId).with(authentication(dioceseCertAdmin()))).andExpect(status().isOk());
        assertEquals("DELETED", jdbc.queryForObject("select status from media_asset where id = ?", String.class, dioceseAsset));
        assertEquals("ACTIVE", jdbc.queryForObject("select status from media_asset where id = ?", String.class, created.getBackgroundAssetId()));

        // Còn 2 ảnh nên thêm lại được 1 ảnh (đủ 3), ảnh thứ 4 vẫn bị từ chối.
        assertEquals(200, addToAlbum("CERTIFICATE", dioceseCertAdmin()).getResponse().getStatus());
        assertEquals(409, addToAlbum("CERTIFICATE", dioceseCertAdmin()).getResponse().getStatus());

        // Giáo hạt biết mã ảnh của album giáo phận cũng không dùng được.
        UUID anotherDioceseAsset = jdbc.queryForObject("select asset_id from background_album_item where scope_key = ? and category = 'CERTIFICATE_BACKGROUND' limit 1",
                UUID.class, "DIOCESE:" + dioceseId);
        phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest stolen =
                new phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest();
        stolen.setName("Dùng ảnh người khác");
        stolen.setBackgroundAssetId(anotherDioceseAsset);
        org.junit.jupiter.api.Assertions.assertThrows(phucuong.ht.modules.auth.exception.ApiException.class,
                () -> certificateConfigService.create(stolen, deaneryCertAdmin()));

        // Nhưng ảnh của chính album giáo hạt thì dùng được.
        UUID ownAsset = jdbc.queryForObject("select asset_id from background_album_item where scope_key = ? limit 1", UUID.class, "DEANERY:" + deaneryIdForAlbum);
        phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest own =
                new phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest();
        own.setName("Mẫu giáo hạt");
        own.setBackgroundAssetId(ownAsset);
        assertNotNull(certificateConfigService.create(own, deaneryCertAdmin()).getBackgroundAssetId());
    }
}
