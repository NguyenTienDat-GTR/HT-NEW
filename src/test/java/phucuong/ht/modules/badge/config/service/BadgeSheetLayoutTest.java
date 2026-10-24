package phucuong.ht.modules.badge.config.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeSheetLayoutTest {

    private static final double EPS = 1e-6;

    @Test
    void paperSizesAndOrientation() {
        assertArrayEquals(new double[]{210, 297}, BadgeSheetLayout.paperMm("A4", "PORTRAIT"), EPS);
        assertArrayEquals(new double[]{297, 210}, BadgeSheetLayout.paperMm("a4", "landscape"), EPS);
        assertArrayEquals(new double[]{297, 420}, BadgeSheetLayout.paperMm("A3", "PORTRAIT"), EPS);
        assertArrayEquals(new double[]{148, 210}, BadgeSheetLayout.paperMm("A5", "PORTRAIT"), EPS);
        assertArrayEquals(new double[]{215.9, 279.4}, BadgeSheetLayout.paperMm("LETTER", "PORTRAIT"), EPS);
        assertArrayEquals(new double[]{355.6, 215.9}, BadgeSheetLayout.paperMm("LEGAL", "LANDSCAPE"), EPS);
    }

    @Test
    void unknownPaperOrMissingOrientationFallsBackToPortraitA4() {
        assertArrayEquals(new double[]{210, 297}, BadgeSheetLayout.paperMm("B7", "PORTRAIT"), EPS);
        assertArrayEquals(new double[]{210, 297}, BadgeSheetLayout.paperMm(null, null), EPS);
        assertFalse(BadgeSheetLayout.isKnownPaper("B7"));
        assertFalse(BadgeSheetLayout.isKnownPaper(null));
        assertTrue(BadgeSheetLayout.isKnownPaper(" a4 "));
    }

    @Test
    void a4PortraitWithNinetyByFiftyFiveFitsTenCards() {
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(210, 297, 90, 55, 10, 0);
        assertEquals(2, grid.columns());
        assertEquals(5, grid.rows());
        assertEquals(10, grid.perPage());
    }

    @Test
    void gridIsCenteredInThePrintableArea() {
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(210, 297, 90, 55, 10, 0);
        // Vùng in 190 x 277, lưới 180 x 275 -> dư 10 x 2, chia đều hai bên.
        assertEquals(15, grid.originXMm(), EPS);
        assertEquals(11, grid.originYMm(), EPS);
    }

    @Test
    void gapReducesTheNumberOfCards() {
        // 210 - 20 = 190: không khoảng cách 2 cột; khoảng cách 11 mm -> (190 + 11) / (90 + 11) = 1.99 -> 1 cột.
        assertEquals(2, BadgeSheetLayout.compute(210, 297, 90, 55, 10, 0).columns());
        assertEquals(1, BadgeSheetLayout.compute(210, 297, 90, 55, 10, 11).columns());
        assertEquals(2, BadgeSheetLayout.compute(210, 297, 90, 55, 10, 10).columns());
    }

    @Test
    void cardExactlyFillingTheUsableAreaIsAccepted() {
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(210, 297, 190, 277, 10, 0);
        assertNotNull(grid);
        assertEquals(1, grid.perPage());
        assertEquals(10, grid.originXMm(), EPS);
        assertEquals(10, grid.originYMm(), EPS);
    }

    @Test
    void floatingPointNoiseDoesNotDropAFittingCard() {
        // 3 thẻ 63.3333... mm vừa khít 190 mm.
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(210, 297, 190.0 / 3, 55, 10, 0);
        assertEquals(3, grid.columns());
    }

    @Test
    void cardLargerThanPrintableAreaReturnsNull() {
        assertNull(BadgeSheetLayout.compute(210, 297, 191, 55, 10, 0));
        assertNull(BadgeSheetLayout.compute(210, 297, 90, 278, 10, 0));
        assertNull(BadgeSheetLayout.compute(148, 210, 150, 55, 0, 0));
    }

    @Test
    void marginsCoveringThePageOrNonPositiveSizesReturnNull() {
        assertNull(BadgeSheetLayout.compute(210, 297, 90, 55, 105, 0));
        assertNull(BadgeSheetLayout.compute(210, 297, 90, 55, 120, 0));
        assertNull(BadgeSheetLayout.compute(210, 297, 0, 55, 10, 0));
        assertNull(BadgeSheetLayout.compute(210, 297, 90, -1, 10, 0));
    }

    @Test
    void zeroMarginUsesTheWholePage() {
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(210, 297, 105, 148.5, 0, 0);
        assertEquals(2, grid.columns());
        assertEquals(2, grid.rows());
        assertEquals(0, grid.originXMm(), EPS);
        assertEquals(0, grid.originYMm(), EPS);
    }

    @Test
    void landscapeCardOnLandscapePaper() {
        double[] paper = BadgeSheetLayout.paperMm("A4", "LANDSCAPE");
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(paper[0], paper[1], 90, 55, 10, 0);
        assertEquals(3, grid.columns());
        assertEquals(3, grid.rows());
    }

    @Test
    void positionsGoRowByRowFromTheTopLeft() {
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(210, 297, 90, 55, 10, 0);
        assertArrayEquals(new double[]{15, 11}, grid.position(0, false, false), EPS);
        assertArrayEquals(new double[]{105, 11}, grid.position(1, false, false), EPS);
        assertArrayEquals(new double[]{15, 66}, grid.position(2, false, false), EPS);
        assertArrayEquals(new double[]{105, 231}, grid.position(9, false, false), EPS);
    }

    @Test
    void mirroredColumnsSwapLeftAndRight() {
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(210, 297, 90, 55, 10, 0);
        assertArrayEquals(grid.position(1, false, false), grid.position(0, true, false), EPS);
        assertArrayEquals(grid.position(0, false, false), grid.position(1, true, false), EPS);
        assertArrayEquals(grid.position(3, false, false), grid.position(2, true, false), EPS);
    }

    @Test
    void mirroredRowsSwapTopAndBottom() {
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(210, 297, 90, 55, 10, 0);
        assertArrayEquals(grid.position(8, false, false), grid.position(0, false, true), EPS);
        assertArrayEquals(grid.position(9, false, false), grid.position(1, false, true), EPS);
    }

    @Test
    void mirrorIsItsOwnInverse() {
        BadgeSheetLayout.Grid grid = BadgeSheetLayout.compute(210, 297, 60, 40, 5, 3);
        for (int i = 0; i < grid.perPage(); i++) {
            double[] mirrored = grid.position(i, true, true);
            int column = i % grid.columns();
            int row = i / grid.columns();
            int mirroredIndex = (grid.rows() - 1 - row) * grid.columns() + (grid.columns() - 1 - column);
            assertArrayEquals(grid.position(mirroredIndex, false, false), mirrored, EPS);
        }
    }

    @Test
    void longEdgeFlipOnPortraitMirrorsColumns() {
        assertTrue(BadgeSheetLayout.mirrorColumns(210, 297, BadgeSheetLayout.FlipEdge.LONG));
        assertFalse(BadgeSheetLayout.mirrorColumns(210, 297, BadgeSheetLayout.FlipEdge.SHORT));
    }

    @Test
    void longEdgeFlipOnLandscapeMirrorsRows() {
        assertFalse(BadgeSheetLayout.mirrorColumns(297, 210, BadgeSheetLayout.FlipEdge.LONG));
        assertTrue(BadgeSheetLayout.mirrorColumns(297, 210, BadgeSheetLayout.FlipEdge.SHORT));
    }

    @Test
    void flipEdgeParsingDefaultsToLong() {
        assertEquals(BadgeSheetLayout.FlipEdge.LONG, BadgeSheetLayout.FlipEdge.parse(null));
        assertEquals(BadgeSheetLayout.FlipEdge.LONG, BadgeSheetLayout.FlipEdge.parse(" "));
        assertEquals(BadgeSheetLayout.FlipEdge.LONG, BadgeSheetLayout.FlipEdge.parse("whatever"));
        assertEquals(BadgeSheetLayout.FlipEdge.SHORT, BadgeSheetLayout.FlipEdge.parse(" short "));
    }

    @Test
    void millimetersConvertToPoints() {
        assertEquals(72.0, BadgeSheetLayout.toPoints(25.4), EPS);
        assertEquals(595.2756, BadgeSheetLayout.toPoints(210), 1e-3);
    }
}
