package phucuong.ht.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import phucuong.ht.modules.auth.model.AuthUserDetails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Huynh trưởng tự sửa hồ sơ cá nhân: không đụng giáo xứ / giáo hạt, chỉ kiểm tra trùng email / SĐT khi thật sự đổi. */
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
        "app.security.jwt.issuer=ht-management-profile-test",
        "app.security.jwt.audience=ht-management-profile-client",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "management.health.mail.enabled=false",
        "app.media.active-provider=local",
        "app.media.local.enabled=true",
        "app.media.local.dir=${java.io.tmpdir}/ht-media-profile-it",
        "app.media.legacy-migration.enabled=false",
        "app.media.provider-migration.enabled=false",
        "app.media.cleanup.enabled=false"
})
class MyProfileUpdateIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ht_management").withUsername("postgres").withPassword("postgres");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withCommand("redis-server", "--requirepass", "profile-redis-password").withExposedPorts(6379);

    @Container
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "profile-rabbit").withEnv("RABBITMQ_DEFAULT_PASS", "profile-rabbit-password")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "profile-redis-password");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "profile-rabbit");
        registry.add("spring.rabbitmq.password", () -> "profile-rabbit-password");
    }

    private static final String PASSWORD = "Mat-khau-hien-tai-1";

    private static UUID parishId;
    private static UUID myLeaderId;
    private static UUID otherLeaderId;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private phucuong.ht.modules.notification.service.NotificationReadRetentionService notificationRetention;

    private Authentication me(String username, UUID leaderId) {
        return me(username, leaderId, List.of());
    }

    private Authentication me(String username, UUID leaderId, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, leaderId == null ? null : leaderId.toString(), List.of("ADMIN_PARISH"), permissions);
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }

    private MockHttpServletRequestBuilder updateRequest(Authentication auth, Map<String, Object> body) throws Exception {
        return put("/api/auth/me/profile").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(withConsent(body)));
    }

    private static Map<String, Object> withConsent(Map<String, Object> body) {
        Map<String, Object> copy = new java.util.HashMap<>(body);
        copy.putIfAbsent("privacyConsent", true);
        return copy;
    }

    private Map<String, Object> row(String sql, Object... args) {
        return jdbc.queryForMap(sql, args);
    }

    @Test
    @Order(1)
    void seed() {
        UUID dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        parishId = UUID.randomUUID();
        myLeaderId = UUID.randomUUID();
        otherLeaderId = UUID.randomUUID();
        jdbc.update("insert into diocese (id, name, union_name, created_at) values (?, 'Phú Cường', 'LĐ', now())", dioceseId);
        jdbc.update("insert into deanery (id, name, association_name, diocese_id, created_at) values (?, 'Thủ Dầu Một', 'HĐ', ?, now())", deaneryId, dioceseId);
        jdbc.update("insert into parish (id, name, chapter_name, deanery_id, created_at) values (?, 'Bà Lụa', 'CĐ', ?, now())", parishId, deaneryId);
        String insertLeader = "insert into leader (id, created_at, status, holy_name, first_name, last_name, birth_date, email, phone_number, gender, leader_level, parish_id) "
                + "values (?, now(), true, ?, ?, ?, ?::date, ?, ?, 'NAM', 'NONE', ?)";
        jdbc.update(insertLeader, myLeaderId, "Phêrô", "An", "Nguyễn Văn", "1995-03-07", "me@example.com", "0901111111", parishId);
        jdbc.update(insertLeader, otherLeaderId, "Giuse", "Bình", "Trần", "1990-01-01", "other@example.com", "0902222222", parishId);
        jdbc.update("insert into account (username, created_at, status, password, leader_id) values ('me', now(), true, ?, ?)",
                passwordEncoder.encode(PASSWORD), myLeaderId);
        jdbc.update("insert into account (username, created_at, status, password) values ('no.leader', now(), true, ?)", passwordEncoder.encode(PASSWORD));
    }

    @Test
    @Order(2)
    void editsPersonalInfoWithoutTouchingUnitOrCheckingContactUniqueness() throws Exception {
        MvcResult result = mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of(
                        "holyName", "Phaolô", "gender", "NU",
                        // Họ và tên không sửa được (giống sửa hồ sơ huynh trưởng) và ngày sinh gửi lại đúng giá trị hiện tại:
                        // cả hai bị bỏ qua / không phải là thay đổi nên không đòi quyền sửa ngày sinh.
                        "fullName", "Nguyễn Văn Bảo", "birthDate", "1995-03-07",
                        // Gửi lại đúng email / SĐT hiện tại: không phải là thay đổi nên không cần mật khẩu và không bị báo trùng.
                        "email", "me@example.com", "phoneNumber", "0901111111",
                        // Giáo xứ / giáo hạt không được phép sửa: trường lạ bị bỏ qua.
                        "parishId", UUID.randomUUID().toString())))
                .andExpect(status().isOk()).andReturn();
        JsonNode leader = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("leader");
        assertEquals("Phaolô", leader.get("holyName").asText());
        assertEquals("Nguyễn Văn An", leader.get("fullName").asText());

        Map<String, Object> saved = row("select holy_name, first_name, last_name, gender, birth_date::text as birth, parish_id, updated_by from leader where id = ?", myLeaderId);
        assertEquals("Phaolô", saved.get("holy_name"));
        assertEquals("An", saved.get("first_name"), "họ và tên không đổi");
        assertEquals("Nguyễn Văn", saved.get("last_name"), "họ và tên không đổi");
        assertEquals("NU", saved.get("gender"));
        assertEquals("1995-03-07", saved.get("birth"));
        assertEquals(parishId, saved.get("parish_id"), "giáo xứ không đổi");
        assertEquals(myLeaderId.toString(), saved.get("updated_by"), "ghi nhận người thực hiện theo mã huynh trưởng như các luồng khác");
    }

    @Test
    @Order(3)
    void changingPhoneChecksUniquenessOnlyWhenItChanges() throws Exception {
        mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("phoneNumber", "0902222222")))
                .andExpect(status().isConflict());
        assertEquals("0901111111", row("select phone_number from leader where id = ?", myLeaderId).get("phone_number"));

        mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("phoneNumber", "0903333333")))
                .andExpect(status().isOk());
        assertEquals("0903333333", row("select phone_number from leader where id = ?", myLeaderId).get("phone_number"));
    }

    @Test
    @Order(4)
    void changingEmailNeedsCurrentPasswordAndChecksUniqueness() throws Exception {
        mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("email", "moi@example.com")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("email", "moi@example.com", "currentPassword", "sai-mat-khau")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("email", "other@example.com", "currentPassword", PASSWORD)))
                .andExpect(status().isConflict());
        assertEquals("me@example.com", row("select email from leader where id = ?", myLeaderId).get("email"));

        mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("email", "moi@example.com", "currentPassword", PASSWORD)))
                .andExpect(status().isOk());
        assertEquals("moi@example.com", row("select email from leader where id = ?", myLeaderId).get("email"));
    }

    @Test
    @Order(5)
    void rejectsInvalidAndEmptyValuesAndAccountsWithoutLeader() throws Exception {
        mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("phoneNumber", "123")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("holyName", "   ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(updateRequest(me("no.leader", null), Map.of("holyName", "An")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/auth/me/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void canReplaceButNotRemoveOwnAvatar() throws Exception {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        // Người dùng tự tải ảnh qua /api/media cần quyền tạo / sửa huynh trưởng; ở đây dùng dịch vụ để chuẩn bị ảnh của chính họ.
        UUID assetId = uploadAs(myLeaderId.toString(), out.toByteArray());

        MvcResult result = mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("imageAssetId", assetId.toString())))
                .andExpect(status().isOk()).andReturn();
        JsonNode leader = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("leader");
        assertEquals(assetId.toString(), leader.get("imageAssetId").asText());
        assertNotNull(leader.get("imageUrl").asText());
        assertEquals("ACTIVE", row("select status from media_asset where id = ?", assetId).get("status"));

        // Không cho xóa ảnh đại diện, chỉ được thay thế.
        mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("clearImage", true)))
                .andExpect(status().isBadRequest());
        assertEquals("ACTIVE", row("select status from media_asset where id = ?", assetId).get("status"));
        assertEquals(assetId, row("select image_asset_id from leader where id = ?", myLeaderId).get("image_asset_id"));
    }

    @Autowired private phucuong.ht.modules.media.service.MediaAssetService mediaAssetService;

    private UUID uploadAs(String actor, byte[] png) {
        return mediaAssetService.upload(png, phucuong.ht.modules.media.model.enums.MediaCategory.LEADER_AVATAR, actor).getId();
    }

    @Test
    @Order(8)
    void accountWithoutLeaderCanSetAndReplaceItsAvatarButLeaderAccountsCannotUseThisEndpoint() throws Exception {
        BufferedImage image = new BufferedImage(200, 300, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        Authentication admin = me("no.leader", null);
        // Mọi tài khoản đã đăng nhập tải được ảnh đại diện của chính mình qua /api/media.
        UUID first = uploadAccountAvatar(admin, out.toByteArray());

        MvcResult result = mockMvc.perform(put("/api/auth/me/avatar").with(authentication(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageAssetId", first.toString(), "privacyConsent", true))))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(first.toString(), data.get("imageAssetId").asText());
        assertNotNull(data.get("imageUrl").asText());
        assertEquals("ACTIVE", row("select status from media_asset where id = ?", first).get("status"));
        assertEquals(first, row("select image_asset_id from account where username = 'no.leader'").get("image_asset_id"));

        // Thay ảnh: ảnh cũ được giải phóng.
        UUID second = uploadAccountAvatar(admin, out.toByteArray());
        mockMvc.perform(put("/api/auth/me/avatar").with(authentication(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageAssetId", second.toString(), "privacyConsent", true))))
                .andExpect(status().isOk());
        assertEquals(second, row("select image_asset_id from account where username = 'no.leader'").get("image_asset_id"));
        assertEquals("DELETED", row("select status from media_asset where id = ?", first).get("status"));

        // Thiếu ảnh -> 400; tài khoản gắn huynh trưởng dùng chức năng sửa hồ sơ -> 400.
        mockMvc.perform(put("/api/auth/me/avatar").with(authentication(admin)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/auth/me/avatar").with(authentication(me("me", myLeaderId))).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageAssetId", second.toString(), "privacyConsent", true))))
                .andExpect(status().isBadRequest());
    }

    private UUID uploadAccountAvatar(Authentication auth, byte[] png) throws Exception {
        MvcResult uploaded = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/media")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", png))
                        .param("category", "ACCOUNT_AVATAR").with(authentication(auth)))
                .andExpect(status().isOk()).andReturn();
        return UUID.fromString(objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("data").get("assetId").asText());
    }

    @Test
    @Order(9)
    void birthDateChangesOnlyWithTheBirthDatePermissionOverOwnProfile() throws Exception {
        String permission = "organization.leader.update_birth_date.parish";

        // Không có quyền sửa ngày sinh: đổi ngày sinh bị từ chối, ngày sinh giữ nguyên và hồ sơ báo không sửa được.
        mockMvc.perform(updateRequest(me("me", myLeaderId), Map.of("birthDate", "1996-04-08")))
                .andExpect(status().isForbidden());
        assertEquals("1995-03-07", row("select birth_date::text as birth from leader where id = ?", myLeaderId).get("birth"));
        assertFalse(canEditBirthDate(me("me", myLeaderId)));

        // Quyền chỉnh sửa hồ sơ huynh trưởng (update) tách khỏi quyền sửa ngày sinh nên không thay thế được.
        mockMvc.perform(updateRequest(me("me", myLeaderId, List.of("organization.leader.update.parish")), Map.of("birthDate", "1996-04-08")))
                .andExpect(status().isForbidden());

        // Có quyền sửa ngày sinh đúng phạm vi: sửa được, các trường khác giữ nguyên.
        Authentication withPermission = me("me", myLeaderId, List.of(permission));
        assertTrue(canEditBirthDate(withPermission));
        mockMvc.perform(updateRequest(withPermission, Map.of("birthDate", "1996-04-08")))
                .andExpect(status().isOk());
        Map<String, Object> saved = row("select birth_date::text as birth, first_name, last_name from leader where id = ?", myLeaderId);
        assertEquals("1996-04-08", saved.get("birth"));
        assertEquals("An", saved.get("first_name"));
        assertEquals("Nguyễn Văn", saved.get("last_name"));

        // Ngày sinh trong tương lai vẫn bị từ chối dù có quyền.
        mockMvc.perform(updateRequest(withPermission, Map.of("birthDate", "2999-01-01")))
                .andExpect(status().isBadRequest());
    }

    private boolean canEditBirthDate(Authentication auth) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me/profile").with(authentication(auth)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("canEditBirthDate").asBoolean();
    }

    @Test
    @Order(7)
    void readNotificationsAreDeletedFifteenDaysAfterBeingRead() {
        String insert = "insert into training_notification (id, username, event_type, title, message, read_at, created_at, status) "
                + "values (?, 'me', 'TEST', 't', 'm', ?::timestamptz, now() - interval '40 days', true)";
        jdbc.update(insert, "read-16-days", java.time.OffsetDateTime.now().minusDays(16).toString());
        jdbc.update(insert, "read-14-days", java.time.OffsetDateTime.now().minusDays(14).toString());
        jdbc.update(insert, "unread-old", null);

        long deleted = notificationRetention.purgeReadNotifications();

        assertEquals(1, deleted);
        assertEquals(java.util.List.of("read-14-days", "unread-old"),
                jdbc.queryForList("select id from training_notification where id in ('read-16-days','read-14-days','unread-old') order by id", String.class));
    }
}
