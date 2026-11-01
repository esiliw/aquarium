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
print("ok")
