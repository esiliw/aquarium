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
    """white salt crust with a few thin, faint crack lines between big polygons (tileable)"""
    r = random.Random(17)
    seeds = [(3.2, 4.1), (11.6, 2.7), (7.4, 11.8), (14.2, 12.9)]
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = []
            for sx, sy in seeds:
                for ox in (-16, 0, 16):
                    for oy in (-16, 0, 16):
                        d.append(((x + .5 - sx - ox) ** 2 + (y + .5 - sy - oy) ** 2) ** .5)
            d.sort()
            n = r.random()
            if d[1] - d[0] < .75:
                c = "#d9d3c7" if n < .8 else "#cec7b8"           # crack: a slightly raised beige ridge
            else:
                c = "#f3f1ec" if n < .6 else ("#eae7e0" if n < .92 else "#fbfaf8")
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


STALKS = [(2, 1), (5, -1), (7, 1), (10, -1), (12, 1), (14, -1)]   # base x and which way each plant leans
HEIGHT = {0: 19, 1: 24, 2: 30, 3: 28}                             # of 32 px; the water surface is at 16


def plant(stage):
    """one rice plant, two blocks tall (16x32), bottom half under water: curving stems with leaves branching off"""
    r = random.Random(300 + stage)
    im = Image.new("RGBA", (16, 32), (0, 0, 0, 0))

    def put(x, y, col):
        if 0 <= x < 16 and 0 <= y < 32:
            im.putpixel((x, y), hexc(col))

    def colour(y):
        if stage == 3:
            return r.choice(YELLOW_GREEN) if y > 22 else r.choice(GOLD[:2])
        return r.choice(GREEN[:3])

    for n, (x0, d) in enumerate(STALKS):
        h = HEIGHT[stage] - r.randint(0, 3)
        bend = r.choice([1, 2, 2, 3]) if stage >= 1 else 1
        tip = (x0, 31)
        for k in range(h):
            t = k / max(1, h - 1)
            x = x0 + d * round(bend * t * t)              # straight at the base, curving more towards the top
            y = 31 - k
            put(x, y, colour(y))
            tip = (x, y)
        # one or two leaves peeling away from the stem, going up and outwards
        for _ in range(1 if stage == 0 else 2):
            ly = 31 - r.randint(h // 4, max(h // 4 + 1, h * 2 // 3))
            lx = x0 + d * round(bend * ((31 - ly) / max(1, h - 1)) ** 2)
            side = -d if r.random() < .6 else d
            for k in range(1, r.randint(3, 6)):
                put(lx + side * ((k + 1) // 2), ly - k, colour(ly - k))
        if stage == 3:
            # ripe: the head arches over and hangs down
            x, y = tip
            put(x + d, y, GOLD[2])
            put(x + 2 * d, y + 1, GRAIN[0])
            for k in range(2, 7):
                put(x + 2 * d, y + k, GRAIN[k % 3])
                if k % 2 == 0:
                    put(x + 3 * d, y + k, GRAIN[(k + 1) % 3])
                    put(x + d, y + k, "#c9a23b")
        elif stage == 2:
            x, y = tip
            put(x + d, y + 1, GREEN[2])                    # leaf tip drooping
    return im


def rice(stage, half):
    im = plant(stage)
    return im.crop((0, 0, 16, 16)) if half == "upper" else im.crop((0, 16, 16, 32))


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
    """a wide wooden bowl (the vanilla bowl shape) heaped with white rice"""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    OUT, DARK, MID, LIGHT, HI = "#2b1d0c", "#4a3214", "#5f421a", "#7a5824", "#94702f"
    body = {9: (2, 13), 10: (3, 12), 11: (4, 11), 12: (6, 9)}
    for y, (x0, x1) in body.items():
        for x in range(x0, x1 + 1):
            if y == 12 or x == x0 and y >= 11 or x == x1:
                c = OUT
            elif y == 10 or y == 11 and 6 <= x <= 9:
                c = LIGHT if 4 <= x <= 10 else MID
            else:
                c = MID if x < 9 else DARK
            im.putpixel((x, y), hexc(c))
    for x in range(3, 12):
        im.putpixel((x, 9), hexc(HI if 4 <= x <= 8 else LIGHT))          # front rim catching the light
    im.putpixel((2, 9), hexc(DARK))
    im.putpixel((13, 9), hexc(OUT))
    for y, (x0, x1) in {6: (3, 12), 7: (2, 13), 8: (2, 13)}.items():  # back rim around the rice
        im.putpixel((x0, y), hexc(DARK))
        im.putpixel((x1, y), hexc(OUT if y > 6 else DARK))
    RICE = ["#f8f6f0", "#eeebe3", "#e2ded3"]
    heap = {4: (6, 9), 5: (4, 11), 6: (4, 11), 7: (3, 12), 8: (3, 12)}
    r = random.Random(6)
    for y, (x0, x1) in heap.items():
        for x in range(x0, x1 + 1):
            c = RICE[0] if x < 8 and y < 7 else r.choice(RICE)
            if y == 8 or x == x1:
                c = "#d0cabc"                                          # shade at the right and where it meets the rim
            im.putpixel((x, y), hexc(c))
    im.save(f"{I}/rice_bowl.png")


item_rice()
item_bowl()
print("ok")
