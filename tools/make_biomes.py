"""Builds Aquarium's custom biomes from vanilla 1.20.1 biome JSON (misode/mcmeta 1.20.1-data), so they stay vanilla in style.
usage: python tools/make_biomes.py <mcmeta dir with biome/*.json>"""
import copy, json, os, sys

M = sys.argv[1]
OUT = "src/main/resources/data/aquarium/worldgen"
for d in ("biome", "configured_feature", "placed_feature"):
    os.makedirs(f"{OUT}/{d}", exist_ok=True)


def vanilla(name):
    return json.load(open(f"{M}/biome/{name}.json"))


def write(kind, name, obj):
    json.dump(obj, open(f"{OUT}/{kind}/{name}.json", "w"), indent=2)


LAVA = {"minecraft:lake_lava_underground", "minecraft:lake_lava_surface"}


def biome(base, vegetal, temperature, downfall, grass=None, foliage=None, precipitation=True, spawners_from=None):
    b = copy.deepcopy(vanilla(base))
    b["features"] = [[f for f in step if f not in LAVA] for step in b["features"]]
    b["features"][9] = vegetal
    b["temperature"], b["downfall"], b["has_precipitation"] = temperature, downfall, precipitation
    if grass is not None:
        b["effects"]["grass_color"] = grass
    if foliage is not None:
        b["effects"]["foliage_color"] = foliage
    if spawners_from:
        s = vanilla(spawners_from)
        b["spawners"], b["spawn_costs"] = s["spawners"], s["spawn_costs"]
    return b


def bush(log, leaves):
    """a one-block trunk with a small leaf ball, like vanilla's jungle bush"""
    cf = json.load(open(f"{M}/cf/jungle_bush.json"))
    cf["config"]["trunk_provider"]["state"]["Name"] = log
    cf["config"]["foliage_provider"]["state"]["Name"] = leaves
    return cf


def placed(feature, counts, sapling="minecraft:oak_sapling"):
    return {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list",
                                              "distribution": [{"data": c, "weight": w} for c, w in counts]}},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive",
                                                                  "state": {"Name": sapling, "Properties": {"stage": "0"}}}},
        {"type": "minecraft:biome"}]}


def checked(feature, sapling):
    """a placed feature for use inside a random selector"""
    return {"feature": feature, "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
        "type": "minecraft:would_survive", "state": {"Name": sapling, "Properties": {"stage": "0"}}}}]}


# --- shared pieces
write("configured_feature", "oak_bush", bush("minecraft:oak_log", "minecraft:oak_leaves"))
write("placed_feature", "oak_bush_checked", checked("aquarium:oak_bush", "minecraft:oak_sapling"))
write("configured_feature", "spruce_bush", bush("minecraft:spruce_log", "minecraft:spruce_leaves"))
write("placed_feature", "spruce_bush_checked", checked("aquarium:spruce_bush", "minecraft:spruce_sapling"))

# Mediterranean woodland: scattered oaks and bushes on dry grass
write("configured_feature", "trees_mediterranean", {"type": "minecraft:random_selector", "config": {
    "default": "minecraft:oak_checked", "features": [
        {"chance": 0.35, "feature": "aquarium:oak_bush_checked"},
        {"chance": 0.25, "feature": "minecraft:fancy_oak_checked"}]}})
write("placed_feature", "trees_mediterranean", placed("aquarium:trees_mediterranean", [(0, 3), (1, 3), (2, 1)]))
# Chaparral: dense low bushes, the odd tree
write("configured_feature", "chaparral_bushes", {"type": "minecraft:random_selector", "config": {
    "default": "aquarium:oak_bush_checked", "features": [{"chance": 0.05, "feature": "minecraft:oak_checked"}]}})
write("placed_feature", "chaparral_bushes", placed("aquarium:chaparral_bushes", [(3, 2), (5, 2), (8, 1)]))
# Tundra: a few low spruce bushes
write("placed_feature", "tundra_bushes", placed("aquarium:spruce_bush", [(0, 4), (1, 1)], "minecraft:spruce_sapling"))
# Subtropical forest: oaks, big oaks and jungle bushes
write("configured_feature", "trees_subtropical", {"type": "minecraft:random_selector", "config": {
    "default": "minecraft:oak_checked", "features": [
        {"chance": 0.3, "feature": "minecraft:jungle_bush"},
        {"chance": 0.2, "feature": "minecraft:fancy_oak_checked"},
        {"chance": 0.05, "feature": "minecraft:birch_checked"}]}})
write("placed_feature", "trees_subtropical", placed("aquarium:trees_subtropical", [(8, 9), (10, 1)]))

V = "minecraft:"
write("biome", "mediterranean_woodland", biome("savanna", [
    V + "glow_lichen", V + "patch_tall_grass", "aquarium:trees_mediterranean", V + "flower_warm", V + "patch_grass_savanna",
    V + "brown_mushroom_normal", V + "red_mushroom_normal", V + "patch_pumpkin"],
    1.2, 0.2, grass=0xA8B05C, foliage=0x8A9A3E, spawners_from="plains"))
write("biome", "chaparral", biome("savanna", [
    V + "glow_lichen", "aquarium:chaparral_bushes", V + "flower_warm", V + "patch_grass_savanna",
    V + "brown_mushroom_normal", V + "red_mushroom_normal"],
    1.2, 0.1, grass=0xB0A867, foliage=0x7E8F3A, spawners_from="plains"))
write("biome", "steppe", biome("savanna", [
    V + "glow_lichen", V + "patch_tall_grass", V + "flower_warm", V + "patch_grass_savanna",
    V + "brown_mushroom_normal", V + "red_mushroom_normal", V + "patch_pumpkin"],
    1.0, 0.1, grass=0xBFB76A, foliage=0x9AA049, spawners_from="plains"))
write("biome", "cold_desert", biome("desert", [
    V + "glow_lichen", V + "patch_grass_badlands", V + "patch_dead_bush_2",
    V + "brown_mushroom_normal", V + "red_mushroom_normal"],
    0.6, 0.0, grass=0xB0A87A, foliage=0x9A9566, precipitation=False))
write("biome", "tundra", biome("plains", [
    V + "glow_lichen", "aquarium:tundra_bushes", V + "patch_grass_badlands",
    V + "brown_mushroom_normal", V + "red_mushroom_normal"],
    0.25, 0.4, grass=0x8E9E6A, foliage=0x6E8050))
write("biome", "subtropical_forest", biome("forest", [
    V + "glow_lichen", V + "forest_flowers", V + "bamboo_light", "aquarium:trees_subtropical", V + "flower_default",
    V + "patch_grass_forest", V + "brown_mushroom_normal", V + "red_mushroom_normal", V + "patch_sugar_cane",
    V + "patch_pumpkin", V + "vines"],
    0.9, 0.85))
# Outback: savanna grass and dead bushes, no trees (Uluru and the red centre)
write("biome", "outback", biome("savanna", [
    V + "glow_lichen", V + "patch_grass_savanna", V + "patch_dead_bush_2",
    V + "brown_mushroom_normal", V + "red_mushroom_normal"],
    2.0, 0.0, grass=0xBFA35A, foliage=0xAE9A4E, precipitation=False))
# ------------------------------------------------------------------ trees: palm and eucalyptus
def leaves_state(name):
    return {"type": "minecraft:simple_state_provider", "state": {"Name": name, "Properties": {"distance": "7", "persistent": "false", "waterlogged": "false"}}}


def log_state(name):
    return {"type": "minecraft:simple_state_provider", "state": {"Name": name, "Properties": {"axis": "y"}}}


# palm: a tall bare trunk with a drooping crown of fronds (cherry-style hanging leaves), growing on sand
write("configured_feature", "palm", {"type": "minecraft:tree", "config": {
    "decorators": [{"type": "minecraft:attached_to_leaves", "probability": 0.08, "exclusion_radius_xz": 0, "exclusion_radius_y": 0,
                    "required_empty_blocks": 1, "directions": ["down"],
                    "block_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "aquarium:coconut"}}}],
    "dirt_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:sand"}},
    "foliage_placer": {"type": "minecraft:cherry_foliage_placer", "radius": 3, "offset": 0, "height": 2,
                       "wide_bottom_layer_hole_chance": 0.6, "corner_hole_chance": 0.5,
                       "hanging_leaves_chance": 0.55, "hanging_leaves_extension_chance": 0.4},
    "foliage_provider": leaves_state("aquarium:palm_leaves"), "force_dirt": False, "ignore_vines": True,
    "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 2},
    "trunk_placer": {"type": "minecraft:straight_trunk_placer", "base_height": 6, "height_rand_a": 3, "height_rand_b": 2},
    "trunk_provider": log_state("aquarium:palm_log")}})
# eucalyptus: tall, branching, with a thin open crown
write("configured_feature", "eucalyptus", {"type": "minecraft:tree", "config": {
    "decorators": [], "dirt_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:dirt"}},
    "foliage_placer": {"type": "minecraft:fancy_foliage_placer", "radius": 2, "offset": 3, "height": 3},
    "foliage_provider": leaves_state("aquarium:eucalyptus_leaves"), "force_dirt": False, "ignore_vines": True,
    "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 0, "lower_size": 0, "min_clipped_height": 6, "upper_size": 0},
    "trunk_placer": {"type": "minecraft:fancy_trunk_placer", "base_height": 8, "height_rand_a": 7, "height_rand_b": 0},
    "trunk_provider": log_state("aquarium:eucalyptus_log")}})
write("placed_feature", "palm_checked", checked("aquarium:palm", "aquarium:palm_sapling"))
write("placed_feature", "eucalyptus_checked", checked("aquarium:eucalyptus", "aquarium:eucalyptus_sapling"))
write("placed_feature", "trees_palm", placed("aquarium:palm", [(0, 3), (1, 2), (2, 1)], "aquarium:palm_sapling"))
write("placed_feature", "trees_eucalyptus_forest", placed("aquarium:eucalyptus", [(5, 4), (7, 2)], "aquarium:eucalyptus_sapling"))
write("placed_feature", "trees_eucalyptus_woodland", placed("aquarium:eucalyptus", [(0, 3), (1, 3), (2, 1)], "aquarium:eucalyptus_sapling"))


# ------------------------------------------------------------------ flowers
def flower_patch(name, chance, tries=48):
    write("configured_feature", f"flower_{name}", {"type": "minecraft:flower", "config": {"tries": tries, "xz_spread": 6, "y_spread": 2,
        "feature": {"feature": {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:simple_state_provider",
                                                                                          "state": {"Name": f"aquarium:{name}"}}}},
                    "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}}]}}})
    write("placed_feature", f"flower_{name}", {"feature": f"aquarium:flower_{name}", "placement": [
        {"type": "minecraft:rarity_filter", "chance": chance}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}]})


flower_patch("lavender", 3, 64)
flower_patch("edelweiss", 6)
flower_patch("lupine", 4, 64)
flower_patch("hibiscus", 6)
flower_patch("desert_pea", 5)
F = "aquarium:flower_"

# flowers go last in each biome's vegetation step (vanilla biomes get theirs appended in Aquarium.java), so the order never conflicts
for name, flower in [("mediterranean_woodland", "lavender"), ("chaparral", "lavender"), ("tundra", "lupine"),
                     ("subtropical_forest", "hibiscus"), ("outback", "desert_pea")]:
    path = f"{OUT}/biome/{name}.json"
    b = json.load(open(path))
    if F + flower not in b["features"][9]:
        b["features"][9].append(F + flower)
    json.dump(b, open(path, "w"), indent=2)

write("biome", "tropical_coast", biome("beach", [
    V + "glow_lichen", "aquarium:trees_palm", V + "patch_grass_savanna", V + "brown_mushroom_normal", V + "red_mushroom_normal",
    F + "hibiscus"], 0.95, 0.8, grass=0x6FBF4A, foliage=0x4FA82E))
write("biome", "eucalyptus_forest", biome("forest", [
    V + "glow_lichen", "aquarium:trees_eucalyptus_forest", V + "patch_grass_forest",
    V + "brown_mushroom_normal", V + "red_mushroom_normal"], 0.9, 0.6, grass=0x9DB46A, foliage=0x7FA58F))
write("biome", "eucalyptus_woodland", biome("savanna", [
    V + "glow_lichen", V + "patch_tall_grass", "aquarium:trees_eucalyptus_woodland", V + "patch_grass_savanna",
    V + "brown_mushroom_normal", V + "red_mushroom_normal", F + "desert_pea"], 1.5, 0.2, grass=0xB7B060, foliage=0x8FA48A))
print("ok")
