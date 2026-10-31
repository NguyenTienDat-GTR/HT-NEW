package phucuong.ht.modules.badge.export.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.badge.config.model.entity.BadgeConfig;
import phucuong.ht.modules.badge.config.repository.BadgeConfigRepository;
import phucuong.ht.modules.badge.config.service.BadgePdfRenderer;
import phucuong.ht.modules.badge.export.model.BadgeExportRequest;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver;
import phucuong.ht.modules.certificate.config.service.CertificatePdfRenderer;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.training.TrainingTestFixtures;
import phucuong.ht.modules.training.courseAttendance.service.AttendanceTokenService;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipantSupport;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BadgeExportServiceTest {

    private BadgeConfigRepository configRepository;
    private CourseParticipantSupport participantSupport;
    private BadgeExportService service;

    private Diocese diocese;
    private Deanery deanery;
    private Parish parish;
    private TrainingCourse course;

    @BeforeEach
    void setUp() {
        configRepository = mock(BadgeConfigRepository.class);
        participantSupport = mock(CourseParticipantSupport.class);
        CommonValidationHelper validationHelper = new CommonValidationHelper(new securityUtils());
        service = new BadgeExportService(configRepository, participantSupport,
                new AttendanceTokenService("unit-test-attendance-secret-0123456789"),
                new BadgePdfRenderer(new CertificatePdfRenderer()), validationHelper,
                org.mockito.Mockito.mock(phucuong.ht.modules.media.service.MediaAssetService.class));

        diocese = TrainingTestFixtures.diocese("Giáo phận Phú Cường");
        deanery = TrainingTestFixtures.deanery("Giáo hạt Thủ Dầu Một", diocese);
        parish = TrainingTestFixtures.parish("Giáo xứ Chánh Tòa", deanery);
        course = TrainingTestFixtures.course("KH-1", "Khóa Dự Trưởng", deanery);
        when(participantSupport.requireRegularCourse(eq("KH-1"), any())).thenReturn(course);
    }

    // ---------- dữ liệu ----------

    private CertificateLayoutItem field(String key, double x, double y) {
        CertificateLayoutItem item = new CertificateLayoutItem();
        item.setType("FIELD");
        item.setFieldKey(key);
        item.setX(x);
        item.setY(y);
        item.setFontSize(0.1);
        item.setAlign("center");
        return item;
    }

    private CertificateLayoutItem qr(double x, double y) {
        CertificateLayoutItem item = new CertificateLayoutItem();
        item.setType("QR");
        item.setX(x);
        item.setY(y);
        item.setSize(0.4);
        return item;
    }

    private BadgeConfig config(String scopeKey, boolean isDefault, boolean hasBack) {
        BadgeConfig config = new BadgeConfig();
        config.setId(UUID.randomUUID());
        config.setName("Cấu hình " + scopeKey);
        config.setScopeKey(scopeKey);
        config.setIsDefault(isDefault);
        config.setPaperSize("A4");
        config.setPaperOrientation("PORTRAIT");
        config.setCardWidthMm(new BigDecimal("90"));
        config.setCardHeightMm(new BigDecimal("55"));
        config.setCardOrientation("LANDSCAPE");
        config.setMarginMm(new BigDecimal("10"));
        config.setGapMm(BigDecimal.ZERO);
        config.setCropMarks(true);
        config.setHasBack(hasBack);
        config.setFrontLayout(new ArrayList<>(List.of(field("leaderName", 0.5, 0.3), field("teamName", 0.5, 0.6))));
        config.setBackLayout(new ArrayList<>(List.of(field("courseName", 0.5, 0.4))));
        config.setStatus(Boolean.TRUE);
        return config;
    }

    private String deaneryScope() {
        return CertificateConfigScopeResolver.keyOf(CertificateConfigScopeResolver.DEANERY, deanery.getId());
    }

    private String dioceseScope() {
        return CertificateConfigScopeResolver.keyOf(CertificateConfigScopeResolver.DIOCESE, diocese.getId());
    }

    private List<CourseParticipation> participants(int count, String team) {
        List<CourseParticipation> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String name = String.format("Tên%02d", i);
            list.add(TrainingTestFixtures.participation("p-" + i,
                    TrainingTestFixtures.leader("Giuse", "Họ", name, "u" + i + "@mail.vn", parish), course, team));
        }
        return list;
    }

    private void givenParticipants(List<CourseParticipation> list) {
        when(participantSupport.participantsOf("KH-1")).thenReturn(list);
    }

    private void givenDefault(BadgeConfig config) {
        when(configRepository.findDefault(config.getScopeKey())).thenReturn(Optional.of(config));
    }

    private BadgeExportRequest all() {
        return new BadgeExportRequest(null, null, null, null, null);
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private static String page(byte[] pdf, int page) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(document).replaceAll("\\s+", " ").trim();
        }
    }

    private ApiException failure(BadgeExportRequest request) {
        return assertThrows(ApiException.class, () -> service.export("KH-1", request));
    }

    // ---------- số trang ----------

    @Test
    void tenCardsPerPageWithoutBackProducesOnePagePerTenParticipants() throws Exception {
        givenDefault(config(deaneryScope(), true, false));
        givenParticipants(participants(25, "Đội 1"));

        BadgeExportService.PdfExport export = service.export("KH-1", all());

        assertEquals(25, export.badgeCount());
        assertEquals(3, export.pageCount());
        assertEquals(3, pages(export.content()));
    }

    @Test
    void configWithBackInterleavesFrontAndBackPages() throws Exception {
        givenDefault(config(deaneryScope(), true, true));
        givenParticipants(participants(25, "Đội 1"));

        BadgeExportService.PdfExport export = service.export("KH-1", all());

        assertEquals(6, export.pageCount());
        assertEquals(6, pages(export.content()));
        // Trang lẻ = mặt trước (có tên), trang chẵn = mặt sau (tên khóa).
        assertTrue(page(export.content(), 1).contains("Tên01"));
        assertFalse(page(export.content(), 1).contains("Khóa Dự Trưởng"));
        assertTrue(page(export.content(), 2).contains("Khóa Dự Trưởng"));
        assertFalse(page(export.content(), 2).contains("Tên01"));
    }

    @Test
    void includeBackFalseExportsOnlyTheFrontEvenIfConfigHasBack() throws Exception {
        givenDefault(config(deaneryScope(), true, true));
        givenParticipants(participants(12, null));

        BadgeExportService.PdfExport export = service.export("KH-1", new BadgeExportRequest(null, null, null, false, null));

        assertEquals(2, export.pageCount());
    }

    @Test
    void includeBackTrueOnAConfigWithoutBackStillExportsOnlyFronts() throws Exception {
        givenDefault(config(deaneryScope(), true, false));
        givenParticipants(participants(3, null));
        assertEquals(1, service.export("KH-1", new BadgeExportRequest(null, null, null, true, null)).pageCount());
    }

    @Test
    void exactMultipleOfCardsPerPageDoesNotAddAnEmptyPage() throws Exception {
        givenDefault(config(deaneryScope(), true, true));
        givenParticipants(participants(10, null));
        assertEquals(2, service.export("KH-1", all()).pageCount());
        givenParticipants(participants(11, null));
        assertEquals(4, service.export("KH-1", all()).pageCount());
    }

    @Test
    void singleParticipantExportsOneCard() throws Exception {
        givenDefault(config(deaneryScope(), true, false));
        givenParticipants(participants(1, "Đội 1"));
        BadgeExportService.PdfExport export = service.export("KH-1", all());
        assertEquals(1, export.badgeCount());
        assertTrue(export.filename().endsWith("(1 thẻ).pdf"));
    }

    // ---------- mặt sau đối xứng ----------

    @Test
    void backPageMirrorsColumnsSoItLinesUpWhenFlippedOnTheLongEdge() throws Exception {
        givenDefault(config(deaneryScope(), true, true));
        givenParticipants(participants(4, null));

        byte[] pdf = service.export("KH-1", all()).content();
        BadgeConfig sameConfig = config(deaneryScope(), true, true);
        sameConfig.setBackLayout(new ArrayList<>(List.of(field("leaderName", 0.5, 0.4))));
        givenDefault(sameConfig);
        pdf = service.export("KH-1", all()).content();

        // Mặt trước: hàng 1 = Tên01 Tên02, hàng 2 = Tên03 Tên04. Mặt sau (lật cạnh dài): đổi cột trái <-> phải.
        assertEquals("Tên01 Tên02 Tên03 Tên04", page(pdf, 1).replaceAll("Họ ", "").replace("Giuse ", ""));
        assertEquals("Tên02 Tên01 Tên04 Tên03", page(pdf, 2).replaceAll("Họ ", "").replace("Giuse ", ""));
    }

    @Test
    void shortEdgeFlipMirrorsRowsInstead() throws Exception {
        BadgeConfig config = config(deaneryScope(), true, true);
        config.setBackLayout(new ArrayList<>(List.of(field("leaderName", 0.5, 0.4))));
        givenDefault(config);
        givenParticipants(participants(4, null));

        byte[] pdf = service.export("KH-1", new BadgeExportRequest(null, null, null, null, "SHORT")).content();

        assertEquals("Tên03 Tên04 Tên01 Tên02", page(pdf, 2).replaceAll("Họ ", "").replace("Giuse ", ""));
    }

    // ---------- chọn hồ sơ ----------

    @Test
    void participantsAreOrderedByTeamThenNameWithUnassignedLast() throws Exception {
        givenDefault(config(deaneryScope(), true, false));
        List<CourseParticipation> list = new ArrayList<>();
        list.add(TrainingTestFixtures.participation("p-a", TrainingTestFixtures.leader("G", "H", "Chưa", "a@x.vn", parish), course, null));
        list.add(TrainingTestFixtures.participation("p-b", TrainingTestFixtures.leader("G", "H", "Mười", "b@x.vn", parish), course, "Đội 10"));
        list.add(TrainingTestFixtures.participation("p-c", TrainingTestFixtures.leader("G", "H", "Hai", "c@x.vn", parish), course, "Đội 2"));
        givenParticipants(list);

        String text = page(service.export("KH-1", all()).content(), 1);

        assertTrue(text.indexOf("Hai") < text.indexOf("Mười"), text);
        assertTrue(text.indexOf("Mười") < text.indexOf("Chưa"), text);
    }

    @Test
    void teamFilterIsCaseAndAccentInsensitive() throws Exception {
        givenDefault(config(deaneryScope(), true, false));
        List<CourseParticipation> list = participants(3, "Đội 1");
        list.addAll(participants(2, "Đội 2"));
        list.forEach(p -> p.setId(UUID.randomUUID().toString()));
        givenParticipants(list);

        assertEquals(3, service.export("KH-1", new BadgeExportRequest(null, null, "doi 1", null, null)).badgeCount());
        assertEquals(2, service.export("KH-1", new BadgeExportRequest(null, null, " ĐỘI 2 ", null, null)).badgeCount());
    }

    @Test
    void blankTeamFilterMeansEveryone() throws Exception {
        givenDefault(config(deaneryScope(), true, false));
        givenParticipants(participants(3, "Đội 1"));
        assertEquals(3, service.export("KH-1", new BadgeExportRequest(null, null, "   ", null, null)).badgeCount());
    }

    @Test
    void explicitParticipationIdsSelectOnlyThoseCards() throws Exception {
        givenDefault(config(deaneryScope(), true, false));
        givenParticipants(participants(5, null));
        BadgeExportService.PdfExport export = service.export("KH-1",
                new BadgeExportRequest(null, List.of("p-2", "p-4", "p-2"), null, null, null));
        assertEquals(2, export.badgeCount());
    }

    @Test
    void idsOutsideTheParticipantListAreRejected() {
        givenDefault(config(deaneryScope(), true, false));
        givenParticipants(participants(2, null));
        ApiException error = failure(new BadgeExportRequest(null, List.of("p-1", "ghost"), null, null, null));
        assertTrue(error.getMessage().contains("không thuộc danh sách tham dự"));
    }

    @Test
    void emptyParticipantListIsRejected() {
        givenDefault(config(deaneryScope(), true, false));
        givenParticipants(List.of());
        assertTrue(failure(all()).getMessage().contains("chưa có hồ sơ nào"));
    }

    @Test
    void teamFilterMatchingNobodyIsRejected() {
        givenDefault(config(deaneryScope(), true, false));
        givenParticipants(participants(2, "Đội 1"));
        assertTrue(failure(new BadgeExportRequest(null, null, "Đội 9", null, null)).getMessage().contains("Không có hồ sơ nào phù hợp"));
    }

    @Test
    void tooManyBadgesInOneExportIsRejected() {
        givenDefault(config(deaneryScope(), true, false));
        givenParticipants(participants(2001, null));
        assertTrue(failure(all()).getMessage().contains("tối đa 2000"));
    }

    // ---------- chọn cấu hình ----------

    @Test
    void usesTheDeaneryDefaultBeforeDioceseAndSystem() throws Exception {
        BadgeConfig deaneryConfig = config(deaneryScope(), true, false);
        deaneryConfig.setFrontLayout(new ArrayList<>(List.of(field("holyName", 0.5, 0.3))));
        givenDefault(deaneryConfig);
        givenDefault(config(dioceseScope(), true, false));
        givenDefault(config("SYSTEM", true, false));
        givenParticipants(participants(1, null));

        assertEquals("Giuse", page(service.export("KH-1", all()).content(), 1));
    }

    @Test
    void fallsBackToDioceseThenSystemDefault() throws Exception {
        BadgeConfig systemConfig = config("SYSTEM", true, false);
        systemConfig.setFrontLayout(new ArrayList<>(List.of(field("courseCode", 0.5, 0.3))));
        givenDefault(systemConfig);
        givenParticipants(participants(1, null));
        assertEquals("KH-1", page(service.export("KH-1", all()).content(), 1));

        BadgeConfig dioceseConfig = config(dioceseScope(), true, false);
        dioceseConfig.setFrontLayout(new ArrayList<>(List.of(field("courseCode", 0.5, 0.3), field("hostYear", 0.5, 0.6))));
        givenDefault(dioceseConfig);
        assertEquals("KH-1 2026", page(service.export("KH-1", all()).content(), 1));
    }

    @Test
    void noConfigAnywhereGivesAHelpfulError() {
        givenParticipants(participants(1, null));
        when(configRepository.findDefault(any())).thenReturn(Optional.empty());
        assertTrue(failure(all()).getMessage().contains("Chưa có cấu hình thẻ tham dự"));
    }

    @Test
    void explicitConfigMustBelongToTheCoursesScopeChain() throws Exception {
        givenParticipants(participants(1, null));
        BadgeConfig inChain = config(dioceseScope(), false, false);
        inChain.setFrontLayout(new ArrayList<>(List.of(field("courseCode", 0.5, 0.3))));
        when(configRepository.findById(inChain.getId())).thenReturn(Optional.of(inChain));
        assertEquals("KH-1", page(service.export("KH-1", new BadgeExportRequest(inChain.getId(), null, null, null, null)).content(), 1));

        BadgeConfig foreign = config("DIOCESE:" + UUID.randomUUID(), false, false);
        when(configRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        ApiException error = failure(new BadgeExportRequest(foreign.getId(), null, null, null, null));
        assertEquals(404, error.getStatus().value());

        UUID unknown = UUID.randomUUID();
        when(configRepository.findById(unknown)).thenReturn(Optional.empty());
        assertEquals(404, failure(new BadgeExportRequest(unknown, null, null, null, null)).getStatus().value());
    }

    @Test
    void dioceseManagedCourseUsesDioceseScopeFirst() throws Exception {
        TrainingCourse dioceseCourse = TrainingTestFixtures.course("KH-2", "Khóa Giáo Phận", null);
        dioceseCourse.setDiocese(diocese);
        when(participantSupport.requireRegularCourse(eq("KH-2"), any())).thenReturn(dioceseCourse);
        when(participantSupport.participantsOf("KH-2")).thenReturn(List.of(TrainingTestFixtures.participation("p-1",
                TrainingTestFixtures.leader("G", "H", "A", "a@x.vn", parish), dioceseCourse, null)));
        BadgeConfig dioceseConfig = config(dioceseScope(), true, false);
        dioceseConfig.setFrontLayout(new ArrayList<>(List.of(field("courseCode", 0.5, 0.3))));
        givenDefault(dioceseConfig);

        assertEquals("KH-2", page(service.export("KH-2", all()).content(), 1));
        assertEquals(List.of(dioceseScope(), "SYSTEM"), BadgeExportService.chainOf(dioceseCourse));
    }

    @Test
    void configThatCannotFitThePaperAnymoreIsRejected() {
        BadgeConfig broken = config(deaneryScope(), true, false);
        broken.setCardWidthMm(new BigDecimal("250"));
        givenDefault(broken);
        givenParticipants(participants(1, null));
        assertTrue(failure(all()).getMessage().contains("không xếp vừa khổ giấy"));
    }

    // ---------- nội dung ----------

    @Test
    void qrIsOnlyGeneratedWhenALayoutContainsOne() throws Exception {
        BadgeConfig plain = config(deaneryScope(), true, false);
        givenDefault(plain);
        givenParticipants(participants(2, null));
        byte[] without = service.export("KH-1", all()).content();

        BadgeConfig withQr = config(deaneryScope(), true, false);
        withQr.setFrontLayout(new ArrayList<>(List.of(field("leaderName", 0.5, 0.3), field("teamName", 0.5, 0.6), qr(0.85, 0.5))));
        givenDefault(withQr);
        byte[] with = service.export("KH-1", all()).content();

        assertTrue(with.length > without.length);
    }

    @Test
    void qrOnTheBackSideAloneStillGeneratesCodes() throws Exception {
        BadgeConfig config = config(deaneryScope(), true, true);
        config.setBackLayout(new ArrayList<>(List.of(qr(0.5, 0.5))));
        givenDefault(config);
        givenParticipants(participants(2, null));
        assertEquals(2, service.export("KH-1", all()).pageCount());
    }

    @Test
    void emptyFieldsAreSkippedInsteadOfPrintingBlanks() throws Exception {
        BadgeConfig config = config(deaneryScope(), true, false);
        givenDefault(config);
        givenParticipants(participants(1, null));
        String text = page(service.export("KH-1", all()).content(), 1);
        // Không có tên đội -> trường teamName bị bỏ qua, vẫn có tên.
        assertEquals("Họ Tên01", text);
    }

    @Test
    void staticTextItemsAreDrawnVerbatim() throws Exception {
        CertificateLayoutItem text = new CertificateLayoutItem();
        text.setType("TEXT");
        text.setText("BAN TỔ CHỨC");
        text.setX(0.5);
        text.setY(0.1);
        text.setFontSize(0.1);
        text.setAlign("center");
        BadgeConfig config = config(deaneryScope(), true, false);
        config.setFrontLayout(new ArrayList<>(List.of(text)));
        givenDefault(config);
        givenParticipants(participants(1, null));
        assertEquals("BAN TỔ CHỨC", page(service.export("KH-1", all()).content(), 1));
    }

    @Test
    void layoutItemsMissingCoordinatesDoNotCrash() throws Exception {
        CertificateLayoutItem bare = new CertificateLayoutItem();
        bare.setType("FIELD");
        bare.setFieldKey("leaderName");
        BadgeConfig config = config(deaneryScope(), true, false);
        config.setFrontLayout(new ArrayList<>(List.of(bare)));
        config.setLegacyFrontBackground("data:image/png;base64,not-base64!!");
        givenDefault(config);
        givenParticipants(participants(1, null));
        assertEquals(1, pages(service.export("KH-1", all()).content()));
    }

    @Test
    void mergeValuesCoverEveryFieldOfTheCatalog() {
        CourseParticipation participation = participants(1, "Đội 7").get(0);
        participation.getLeader().setHolyName("Maria");
        participation.getLeader().setLastName("Trần Thị");
        participation.getLeader().setFirstName("Hoa");

        Map<String, String> values = BadgeExportService.buildValues(course, participation);

        assertEquals("Maria", values.get("holyName"));
        assertEquals("Trần Thị Hoa", values.get("leaderName"));
        assertEquals("Maria Trần Thị Hoa", values.get("leaderFullName"));
        assertEquals("Giáo xứ Chánh Tòa", values.get("leaderParishName"));
        assertEquals("Giáo hạt Thủ Dầu Một", values.get("leaderDeaneryName"));
        assertEquals("Giáo phận Phú Cường", values.get("leaderDioceseName"));
        assertEquals("Đội 7", values.get("teamName"));
        assertEquals("Khóa Dự Trưởng", values.get("courseName"));
        assertEquals("KH-1", values.get("courseCode"));
        assertEquals("2026", values.get("hostYear"));
        assertEquals("Nhà thờ Chánh Tòa", values.get("courseLocation"));
        assertTrue(values.get("coursePeriod").startsWith("từ "));
        assertFalse(values.get("courseStartDate").isEmpty());
        for (phucuong.ht.modules.badge.config.model.BadgeMergeField field : phucuong.ht.modules.badge.config.model.BadgeMergeField.values()) {
            assertTrue(values.containsKey(field.getKey()), "thiếu giá trị cho trường " + field.getKey());
        }
    }

    @Test
    void mergeValuesToleratesMissingOptionalData() {
        TrainingCourse bare = new TrainingCourse();
        bare.setCourseCode("K");
        CourseParticipation participation = TrainingTestFixtures.participation("p", TrainingTestFixtures.leader(null, null, "Chỉ tên", "a@x.vn", parish), bare, null);
        Map<String, String> values = BadgeExportService.buildValues(bare, participation);
        assertEquals("", values.get("holyName"));
        assertEquals("Chỉ tên", values.get("leaderName"));
        assertEquals("", values.get("hostYear"));
        assertEquals("", values.get("coursePeriod"));
        assertEquals("", values.get("teamName"));

        bare.setStartDate(java.time.LocalDate.of(2026, 8, 1));
        assertEquals("01/08/2026", BadgeExportService.buildValues(bare, participation).get("coursePeriod"));
    }

    @Test
    void backgroundDecodingHandlesDataUrlsAndGarbage() {
        byte[] bytes = {1, 2, 3};
        String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, BadgeExportService.decodeBackground("data:image/png;base64," + base64));
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, BadgeExportService.decodeBackground(base64));
        assertEquals(null, BadgeExportService.decodeBackground(null));
        assertEquals(null, BadgeExportService.decodeBackground("  "));
        assertEquals(null, BadgeExportService.decodeBackground("data:image/png;base64,%%%"));
    }

    // ---------- cấu hình chọn được cho khóa ----------

    @Test
    void usableConfigsAreTheOnesInTheCoursesScopeChainClosestFirstWithTheAppliedOneFlagged() {
        BadgeConfig deaneryDefault = config(deaneryScope(), true, false);
        deaneryDefault.setName("Giáo hạt chung");
        BadgeConfig deaneryOther = config(deaneryScope(), false, true);
        deaneryOther.setName("Giáo hạt hai mặt");
        BadgeConfig dioceseDefault = config(dioceseScope(), true, false);
        dioceseDefault.setName("Giáo phận chung");
        BadgeConfig systemDefault = config("SYSTEM", true, false);
        systemDefault.setName("Hệ thống");
        when(configRepository.findDefault(deaneryScope())).thenReturn(Optional.of(deaneryDefault));
        when(configRepository.findByScopeKeys(List.of(deaneryScope(), dioceseScope(), "SYSTEM")))
                .thenReturn(List.of(systemDefault, dioceseDefault, deaneryOther, deaneryDefault));

        List<BadgeExportService.UsableConfig> configs = service.usableConfigs("KH-1");

        assertEquals(List.of("Giáo hạt chung", "Giáo hạt hai mặt", "Giáo phận chung", "Hệ thống"), configs.stream().map(BadgeExportService.UsableConfig::name).toList());
        assertEquals(List.of(true, false, false, false), configs.stream().map(BadgeExportService.UsableConfig::applied).toList());
        assertEquals(List.of("DEANERY", "DEANERY", "DIOCESE", "SYSTEM"), configs.stream().map(BadgeExportService.UsableConfig::scopeLevel).toList());
        assertTrue(configs.get(1).hasBack());
        assertEquals(10, configs.get(0).cardsPerPage());
        assertEquals(90.0, configs.get(0).cardWidthMm());
        assertEquals("A4", configs.get(0).paperSize());
    }

    @Test
    void usableConfigsAppliedFlagFallsBackToTheNearestScopeWithADefault() {
        BadgeConfig systemDefault = config("SYSTEM", true, false);
        BadgeConfig deaneryNonDefault = config(deaneryScope(), false, false);
        when(configRepository.findDefault("SYSTEM")).thenReturn(Optional.of(systemDefault));
        when(configRepository.findByScopeKeys(List.of(deaneryScope(), dioceseScope(), "SYSTEM"))).thenReturn(List.of(deaneryNonDefault, systemDefault));

        List<BadgeExportService.UsableConfig> configs = service.usableConfigs("KH-1");

        assertEquals(2, configs.size());
        assertFalse(configs.get(0).applied());
        assertTrue(configs.get(1).applied());
    }

    @Test
    void usableConfigsIsEmptyWhenThereIsNoneAndReportsZeroCapacityForBrokenOnes() {
        when(configRepository.findByScopeKeys(List.of(deaneryScope(), dioceseScope(), "SYSTEM"))).thenReturn(List.of());
        assertTrue(service.usableConfigs("KH-1").isEmpty());

        BadgeConfig broken = config(deaneryScope(), true, false);
        broken.setCardWidthMm(new BigDecimal("250"));
        when(configRepository.findByScopeKeys(List.of(deaneryScope(), dioceseScope(), "SYSTEM"))).thenReturn(List.of(broken));
        assertEquals(0, service.usableConfigs("KH-1").get(0).cardsPerPage());
    }

    @Test
    void usableConfigsRejectsPastCourses() {
        when(participantSupport.requireRegularCourse(eq("PAST"), any()))
                .thenThrow(new CommonValidationHelper(new securityUtils()).validationError("Khóa quá khứ không áp dụng thẻ tham dự"));
        assertThrows(ApiException.class, () -> service.usableConfigs("PAST"));
    }

    @Test
    void filenameIsSafeForDownloads() throws Exception {
        TrainingCourse unsafe = TrainingTestFixtures.course("KH-3", "Khóa: A/B\\C?*", deanery);
        when(participantSupport.requireRegularCourse(eq("KH-3"), any())).thenReturn(unsafe);
        when(participantSupport.participantsOf("KH-3")).thenReturn(List.of(
                TrainingTestFixtures.participation("p-1", TrainingTestFixtures.leader("G", "H", "A", "a@x.vn", parish), unsafe, "Đội 1"),
                TrainingTestFixtures.participation("p-2", TrainingTestFixtures.leader("G", "H", "B", "b@x.vn", parish), unsafe, "Đội 1")));
        givenDefault(config(deaneryScope(), true, false));

        String name = service.export("KH-3", new BadgeExportRequest(null, null, "Đội 1", null, null)).filename();

        assertTrue(name.startsWith("Thẻ tham dự - Khóa"), name);
        assertTrue(name.endsWith(" - Đội 1.pdf"), name);
        assertFalse(name.matches(".*[\\\\/:*?\"<>|].*"));
        assertTrue(name.endsWith(".pdf"));
    }
}
