"""Draws Aquarium's 16x16 block and item textures (vanilla-style pixel art), deterministic.
usage: python tools/make_textures.py"""
import os, random
from PIL import Image

B = "src/main/resources/assets/aquarium/textures/block"
I = "src/main/resources/assets/aquarium/textures/item"
os.makedirs(B, exist_ok=True)
os.makedirs(I, exist_ok=True)


def hexc(h, a=255):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), a)


def grain(name, base, tones, seed):
    """sand-like: base colour with scattered lighter and darker grains"""
    r = random.Random(seed)
    im = Image.new("RGBA", (16, 16), hexc(base))
    for y in range(16):
        for x in range(16):
            v = r.random()
            acc = 0
            for col, p in tones:
                acc += p
                if v < acc:
                    im.putpixel((x, y), hexc(col))
                    break
    im.save(f"{B}/{name}.png")


# black volcanic sand: dark grey-black grains with a few glassy glints
grain("black_sand", "#2b282a", [("#3b3739", .22), ("#1d1b1d", .2), ("#474244", .07), ("#5d5862", .025), ("#151314", .05)], 11)


def salt_top():
    """white salt crust broken into polygons with slightly raised grey-beige rims (tileable)"""
    r = random.Random(7)
    seeds = [(r.uniform(0, 16), r.uniform(0, 16)) for _ in range(6)]
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = []
            for sx, sy in seeds:
                for ox in (-16, 0, 16):
                    for oy in (-16, 0, 16):
                        d.append(((x + .5 - sx - ox) ** 2 + (y + .5 - sy - oy) ** 2) ** .5)
            d.sort()
            edge = d[1] - d[0] < 1.1
            n = r.random()
            if edge:
                c = "#c9c2b4" if n < .7 else "#b8b0a0"
            else:
                c = "#f2f0ea" if n < .55 else ("#e8e5dd" if n < .9 else "#fbfaf7")
            im.putpixel((x, y), hexc(c))
    im.save(f"{B}/salt_crust_top.png")


def salt_side():
    """thin salt layers over each other"""
    r = random.Random(8)
    im = Image.new("RGBA", (16, 16))
    bands = ["#f0ede6", "#e6e2d8", "#f4f2ec", "#dcd6c9", "#ece8e0"]
    for y in range(16):
        b = bands[(y // 3 + (1 if y > 9 else 0)) % len(bands)]
        for x in range(16):
            n = r.random()
            c = b if n < .75 else ("#d2cbbd" if n < .88 else "#f8f7f3")
            im.putpixel((x, y), hexc(c))
    im.save(f"{B}/salt_crust.png")


salt_top()
salt_side()

# ------------------------------------------------------------------ rice (crop model: two crossed pairs of planes)
GREEN = ["#3f8a2e", "#4f9d35", "#5fae3e", "#336f26"]
YELLOW_GREEN = ["#8fa93e", "#a2b548", "#7d9a36"]
GOLD = ["#d9b84a", "#c9a23b", "#e6ca62", "#b38c2f"]
GRAIN = ["#e8d27a", "#f0de92", "#d4b85c"]


def blades(im, r, x, y0, y1, cols, lean=0):
    """a leaf blade from y0 (bottom) up to y1, drifting sideways by lean"""
    for y in range(y0, y1 - 1, -1):
        t = (y0 - y) / max(1, y0 - y1)
        xx = int(round(x + lean * t * t))
        if 0 <= xx < 16:
            im.putpixel((xx, y), hexc(r.choice(cols)))


def rice(stage, half):
    r = random.Random(100 + stage * 10 + (0 if half == "lower" else 5))
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    xs = [1, 3, 5, 7, 8, 10, 12, 14]
    if half == "lower":
        # stems in the water: young plants are short, older ones fill the block
        top = {0: 6, 1: 0, 2: 0, 3: 0}[stage]
        cols = YELLOW_GREEN if stage == 3 else GREEN
        for x in xs:
            blades(im, r, x, 15, top + r.randint(0, 2), cols, r.choice([-1, 0, 0, 1]))
    else:
        if stage == 0:
            return im
        if stage == 1:
            for x in xs[::2]:
                blades(im, r, x, 15, 9 + r.randint(0, 3), GREEN, r.choice([-1, 1]))
        elif stage == 2:
            for x in xs:
                blades(im, r, x, 15, 2 + r.randint(0, 4), GREEN, r.choice([-2, -1, 1, 2]))
        else:
            # ripe: yellowing leaves low down, three stalks whose heavy grain heads bend over to one side
            for x in [1, 5, 10, 14]:
                blades(im, r, x, 15, 9 + r.randint(0, 2), YELLOW_GREEN + GOLD[:1], r.choice([-1, 1]))
            for x, d in [(3, 1), (8, -1), (12, 1)]:
                for y in range(15, 2, -1):
                    im.putpixel((x, y), hexc(GOLD[1] if y > 8 else GOLD[0]))
                # head: arches over at the top, grains hanging in a column beside the stalk
                im.putpixel((x + d, 2), hexc(GOLD[2]))
                for y in range(3, 9):
                    im.putpixel((x + d, y), hexc(GRAIN[y % 3]))
                    if 4 <= y <= 7:
                        im.putpixel((x + 2 * d, y), hexc(GRAIN[(y + 1) % 3] if y % 2 else "#c9a23b"))
    return im


for st in range(4):
    for half in ("lower", "upper"):
        rice(st, half).save(f"{B}/rice_{half}_stage{st}.png")


def item_rice():
    """a small bunch of ripe rice: three stalks bending over into hanging grain heads"""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for sx in (3, 5, 7):
        x, y = sx, 15
        while y > 4:                          # stalk going up and slightly right
            im.putpixel((x, y), hexc(GOLD[1] if y > 9 else GOLD[0]))
            y -= 1
            if y % 3 == 0:
                x += 1
        im.putpixel((x + 1, y), hexc(GOLD[2]))   # arching over
        for k in range(5):                    # the head hanging down
            im.putpixel((x + 2, y + 1 + k), hexc(GRAIN[k % 3]))
            if 1 <= k <= 3:
                im.putpixel((x + 3, y + 1 + k), hexc("#c9a23b" if k == 2 else GRAIN[(k + 1) % 3]))
    im.save(f"{I}/rice.png")


def item_bowl():
    """a wooden bowl heaped with white rice"""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    r = random.Random(6)
    wood = ["#7a5230", "#8f6239", "#694626"]
    for y in range(9, 14):
        w = {9: 7, 10: 7, 11: 6, 12: 5, 13: 3}[y]
        for x in range(8 - w, 8 + w):
            c = "#4f331b" if y == 13 or x in (8 - w, 8 + w - 1) else r.choice(wood)
            im.putpixel((x, y), hexc(c))
    for y in range(4, 10):
        w = {4: 2, 5: 4, 6: 5, 7: 6, 8: 6, 9: 7}[y]
        for x in range(8 - w, 8 + w):
            c = r.choice(["#fbfaf6", "#f1efe8", "#e4e0d6"]) if y < 9 else "#d8d3c6"
            im.putpixel((x, y), hexc(c))
    im.save(f"{I}/rice_bowl.png")


item_rice()
item_bowl()
print("ok")
