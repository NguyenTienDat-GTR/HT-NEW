package phucuong.ht.modules.badge.config.service;

import java.util.Locale;

// Hình học xếp thẻ lên khổ giấy (đơn vị mm): số cột / hàng, vị trí từng thẻ và vị trí đối xứng của mặt sau khi in hai mặt.
public final class BadgeSheetLayout {

    public static final double MM_TO_POINT = 72.0 / 25.4;

    public static final double MIN_CARD_MM = 20;
    public static final double MAX_CARD_MM = 400;
    public static final double MAX_MARGIN_MM = 50;
    public static final double MAX_GAP_MM = 30;

    private BadgeSheetLayout() {
    }

    // Lưới thẻ trên một trang; originX/originY là góc trên-trái của thẻ đầu tiên (mm, tính từ góc trên-trái trang).
    public record Grid(int columns, int rows, double paperWidthMm, double paperHeightMm, double cardWidthMm,
                       double cardHeightMm, double gapMm, double originXMm, double originYMm) {

        public int perPage() {
            return columns * rows;
        }

        // Vị trí góc trên-trái (mm) của thẻ thứ index (0-based, theo hàng từ trên xuống, trái sang phải) trên trang;
        // mirrorColumns / mirrorRows lật đối xứng để mặt sau trùng khớp mặt trước khi lật tờ giấy.
        public double[] position(int index, boolean mirrorColumns, boolean mirrorRows) {
            int column = index % columns;
            int row = index / columns;
            if (mirrorColumns) {
                column = columns - 1 - column;
            }
            if (mirrorRows) {
                row = rows - 1 - row;
            }
            return new double[]{originXMm + column * (cardWidthMm + gapMm), originYMm + row * (cardHeightMm + gapMm)};
        }
    }

    // Cạnh lật khi in hai mặt: LONG = lật theo cạnh dài của tờ giấy (mặc định của máy in), SHORT = lật theo cạnh ngắn.
    public enum FlipEdge {
        LONG, SHORT;

        public static FlipEdge parse(String value) {
            if (value == null || value.isBlank()) {
                return LONG;
            }
            return "SHORT".equalsIgnoreCase(value.trim()) ? SHORT : LONG;
        }
    }

    // Kích thước giấy (rộng, cao) theo mm sau khi áp dụng hướng giấy. Khổ lạ dùng A4.
    public static double[] paperMm(String paperSize, String orientation) {
        String size = paperSize == null ? "A4" : paperSize.trim().toUpperCase(Locale.ROOT);
        double w;
        double h;
        switch (size) {
            case "A3" -> { w = 297; h = 420; }
            case "A5" -> { w = 148; h = 210; }
            case "LETTER" -> { w = 215.9; h = 279.4; }
            case "LEGAL" -> { w = 215.9; h = 355.6; }
            default -> { w = 210; h = 297; }
        }
        boolean landscape = orientation != null && "LANDSCAPE".equalsIgnoreCase(orientation.trim());
        return landscape ? new double[]{h, w} : new double[]{w, h};
    }

    public static boolean isKnownPaper(String paperSize) {
        if (paperSize == null) {
            return false;
        }
        return switch (paperSize.trim().toUpperCase(Locale.ROOT)) {
            case "A3", "A4", "A5", "LETTER", "LEGAL" -> true;
            default -> false;
        };
    }

    // Tính lưới; trả null khi không xếp nổi một thẻ nào (thẻ lớn hơn vùng in). Lưới được căn giữa vùng in.
    public static Grid compute(double paperWidthMm, double paperHeightMm, double cardWidthMm, double cardHeightMm,
                               double marginMm, double gapMm) {
        double usableWidth = paperWidthMm - 2 * marginMm;
        double usableHeight = paperHeightMm - 2 * marginMm;
        if (cardWidthMm <= 0 || cardHeightMm <= 0 || usableWidth <= 0 || usableHeight <= 0) {
            return null;
        }
        // Sai số nhỏ (1e-9) để thẻ vừa khít không bị loại do làm tròn số thực.
        int columns = (int) Math.floor((usableWidth + gapMm) / (cardWidthMm + gapMm) + 1e-9);
        int rows = (int) Math.floor((usableHeight + gapMm) / (cardHeightMm + gapMm) + 1e-9);
        if (columns < 1 || rows < 1) {
            return null;
        }
        double gridWidth = columns * cardWidthMm + (columns - 1) * gapMm;
        double gridHeight = rows * cardHeightMm + (rows - 1) * gapMm;
        double originX = marginMm + (usableWidth - gridWidth) / 2;
        double originY = marginMm + (usableHeight - gridHeight) / 2;
        return new Grid(columns, rows, paperWidthMm, paperHeightMm, cardWidthMm, cardHeightMm, gapMm, originX, originY);
    }

    // Mặt sau đối xứng theo cạnh lật: lật theo cạnh dài của giấy đứng (hoặc cạnh ngắn của giấy ngang) đổi cột trái <-> phải,
    // ngược lại đổi hàng trên <-> dưới.
    public static boolean mirrorColumns(double paperWidthMm, double paperHeightMm, FlipEdge flipEdge) {
        boolean portrait = paperWidthMm <= paperHeightMm;
        return portrait == (flipEdge == FlipEdge.LONG);
    }

    public static double toPoints(double mm) {
        return mm * MM_TO_POINT;
    }
}
