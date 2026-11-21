package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OverlayGeometryTest {
    private static final int LEFT = 10;
    private static final int TOP = 20;
    private static final int RIGHT = 1010;
    private static final int BOTTOM = 1520;
    private static final int WIDTH = 300;
    private static final int HEIGHT = 400;

    @Test public void zeroOffsetsAnchorEachCorner() {
        assertPosition(OverlayCorner.TOP_START, 10, 20);
        assertPosition(OverlayCorner.TOP_END, 710, 20);
        assertPosition(OverlayCorner.BOTTOM_START, 10, 1120);
        assertPosition(OverlayCorner.BOTTOM_END, 710, 1120);
    }

    @Test public void offsetsMoveAwayFromSelectedEdges() {
        OverlayGeometry.Position position = OverlayGeometry.positionFor(
                OverlayCorner.BOTTOM_END, LEFT, TOP, RIGHT, BOTTOM,
                WIDTH, HEIGHT, 7, 11);

        assertEquals(703, position.x());
        assertEquals(1109, position.y());
    }

    @Test public void coordinatesClampToSafeBounds() {
        OverlayGeometry.Position position = OverlayGeometry.positionFor(
                OverlayCorner.TOP_START, LEFT, TOP, RIGHT, BOTTOM,
                WIDTH, HEIGHT, 10_000, 10_000);

        assertEquals(710, position.x());
        assertEquals(1120, position.y());
    }

    @Test public void oversizedPanelStaysAtSafeOrigin() {
        OverlayGeometry.Position position = OverlayGeometry.positionFor(
                OverlayCorner.BOTTOM_END, LEFT, TOP, 200, 300,
                500, 600, 0, 0);

        assertEquals(10, position.x());
        assertEquals(20, position.y());
    }

    @Test public void reanchoringBetweenCornersPreservesScreenPosition() {
        OverlayGeometry.Position initial = OverlayGeometry.positionFor(
                OverlayCorner.BOTTOM_START, LEFT, TOP, RIGHT, BOTTOM,
                WIDTH, HEIGHT, 45, 124);
        for (OverlayCorner next : OverlayCorner.values()) {
            OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(next,
                    LEFT, TOP, RIGHT, BOTTOM, WIDTH, HEIGHT, initial.x(), initial.y());
            OverlayGeometry.Position recomputed = OverlayGeometry.positionFor(next,
                    LEFT, TOP, RIGHT, BOTTOM, WIDTH, HEIGHT, offsets.x(), offsets.y());
            assertEquals(next.name() + " x", initial.x(), recomputed.x());
            assertEquals(next.name() + " y", initial.y(), recomputed.y());
        }
    }

    private static void assertPosition(OverlayCorner corner, int x, int y) {
        OverlayGeometry.Position position = OverlayGeometry.positionFor(corner,
                LEFT, TOP, RIGHT, BOTTOM, WIDTH, HEIGHT, 0, 0);
        assertEquals(x, position.x());
        assertEquals(y, position.y());
    }
}
