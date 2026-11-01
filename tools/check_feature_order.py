"""Minecraft refuses to start if two biomes order the same features differently within a step ("feature order cycle").
Checks every biome the generator can pick. usage: python tools/check_feature_order.py <mcmeta dir> <biome ids...>"""
import json, sys
M, ids = sys.argv[1], sys.argv[2:]
graphs = {}
for bid in ids:
    ns, name = bid.split(":")
    path = f"{M}/biome/{name}.json" if ns == "minecraft" else f"src/main/resources/data/aquarium/worldgen/biome/{name}.json"
    feats = json.load(open(path))["features"]
    for step, lst in enumerate(feats):
        g = graphs.setdefault(step, {})
        for i, a in enumerate(lst):
            for b in lst[i + 1:]:
                g.setdefault(a, set()).add(b)
bad = 0
for step, g in graphs.items():
    # any cycle in the "comes before" graph, of any length
    state = {}
    def visit(n, path):
        global bad
        state[n] = 1
        for m in g.get(n, ()):
            if state.get(m) == 1:
                print(f"CYCLE step {step}: " + " -> ".join(path[path.index(m):] + [m]) if m in path else f"CYCLE step {step} at {m}")
                bad += 1
            elif m not in state:
                visit(m, path + [m])
        state[n] = 2
    for n in list(g):
        if n not in state:
            visit(n, [n])
# features may also not appear in two different steps
where = {}
for step, g in graphs.items():
    for a in set(g) | {b for o in g.values() for b in o}:
        where.setdefault(a, set()).add(step)
for a, steps in where.items():
    if len(steps) > 1:
        print(f"MULTI-STEP {a}: {sorted(steps)}")
        bad += 1
print("feature order OK" if bad == 0 else f"{bad} problems")
sys.exit(1 if bad else 0)
