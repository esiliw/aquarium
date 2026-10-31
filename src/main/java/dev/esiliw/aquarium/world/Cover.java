/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0. Port of net.gegy1000.earth.server.world.cover.Cover (ESA CCI land cover classes).
 */
package dev.esiliw.aquarium.world;

import java.util.Arrays;

public enum Cover {
    NO(0),
    RAINFED_CROPLAND(10),
    HERBACEOUS_COVER(11),
    TREE_OR_SHRUB_COVER(12),
    IRRIGATED_CROPLAND(20),
    CROPLAND_WITH_VEGETATION(30),
    VEGETATION_WITH_CROPLAND(40),
    BROADLEAF_EVERGREEN(50),
    BROADLEAF_DECIDUOUS(60),
    BROADLEAF_DECIDUOUS_CLOSED(61),
    BROADLEAF_DECIDUOUS_OPEN(62),
    NEEDLEAF_EVERGREEN(70),
    NEEDLEAF_EVERGREEN_CLOSED(71),
    NEEDLEAF_EVERGREEN_OPEN(72),
    NEEDLEAF_DECIDUOUS(80),
    NEEDLEAF_DECIDUOUS_CLOSED(81),
    NEEDLEAF_DECIDUOUS_OPEN(82),
    MIXED_LEAF_TYPE(90),
    TREE_AND_SHRUB_WITH_HERBACEOUS_COVER(100),
    HERBACEOUS_COVER_WITH_TREE_AND_SHRUB(110),
    SHRUBLAND(120),
    SHRUBLAND_EVERGREEN(121),
    SHRUBLAND_DECIDUOUS(122),
    GRASSLAND(130),
    LICHENS_AND_MOSSES(140),
    SPARSE_VEGETATION(150),
    SPARSE_TREE(151),
    SPARSE_SHRUB(152),
    SPARSE_HERBACEOUS_COVER(153),
    FRESH_FLOODED_FOREST(160),
    SALINE_FLOODED_FOREST(170),
    FLOODED_VEGETATION(180),
    URBAN(190),
    BARE(200),
    BARE_CONSOLIDATED(201),
    BARE_UNCONSOLIDATED(202),
    WATER(210),
    PERMANENT_SNOW(220);

    private static final Cover[] BY_ID = new Cover[256];

    public final int id;

    Cover(int id) {
        this.id = id;
    }

    public static Cover byId(int id) {
        return id >= 0 && id < 256 ? BY_ID[id] : NO;
    }

    public boolean isForest() {
        return switch (this) {
            case BROADLEAF_EVERGREEN, BROADLEAF_DECIDUOUS, BROADLEAF_DECIDUOUS_CLOSED, BROADLEAF_DECIDUOUS_OPEN,
                 NEEDLEAF_EVERGREEN, NEEDLEAF_EVERGREEN_CLOSED, NEEDLEAF_EVERGREEN_OPEN,
                 NEEDLEAF_DECIDUOUS, NEEDLEAF_DECIDUOUS_CLOSED, NEEDLEAF_DECIDUOUS_OPEN, MIXED_LEAF_TYPE,
                 FRESH_FLOODED_FOREST, SALINE_FLOODED_FOREST -> true;
            default -> false;
        };
    }

    public boolean isBare() {
        return this == BARE || this == BARE_CONSOLIDATED || this == BARE_UNCONSOLIDATED;
    }

    static {
        Arrays.fill(BY_ID, NO);
        for (Cover c : values()) BY_ID[c.id] = c;
    }
}
