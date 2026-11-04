package phucuong.ht.modules.badge.config.service;

import lombok.RequiredArgsConstructor;
import phucuong.ht.modules.certificate.config.service.LayoutConfigRules;
import phucuong.ht.common.utils.TextFolding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.layoutconfig.LayoutConfigVersionResponse;
import phucuong.ht.common.layoutconfig.LayoutConfigVersionService;
import phucuong.ht.modules.badge.config.model.BadgeMergeField;
import phucuong.ht.modules.badge.config.model.entity.BadgeConfig;
import phucuong.ht.modules.badge.config.model.request.BadgePreviewPdfRequest;
import phucuong.ht.modules.badge.export.service.BadgeExportService;
import phucuong.ht.modules.certificate.config.service.PreviewSamples;
import com.google.zxing.common.BitMatrix;
import phucuong.ht.modules.badge.config.model.request.BadgeConfigUpsertRequest;
import phucuong.ht.modules.badge.config.model.response.BadgeConfigResponse;
import phucuong.ht.modules.badge.config.repository.BadgeConfigRepository;
import phucuong.ht.modules.badge.config.security.BadgeConfigAccessEvaluator;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;
import phucuong.ht.modules.certificate.config.model.response.CertificateFieldCatalogItem;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver.ActorScope;

import phucuong.ht.modules.media.model.enums.MediaCategory;
import phucuong.ht.modules.media.service.MediaAssetService;
import phucuong.ht.modules.media.service.MediaFolderService;
import phucuong.ht.modules.media.service.MediaUrls;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Thư viện cấu hình thẻ tham dự: CRUD theo phạm vi sở hữu (hệ thống / giáo phận / giáo hạt), tên duy nhất trong phạm vi,
// mỗi phạm vi một cấu hình chung. Cấu hình của cấp trên chỉ được xem và dùng.
@Service
@RequiredArgsConstructor
public class BadgeConfigService {

    public static final double DEFAULT_CARD_WIDTH_MM = 90;
    public static final double DEFAULT_CARD_HEIGHT_MM = 55;
    public static final double DEFAULT_MARGIN_MM = 10;

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_LAYOUT_ITEMS = 100;
    private static final int MAX_TEXT_LENGTH = 500;
    private static final Set<String> ITEM_TYPES = Set.of("FIELD", "TEXT", "QR", "PHOTO");

    private final BadgeConfigRepository configRepository;
    private final CertificateConfigScopeResolver scopeResolver;
    private final CommonValidationHelper validationHelper;
    private final securityUtils securityUtils;
    private final MediaAssetService mediaAssetService;
    private final MediaFolderService mediaFolderService;
    private final MediaUrls mediaUrls;
    private final phucuong.ht.modules.media.album.BackgroundAlbumService albumService;
    private final BadgePdfRenderer pdfRenderer;
    private final LayoutConfigVersionService versionService;

    private ActorScope actorOf(Authentication authentication) {
        return scopeResolver.resolve(authentication, BadgeConfigAccessEvaluator.PREFIX, "Bạn không có quyền với cấu hình thẻ tham dự");
    }

    // Danh sách trường cố định cho trình soạn bố cục thẻ.
    public List<CertificateFieldCatalogItem> fieldCatalog() {
        return Arrays.stream(BadgeMergeField.values())
                .map(f -> new CertificateFieldCatalogItem(f.getKey(), f.getLabel(), f.getGroup()))
                .toList();
    }

    // Toàn bộ cấu hình xem / dùng được ở dạng gọn (không kèm ảnh nền và bố cục), cho ô chọn cấu hình.
    @Transactional(readOnly = true)
    public List<BadgeConfigResponse> options(Authentication authentication) {
        ActorScope actor = actorOf(authentication);
        return visibleConfigs(actor).stream().map(config -> toSummary(config, actor)).toList();
    }

    @Transactional(readOnly = true)
    public BadgeConfigResponse get(UUID id, Authentication authentication) {
        ActorScope actor = actorOf(authentication);
        BadgeConfig config = configRepository.findById(id)
                .filter(c -> actor.canSee(c.getScopeKey()))
                .orElseThrow(() -> validationHelper.notFound("Không tìm thấy cấu hình thẻ tham dự"));
        return toResponse(config, actor);
    }

    // Tìm kiếm / lọc / sắp xếp / phân trang thư viện (dạng gọn).
    // search: tên cấu hình (không phân biệt hoa thường và dấu); filters: scope (SYSTEM|DIOCESE|DEANERY), isDefault,
    // paperSize, cardOrientation, hasBack; sortBy: name | updatedAt | scopeLevel (mặc định cấu hình chung lên đầu rồi theo tên).
    @Transactional(readOnly = true)
    public Page<BadgeConfigResponse> search(BaseSearchRequest request, Authentication authentication) {
        ActorScope actor = actorOf(authentication);
        Map<String, Object> filters = request.getFilters() == null ? Map.of() : request.getFilters();
        String keyword = TextFolding.foldText(request.getSearch());
        String scope = TextFolding.filterText(filters, "scope");
        Boolean isDefault = TextFolding.filterBoolean(filters, "isDefault");
        Boolean hasBack = TextFolding.filterBoolean(filters, "hasBack");
        String paperSize = TextFolding.filterText(filters, "paperSize");
        String cardOrientation = TextFolding.filterText(filters, "cardOrientation");

        List<BadgeConfigResponse> matched = new ArrayList<>();
        for (BadgeConfig config : visibleConfigs(actor)) {
            if (scope != null && !scope.equalsIgnoreCase(CertificateConfigScopeResolver.levelOf(config.getScopeKey()))) continue;
            if (isDefault != null && isDefault != Boolean.TRUE.equals(config.getIsDefault())) continue;
            if (hasBack != null && hasBack != Boolean.TRUE.equals(config.getHasBack())) continue;
            if (paperSize != null && !paperSize.equalsIgnoreCase(config.getPaperSize())) continue;
            if (cardOrientation != null && !cardOrientation.equalsIgnoreCase(config.getCardOrientation())) continue;
            if (!keyword.isEmpty() && !TextFolding.foldText(config.getName()).contains(keyword)) continue;
            matched.add(toSummary(config, actor));
        }

        Comparator<BadgeConfigResponse> byName = Comparator.comparing(c -> TextFolding.foldText(c.getName()));
        Comparator<BadgeConfigResponse> order = switch (request.getSortBy() == null ? "" : request.getSortBy()) {
            case "name" -> byName;
            case "updatedAt" -> Comparator.comparing(BadgeConfigResponse::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
            case "scopeLevel" -> Comparator.comparing(BadgeConfigResponse::getScopeLevel).thenComparing(byName);
            default -> Comparator.comparing((BadgeConfigResponse c) -> !Boolean.TRUE.equals(c.getIsDefault())).thenComparing(byName);
        };
        if (request.getSortBy() != null && "DESC".equalsIgnoreCase(request.getSortDirection())) {
            order = order.reversed();
        }
        matched.sort(order);

        int size = Math.max(1, request.getSize());
        int from = (int) Math.min((long) request.getPage() * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return new PageImpl<>(matched.subList(from, to), PageRequest.of(request.getPage(), size), matched.size());
    }

    // Tạo cấu hình mới trong phạm vi của người dùng. Trùng tên thì thêm hậu tố số; cấu hình đầu tiên của phạm vi là cấu hình chung.
    @Transactional
    public BadgeConfigResponse create(BadgeConfigUpsertRequest request, Authentication authentication) {
        ActorScope actor = actorOf(authentication);
        String audit = securityUtils.currentAuditActor(authentication);
        OffsetDateTime now = OffsetDateTime.now();

        BadgeConfig config = new BadgeConfig();
        config.setId(UUID.randomUUID());
        config.setScopeKey(actor.key());
        config.setName(uniqueName(actor.key(), request.getName(), null));
        config.setIsDefault(configRepository.findDefault(actor.key()).isEmpty());
        config.setCreatedAt(now);
        config.setCreatedBy(audit);
        config.setStatus(Boolean.TRUE);
        applyContent(config, request, now, audit, actor);
        return toResponse(configRepository.persist(config), actor);
    }

    @Transactional
    public BadgeConfigResponse update(UUID id, BadgeConfigUpsertRequest request, Authentication authentication) {
        ActorScope actor = actorOf(authentication);
        BadgeConfig config = requireEditable(id, actor);
        String audit = securityUtils.currentAuditActor(authentication);
        OffsetDateTime now = OffsetDateTime.now();
        // Ghi lại bản hiện tại trước khi đổi để người dùng xem lịch sử và khôi phục.
        versionService.record(LayoutConfigVersionService.BADGE, id, snapshotOf(config), audit);

        if (TextFolding.normalize(request.getName()) != null) {
            config.setName(uniqueName(config.getScopeKey(), request.getName(), config.getName()));
        }
        applyContent(config, request, now, audit, actor);
        return toResponse(configRepository.merge(config), actor);
    }

    // Nội dung cấu hình tại một thời điểm (chỉ mã ảnh, không có URL đã ký) để lưu lịch sử phiên bản.
    private Map<String, Object> snapshotOf(BadgeConfig config) {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("name", config.getName());
        snapshot.put("paperSize", config.getPaperSize());
        snapshot.put("paperOrientation", config.getPaperOrientation());
        snapshot.put("cardWidthMm", config.getCardWidthMm());
        snapshot.put("cardHeightMm", config.getCardHeightMm());
        snapshot.put("cardOrientation", config.getCardOrientation());
        snapshot.put("marginMm", config.getMarginMm());
        snapshot.put("gapMm", config.getGapMm());
        snapshot.put("cropMarks", config.getCropMarks());
        snapshot.put("hasBack", config.getHasBack());
        snapshot.put("frontBackgroundAssetId", config.getFrontBackgroundAssetId() == null ? null : config.getFrontBackgroundAssetId().toString());
        snapshot.put("backBackgroundAssetId", config.getBackBackgroundAssetId() == null ? null : config.getBackBackgroundAssetId().toString());
        snapshot.put("frontLayout", config.getFrontLayout() == null ? List.of() : config.getFrontLayout());
        snapshot.put("backLayout", config.getBackLayout() == null ? List.of() : config.getBackLayout());
        return snapshot;
    }

    // Lịch sử các bản đã lưu của một cấu hình thẻ (mới nhất trước); quyền như xem cấu hình đó.
    @Transactional(readOnly = true)
    public List<LayoutConfigVersionResponse> versions(UUID id, Authentication authentication) {
        BadgeConfig config = configRepository.findById(id)
                .filter(found -> actorOf(authentication).canSee(found.getScopeKey()))
                .orElseThrow(() -> validationHelper.notFound("Không tìm thấy cấu hình thẻ tham dự"));
        return versionService.list(LayoutConfigVersionService.BADGE, config.getId()).stream()
                .map(version -> {
                    Map<String, Object> snapshot = new java.util.LinkedHashMap<>(version.snapshot());
                    snapshot.put("frontBackground", usableUrl(snapshot.get("frontBackgroundAssetId")));
                    snapshot.put("backBackground", usableUrl(snapshot.get("backBackgroundAssetId")));
                    return new LayoutConfigVersionResponse(version.id(), version.versionNo(), version.createdAt(), version.createdBy(), snapshot);
                })
                .toList();
    }

    // URL ảnh đã ký nếu ảnh còn tồn tại; ảnh đã bị gỡ khỏi cấu hình sau các lần lưu thì trả null.
    private String usableUrl(Object assetId) {
        if (assetId == null) {
            return null;
        }
        UUID id = UUID.fromString(assetId.toString());
        var asset = mediaAssetService.find(id);
        if (asset == null || asset.getStatus() == phucuong.ht.modules.media.model.enums.MediaStatus.DELETED) {
            return null;
        }
        return mediaUrls.display(id, MediaCategory.BADGE_BACKGROUND);
    }

    // Đặt cấu hình làm cấu hình chung của phạm vi chứa nó; cấu hình chung cũ trở thành cấu hình thường.
    @Transactional
    public BadgeConfigResponse setDefault(UUID id, Authentication authentication) {
        ActorScope actor = actorOf(authentication);
        BadgeConfig config = requireEditable(id, actor);
        String audit = securityUtils.currentAuditActor(authentication);
        OffsetDateTime now = OffsetDateTime.now();

        config = LayoutConfigRules.makeDefault(configRepository, config, audit, now);
        return toResponse(config, actor);
    }

    // Không xóa được cấu hình chung (phải chọn cấu hình khác làm chung trước).
    @Transactional
    public void delete(UUID id, Authentication authentication) {
        ActorScope actor = actorOf(authentication);
        BadgeConfig config = requireEditable(id, actor);
        LayoutConfigRules.requireNotDefault(config, validationHelper);
        mediaAssetService.release(config.getFrontBackgroundAssetId());
        mediaAssetService.release(config.getBackBackgroundAssetId());
        versionService.deleteAll(LayoutConfigVersionService.BADGE, id);
        configRepository.remove(config.getId());
    }

    private BadgeConfig requireEditable(UUID id, ActorScope actor) {
        return LayoutConfigRules.requireEditable(configRepository, validationHelper, id, actor, "Không tìm thấy cấu hình thẻ tham dự");
    }

    private List<BadgeConfig> visibleConfigs(ActorScope actor) {
        return LayoutConfigRules.visible(configRepository, actor);
    }

    // Kiểm tra và áp nội dung cấu hình. Mọi sai lệch trả 400 kèm thông báo tiếng Việt cụ thể.
    void applyContent(BadgeConfig config, BadgeConfigUpsertRequest request, OffsetDateTime now, String audit, ActorScope actor) {
        boolean hasBack = applySheet(config, request);
        UUID dioceseId = mediaFolderService.dioceseOfScopeKey(config.getScopeKey());
        boolean clearFront = Boolean.TRUE.equals(request.getClearFrontBackground());
        UUID previousFront = config.getFrontBackgroundAssetId();
        UUID nextFront = swapBackground(previousFront, request.getFrontBackgroundAssetId(), dioceseId, audit, actor);
        if (!Objects.equals(nextFront, previousFront) || clearFront) {
            config.setLegacyFrontBackground(null);
        }
        config.setFrontBackgroundAssetId(nextFront);
        config.setFrontLayout(checkLayout(request.getFrontLayout(), "mặt trước"));
        // Không có mặt sau thì bỏ hẳn nội dung mặt sau.
        boolean clearBack = Boolean.TRUE.equals(request.getClearBackBackground()) || !hasBack;
        UUID previousBack = config.getBackBackgroundAssetId();
        UUID nextBack = swapBackground(previousBack, hasBack ? request.getBackBackgroundAssetId() : null, dioceseId, audit, actor);
        if (!Objects.equals(nextBack, previousBack) || clearBack) {
            config.setLegacyBackBackground(null);
        }
        config.setBackBackgroundAssetId(nextBack);
        config.setBackLayout(hasBack ? checkLayout(request.getBackLayout(), "mặt sau") : new ArrayList<>());
        config.setUpdatedAt(now);
        config.setUpdatedBy(audit);
    }

    // Kiểm tra và áp khổ giấy, khổ thẻ, lề, khoảng cách; trả về thẻ có mặt sau hay không.
    private boolean applySheet(BadgeConfig config, BadgeConfigUpsertRequest request) {
        String paperSize = TextFolding.normalize(request.getPaperSize()) == null ? "A4" : request.getPaperSize().trim().toUpperCase(Locale.ROOT);
        if (!BadgeSheetLayout.isKnownPaper(paperSize)) {
            throw validationHelper.validationError("Khổ giấy không hợp lệ: " + request.getPaperSize() + " (chọn A3, A4, A5, Letter hoặc Legal)");
        }
        String paperOrientation = orientation(request.getPaperOrientation(), "PORTRAIT", "Hướng giấy");
        String cardOrientation = orientation(request.getCardOrientation(), "LANDSCAPE", "Hướng thẻ");

        double width = range(request.getCardWidthMm(), DEFAULT_CARD_WIDTH_MM, BadgeSheetLayout.MIN_CARD_MM, BadgeSheetLayout.MAX_CARD_MM, "Chiều rộng thẻ");
        double height = range(request.getCardHeightMm(), DEFAULT_CARD_HEIGHT_MM, BadgeSheetLayout.MIN_CARD_MM, BadgeSheetLayout.MAX_CARD_MM, "Chiều cao thẻ");
        // Hướng thẻ quyết định cạnh dài nằm ngang (LANDSCAPE) hay thẳng đứng (PORTRAIT).
        double longSide = Math.max(width, height);
        double shortSide = Math.min(width, height);
        width = "LANDSCAPE".equals(cardOrientation) ? longSide : shortSide;
        height = "LANDSCAPE".equals(cardOrientation) ? shortSide : longSide;

        double margin = range(request.getMarginMm(), DEFAULT_MARGIN_MM, 0, BadgeSheetLayout.MAX_MARGIN_MM, "Lề giấy");
        double gap = range(request.getGapMm(), 0, 0, BadgeSheetLayout.MAX_GAP_MM, "Khoảng cách giữa các thẻ");

        double[] paper = BadgeSheetLayout.paperMm(paperSize, paperOrientation);
        if (BadgeSheetLayout.compute(paper[0], paper[1], width, height, margin, gap) == null) {
            throw validationHelper.validationError("Thẻ " + trim(width) + " x " + trim(height) + " mm lớn hơn vùng in của khổ "
                    + paperSize + " (lề " + trim(margin) + " mm). Hãy giảm kích thước thẻ, giảm lề hoặc chọn khổ giấy lớn hơn");
        }

        boolean hasBack = Boolean.TRUE.equals(request.getHasBack());
        config.setPaperSize(paperSize);
        config.setPaperOrientation(paperOrientation);
        config.setCardWidthMm(mm(width));
        config.setCardHeightMm(mm(height));
        config.setCardOrientation(cardOrientation);
        config.setMarginMm(mm(margin));
        config.setGapMm(mm(gap));
        config.setCropMarks(request.getCropMarks() == null || request.getCropMarks());
        config.setHasBack(hasBack);
        return hasBack;
    }

    private static final int PREVIEW_MAX_VALUES = 100;
    private static final int PREVIEW_MAX_VALUE_LENGTH = 500;

    // PDF xem trước của cấu hình thẻ đang soạn: một tờ giấy xếp đầy thẻ mẫu (kèm tờ mặt sau nếu có), cùng bộ vẽ với PDF thật.
    @Transactional(readOnly = true)
    public BadgeExportService.PdfExport previewPdf(BadgePreviewPdfRequest request, Authentication authentication) {
        ActorScope actor = actorOf(authentication);
        if (request.getConfig() == null) {
            throw validationHelper.validationError("Thiếu nội dung cấu hình thẻ để xem trước");
        }
        BadgeConfigUpsertRequest content = request.getConfig();
        BadgeConfig draft = draftOf(content);
        BadgeSheetLayout.Grid grid = gridOf(draft);
        Map<String, String> values = new LinkedHashMap<>();
        if (request.getValues() != null) {
            if (request.getValues().size() > PREVIEW_MAX_VALUES) {
                throw validationHelper.validationError("Dữ liệu mẫu có quá nhiều trường");
            }
            request.getValues().forEach((key, value) -> values.put(key, value == null ? ""
                    : value.length() > PREVIEW_MAX_VALUE_LENGTH ? value.substring(0, PREVIEW_MAX_VALUE_LENGTH) : value));
        }
        boolean hasBack = Boolean.TRUE.equals(draft.getHasBack());
        byte[] frontBackground = previewBackground(content.getFrontBackgroundAssetId(), actor);
        byte[] backBackground = hasBack ? previewBackground(content.getBackBackgroundAssetId(), actor) : null;
        BitMatrix qr = PreviewSamples.qr("MAU-XEM-TRUOC");
        byte[] photo = PreviewSamples.photoPng();

        List<BadgePdfRenderer.Side> fronts = new ArrayList<>();
        List<BadgePdfRenderer.Side> backs = new ArrayList<>();
        BadgePdfRenderer.Side front = BadgeExportService.side(frontBackground, draft.getFrontLayout(), values, qr, photo);
        BadgePdfRenderer.Side back = hasBack ? BadgeExportService.side(backBackground, draft.getBackLayout(), values, qr, photo) : null;
        for (int i = 0; i < grid.perPage(); i++) {
            fronts.add(front);
            if (hasBack) {
                backs.add(back);
            }
        }
        List<BadgePdfRenderer.Sheet> sheets = new ArrayList<>();
        sheets.add(BadgeExportService.sheet(grid, draft, fronts, false, false));
        if (hasBack) {
            boolean mirrorColumns = BadgeSheetLayout.mirrorColumns(grid.paperWidthMm(), grid.paperHeightMm(), BadgeSheetLayout.FlipEdge.parse(null));
            sheets.add(BadgeExportService.sheet(grid, draft, backs, mirrorColumns, !mirrorColumns));
        }
        byte[] pdf;
        try {
            pdf = pdfRenderer.render(sheets);
        } catch (Exception e) {
            throw validationHelper.validationError("Không thể tạo PDF xem trước thẻ: " + e.getMessage());
        }
        return new BadgeExportService.PdfExport(pdf, "xem-truoc-the-tham-du.pdf", grid.perPage(), sheets.size());
    }

    private byte[] previewBackground(UUID assetId, ActorScope actor) {
        if (assetId == null) {
            return null;
        }
        // Ảnh album của đơn vị khác không dùng được dù biết mã ảnh (giống khi lưu cấu hình).
        albumService.assertUsable(assetId, actor);
        return mediaAssetService.loadMaster(assetId);
    }

    // Bản nháp (không lưu) từ nội dung đang soạn để dựng PDF xem trước: kiểm tra giống khi lưu nhưng không gắn / gỡ ảnh nền.
    public BadgeConfig draftOf(BadgeConfigUpsertRequest request) {
        BadgeConfig draft = new BadgeConfig();
        boolean hasBack = applySheet(draft, request);
        draft.setFrontLayout(checkLayout(request.getFrontLayout(), "mặt trước"));
        draft.setBackLayout(hasBack ? checkLayout(request.getBackLayout(), "mặt sau") : new ArrayList<>());
        return draft;
    }

    private String orientation(String value, String fallback, String label) {
        if (TextFolding.normalize(value) == null) {
            return fallback;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (!"PORTRAIT".equals(upper) && !"LANDSCAPE".equals(upper)) {
            throw validationHelper.validationError(label + " không hợp lệ: " + value + " (chọn PORTRAIT hoặc LANDSCAPE)");
        }
        return upper;
    }

    private double range(Double value, double fallback, double min, double max, String label) {
        if (value == null) {
            return fallback;
        }
        if (value.isNaN() || value.isInfinite() || value < min || value > max) {
            throw validationHelper.validationError(label + " phải từ " + trim(min) + " đến " + trim(max) + " mm");
        }
        return value;
    }

    // Trả id asset sau khi gắn / đổi / gỡ ảnh nền: ảnh mới chuyển vào thư mục giáo phận sở hữu cấu hình, ảnh cũ bị gỡ.
    private UUID swapBackground(UUID current, UUID requested, UUID dioceseId, String audit, ActorScope actor) {
        if (requested != null && requested.equals(current)) {
            return current;
        }
        if (requested == null && current == null) {
            return null;
        }
        if (requested != null) {
            // Ảnh từ album của đơn vị khác (hoặc cấu hình không được xem) không được dùng dù biết mã ảnh.
            albumService.assertUsable(requested, actor);
        }
        UUID next = requested == null ? null : mediaAssetService.attach(requested, MediaCategory.BADGE_BACKGROUND, dioceseId, audit);
        mediaAssetService.release(current);
        return next;
    }

    private List<CertificateLayoutItem> checkLayout(List<CertificateLayoutItem> layout, String side) {
        if (layout == null || layout.isEmpty()) {
            return new ArrayList<>();
        }
        if (layout.size() > MAX_LAYOUT_ITEMS) {
            throw validationHelper.validationError("Bố cục " + side + " tối đa " + MAX_LAYOUT_ITEMS + " phần tử");
        }
        for (CertificateLayoutItem item : layout) {
            if (item == null || item.getType() == null || !ITEM_TYPES.contains(item.getType().trim().toUpperCase(Locale.ROOT))) {
                throw validationHelper.validationError("Bố cục " + side + " có phần tử sai loại (chỉ nhận FIELD, TEXT, QR)");
            }
            String type = item.getType().trim().toUpperCase(Locale.ROOT);
            if ("FIELD".equals(type) && !BadgeMergeField.isKnown(item.getFieldKey())) {
                throw validationHelper.validationError("Bố cục " + side + " có trường không tồn tại: " + item.getFieldKey());
            }
            if ("TEXT".equals(type) && item.getText() != null && item.getText().length() > MAX_TEXT_LENGTH) {
                throw validationHelper.validationError("Văn bản trong bố cục " + side + " tối đa " + MAX_TEXT_LENGTH + " ký tự");
            }
            checkRatio(item.getX(), "Tọa độ X", side);
            checkRatio(item.getY(), "Tọa độ Y", side);
            checkRatio(item.getFontSize(), "Cỡ chữ", side);
            checkRatio(item.getSize(), "Kích thước mã QR", side);
        }
        return new ArrayList<>(layout);
    }

    private void checkRatio(Double value, String label, String side) {
        if (value != null && (value.isNaN() || value.isInfinite() || value < 0 || value > 1)) {
            throw validationHelper.validationError(label + " trong bố cục " + side + " phải là tỉ lệ từ 0 đến 1");
        }
    }

    // Tên duy nhất (không phân biệt hoa thường) trong phạm vi; trùng thì thêm " (1)", " (2)"...
    private String uniqueName(String scopeKey, String requested, String currentName) {
        return TextFolding.uniqueName(configRepository.findNamesInScope(scopeKey), requested, currentName, "Cấu hình thẻ tham dự", MAX_NAME_LENGTH);
    }

    BadgeConfigResponse toResponse(BadgeConfig config, ActorScope actor) {
        return summaryBuilder(config, actor)
                .frontBackground(mediaUrls.displayOrLegacy(config.getFrontBackgroundAssetId(), config.getLegacyFrontBackground(), MediaCategory.BADGE_BACKGROUND))
                .backBackground(mediaUrls.displayOrLegacy(config.getBackBackgroundAssetId(), config.getLegacyBackBackground(), MediaCategory.BADGE_BACKGROUND))
                .frontBackgroundAssetId(config.getFrontBackgroundAssetId())
                .backBackgroundAssetId(config.getBackBackgroundAssetId())
                .frontBackgroundWidthPx(widthOf(mediaAssetService.masterSize(config.getFrontBackgroundAssetId())))
                .frontBackgroundHeightPx(heightOf(mediaAssetService.masterSize(config.getFrontBackgroundAssetId())))
                .backBackgroundWidthPx(widthOf(mediaAssetService.masterSize(config.getBackBackgroundAssetId())))
                .backBackgroundHeightPx(heightOf(mediaAssetService.masterSize(config.getBackBackgroundAssetId())))
                .frontLayout(config.getFrontLayout())
                .backLayout(config.getBackLayout())
                .build();
    }

    // Dạng gọn cho danh sách: không kèm ảnh nền base64 và bố cục.
    private BadgeConfigResponse toSummary(BadgeConfig config, ActorScope actor) {
        return summaryBuilder(config, actor).build();
    }

    private BadgeConfigResponse.BadgeConfigResponseBuilder summaryBuilder(BadgeConfig config, ActorScope actor) {
        BadgeSheetLayout.Grid grid = gridOf(config);
        return BadgeConfigResponse.builder()
                .id(config.getId())
                .name(config.getName())
                .isDefault(config.getIsDefault())
                .scopeLevel(CertificateConfigScopeResolver.levelOf(config.getScopeKey()))
                .editable(actor.canEdit(config.getScopeKey()))
                .paperSize(config.getPaperSize())
                .paperOrientation(config.getPaperOrientation())
                .cardWidthMm(config.getCardWidthMm().doubleValue())
                .cardHeightMm(config.getCardHeightMm().doubleValue())
                .cardOrientation(config.getCardOrientation())
                .marginMm(config.getMarginMm().doubleValue())
                .gapMm(config.getGapMm().doubleValue())
                .cropMarks(config.getCropMarks())
                .hasBack(config.getHasBack())
                .columns(grid == null ? 0 : grid.columns())
                .rows(grid == null ? 0 : grid.rows())
                .cardsPerPage(grid == null ? 0 : grid.perPage())
                .status(config.getStatus())
                .updatedAt(config.getUpdatedAt());
    }

    // Lưới xếp thẻ của cấu hình trên khổ giấy của nó; null nếu dữ liệu cũ không còn xếp nổi một thẻ.
    public static BadgeSheetLayout.Grid gridOf(BadgeConfig config) {
        double[] paper = BadgeSheetLayout.paperMm(config.getPaperSize(), config.getPaperOrientation());
        return BadgeSheetLayout.compute(paper[0], paper[1], config.getCardWidthMm().doubleValue(),
                config.getCardHeightMm().doubleValue(), config.getMarginMm().doubleValue(), config.getGapMm().doubleValue());
    }

    private static Integer widthOf(int[] size) {
        return size == null ? null : size[0];
    }

    private static Integer heightOf(int[] size) {
        return size == null ? null : size[1];
    }

    private static BigDecimal mm(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

}
