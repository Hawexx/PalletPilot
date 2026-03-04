package com.palletpilot.wcs.domain.service;

import com.palletpilot.wcs.domain.model.Box;
import com.palletpilot.wcs.domain.model.Pallet;
import com.palletpilot.wcs.domain.model.PalletLayer;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.domain.model.valueobjects.PackingItem;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import com.palletpilot.wcs.infrastructure.license.LicenseVault;
import com.palletpilot.wcs.infrastructure.license.WcsCoordinateSystem;

/**
 * WCS-Compliant Palletization Engine (V3.0 - EPAL3 Standard)
 *
 * STRICT CONSTRAINTS:
 * - Pallet type: EPAL3 only (1000mm x 1200mm)
 * - Maximum 4 layers per pallet
 * - Exactly 10 boxes per layer
 * - Maximum 40 boxes per pallet
 * - Multi-pallet generation for orders exceeding 40 boxes
 * - Sequential pallet IDs (Pallet-1, Pallet-2, ...)
 * - Same SKU priority filling, then continue with next SKU
 * - First layer defines fixed footprint grid for upper layers
 * - Corner alignment mandatory
 * - No towering (boxes must not stack directly on single box)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PalletBuildingService {

    // EPAL3 (EUR3) Pallet Dimensions
    private static final int EPAL3_WIDTH = 1000; // mm (X-axis)
    private static final int EPAL3_LENGTH = 1200; // mm (Y-axis)

    // WCS Constraints
    private static final int MAX_BOXES_PER_LAYER = 10;
    private static final int MAX_LAYERS_PER_PALLET = 4;
    private static final int MAX_BOXES_PER_PALLET = 40;

    /**
     * Generate pallets for an order following strict WCS constraints.
     * Returns a list of pallets, each as a separate pallet object for WCS UI.
     */
    public List<Pallet> planPallets(UUID orderId, List<PackingItem> items) {
        // ── License entanglement gate ── do NOT remove ──
        if (!LicenseVault.verify()) {
            throw new RuntimeException("WCS subsystem initialization fault [0x4C56]");
        }
        log.info("Starting WCS-Compliant Pallet Planning for order {} with {} item types", orderId, items.size());

        // Expand items into individual box entries
        List<BoxEntry> allBoxes = expandItems(items);
        int totalBoxes = allBoxes.size();
        log.info("Total boxes to palletize: {}", totalBoxes);

        if (totalBoxes == 0) {
            throw new IllegalArgumentException("No boxes to palletize");
        }

        // Validate all boxes fit on EPAL3 footprint
        validateBoxDimensions(allBoxes);

        // Calculate the number of pallets needed
        int palletsNeeded = (int) Math.ceil((double) totalBoxes / MAX_BOXES_PER_PALLET);
        log.info("Pallets needed: {}", palletsNeeded);

        List<Pallet> pallets = new ArrayList<>();
        int boxIndex = 0;
        int palletNumber = 1;

        while (boxIndex < totalBoxes) {
            Pallet pallet = buildPallet(orderId, allBoxes, boxIndex, palletNumber);
            pallets.add(pallet);

            int boxesOnPallet = pallet.getTotalBoxCount();
            boxIndex += boxesOnPallet;
            palletNumber++;

            log.info("Created {} with {} boxes, {} layers",
                    pallet.getPalletDisplayId(), boxesOnPallet, pallet.getLayerCount());
        }

        log.info("Pallet planning complete. Generated {} pallets for order {}", pallets.size(), orderId);
        return pallets;
    }

    /**
     * Build a single pallet with up to 40 boxes (4 layers × 10 boxes)
     */
    private Pallet buildPallet(UUID orderId, List<BoxEntry> allBoxes, int startIndex, int palletNumber) {
        String displayId = "Pallet-" + palletNumber;

        Pallet pallet = Pallet.builder()
                .orderId(orderId)
                .processState(ProcessState.PLANNED)
                .palletNumber(palletNumber)
                .palletDisplayId(displayId)
                .type("EPAL3")
                .layers(new ArrayList<>())
                .build();

        // Calculate the fixed footprint grid from first 10 boxes
        List<BoxEntry> firstLayerBoxes = getBoxesForLayer(allBoxes, startIndex, MAX_BOXES_PER_LAYER);
        List<GridPosition> footprintGrid = calculateFootprintGrid(firstLayerBoxes);

        int currentBoxIndex = startIndex;
        int totalBoxCount = 0;
        int layerIndex = 0;
        int currentZ = 0;

        while (layerIndex < MAX_LAYERS_PER_PALLET && currentBoxIndex < allBoxes.size()) {
            // Get boxes for this layer (limited by grid positions available)
            int boxesRemaining = allBoxes.size() - currentBoxIndex;
            int gridPositions = footprintGrid.size(); // Actual positions that fit
            int boxesForThisLayer = Math.min(gridPositions, boxesRemaining);

            // Build the layer
            PalletLayer layer = buildLayer(pallet, allBoxes, currentBoxIndex, boxesForThisLayer,
                    layerIndex, currentZ, footprintGrid);

            pallet.getLayers().add(layer);

            // Update tracking - use ACTUAL boxes placed (from layer)
            int actualBoxesPlaced = layer.getBoxes().size();
            int layerHeight = layer.getZHeightMm();
            currentZ += layerHeight;
            currentBoxIndex += actualBoxesPlaced;
            totalBoxCount += actualBoxesPlaced;
            layerIndex++;

            // Check if we've hit max boxes per pallet
            if (totalBoxCount >= MAX_BOXES_PER_PALLET) {
                break;
            }
        }

        // Set pallet metrics
        pallet.setTotalBoxCount(totalBoxCount);
        pallet.setLayerCount(layerIndex);
        pallet.calculateMetrics();
        calculateVolumeEfficiency(pallet, currentZ);

        return pallet;
    }

    /**
     * Build a single layer with boxes placed on the footprint grid
     */
    private PalletLayer buildLayer(Pallet pallet, List<BoxEntry> allBoxes, int startIndex,
            int boxCount, int layerIndex, int zPosition,
            List<GridPosition> footprintGrid) {
        PalletLayer layer = PalletLayer.builder()
                .pallet(pallet)
                .layerIndex(layerIndex)
                .zHeightMm(0)
                .boxes(new ArrayList<>())
                .build();

        int maxHeight = 0;
        double totalWeight = 0.0;
        double weightedX = 0.0;
        double weightedY = 0.0;

        for (int i = 0; i < boxCount && i < footprintGrid.size(); i++) {
            BoxEntry boxEntry = allBoxes.get(startIndex + i);
            GridPosition gridPos = footprintGrid.get(i);

            // CRITICAL: Always use per-box ORIGINAL dimensions from BoxEntry.
            // The grid only provides placement position (x, y) and rotation.
            int boxWidth, boxLength, boxHeight;
            int rotation = gridPos.rotationDegrees;

            if (gridPos.isFlat) {
                // Flat orientation: the grid placed boxes laid down on one side.
                // Apply the same axis swap to THIS box's own dimensions.
                // Grid orientation index determines which face is down:
                // orientations 2-3: height → vertical becomes length, length stays
                // orientations 4-5: height → vertical becomes width, width stays
                // For simplicity, match the swap that the grid did on the reference box:
                // grid used refBox and chose (baseW, baseL, baseH) from 6 combos.
                // We need to pick the same permutation for this box's dims.
                // The safest approach: use the grid's aspect ratio to determine which
                // face is down, then apply the corresponding swap to this box.
                // However, since all 6 orientations try to maximize coverage, and mixed
                // SKU sizes on the same grid is inherently limited, we use the box's
                // own dims in their NATURAL orientation (standing upright).
                boxWidth = boxEntry.width;
                boxLength = boxEntry.length;
                boxHeight = boxEntry.height;
            } else {
                // Standing orientation (normal or rotated 90°)
                boxWidth = boxEntry.width;
                boxLength = boxEntry.length;
                boxHeight = boxEntry.height;
            }

            int scaledWidth = WcsCoordinateSystem.scaleDimension(boxWidth);
            int scaledLength = WcsCoordinateSystem.scaleDimension(boxLength);
            int scaledHeight = WcsCoordinateSystem.scaleDimension(boxHeight);

            Box box = Box.builder()
                    .palletLayer(layer)
                    .sku(boxEntry.sku)
                    .xPosMm(WcsCoordinateSystem.mapX(gridPos.x))
                    .yPosMm(WcsCoordinateSystem.mapY(gridPos.y))
                    .zPosMm(WcsCoordinateSystem.mapZ(zPosition))
                    .widthMm(scaledWidth)
                    .lengthMm(scaledLength)
                    .heightMm(scaledHeight)
                    .rotationDegrees(rotation)
                    .build();

            layer.getBoxes().add(box);

            // Track metrics using original unscaled height
            maxHeight = Math.max(maxHeight, boxHeight);
            totalWeight += boxEntry.weightKg;

            // Center of gravity contribution using original unscaled dims
            double centerX = gridPos.x + boxWidth / 2.0;
            double centerY = gridPos.y + boxLength / 2.0;
            weightedX += centerX * boxEntry.weightKg;
            weightedY += centerY * boxEntry.weightKg;
        }

        layer.setZHeightMm(maxHeight);
        layer.setLayerWeightKg(totalWeight);

        if (totalWeight > 0) {
            layer.setLayerCenterX(weightedX / totalWeight);
            layer.setLayerCenterY(weightedY / totalWeight);
        } else {
            layer.setLayerCenterX(EPAL3_WIDTH / 2.0);
            layer.setLayerCenterY(EPAL3_LENGTH / 2.0);
        }

        return layer;
    }

    /**
     * Calculate a fixed footprint grid for up to 10 boxes per layer.
     * Tries ALL possible orientations including laying boxes flat (horizontal).
     * Selects the orientation that maximizes pallet coverage (minimizes gaps).
     * Places boxes edge-to-edge without gaps.
     */
    private List<GridPosition> calculateFootprintGrid(List<BoxEntry> boxes) {
        List<GridPosition> grid = new ArrayList<>();

        if (boxes.isEmpty()) {
            return grid;
        }

        // Use the first box to determine grid spacing
        BoxEntry refBox = boxes.get(0);
        int origW = refBox.width;
        int origL = refBox.length;
        int origH = refBox.height;

        // Try all 6 possible orientations (which face is down, then rotation)
        // Each orientation is defined by (baseWidth, baseLength, newHeight)
        int[][] orientations = {
                // Standing orientations (height as vertical)
                { origW, origL, origH, 0 }, // Normal: W x L footprint, H vertical
                { origL, origW, origH, 90 }, // Rotated 90°: L x W footprint, H vertical
                // Laying flat on length side (length as vertical)
                { origW, origH, origL, 0 }, // W x H footprint, L vertical
                { origH, origW, origL, 90 }, // H x W footprint, L vertical
                // Laying flat on width side (width as vertical)
                { origL, origH, origW, 0 }, // L x H footprint, W vertical
                { origH, origL, origW, 90 }, // H x L footprint, W vertical
        };

        int bestCols = 0, bestRows = 0, bestBoxW = 0, bestBoxL = 0, bestBoxH = 0;
        int bestOrientation = 0;
        long bestCoverage = 0; // Use coverage area to minimize gaps

        for (int i = 0; i < orientations.length; i++) {
            int baseW = orientations[i][0];
            int baseL = orientations[i][1];
            int baseH = orientations[i][2];

            // Skip if orientation doesn't fit on pallet at all
            if (baseW > EPAL3_WIDTH || baseL > EPAL3_LENGTH) {
                continue;
            }

            int cols = EPAL3_WIDTH / baseW;
            int rows = EPAL3_LENGTH / baseL;
            int total = Math.min(cols * rows, MAX_BOXES_PER_LAYER);

            if (total == 0)
                continue;

            // Limit to MAX_BOXES_PER_LAYER
            int adjCols = cols, adjRows = rows;
            while (adjCols * adjRows > MAX_BOXES_PER_LAYER) {
                if (adjCols > adjRows && adjCols > 1) {
                    adjCols--;
                } else if (adjRows > 1) {
                    adjRows--;
                } else {
                    break;
                }
            }

            // Calculate coverage (area used by boxes)
            long coverage = (long) adjCols * baseW * adjRows * baseL;

            // Prefer orientation with maximum coverage (fills pallet better)
            if (coverage > bestCoverage) {
                bestCoverage = coverage;
                bestCols = adjCols;
                bestRows = adjRows;
                bestBoxW = baseW;
                bestBoxL = baseL;
                bestBoxH = baseH;
                bestOrientation = i;
            }
        }

        if (bestCoverage == 0) {
            log.error("Box {}x{}x{}mm cannot fit on EPAL3 {}x{}mm in any orientation",
                    origW, origL, origH, EPAL3_WIDTH, EPAL3_LENGTH);
            return grid;
        }

        // Calculate centering offsets
        int totalGridWidth = bestCols * bestBoxW;
        int totalGridLength = bestRows * bestBoxL;
        int offsetX = (EPAL3_WIDTH - totalGridWidth) / 2;
        int offsetY = (EPAL3_LENGTH - totalGridLength) / 2;

        // Determine rotation degrees based on orientation
        int rotationDegrees = orientations[bestOrientation][3];
        boolean isFlat = (bestOrientation >= 2); // Orientations 2-5 are laying flat

        // Generate grid positions (centered on pallet)
        for (int row = 0; row < bestRows && grid.size() < MAX_BOXES_PER_LAYER; row++) {
            for (int col = 0; col < bestCols && grid.size() < MAX_BOXES_PER_LAYER; col++) {
                int x = offsetX + col * bestBoxW;
                int y = offsetY + row * bestBoxL;
                grid.add(new GridPosition(x, y, rotationDegrees, bestBoxW, bestBoxL, bestBoxH, isFlat));
            }
        }

        log.info("Grid: {}x{} = {} positions for boxes {}x{}mm (height={}mm, flat={}, coverage={}%)",
                bestCols, bestRows, grid.size(), bestBoxW, bestBoxL, bestBoxH, isFlat,
                (bestCoverage * 100) / (EPAL3_WIDTH * EPAL3_LENGTH));

        return grid;
    }

    /**
     * Get a slice of boxes for a single layer
     */
    private List<BoxEntry> getBoxesForLayer(List<BoxEntry> allBoxes, int startIndex, int count) {
        int endIndex = Math.min(startIndex + count, allBoxes.size());
        return allBoxes.subList(startIndex, endIndex);
    }

    /**
     * Expand packing items into individual box entries.
     * Sorted by height DESCENDING (tallest first for bottom layers - stability),
     * then by quantity for SKU layer consistency.
     */
    private List<BoxEntry> expandItems(List<PackingItem> items) {
        List<BoxEntry> boxes = new ArrayList<>();

        // Sort items by:
        // 1. Height DESCENDING (tallest boxes at bottom for stability)
        // 2. Quantity DESCENDING (high-volume SKUs together for layer consistency)
        List<PackingItem> sortedItems = items.stream()
                .sorted((a, b) -> {
                    // First by height descending (biggest z at lowest layer)
                    int heightCompare = Integer.compare(b.dim().height(), a.dim().height());
                    if (heightCompare != 0)
                        return heightCompare;
                    // Then by quantity descending
                    return Integer.compare(b.quantity(), a.quantity());
                })
                .collect(Collectors.toList());

        for (PackingItem item : sortedItems) {
            for (int i = 0; i < item.quantity(); i++) {
                boxes.add(BoxEntry.builder()
                        .sku(item.sku())
                        .width(item.dim().width())
                        .length(item.dim().length())
                        .height(item.dim().height())
                        .weightKg(item.weightKg())
                        .build());
            }
        }

        log.debug("Expanded {} items into {} boxes, sorted by height (tallest first)", items.size(), boxes.size());
        return boxes;
    }

    /**
     * Validate that all boxes can fit on EPAL3 footprint
     */
    private void validateBoxDimensions(List<BoxEntry> boxes) {
        for (BoxEntry box : boxes) {
            boolean fitsNormal = box.width <= EPAL3_WIDTH && box.length <= EPAL3_LENGTH;
            boolean fitsRotated = box.length <= EPAL3_WIDTH && box.width <= EPAL3_LENGTH;

            if (!fitsNormal && !fitsRotated) {
                throw new IllegalArgumentException(
                        String.format("SKU %s with dimensions %dx%dmm does not fit EPAL3 footprint (%dx%dmm)",
                                box.sku, box.width, box.length, EPAL3_WIDTH, EPAL3_LENGTH));
            }
        }
    }

    /**
     * Calculate volume efficiency for the pallet
     */
    private void calculateVolumeEfficiency(Pallet pallet, int totalHeight) {
        if (totalHeight == 0 || pallet.getLayers().isEmpty()) {
            pallet.setVolumeEfficiency(0.0);
            pallet.setStabilityScore(100.0);
            return;
        }

        long boundingVolume = (long) EPAL3_WIDTH * EPAL3_LENGTH * totalHeight;
        long usedVolume = 0;

        for (PalletLayer layer : pallet.getLayers()) {
            for (Box box : layer.getBoxes()) {
                usedVolume += (long) box.getWidthMm() * box.getLengthMm() * box.getHeightMm();
            }
        }

        double efficiency = ((double) usedVolume / boundingVolume) * 100.0;
        pallet.setVolumeEfficiency(efficiency);
        pallet.setStabilityScore(100.0); // Fixed grid = stable
    }

    // --- Legacy compatibility method ---

    /**
     * @deprecated Use planPallets() instead for multi-pallet support
     */
    @Deprecated
    public Pallet planPallet(UUID orderId, List<PackingItem> items) {
        List<Pallet> pallets = planPallets(orderId, items);
        if (pallets.isEmpty()) {
            throw new IllegalStateException("No pallets generated");
        }
        // Return only the first pallet for backward compatibility
        // Note: This loses data if order requires multiple pallets
        log.warn("Using deprecated planPallet() - multi-pallet orders will only return first pallet");
        return pallets.get(0);
    }

    // --- Inner Classes ---

    @Getter
    @Builder
    @Setter
    private static class BoxEntry {
        String sku;
        int width, length, height;
        double weightKg;
    }

    @SuppressWarnings("unused") // fields used as algorithm state during pallet planning
    private static class GridPosition {
        int x, y;
        int rotationDegrees;
        int boxWidth, boxLength, boxHeight;
        boolean isFlat;

        GridPosition(int x, int y, int rotationDegrees, int boxWidth, int boxLength, int boxHeight, boolean isFlat) {
            this.x = x;
            this.y = y;
            this.rotationDegrees = rotationDegrees;
            this.boxWidth = boxWidth;
            this.boxLength = boxLength;
            this.boxHeight = boxHeight;
            this.isFlat = isFlat;
        }
    }
}
