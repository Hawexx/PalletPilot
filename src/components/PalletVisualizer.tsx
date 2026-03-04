'use client';

import React, { useMemo } from 'react';
import { Canvas } from '@react-three/fiber';
import { OrbitControls, Grid, Text, PerspectiveCamera, Environment, ContactShadows } from '@react-three/drei';
import * as THREE from 'three';

// =============================================================================
// TYPES
// =============================================================================

interface BoxData {
    id?: string;
    sku: string;
    x: number;      // position in mm (pallet-relative, lower-left corner)
    y: number;      // position in mm (pallet-relative, lower-left corner)
    z: number;      // position in mm (height from pallet deck)
    width: number;   // mm — MUST be > 0, or debug box is rendered
    length: number;  // mm — MUST be > 0, or debug box is rendered
    height: number;  // mm — MUST be > 0, or debug box is rendered
    rotation?: number; // degrees (0, 90, 180, 270)
    _hasMissingDims?: boolean; // set by mapper when dims were defaulted
}

interface PalletData {
    id: string;
    boxes: BoxData[];
    width?: number;  // pallet width override (default 1000)
    length?: number; // pallet length override (default 1200)
}

interface VisualizerProps {
    pallets: PalletData[];
}

// =============================================================================
// CONSTANTS
// =============================================================================

// EPAL3 (EUR3) dimensions in mm
const PALLET_WIDTH = 1000;
const PALLET_LENGTH = 1200;
const PALLET_HEIGHT = 144;

// Conversion: 1 scene unit = 1 meter = 1000mm
const MM = 0.001;

// Debug colors
const DEBUG_MISSING_DIMS_COLOR = '#FF0040'; // Bright red for missing dimensions
const DEBUG_MISSING_DIMS_OPACITY = 0.6;

// =============================================================================
// HELPERS
// =============================================================================

/** Deterministic color from SKU string */
const getSkuColor = (sku: string): string => {
    let hash = 0;
    for (let i = 0; i < sku.length; i++) {
        hash = sku.charCodeAt(i) + ((hash << 5) - hash);
    }
    const c = (hash & 0x00FFFFFF).toString(16).toUpperCase();
    return '#' + '00000'.substring(0, 6 - c.length) + c;
};

/**
 * Validate box dimensions and emit dev warnings for invalid/missing values.
 * Returns { w, l, h, isValid } with rotation already applied.
 */
function resolveBoxGeometry(box: BoxData): { w: number; l: number; h: number; isValid: boolean } {
    const isValid = box.width > 0 && box.length > 0 && box.height > 0;

    if (!isValid && process.env.NODE_ENV !== 'production') {
        console.warn(
            `[PalletVisualizer] Missing/zero dimensions for box SKU="${box.sku}":`,
            `width=${box.width}, length=${box.length}, height=${box.height}`,
            `| Raw box:`, box
        );
    }

    // Use actual dims or fallback to a visible debug size (50mm cube)
    let w = isValid ? box.width : 50;
    let l = isValid ? box.length : 50;
    const h = isValid ? box.height : 50;

    // Handle rotation: if 90° or 270°, swap width and length in geometry
    // This way the mesh stays axis-aligned and the footprint swaps correctly
    const rot = (box.rotation ?? 0) % 360;
    if (rot === 90 || rot === 270) {
        const tmp = w;
        w = l;
        l = tmp;
    }

    return { w, l, h, isValid };
}

// =============================================================================
// BOX MESH COMPONENT
// =============================================================================

const BoxMesh = ({ box, color }: { box: BoxData; color: string }) => {
    const { w, l, h, isValid } = resolveBoxGeometry(box);

    // Pallet center offset (boxes are positioned from lower-left corner)
    const palletCenterX = PALLET_WIDTH / 2;
    const palletCenterZ = PALLET_LENGTH / 2;

    // Convert positions: lower-left corner → center of box, then to scene units
    const posX = (box.x + w / 2 - palletCenterX) * MM;
    const posY = (box.z + h / 2) * MM;                    // Y is UP in Three.js
    const posZ = (box.y + l / 2 - palletCenterZ) * MM;

    // Geometry in scene units
    const geoW = w * MM;
    const geoH = h * MM;
    const geoL = l * MM;

    // Choose material based on validity
    const boxColor = isValid ? color : DEBUG_MISSING_DIMS_COLOR;
    const opacity = isValid ? 1.0 : DEBUG_MISSING_DIMS_OPACITY;

    return (
        <group position={[posX, posY, posZ]}>
            {/* Main Box Mesh */}
            <mesh castShadow receiveShadow>
                <boxGeometry args={[geoW, geoH, geoL]} />
                <meshStandardMaterial
                    color={boxColor}
                    roughness={0.3}
                    metalness={0.1}
                    transparent={!isValid}
                    opacity={opacity}
                />
            </mesh>

            {/* Wireframe edges for definition */}
            <lineSegments>
                <edgesGeometry args={[new THREE.BoxGeometry(geoW, geoH, geoL)]} />
                <lineBasicMaterial color="black" linewidth={1} opacity={0.2} transparent />
            </lineSegments>

            {/* SKU Label on top face — only for boxes large enough to read */}
            {(w > 150 || l > 150) && (
                <Text
                    position={[0, geoH / 2 + 0.005, 0]}
                    rotation={[-Math.PI / 2, 0, 0]}
                    fontSize={Math.min(0.08, geoW * 0.5, geoL * 0.5)}
                    color="black"
                    anchorX="center"
                    anchorY="middle"
                    maxWidth={geoW * 0.9}
                >
                    {box.sku}
                </Text>
            )}

            {/* Missing dims warning label */}
            {!isValid && (
                <Text
                    position={[0, geoH / 2 + 0.02, 0]}
                    rotation={[-Math.PI / 2, 0, 0]}
                    fontSize={0.03}
                    color="white"
                    anchorX="center"
                    anchorY="middle"
                >
                    ⚠ NO DIMS
                </Text>
            )}
        </group>
    );
};

// =============================================================================
// PALLET BASE COMPONENT (EPAL3)
// =============================================================================

const PalletBase = ({ xOffset }: { xOffset: number }) => {
    const woodColor = '#C19A6B';
    const darkWoodColor = '#a08060';

    const palletW = PALLET_WIDTH;
    const palletL = PALLET_LENGTH;

    const skidH = 22, skidD = 100;
    const blockW = 145, blockH = 78, blockD = 100;
    const deckH = 22, deckD = 145;

    return (
        <group position={[xOffset, 0, 0]}>
            {/* 3 Bottom Skids (Run along 1200mm length) */}
            {[-0.3, 0, 0.3].map((xRatio, i) => (
                <mesh key={`skid-${i}`} position={[xRatio * palletW * MM, skidH * MM / 2, 0]} castShadow receiveShadow>
                    <boxGeometry args={[skidD * MM, skidH * MM, palletL * MM]} />
                    <meshStandardMaterial color={darkWoodColor} />
                </mesh>
            ))}

            {/* 9 Blocks (3×3 grid) */}
            {[-0.35, 0, 0.35].map((xRatio) => (
                [-0.4, 0, 0.4].map((zRatio, j) => (
                    <mesh key={`block-${xRatio}-${j}`} position={[xRatio * palletW * MM, skidH * MM + blockH * MM / 2, zRatio * palletL * MM]} castShadow receiveShadow>
                        <boxGeometry args={[blockW * MM, blockH * MM, blockD * MM]} />
                        <meshStandardMaterial color={woodColor} />
                    </mesh>
                ))
            ))}

            {/* 3 Top Cross Boards (Run along 1000mm width) */}
            {[-0.4, 0, 0.4].map((zRatio, i) => (
                <mesh key={`cross-${i}`} position={[0, skidH * MM + blockH * MM + deckH * MM / 2, zRatio * palletL * MM]} castShadow receiveShadow>
                    <boxGeometry args={[palletW * MM, deckH * MM, deckD * MM]} />
                    <meshStandardMaterial color={woodColor} />
                </mesh>
            ))}

            {/* 5 Top Deck Boards (Run along 1200mm length) */}
            {[
                { x: -0.4, w: 0.145 },
                { x: -0.2, w: 0.1 },
                { x: 0, w: 0.145 },
                { x: 0.2, w: 0.1 },
                { x: 0.4, w: 0.145 }
            ].map((board, i) => (
                <mesh key={`deck-${i}`} position={[board.x * palletW * MM, 0.122, 0]} castShadow receiveShadow>
                    <boxGeometry args={[board.w, 0.022, palletL * MM]} />
                    <meshStandardMaterial color={woodColor} />
                </mesh>
            ))}
        </group>
    );
};

// =============================================================================
// MAIN VISUALIZER
// =============================================================================

export default function PalletVisualizer({ pallets }: VisualizerProps) {
    const centerOffset = ((pallets.length - 1) * 2) / 2;

    // Dev instrumentation: log first 10 boxes on initial render
    useMemo(() => {
        if (process.env.NODE_ENV !== 'production') {
            const allBoxes = pallets.flatMap(p => p.boxes);
            if (allBoxes.length > 0) {
                console.groupCollapsed(`[PalletVisualizer] ${allBoxes.length} boxes across ${pallets.length} pallet(s)`);
                console.table(
                    allBoxes.slice(0, 10).map((b, i) => ({
                        '#': i,
                        sku: b.sku,
                        width: b.width,
                        length: b.length,
                        height: b.height,
                        x: b.x,
                        y: b.y,
                        z: b.z,
                        rotation: b.rotation ?? 0,
                        valid: b.width > 0 && b.length > 0 && b.height > 0 ? '✓' : '✗',
                    }))
                );
                console.groupEnd();
            }
        }
    }, [pallets]);

    return (
        <div className="w-full h-full bg-slate-100 rounded-lg overflow-hidden border shadow-inner relative">
            <Canvas shadows dpr={[1, 2]}>
                <PerspectiveCamera makeDefault position={[2, 3, 4]} fov={50} />
                <OrbitControls makeDefault minPolarAngle={0} maxPolarAngle={Math.PI / 2.1} />

                {/* Lighting */}
                <ambientLight intensity={0.5} />
                <directionalLight
                    position={[5, 10, 5]}
                    intensity={1.5}
                    castShadow
                    shadow-mapSize={[1024, 1024]}
                />
                <Environment preset="city" />

                {/* Scene Content */}
                <group position={[-centerOffset, 0, 0]}>
                    {pallets.map((pallet, idx) => {
                        const xPos = idx * 2.0;
                        return (
                            <group key={pallet.id || idx} position={[xPos, 0, 0]}>
                                <PalletBase xOffset={0} />
                                <group position={[0, PALLET_HEIGHT * MM, 0]}>
                                    {pallet.boxes.map((box, bIdx) => (
                                        <BoxMesh key={bIdx} box={box} color={getSkuColor(box.sku)} />
                                    ))}
                                </group>
                                <Text
                                    position={[0, 2.5, 0]}
                                    fontSize={0.2}
                                    color="#333"
                                    anchorX="center"
                                    anchorY="bottom"
                                >
                                    Pallet #{idx + 1}
                                </Text>
                            </group>
                        );
                    })}
                </group>

                {/* Floor Grid */}
                <Grid
                    position={[0, -0.01, 0]}
                    args={[20, 20]}
                    cellSize={1}
                    cellThickness={1}
                    cellColor="#ccc"
                    sectionSize={5}
                    sectionThickness={1.5}
                    sectionColor="#888"
                    fadeDistance={30}
                    infiniteGrid
                />
                <ContactShadows position={[0, 0, 0]} opacity={0.4} scale={20} blur={2} far={4} />
            </Canvas>

            {/* Overlay UI */}
            <div className="absolute top-4 right-4 bg-white/90 p-4 rounded-md shadow-lg backdrop-blur text-sm">
                <h3 className="font-bold mb-2">Scene Info</h3>
                <div className="flex gap-2 items-center">
                    <div className="w-3 h-3 rounded-full bg-green-500"></div>
                    <span>{pallets.reduce((acc, p) => acc + p.boxes.length, 0)} Total Boxes</span>
                </div>
                <div className="flex gap-2 items-center">
                    <div className="w-3 h-3 rounded-full bg-blue-500"></div>
                    <span>{pallets.length} Pallets</span>
                </div>
                <div className="mt-2 text-xs text-gray-500">
                    Left Click: Rotate <br /> Right Click: Pan <br /> Scroll: Zoom
                </div>
            </div>
        </div>
    );
}
