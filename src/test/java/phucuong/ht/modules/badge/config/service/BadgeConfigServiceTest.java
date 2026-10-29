package phucuong.ht.modules.badge.config.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.badge.config.model.entity.BadgeConfig;
import phucuong.ht.modules.badge.config.model.request.BadgeConfigUpsertRequest;
import phucuong.ht.modules.badge.config.model.response.BadgeConfigResponse;
import phucuong.ht.modules.badge.config.repository.BadgeConfigRepository;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver;
import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.service.MediaAssetService;
import phucuong.ht.modules.media.service.MediaFolderService;
import phucuong.ht.modules.media.service.MediaUrls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BadgeConfigServiceTest {

    private static final String SYSTEM = "SYSTEM";

    private final Map<UUID, BadgeConfig> store = new HashMap<>();
    private AccountRepository accountRepository;
    private BadgeConfigService service;

    private final Authentication superAdmin = new UsernamePasswordAuthenticationToken(
            "super-admin", null, List.of(() -> "ROLE_SUPER_ADMIN", () -> "training.badge_config.update.diocese"));

    private MediaAssetService mediaAssets;

    @BeforeEach
    void setUp() {
        BadgeConfigRepository repository = mock(BadgeConfigRepository.class);
        accountRepository = mock(AccountRepository.class);
        securityUtils securityUtils = new securityUtils();
        CommonValidationHelper validationHelper = new CommonValidationHelper(securityUtils);
        CertificateConfigScopeResolver scopeResolver = new CertificateConfigScopeResolver(securityUtils, accountRepository, validationHelper);
        mediaAssets = mock(MediaAssetService.class);
        when(mediaAssets.attach(any(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        MediaFolderService mediaFolders = mock(MediaFolderService.class);
        MediaUrls mediaUrls = mock(MediaUrls.class);
        when(mediaUrls.displayOrLegacy(any(), any(), any())).thenAnswer(invocation -> {
            UUID assetId = invocation.getArgument(0);
            return assetId != null ? "url:" + assetId : invocation.getArgument(1);
        });
        service = new BadgeConfigService(repository, scopeResolver, validationHelper, securityUtils, mediaAssets, mediaFolders, mediaUrls,
                mock(phucuong.ht.modules.media.album.BackgroundAlbumService.class));

        when(repository.persist(any())).thenAnswer(invocation -> {
            BadgeConfig config = invocation.getArgument(0);
            store.put(config.getId(), config);
            return config;
        });
        when(repository.merge(any())).thenAnswer(invocation -> {
            BadgeConfig config = invocation.getArgument(0);
            store.put(config.getId(), config);
            return config;
        });
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(store.get((UUID) invocation.getArgument(0))));
        when(repository.findAllConfigs()).thenAnswer(invocation -> new ArrayList<>(store.values()));
        when(repository.findByScopeKeys(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<String> keys = invocation.getArgument(0);
            return store.values().stream().filter(c -> keys.contains(c.getScopeKey())).toList();
        });
        when(repository.findDefault(anyString())).thenAnswer(invocation -> store.values().stream()
                .filter(c -> c.getScopeKey().equals(invocation.getArgument(0)) && Boolean.TRUE.equals(c.getIsDefault())).findFirst());
        when(repository.findNamesInScope(anyString())).thenAnswer(invocation -> store.values().stream()
                .filter(c -> c.getScopeKey().equals(invocation.getArgument(0))).map(BadgeConfig::getName).toList());
        org.mockito.Mockito.doAnswer(invocation -> {
            store.remove((UUID) invocation.getArgument(0));
            return null;
        }).when(repository).remove(any());
    }

    private Authentication dioceseAdmin(UUID dioceseId, String... authorities) {
        when(accountRepository.findActiveDioceseIdByUsername("admin-diocese")).thenReturn(Optional.of(dioceseId));
        return new UsernamePasswordAuthenticationToken("admin-diocese", null,
                java.util.Arrays.stream(authorities).map(a -> (org.springframework.security.core.GrantedAuthority) () -> a).toList());
    }

    private Authentication deaneryAdmin(UUID deaneryId, UUID dioceseId, String... authorities) {
        when(accountRepository.findActiveDeaneryIdByUsername("admin-deanery")).thenReturn(Optional.of(deaneryId));
        when(accountRepository.findActiveDioceseIdByUsername("admin-deanery")).thenReturn(Optional.of(dioceseId));
        return new UsernamePasswordAuthenticationToken("admin-deanery", null,
                java.util.Arrays.stream(authorities).map(a -> (org.springframework.security.core.GrantedAuthority) () -> a).toList());
    }

    private BadgeConfigUpsertRequest request(String name) {
        BadgeConfigUpsertRequest request = new BadgeConfigUpsertRequest();
        request.setName(name);
        return request;
    }

    private CertificateLayoutItem field(String key, double x, double y) {
        CertificateLayoutItem item = new CertificateLayoutItem();
        item.setType("FIELD");
        item.setFieldKey(key);
        item.setX(x);
        item.setY(y);
        item.setFontSize(0.1);
        return item;
    }

    private ApiException rejected(BadgeConfigUpsertRequest request) {
        return assertThrows(ApiException.class, () -> service.create(request, superAdmin));
    }

    // ---------- tạo ----------

    @Test
    void createAppliesDefaultsAndMakesTheFirstConfigTheScopeDefault() {
        BadgeConfigResponse created = service.create(request("Thẻ chuẩn"), superAdmin);

        assertEquals("Thẻ chuẩn", created.getName());
        assertTrue(created.getIsDefault());
        assertEquals("SYSTEM", created.getScopeLevel());
        assertEquals("A4", created.getPaperSize());
        assertEquals("PORTRAIT", created.getPaperOrientation());
        assertEquals(90.0, created.getCardWidthMm());
        assertEquals(55.0, created.getCardHeightMm());
        assertEquals("LANDSCAPE", created.getCardOrientation());
        assertEquals(10.0, created.getMarginMm());
        assertEquals(0.0, created.getGapMm());
        assertTrue(created.getCropMarks());
        assertFalse(created.getHasBack());
        assertEquals(10, created.getCardsPerPage());
        assertEquals(2, created.getColumns());
        assertEquals(5, created.getRows());
        assertTrue(created.getEditable());
    }

    @Test
    void secondConfigInTheSameScopeIsNotDefault() {
        service.create(request("A"), superAdmin);
        assertFalse(service.create(request("B"), superAdmin).getIsDefault());
    }

    @Test
    void duplicateNamesGetNumericSuffixIgnoringCase() {
        service.create(request("Thẻ"), superAdmin);
        assertEquals("THẺ (1)", service.create(request("THẺ"), superAdmin).getName());
        assertEquals("Thẻ (2)", service.create(request("Thẻ"), superAdmin).getName());
    }

    @Test
    void blankNameFallsBackToDefaultName() {
        assertEquals("Cấu hình thẻ tham dự", service.create(request("   "), superAdmin).getName());
        assertEquals("Cấu hình thẻ tham dự (1)", service.create(request(null), superAdmin).getName());
    }

    @Test
    void overlongNameIsTruncatedTo200Characters() {
        assertEquals(200, service.create(request("x".repeat(300)), superAdmin).getName().length());
    }

    @Test
    void portraitCardSwapsDimensionsSoWidthIsTheShortSide() {
        BadgeConfigUpsertRequest request = request("Dọc");
        request.setCardWidthMm(90.0);
        request.setCardHeightMm(55.0);
        request.setCardOrientation("portrait");
        BadgeConfigResponse created = service.create(request, superAdmin);
        assertEquals(55.0, created.getCardWidthMm());
        assertEquals(90.0, created.getCardHeightMm());
        assertEquals("PORTRAIT", created.getCardOrientation());
    }

    @Test
    void landscapeCardSwapsDimensionsSoWidthIsTheLongSide() {
        BadgeConfigUpsertRequest request = request("Ngang");
        request.setCardWidthMm(55.0);
        request.setCardHeightMm(90.0);
        request.setCardOrientation("LANDSCAPE");
        BadgeConfigResponse created = service.create(request, superAdmin);
        assertEquals(90.0, created.getCardWidthMm());
        assertEquals(55.0, created.getCardHeightMm());
    }

    @Test
    void squareCardIsAllowed() {
        BadgeConfigUpsertRequest request = request("Vuông");
        request.setCardWidthMm(60.0);
        request.setCardHeightMm(60.0);
        BadgeConfigResponse created = service.create(request, superAdmin);
        assertEquals(60.0, created.getCardWidthMm());
        assertEquals(60.0, created.getCardHeightMm());
    }

    @Test
    void cardsPerPageFollowsPaperAndCardSize() {
        BadgeConfigUpsertRequest request = request("A6 trên A4");
        request.setCardWidthMm(105.0);
        request.setCardHeightMm(148.0);
        request.setCardOrientation("PORTRAIT");
        request.setMarginMm(0.0);
        assertEquals(4, service.create(request, superAdmin).getCardsPerPage());

        BadgeConfigUpsertRequest crowded = request("Nhiều thẻ");
        crowded.setPaperSize("a3");
        crowded.setCardWidthMm(85.6);
        crowded.setCardHeightMm(54.0);
        crowded.setMarginMm(5.0);
        crowded.setGapMm(2.0);
        BadgeConfigResponse created = service.create(crowded, superAdmin);
        assertEquals("A3", created.getPaperSize());
        assertEquals(3, created.getColumns());
        assertEquals(7, created.getRows());
    }

    // ---------- kiểm tra đầu vào ----------

    @Test
    void rejectsUnknownPaperSize() {
        BadgeConfigUpsertRequest request = request("x");
        request.setPaperSize("B7");
        assertTrue(rejected(request).getMessage().contains("Khổ giấy không hợp lệ"));
    }

    @Test
    void rejectsUnknownOrientations() {
        BadgeConfigUpsertRequest paper = request("x");
        paper.setPaperOrientation("DIAGONAL");
        assertTrue(rejected(paper).getMessage().contains("Hướng giấy"));

        BadgeConfigUpsertRequest card = request("x");
        card.setCardOrientation("DIAGONAL");
        assertTrue(rejected(card).getMessage().contains("Hướng thẻ"));
    }

    @Test
    void rejectsCardSizeOutsideAllowedRange() {
        for (double invalid : new double[]{19.99, 0, -5, 400.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            BadgeConfigUpsertRequest width = request("x");
            width.setCardWidthMm(invalid);
            assertTrue(rejected(width).getMessage().contains("Chiều rộng thẻ"), "width " + invalid);

            BadgeConfigUpsertRequest height = request("x");
            height.setCardHeightMm(invalid);
            assertTrue(rejected(height).getMessage().contains("Chiều cao thẻ"), "height " + invalid);
        }
    }

    @Test
    void acceptsBoundaryCardSizes() {
        BadgeConfigUpsertRequest request = request("nhỏ nhất");
        request.setCardWidthMm(20.0);
        request.setCardHeightMm(20.0);
        request.setMarginMm(0.0);
        assertNotNull(service.create(request, superAdmin));
    }

    @Test
    void rejectsMarginAndGapOutsideAllowedRange() {
        BadgeConfigUpsertRequest negativeMargin = request("x");
        negativeMargin.setMarginMm(-1.0);
        assertTrue(rejected(negativeMargin).getMessage().contains("Lề giấy"));

        BadgeConfigUpsertRequest hugeMargin = request("x");
        hugeMargin.setMarginMm(50.5);
        assertTrue(rejected(hugeMargin).getMessage().contains("Lề giấy"));

        BadgeConfigUpsertRequest negativeGap = request("x");
        negativeGap.setGapMm(-0.1);
        assertTrue(rejected(negativeGap).getMessage().contains("Khoảng cách"));

        BadgeConfigUpsertRequest hugeGap = request("x");
        hugeGap.setGapMm(31.0);
        assertTrue(rejected(hugeGap).getMessage().contains("Khoảng cách"));
    }

    @Test
    void rejectsCardThatDoesNotFitThePaper() {
        BadgeConfigUpsertRequest request = request("quá to");
        request.setPaperSize("A5");
        request.setCardWidthMm(200.0);
        request.setCardHeightMm(100.0);
        ApiException error = rejected(request);
        assertTrue(error.getMessage().contains("lớn hơn vùng in"));
        assertTrue(store.isEmpty());
    }

    @Test
    void rejectsLayoutItemsWithUnknownTypeOrField() {
        BadgeConfigUpsertRequest badType = request("x");
        CertificateLayoutItem weird = new CertificateLayoutItem();
        weird.setType("IMAGE");
        badType.setFrontLayout(List.of(weird));
        assertTrue(rejected(badType).getMessage().contains("sai loại"));

        BadgeConfigUpsertRequest noType = request("x");
        noType.setFrontLayout(List.of(new CertificateLayoutItem()));
        assertTrue(rejected(noType).getMessage().contains("sai loại"));

        BadgeConfigUpsertRequest badField = request("x");
        badField.setFrontLayout(List.of(field("signerName", 0.5, 0.5)));
        assertTrue(rejected(badField).getMessage().contains("không tồn tại"));

        BadgeConfigUpsertRequest nullItem = request("x");
        nullItem.setFrontLayout(java.util.Arrays.asList((CertificateLayoutItem) null));
        assertTrue(rejected(nullItem).getMessage().contains("sai loại"));
    }

    @Test
    void rejectsLayoutRatiosOutsideZeroToOne() {
        for (double invalid : new double[]{-0.01, 1.01, Double.NaN}) {
            BadgeConfigUpsertRequest request = request("x");
            request.setFrontLayout(List.of(field("holyName", invalid, 0.5)));
            assertTrue(rejected(request).getMessage().contains("Tọa độ X"), "x " + invalid);
        }
        BadgeConfigUpsertRequest y = request("x");
        y.setFrontLayout(List.of(field("holyName", 0.5, 2)));
        assertTrue(rejected(y).getMessage().contains("Tọa độ Y"));

        BadgeConfigUpsertRequest font = request("x");
        CertificateLayoutItem bigFont = field("holyName", 0.5, 0.5);
        bigFont.setFontSize(1.5);
        font.setFrontLayout(List.of(bigFont));
        assertTrue(rejected(font).getMessage().contains("Cỡ chữ"));

        BadgeConfigUpsertRequest qr = request("x");
        CertificateLayoutItem bigQr = new CertificateLayoutItem();
        bigQr.setType("QR");
        bigQr.setSize(3.0);
        qr.setFrontLayout(List.of(bigQr));
        assertTrue(rejected(qr).getMessage().contains("mã QR"));
    }

    @Test
    void acceptsBoundaryRatios() {
        BadgeConfigUpsertRequest request = request("biên");
        request.setFrontLayout(List.of(field("holyName", 0, 0), field("leaderName", 1, 1)));
        assertNotNull(service.create(request, superAdmin));
    }

    @Test
    void rejectsTooManyItemsAndOverlongText() {
        BadgeConfigUpsertRequest many = request("x");
        List<CertificateLayoutItem> items = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            items.add(field("holyName", 0.1, 0.1));
        }
        many.setFrontLayout(items);
        assertTrue(rejected(many).getMessage().contains("tối đa 100"));

        BadgeConfigUpsertRequest longText = request("x");
        CertificateLayoutItem text = new CertificateLayoutItem();
        text.setType("TEXT");
        text.setText("a".repeat(501));
        longText.setFrontLayout(List.of(text));
        assertTrue(rejected(longText).getMessage().contains("tối đa 500"));
    }

    @Test
    void attachesBackgroundsAsAssetsAndReturnsSignedUrls() {
        UUID front = UUID.randomUUID();
        UUID back = UUID.randomUUID();
        BadgeConfigUpsertRequest request = request("nền");
        request.setHasBack(true);
        request.setFrontBackgroundAssetId(front);
        request.setBackBackgroundAssetId(back);
        BadgeConfigResponse created = service.get(service.create(request, superAdmin).getId(), superAdmin);
        assertEquals(front, created.getFrontBackgroundAssetId());
        assertEquals(back, created.getBackBackgroundAssetId());
        assertEquals("url:" + front, created.getFrontBackground());
        org.mockito.Mockito.verify(mediaAssets).attach(front, MediaCategory.BADGE_BACKGROUND, null, "super-admin");
    }

    @Test
    void layoutTypesAreAcceptedCaseInsensitively() {
        BadgeConfigUpsertRequest request = request("chữ thường");
        CertificateLayoutItem text = new CertificateLayoutItem();
        text.setType("text");
        text.setText("Thẻ");
        CertificateLayoutItem qr = new CertificateLayoutItem();
        qr.setType("qr");
        CertificateLayoutItem fieldItem = field("teamName", 0.5, 0.5);
        fieldItem.setType("field");
        request.setFrontLayout(List.of(text, qr, fieldItem));
        assertEquals(3, service.get(service.create(request, superAdmin).getId(), superAdmin).getFrontLayout().size());
    }

    // ---------- mặt sau ----------

    @Test
    void backSideIsDroppedWhenConfigHasNoBack() {
        BadgeConfigUpsertRequest request = request("một mặt");
        request.setHasBack(false);
        request.setBackBackgroundAssetId(UUID.randomUUID());
        request.setBackLayout(List.of(field("teamName", 0.5, 0.5)));
        BadgeConfigResponse created = service.get(service.create(request, superAdmin).getId(), superAdmin);
        assertFalse(created.getHasBack());
        assertNull(created.getBackBackground());
        assertNull(created.getBackBackgroundAssetId());
        assertTrue(created.getBackLayout().isEmpty());
    }

    @Test
    void backSideIsKeptAndSupportsTextAndQr() {
        UUID frontAsset = UUID.randomUUID();
        UUID backAsset = UUID.randomUUID();
        BadgeConfigUpsertRequest request = request("hai mặt");
        request.setHasBack(true);
        request.setFrontBackgroundAssetId(frontAsset);
        request.setBackBackgroundAssetId(backAsset);
        CertificateLayoutItem text = new CertificateLayoutItem();
        text.setType("TEXT");
        text.setText("Lưu ý: mang theo thẻ");
        text.setX(0.5);
        text.setY(0.9);
        CertificateLayoutItem qr = new CertificateLayoutItem();
        qr.setType("QR");
        qr.setX(0.5);
        qr.setY(0.4);
        qr.setSize(0.4);
        request.setBackLayout(List.of(text, qr));
        BadgeConfigResponse created = service.get(service.create(request, superAdmin).getId(), superAdmin);
        assertTrue(created.getHasBack());
        assertEquals("url:" + frontAsset, created.getFrontBackground());
        assertEquals("url:" + backAsset, created.getBackBackground());
        assertEquals(2, created.getBackLayout().size());
    }

    @Test
    void turningBackOffOnUpdateClearsThePreviousBack() {
        BadgeConfigUpsertRequest request = request("đổi");
        UUID backAsset = UUID.randomUUID();
        request.setHasBack(true);
        request.setBackBackgroundAssetId(backAsset);
        request.setBackLayout(List.of(field("teamName", 0.5, 0.5)));
        UUID id = service.create(request, superAdmin).getId();

        BadgeConfigUpsertRequest off = request("đổi");
        off.setHasBack(false);
        BadgeConfigResponse updated = service.update(id, off, superAdmin);
        assertFalse(updated.getHasBack());
        assertNull(updated.getBackBackground());
        assertTrue(updated.getBackLayout().isEmpty());
        org.mockito.Mockito.verify(mediaAssets).release(backAsset);
    }

    // ---------- sửa / mặc định / xóa ----------

    @Test
    void updateKeepsNameWhenRequestNameIsBlankAndAllowsSameNameOfItself() {
        UUID id = service.create(request("Giữ tên"), superAdmin).getId();
        assertEquals("Giữ tên", service.update(id, request(null), superAdmin).getName());
        assertEquals("giữ tên", service.update(id, request("giữ tên"), superAdmin).getName());
    }

    @Test
    void updateRenamesWithSuffixWhenAnotherConfigHasTheName() {
        service.create(request("Một"), superAdmin);
        UUID second = service.create(request("Hai"), superAdmin).getId();
        assertEquals("Một (1)", service.update(second, request("Một"), superAdmin).getName());
    }

    @Test
    void updateRejectsAnUnfittingCardAndLeavesTheStoredConfigUntouched() {
        UUID id = service.create(request("ổn"), superAdmin).getId();
        BadgeConfigUpsertRequest bad = request("ổn");
        bad.setCardWidthMm(300.0);
        assertThrows(ApiException.class, () -> service.update(id, bad, superAdmin));
        assertEquals(90.0, service.get(id, superAdmin).getCardWidthMm());
    }

    @Test
    void updateOfUnknownConfigIsNotFound() {
        ApiException error = assertThrows(ApiException.class, () -> service.update(UUID.randomUUID(), request("x"), superAdmin));
        assertEquals(404, error.getStatus().value());
    }

    @Test
    void setDefaultMovesTheDefaultFlag() {
        UUID first = service.create(request("Một"), superAdmin).getId();
        UUID second = service.create(request("Hai"), superAdmin).getId();
        assertTrue(service.setDefault(second, superAdmin).getIsDefault());
        assertFalse(service.get(first, superAdmin).getIsDefault());
        assertTrue(service.get(second, superAdmin).getIsDefault());
        // Đặt lại cấu hình đã là chung: không đổi gì.
        assertTrue(service.setDefault(second, superAdmin).getIsDefault());
    }

    @Test
    void defaultConfigCannotBeDeletedButOthersCan() {
        UUID first = service.create(request("Một"), superAdmin).getId();
        UUID second = service.create(request("Hai"), superAdmin).getId();
        ApiException error = assertThrows(ApiException.class, () -> service.delete(first, superAdmin));
        assertEquals(409, error.getStatus().value());
        service.delete(second, superAdmin);
        assertFalse(store.containsKey(second));
        assertTrue(store.containsKey(first));
    }

    @Test
    void deleteOfUnknownConfigIsNotFound() {
        assertEquals(404, assertThrows(ApiException.class, () -> service.delete(UUID.randomUUID(), superAdmin)).getStatus().value());
    }

    // ---------- phạm vi sở hữu ----------

    @Test
    void dioceseAdminOwnsItsConfigsAndSeesSystemOnesReadOnly() {
        UUID dioceseId = UUID.randomUUID();
        Authentication admin = dioceseAdmin(dioceseId, "training.badge_config.read.diocese", "training.badge_config.create.diocese",
                "training.badge_config.update.diocese", "training.badge_config.delete.diocese");
        UUID systemConfig = service.create(request("Hệ thống"), superAdmin).getId();
        BadgeConfigResponse own = service.create(request("Giáo phận"), admin);

        assertEquals("DIOCESE", own.getScopeLevel());
        // Phạm vi giáo phận chưa có cấu hình chung nên cấu hình đầu tiên là chung của giáo phận.
        assertTrue(own.getIsDefault());
        List<BadgeConfigResponse> visible = service.options(admin);
        assertEquals(2, visible.size());
        assertFalse(visible.stream().filter(c -> c.getId().equals(systemConfig)).findFirst().orElseThrow().getEditable());

        assertEquals(403, assertThrows(ApiException.class, () -> service.update(systemConfig, request("sửa"), admin)).getStatus().value());
        assertEquals(403, assertThrows(ApiException.class, () -> service.setDefault(systemConfig, admin)).getStatus().value());
        assertEquals(403, assertThrows(ApiException.class, () -> service.delete(systemConfig, admin)).getStatus().value());
    }

    @Test
    void adminCannotSeeAnotherDiocesesConfigs() {
        Authentication other = dioceseAdmin(UUID.randomUUID(), "training.badge_config.read.diocese", "training.badge_config.create.diocese");
        UUID foreignId = service.create(request("Của giáo phận khác"), other).getId();

        Authentication mine = dioceseAdmin(UUID.randomUUID(), "training.badge_config.read.diocese");
        assertEquals(404, assertThrows(ApiException.class, () -> service.get(foreignId, mine)).getStatus().value());
        assertTrue(service.options(mine).isEmpty());
    }

    @Test
    void deaneryAdminInheritsDioceseAndSystemConfigs() {
        UUID dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        Authentication diocese = dioceseAdmin(dioceseId, "training.badge_config.create.diocese");
        UUID dioceseConfig = service.create(request("GP"), diocese).getId();
        UUID systemConfig = service.create(request("HT"), superAdmin).getId();

        Authentication deanery = deaneryAdmin(deaneryId, dioceseId, "training.badge_config.read.deanery", "training.badge_config.create.deanery");
        BadgeConfigResponse own = service.create(request("GH"), deanery);
        assertEquals("DEANERY", own.getScopeLevel());
        List<UUID> ids = service.options(deanery).stream().map(BadgeConfigResponse::getId).toList();
        assertTrue(ids.contains(dioceseConfig));
        assertTrue(ids.contains(systemConfig));
        assertTrue(ids.contains(own.getId()));
        assertFalse(service.get(dioceseConfig, deanery).getEditable());
    }

    @Test
    void userWithoutAnyBadgePermissionIsForbidden() {
        Authentication nobody = new UsernamePasswordAuthenticationToken("nobody", null,
                List.of((org.springframework.security.core.GrantedAuthority) () -> "training.certificate_config.read.diocese"));
        assertEquals(403, assertThrows(ApiException.class, () -> service.options(nobody)).getStatus().value());
    }

    // ---------- tìm kiếm ----------

    @Test
    void searchFiltersSortsAndPaginates() {
        BadgeConfigUpsertRequest a5 = request("Đội trưởng");
        a5.setPaperSize("A5");
        a5.setCardWidthMm(60.0);
        a5.setCardHeightMm(40.0);
        service.create(a5, superAdmin);
        BadgeConfigUpsertRequest back = request("Ban tổ chức");
        back.setHasBack(true);
        service.create(back, superAdmin);
        service.create(request("Tham dự"), superAdmin);

        BaseSearchRequest all = new BaseSearchRequest();
        all.setPage(0);
        all.setSize(2);
        Page<BadgeConfigResponse> first = service.search(all, superAdmin);
        assertEquals(3, first.getTotalElements());
        assertEquals(2, first.getContent().size());
        // Cấu hình chung (đầu tiên được tạo) đứng đầu.
        assertEquals("Đội trưởng", first.getContent().get(0).getName());

        BaseSearchRequest byAccentless = new BaseSearchRequest();
        byAccentless.setPage(0);
        byAccentless.setSize(10);
        byAccentless.setSearch("doi truong");
        assertEquals(1, service.search(byAccentless, superAdmin).getTotalElements());

        BaseSearchRequest withBack = new BaseSearchRequest();
        withBack.setPage(0);
        withBack.setSize(10);
        withBack.setFilters(new HashMap<>(Map.of("hasBack", "true")));
        assertEquals("Ban tổ chức", service.search(withBack, superAdmin).getContent().get(0).getName());

        BaseSearchRequest paper = new BaseSearchRequest();
        paper.setPage(0);
        paper.setSize(10);
        paper.setFilters(new HashMap<>(Map.of("paperSize", "a5")));
        assertEquals(1, service.search(paper, superAdmin).getTotalElements());

        BaseSearchRequest outOfRange = new BaseSearchRequest();
        outOfRange.setPage(9);
        outOfRange.setSize(10);
        assertTrue(service.search(outOfRange, superAdmin).getContent().isEmpty());

        BaseSearchRequest byName = new BaseSearchRequest();
        byName.setPage(0);
        byName.setSize(10);
        byName.setSortBy("name");
        byName.setSortDirection("DESC");
        assertEquals("Tham dự", service.search(byName, superAdmin).getContent().get(0).getName());
    }

    @Test
    void summariesDoNotCarryBackgroundsOrLayouts() {
        BadgeConfigUpsertRequest request = request("nặng");
        request.setFrontBackgroundAssetId(UUID.randomUUID());
        request.setFrontLayout(List.of(field("holyName", 0.5, 0.5)));
        service.create(request, superAdmin);
        BadgeConfigResponse summary = service.options(superAdmin).get(0);
        assertNull(summary.getFrontBackground());
        assertNull(summary.getFrontLayout());
    }

    @Test
    void fieldCatalogHasNoCertificateOnlyFieldsAndHasTeamName() {
        List<String> keys = service.fieldCatalog().stream().map(item -> item.getKey()).toList();
        assertTrue(keys.contains("teamName"));
        assertTrue(keys.contains("holyName"));
        assertTrue(keys.contains("leaderFullName"));
        assertFalse(keys.contains("signerName"));
        assertFalse(keys.contains("certificateCode"));
        assertFalse(keys.contains("result"));
        assertEquals(keys.size(), keys.stream().distinct().count());
    }
}
