"""Generates blockstates, models, loot tables, recipes and tags for Aquarium's wood sets and flowers from the vanilla
oak / poppy JSON (misode/mcmeta 1.20.1-assets-json and 1.20.1-data). usage: python tools/make_wood.py <vanilla dir>"""
import json, os, re, sys

V = sys.argv[1]
R = "src/main/resources"
WOODS = ["palm", "eucalyptus"]
FLOWERS = {"lavender": "purple", "edelweiss": "white", "lupine": "blue", "hibiscus": "red", "desert_pea": "red"}


def load(p):
    return open(f"{V}/{p}").read()


def write(p, text):
    os.makedirs(os.path.dirname(f"{R}/{p}"), exist_ok=True)
    obj = json.loads(text)
    json.dump(obj, open(f"{R}/{p}", "w"), indent=2)


def sub(text, old, new):
    # minecraft:[block/|item/|blocks/][stripped_|potted_]oak_x -> aquarium:...<new>_x
    text = re.sub(r'minecraft:(block/|item/|blocks/)?(stripped_|potted_)?' + old + '_', lambda m: f"aquarium:{m.group(1) or ''}{m.group(2) or ''}{new}_", text)
    text = text.replace(f'"#minecraft:{old}_logs"', f'"#aquarium:{new}_logs"').replace(f'minecraft:{old}_logs', f'aquarium:{new}_logs')
    return text


names = ["oak_log", "oak_wood", "stripped_oak_log", "stripped_oak_wood", "oak_planks", "oak_stairs", "oak_slab", "oak_fence",
         "oak_fence_gate", "oak_door", "oak_trapdoor", "oak_button", "oak_pressure_plate", "oak_leaves", "oak_sapling", "potted_oak_sapling"]
for w in WOODS:
    for n in names:
        target = n.replace("oak", w)
        write(f"assets/aquarium/blockstates/{target}.json", sub(load(f"blockstates/{n}.json"), "oak", w))
        loot = json.loads(sub(load(f"loot_tables/{n}.json"), "oak", w))
        if n == "oak_leaves":   # no apples from palms or gum trees
            loot["pools"] = [p for p in loot["pools"] if "minecraft:apple" not in json.dumps(p)]
        write(f"data/aquarium/loot_tables/blocks/{target}.json", json.dumps(loot))
        if not n.startswith("potted"):
            write(f"assets/aquarium/models/item/{target}.json", sub(load(f"models_item/{n}.json"), "oak", w))
    for f in os.listdir(f"{V}/models_block"):
        if "oak" in f:
            write(f"assets/aquarium/models/block/{f.replace('oak', w)}", sub(load(f"models_block/{f}"), "oak", w))
    for r in ["oak_planks", "oak_wood", "stripped_oak_wood", "oak_stairs", "oak_slab", "oak_fence", "oak_fence_gate", "oak_door",
              "oak_trapdoor", "oak_button", "oak_pressure_plate"]:
        write(f"data/aquarium/recipes/{r.replace('oak', w)}.json", sub(load(f"recipes/{r}.json"), "oak", w))
    # the log tag the planks recipe asks for
    write(f"data/aquarium/tags/items/{w}_logs.json", json.dumps({"values": [f"aquarium:{w}_log", f"aquarium:{w}_wood", f"aquarium:stripped_{w}_log", f"aquarium:stripped_{w}_wood"]}))
    write(f"data/aquarium/tags/blocks/{w}_logs.json", json.dumps({"values": [f"aquarium:{w}_log", f"aquarium:{w}_wood", f"aquarium:stripped_{w}_log", f"aquarium:stripped_{w}_wood"]}))

for fl, dye in FLOWERS.items():
    write(f"assets/aquarium/blockstates/{fl}.json", load("blockstates/poppy.json").replace("minecraft:block/poppy", f"aquarium:block/{fl}"))
    write(f"assets/aquarium/blockstates/potted_{fl}.json", load("blockstates/potted_poppy.json").replace("minecraft:block/potted_poppy", f"aquarium:block/potted_{fl}"))
    write(f"assets/aquarium/models/block/{fl}.json", load("models_block/poppy.json").replace("minecraft:block/poppy", f"aquarium:block/{fl}"))
    write(f"assets/aquarium/models/block/potted_{fl}.json", load("models_block/potted_poppy.json").replace("minecraft:block/poppy", f"aquarium:block/{fl}"))
    write(f"assets/aquarium/models/item/{fl}.json", json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"aquarium:block/{fl}"}}))
    write(f"data/aquarium/loot_tables/blocks/{fl}.json", load("loot_tables/poppy.json").replace("minecraft:poppy", f"aquarium:{fl}").replace("minecraft:blocks/poppy", f"aquarium:blocks/{fl}"))
    write(f"data/aquarium/loot_tables/blocks/potted_{fl}.json", load("loot_tables/potted_poppy.json").replace("minecraft:poppy", f"aquarium:{fl}").replace("minecraft:blocks/potted_poppy", f"aquarium:blocks/potted_{fl}"))
    rec = json.loads(load("recipes/red_dye_from_poppy.json"))
    rec["group"] = f"{dye}_dye"
    rec["ingredients"] = [{"item": f"aquarium:{fl}"}]
    rec["result"] = {"item": f"minecraft:{dye}_dye"}
    write(f"data/aquarium/recipes/{dye}_dye_from_{fl}.json", json.dumps(rec))


def tag(path, values):
    p = f"{R}/data/minecraft/tags/{path}.json"
    old = json.load(open(p))["values"] if os.path.exists(p) else []
    write(f"data/minecraft/tags/{path}.json", json.dumps({"replace": False, "values": sorted(set(old) | set(values))}))


A = lambda fmt: [f"aquarium:{fmt.format(w=w)}" for w in WOODS]
logs = A("{w}_log") + A("{w}_wood") + A("stripped_{w}_log") + A("stripped_{w}_wood")
fl = [f"aquarium:{f}" for f in FLOWERS]
for kind in ("blocks", "items"):
    tag(f"{kind}/logs_that_burn", logs)
    tag(f"{kind}/logs", logs)
    tag(f"{kind}/planks", A("{w}_planks"))
    tag(f"{kind}/wooden_stairs", A("{w}_stairs"))
    tag(f"{kind}/wooden_slabs", A("{w}_slab"))
    tag(f"{kind}/wooden_fences", A("{w}_fence"))
    tag(f"{kind}/fence_gates", A("{w}_fence_gate"))
    tag(f"{kind}/wooden_doors", A("{w}_door"))
    tag(f"{kind}/wooden_trapdoors", A("{w}_trapdoor"))
    tag(f"{kind}/wooden_buttons", A("{w}_button"))
    tag(f"{kind}/wooden_pressure_plates", A("{w}_pressure_plate"))
    tag(f"{kind}/leaves", A("{w}_leaves"))
    tag(f"{kind}/saplings", A("{w}_sapling"))
    tag(f"{kind}/small_flowers", fl)
tag("blocks/flower_pots", A("potted_{w}_sapling") + [f"aquarium:potted_{f}" for f in FLOWERS])
tag("blocks/mineable/axe", logs + A("{w}_planks") + A("{w}_stairs") + A("{w}_slab") + A("{w}_fence") + A("{w}_fence_gate")
    + A("{w}_door") + A("{w}_trapdoor") + A("{w}_button") + A("{w}_pressure_plate"))
tag("blocks/mineable/hoe", A("{w}_leaves"))
tag("items/non_flammable_wood", [])
print("ok")
