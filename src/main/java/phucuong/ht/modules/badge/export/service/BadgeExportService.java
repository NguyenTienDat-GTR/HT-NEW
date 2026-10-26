package phucuong.ht.modules.badge.export.service;

import com.google.zxing.common.BitMatrix;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.badge.config.model.entity.BadgeConfig;
import phucuong.ht.modules.badge.config.repository.BadgeConfigRepository;
import phucuong.ht.modules.badge.config.service.BadgeConfigService;
import phucuong.ht.modules.badge.config.service.BadgePdfRenderer;
import phucuong.ht.modules.badge.config.service.BadgeSheetLayout;
import phucuong.ht.modules.badge.export.model.BadgeExportRequest;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver;
import phucuong.ht.modules.certificate.config.service.CertificatePdfRenderer;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.courseAttendance.service.AttendanceTokenService;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipantSupport;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Xuất thẻ tham dự của một khóa ra PDF: mỗi trang giấy xếp nhiều thẻ theo khổ thẻ của cấu hình; có mặt sau thì sau mỗi trang
// mặt trước là một trang mặt sau đối xứng (để in hai mặt khớp nhau). Mã QR (nếu bố cục có) là mã điểm danh ký số của hồ sơ.
@Service
@RequiredArgsConstructor
public class BadgeExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_BADGES = 2000;
    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_QR = "QR";

    private final BadgeConfigRepository configRepository;
    private final CourseParticipantSupport participantSupport;
    private final AttendanceTokenService tokenService;
    private final BadgePdfRenderer pdfRenderer;
    private final CommonValidationHelper validationHelper;

    public record PdfExport(byte[] content, String filename, int badgeCount, int pageCount) {
    }

    // Cấu hình xuất thẻ được cho khóa (thuộc chuỗi phạm vi của khóa). applied = cấu hình sẽ được dùng khi không chọn cấu hình nào.
    public record UsableConfig(UUID id, String name, boolean isDefault, boolean applied, String scopeLevel, boolean hasBack,
                               double cardWidthMm, double cardHeightMm, int cardsPerPage, String paperSize, String paperOrientation) {
    }

    // Danh sách cấu hình thẻ chọn được khi xuất thẻ cho khóa; quyền như xem hồ sơ tham dự của khóa (không cần quyền cấu hình thẻ).
    @Transactional(readOnly = true)
    public List<UsableConfig> usableConfigs(String courseCode) {
        TrainingCourse course = participantSupport.requireRegularCourse(courseCode, "thẻ tham dự");
        List<String> chain = chainOf(course);
        UUID appliedId = null;
        for (String scopeKey : chain) {
            Optional<BadgeConfig> found = configRepository.findDefault(scopeKey);
            if (found.isPresent()) {
                appliedId = found.get().getId();
                break;
            }
        }
        UUID applied = appliedId;
        return configRepository.findByScopeKeys(chain).stream()
                .sorted(java.util.Comparator.comparingInt((BadgeConfig c) -> chain.indexOf(c.getScopeKey()))
                        .thenComparing(c -> !Boolean.TRUE.equals(c.getIsDefault()))
                        .thenComparing(c -> c.getName() == null ? "" : c.getName().toLowerCase(Locale.ROOT)))
                .map(config -> {
                    BadgeSheetLayout.Grid grid = BadgeConfigService.gridOf(config);
                    return new UsableConfig(config.getId(), config.getName(), Boolean.TRUE.equals(config.getIsDefault()),
                            config.getId().equals(applied), CertificateConfigScopeResolver.levelOf(config.getScopeKey()),
                            Boolean.TRUE.equals(config.getHasBack()), config.getCardWidthMm().doubleValue(), config.getCardHeightMm().doubleValue(),
                            grid == null ? 0 : grid.perPage(), config.getPaperSize(), config.getPaperOrientation());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PdfExport export(String courseCode, BadgeExportRequest request) {
        TrainingCourse course = participantSupport.requireRegularCourse(courseCode, "thẻ tham dự");
        BadgeConfig config = resolveConfig(course, request.configId());
        BadgeSheetLayout.Grid grid = BadgeConfigService.gridOf(config);
        if (grid == null) {
            throw validationHelper.validationError("Khổ thẻ của cấu hình \"" + config.getName() + "\" không xếp vừa khổ giấy, hãy sửa lại cấu hình");
        }

        List<CourseParticipation> participants = selectParticipants(courseCode, request);
        if (participants.size() > MAX_BADGES) {
            throw validationHelper.validationError("Mỗi lần xuất tối đa " + MAX_BADGES + " thẻ, hãy lọc theo đội hoặc chọn bớt hồ sơ");
        }

        boolean includeBack = Boolean.TRUE.equals(config.getHasBack()) && (request.includeBack() == null || request.includeBack());
        BadgeSheetLayout.FlipEdge flipEdge = BadgeSheetLayout.FlipEdge.parse(request.flipEdge());
        boolean mirrorColumns = BadgeSheetLayout.mirrorColumns(grid.paperWidthMm(), grid.paperHeightMm(), flipEdge);

        byte[] frontBackground = decodeBackground(config.getFrontBackground());
        byte[] backBackground = includeBack ? decodeBackground(config.getBackBackground()) : null;

        List<BadgePdfRenderer.Sheet> sheets = new ArrayList<>();
        int perPage = grid.perPage();
        for (int start = 0; start < participants.size(); start += perPage) {
            List<CourseParticipation> chunk = participants.subList(start, Math.min(start + perPage, participants.size()));
            List<BadgePdfRenderer.Side> fronts = new ArrayList<>();
            List<BadgePdfRenderer.Side> backs = new ArrayList<>();
            for (CourseParticipation participation : chunk) {
                Map<String, String> values = buildValues(course, participation);
                BitMatrix qr = needsQr(config, includeBack) ? tokenService.matrixFor(course.getCourseCode(), participation.getId()) : null;
                fronts.add(side(frontBackground, config.getFrontLayout(), values, qr));
                if (includeBack) {
                    backs.add(side(backBackground, config.getBackLayout(), values, qr));
                }
            }
            sheets.add(sheet(grid, config, fronts, false, false));
            if (includeBack) {
                sheets.add(sheet(grid, config, backs, mirrorColumns, !mirrorColumns));
            }
        }

        byte[] content;
        try {
            content = pdfRenderer.render(sheets);
        } catch (Exception e) {
            throw validationHelper.validationError("Không thể tạo PDF thẻ tham dự: " + e.getMessage());
        }
        return new PdfExport(content, filename(course, request.teamName(), participants.size()), participants.size(), sheets.size());
    }

    private BadgePdfRenderer.Sheet sheet(BadgeSheetLayout.Grid grid, BadgeConfig config, List<BadgePdfRenderer.Side> sides,
                                         boolean mirrorColumns, boolean mirrorRows) {
        List<BadgePdfRenderer.Placed> placed = new ArrayList<>();
        for (int i = 0; i < sides.size(); i++) {
            double[] position = grid.position(i, mirrorColumns, mirrorRows);
            placed.add(new BadgePdfRenderer.Placed(sides.get(i), position[0], position[1]));
        }
        return new BadgePdfRenderer.Sheet(grid.paperWidthMm(), grid.paperHeightMm(), grid.cardWidthMm(), grid.cardHeightMm(),
                Boolean.TRUE.equals(config.getCropMarks()), placed);
    }

    private BadgePdfRenderer.Side side(byte[] background, List<CertificateLayoutItem> layout, Map<String, String> values, BitMatrix qr) {
        List<CertificatePdfRenderer.RenderItem> items = new ArrayList<>();
        List<CertificatePdfRenderer.RenderQr> qrCodes = new ArrayList<>();
        if (layout != null) {
            for (CertificateLayoutItem item : layout) {
                String type = item.getType() == null ? "" : item.getType().trim().toUpperCase(Locale.ROOT);
                if (TYPE_QR.equals(type)) {
                    if (qr != null) {
                        qrCodes.add(new CertificatePdfRenderer.RenderQr(qr, valueOr(item.getX(), 0.5), valueOr(item.getY(), 0.5), qrSize(item)));
                    }
                    continue;
                }
                String text = TYPE_TEXT.equals(type) ? item.getText() : values.getOrDefault(item.getFieldKey(), "");
                if (text == null || text.isBlank()) {
                    continue;
                }
                items.add(new CertificatePdfRenderer.RenderItem(text, valueOr(item.getX(), 0), valueOr(item.getY(), 0),
                        valueOr(item.getFontSize(), 0), item.getColor(), item.getAlign(), item.getFontFamily(),
                        Boolean.TRUE.equals(item.getBold()), Boolean.TRUE.equals(item.getItalic()), Boolean.TRUE.equals(item.getUnderline())));
            }
        }
        return new BadgePdfRenderer.Side(background, items, qrCodes);
    }

    // Chỉ sinh mã QR khi mặt nào đó thật sự có phần tử QR.
    private boolean needsQr(BadgeConfig config, boolean includeBack) {
        return hasQr(config.getFrontLayout()) || (includeBack && hasQr(config.getBackLayout()));
    }

    private boolean hasQr(List<CertificateLayoutItem> layout) {
        return layout != null && layout.stream().anyMatch(item -> TYPE_QR.equalsIgnoreCase(item.getType()));
    }

    // Cạnh mã QR theo tỉ lệ chiều cao thẻ; giới hạn để mã luôn đủ lớn để quét và không tràn thẻ.
    private double qrSize(CertificateLayoutItem item) {
        double size = item.getSize() == null || item.getSize() <= 0 ? 0.3 : item.getSize();
        return Math.min(0.9, Math.max(0.1, size));
    }

    private static double valueOr(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private List<CourseParticipation> selectParticipants(String courseCode, BadgeExportRequest request) {
        List<CourseParticipation> all = participantSupport.participantsOf(courseCode);
        if (all.isEmpty()) {
            throw validationHelper.validationError("Khóa huấn luyện chưa có hồ sơ nào trong danh sách tham dự");
        }
        List<CourseParticipation> selected = new ArrayList<>(all);
        if (request.participationIds() != null && !request.participationIds().isEmpty()) {
            Set<String> wanted = new LinkedHashSet<>(request.participationIds());
            Set<String> known = new LinkedHashSet<>();
            all.forEach(p -> known.add(p.getId()));
            List<String> missing = wanted.stream().filter(id -> !known.contains(id)).toList();
            if (!missing.isEmpty()) {
                throw validationHelper.validationError("Có " + missing.size() + " hồ sơ không thuộc danh sách tham dự của khóa");
            }
            selected.removeIf(p -> !wanted.contains(p.getId()));
        }
        String team = request.teamName() == null ? null : request.teamName().trim();
        if (team != null && !team.isEmpty()) {
            selected.removeIf(p -> p.getTeamName() == null || !fold(p.getTeamName()).equals(fold(team)));
        }
        if (selected.isEmpty()) {
            throw validationHelper.validationError("Không có hồ sơ nào phù hợp để xuất thẻ");
        }
        selected.sort(CourseParticipantSupport.byTeamThenName());
        return selected;
    }

    // Cấu hình chỉ định (phải nằm trong chuỗi phạm vi của khóa) hoặc cấu hình chung của phạm vi gần khóa nhất.
    private BadgeConfig resolveConfig(TrainingCourse course, UUID configId) {
        List<String> chain = chainOf(course);
        if (configId != null) {
            return configRepository.findById(configId)
                    .filter(config -> chain.contains(config.getScopeKey()))
                    .orElseThrow(() -> validationHelper.notFound("Không tìm thấy cấu hình thẻ tham dự hoặc cấu hình không dùng được cho khóa này"));
        }
        for (String scopeKey : chain) {
            Optional<BadgeConfig> found = configRepository.findDefault(scopeKey);
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw validationHelper.validationError("Chưa có cấu hình thẻ tham dự (riêng hoặc chung) cho khóa này. Hãy tạo cấu hình ở menu Cấu hình thẻ tham dự");
    }

    // Chuỗi phạm vi của khóa: giáo hạt (nếu khóa do giáo hạt quản lý) -> giáo phận -> hệ thống.
    public static List<String> chainOf(TrainingCourse course) {
        List<String> chain = new ArrayList<>();
        boolean deaneryManaged = course.getCourseType() != null && course.getCourseType().getManagementUnitType() == UnitType.DEANERY;
        var deanery = course.getDeanery();
        UUID dioceseId = course.getDiocese() != null ? course.getDiocese().getId()
                : (deanery != null && deanery.getDiocese() != null ? deanery.getDiocese().getId() : null);
        if (deaneryManaged && deanery != null) {
            chain.add(CertificateConfigScopeResolver.keyOf(CertificateConfigScopeResolver.DEANERY, deanery.getId()));
        }
        if (dioceseId != null) {
            chain.add(CertificateConfigScopeResolver.keyOf(CertificateConfigScopeResolver.DIOCESE, dioceseId));
        }
        chain.add(CertificateConfigScopeResolver.SYSTEM);
        return chain;
    }

    static Map<String, String> buildValues(TrainingCourse course, CourseParticipation participation) {
        Map<String, String> v = new LinkedHashMap<>();
        Leader leader = participation.getLeader();
        var parish = leader.getParish();
        var deanery = parish == null ? null : parish.getDeanery();
        var diocese = deanery == null ? null : deanery.getDiocese();
        v.put("holyName", nullToEmpty(leader.getHolyName()).trim());
        v.put("leaderName", CourseParticipantSupport.plainName(leader));
        v.put("leaderFullName", CourseParticipantSupport.fullName(leader));
        // Giáo xứ dùng tên giáo xứ (name), không phải xứ đoàn (chapterName).
        v.put("leaderParishName", parish == null ? "" : nullToEmpty(parish.getName()));
        v.put("leaderDeaneryName", deanery == null ? "" : nullToEmpty(deanery.getName()));
        v.put("leaderDioceseName", diocese == null ? "" : nullToEmpty(diocese.getName()));
        v.put("teamName", nullToEmpty(participation.getTeamName()));
        v.put("courseName", nullToEmpty(course.getCourseName()));
        v.put("courseCode", nullToEmpty(course.getCourseCode()));
        v.put("hostYear", course.getHostYear() == null ? "" : String.valueOf(course.getHostYear()));
        String start = course.getStartDate() == null ? "" : course.getStartDate().format(DATE_FORMAT);
        String end = course.getEndDate() == null ? "" : course.getEndDate().format(DATE_FORMAT);
        v.put("courseStartDate", start);
        v.put("courseEndDate", end);
        v.put("coursePeriod", !start.isEmpty() && !end.isEmpty() ? ("từ " + start + " đến " + end) : (start + end));
        v.put("courseLocation", nullToEmpty(course.getLocation()));
        return v;
    }

    static byte[] decodeBackground(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        String base64 = dataUrl;
        int comma = dataUrl.indexOf(',');
        if (dataUrl.startsWith("data:") && comma >= 0) {
            base64 = dataUrl.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String filename(TrainingCourse course, String teamName, int count) {
        String base = "Thẻ tham dự - " + nullToEmpty(course.getCourseName()).trim();
        if (teamName != null && !teamName.isBlank()) {
            base += " - " + teamName.trim();
        }
        if (count == 1) {
            base += " (1 thẻ)";
        }
        return base.replaceAll("[\\\\/:*?\"<>|\\r\\n]", " ").trim() + ".pdf";
    }

    private static String fold(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
