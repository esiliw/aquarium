"""Draws the palm and eucalyptus wood textures and the five flowers (16x16, vanilla style). usage: python tools/make_wood_textures.py"""
import os, random
from PIL import Image

B = "src/main/resources/assets/aquarium/textures/block"
I = "src/main/resources/assets/aquarium/textures/item"


def hexc(h):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255)


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def pick(r, cols, w=None):
    return r.choices(cols, weights=w)[0] if w else r.choice(cols)


def grid(rows, pal):
    """pixel map: one character per pixel, '.' transparent"""
    im = new()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x, y), hexc(pal[ch]))
    return im


# ------------------------------------------------------------------ wood palettes
WOOD = {
    "palm": dict(
        bark=["#bab4a9", "#aea89c", "#c6c1b6"], bark_dark="#7d766b", bark_light="#d6d1c6",
        inner=["#b47c56", "#a9714c", "#bd875f"], inner_dark="#4f2f22",
        planks=["#b07a55", "#a46e4b", "#bb8660"], seam="#8a5739", grain="#4f2f22"),
    "eucalyptus": dict(
        bark=["#a9a497", "#cfc6ae", "#9a978a", "#c79f8c", "#b8a07f"], bark_dark="#7a776a", bark_light="#ddd4bd",
        inner=["#c98e6c", "#bf805f", "#d49c7b"], inner_dark="#9c5e40",
        planks=["#a35f40", "#984f35", "#b06d4b"], seam="#7f4329", grain="#8f4f33"),
}


def bark(w, p):
    r = random.Random(sum(map(ord, w)))
    im = new()
    if w == "palm":
        # coconut trunk: pale grey-white bands, darker ring scars where fronds fell, fine wavy cracks
        kind = [k for h, k in [(5, "b"), (1, "s"), (1, "l"), (5, "b"), (1, "s"), (3, "l")] for _ in range(h)]
        for y in range(16):
            for x in range(16):
                k = kind[y]
                c = pick(r, p["bark"]) if k == "b" else (p["bark_dark"] if k == "s" and r.random() < .7 else pick(r, [p["bark_light"], p["bark"][2]]))
                im.putpixel((x, y), hexc(c))
        for x0 in (2, 7, 11, 14):
            x = x0
            for y in range(16):
                if kind[y] == "b" and r.random() < .8:
                    im.putpixel((x % 16, y), hexc("#8f897e"))
                if r.random() < .25:
                    x += r.choice([-1, 1])
    else:
        # pale smooth gum bark with long vertical streaks where it peels, a few rainbow tints
        base = ["#e3dccb", "#d9d2c0", "#cfcbc0", "#e8e2d3"]
        streaks = ["#a9b0b0", "#b8bdb8", "#c9a497", "#d39a62", "#a6b77a", "#9fa7a6"]
        for y in range(16):
            for x in range(16):
                im.putpixel((x, y), hexc(pick(r, base, [4, 3, 2, 1])))
        for _ in range(9):
            x = r.randrange(16)
            y0 = r.randrange(16)
            col = pick(r, streaks, [4, 3, 2, 2, 1, 2])
            for k in range(r.randint(4, 11)):
                im.putpixel((x, (y0 + k) % 16), hexc(col))
                if r.random() < .25 and x + 1 < 16:
                    im.putpixel((x + 1, (y0 + k) % 16), hexc(col))
            im.putpixel((x, (y0 - 1) % 16), hexc("#8a7d6c"))      # the curled edge where the bark lifts
    return im


def log_top(w, p, stripped):
    r = random.Random(sum(map(ord, w)) + 7)
    im = new()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            d = max(abs(x - 7.5), abs(y - 7.5))
            if w == "palm":
                if edge and not stripped:
                    c = pick(r, p["bark"])
                elif d > 5.5:
                    c = pick(r, ["#7a5a3a", "#6c4f33"])               # dark outer ring of hard fibres
                else:
                    c = pick(r, ["#d89a4a", "#cf9045", "#e0a65a"]) if r.random() < .85 else "#b87a3a"
            else:
                if edge and not stripped:
                    c = pick(r, p["bark"])
                else:
                    c = p["inner_dark"] if int(d) % 3 == 0 else pick(r, p["inner"])
                    if edge:
                        c = p["inner_dark"]
            im.putpixel((x, y), hexc(c))
    return im


def stripped(w, p):
    r = random.Random(sum(map(ord, w)) + 3)
    im = new()
    if w == "eucalyptus":
        # fresh underbark: smooth cream with faint green and orange
        for x in range(16):
            base = pick(r, ["#e6dcc3", "#dfd3b6", "#e9e1cc"])
            for y in range(16):
                c = base if r.random() < .8 else pick(r, ["#d6c99c", "#e0c6a4", "#cfd3ad"])
                im.putpixel((x, y), hexc(c))
        return im
    # coconut timber: reddish brown with dark fibre flecks running along the trunk
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), hexc(pick(r, p["inner"])))
    for _ in range(22):
        x, y = r.randrange(16), r.randrange(16)
        for k in range(r.randint(2, 4)):
            im.putpixel((x, (y + k) % 16), hexc(p["inner_dark"] if k else "#6a3f2c"))
    return im


def planks(w, p):
    r = random.Random(sum(map(ord, w)) + 5)
    im = new()
    for y in range(16):
        board = y // 4
        for x in range(16):
            c = pick(r, p["planks"], [5, 3, 2])
            if y % 4 == 3:
                c = p["seam"]
            elif x == (5 + board * 7) % 16:
                c = p["seam"]                                   # board ends, staggered
            elif w != "palm" and r.random() < .12:
                c = p["grain"]
            im.putpixel((x, y), hexc(c))
    if w == "palm":
        # fibres hinted by shading: small flecks only a little darker (or lighter) than the wood
        for _ in range(26):
            x, y = r.randrange(16), r.randrange(16)
            if y % 4 == 3 or x == (5 + (y // 4) * 7) % 16:
                continue
            im.putpixel((x, y), hexc(pick(r, ["#986443", "#8f5d3e", "#c39070"], [4, 2, 2])))
            if r.random() < .3 and (x + 1) % 16 != (5 + (y // 4) * 7) % 16:
                im.putpixel(((x + 1) % 16, y), hexc("#9e6a48"))
    return im


def door(w, p, half):
    r = random.Random(sum(map(ord, w)) + (11 if half == "top" else 12))
    im = new()
    for y in range(16):
        for x in range(16):
            frame = x in (0, 15) or (half == "top" and y == 0) or (half == "bottom" and y == 15)
            c = p["seam"] if frame else (p["planks"][0] if x % 5 else p["grain"])
            if not frame and r.random() < .15:
                c = pick(r, p["planks"])
            if half == "top" and 3 <= y <= 9 and x in (3, 4, 5, 6, 9, 10, 11, 12):
                im.putpixel((x, y), (0, 0, 0, 0))               # two little windows
                continue
            if half == "top" and (y in (2, 10) and 2 <= x <= 13 or x in (2, 7, 8, 13) and 2 <= y <= 10):
                c = p["seam"]
            if half == "bottom" and x == 12 and y in (1, 2):
                c = "#3a3a3a"                                    # handle
            im.putpixel((x, y), hexc(c))
    return im


def trapdoor(w, p):
    im = new()
    r = random.Random(sum(map(ord, w)) + 13)
    for y in range(16):
        for x in range(16):
            frame = x in (0, 15) or y in (0, 15) or x in (7, 8) or y in (7, 8)
            if not frame and (x + y) % 2 == 0 and 2 <= x <= 13 and 2 <= y <= 13 and x not in (6, 9) and y not in (6, 9):
                continue                                         # lattice holes
            c = p["seam"] if frame and (x in (0, 15) or y in (0, 15)) else pick(r, p["planks"])
            im.putpixel((x, y), hexc(c))
    return im


def door_item(w, p):
    top, bottom = door(w, p, "top"), door(w, p, "bottom")
    im = new()
    for y in range(16):
        for x in range(4, 12):
            src = top if y < 8 else bottom
            px = src.getpixel((int((x - 4) * 2), int((y % 8) * 2)))
            if px[3]:
                im.putpixel((x, y), px)
    return im


def leaves(w):
    r = random.Random(sum(map(ord, w)) + 17)
    im = new()
    if w == "palm":
        greens = ["#4f8f2c", "#5fa236", "#3d7524", "#6db540"]
        for y in range(16):
            for x in range(16):
                # long frond strips running diagonally, with gaps between them
                band = (x + 2 * y) % 8
                if band in (6, 7) and r.random() < .7:
                    continue
                im.putpixel((x, y), hexc(pick(r, greens, [4, 3, 2, 1])))
    else:
        # gum leaves: dense blue-grey-green, a few small gaps
        greens = ["#5b8a78", "#4c7a69", "#6d9c89", "#3f6858", "#7aa894"]
        for y in range(16):
            for x in range(16):
                if r.random() < .14:
                    continue
                im.putpixel((x, y), hexc(pick(r, greens, [4, 4, 2, 2, 1])))
    return im


PALM_SAPLING = [
    "................",
    "................",
    "....GG....gg....",
    "..gG..G..G..Gg..",
    ".g.....GgG.....g",
    "......gGGGg.....",
    ".....G..t..G....",
    "....g...t...g...",
    "........t.......",
    "........t.......",
    ".......tt.......",
    ".......t........",
    ".......t........",
    ".......tt.......",
    "........t.......",
    "........t.......",
]
EUC_SAPLING = [
    "................",
    "......e.........",
    ".....eE..E......",
    "......s.eE......",
    "....E.s.s..e....",
    "...eE.ss...Ee...",
    "......s..e......",
    ".....Es.Ee......",
    ".....e.s........",
    ".......s........",
    ".......s........",
    "......ss........",
    ".......s........",
    ".......s........",
    "......ss........",
    ".......s........",
]


def flowers():
    pal = {"g": "#4e8f30", "G": "#3b7324", "l": "#6aa63e", "w": "#9fae94", "W": "#c4ccbb",
           "p": "#8a6cc4", "P": "#a58ad8", "q": "#6f52a8",
           "e": "#f4f4ee", "E": "#d9dccf", "y": "#e8cf5a",
           "b": "#5d6fd0", "B": "#8a95e6", "v": "#7a62c8",
           "r": "#d42a3a", "R": "#a81d2b", "Y": "#f2d04a",
           "d": "#d8262e", "D": "#a51b22", "k": "#1b1717", "s": "#8e9b84"}
    art = {
        "lavender": [
            "................",
            "......P.........",
            ".....Pp...P.....",
            ".....pP..Pq.....",
            ".....qp..pP..P..",
            ".....Pq..qp.Pp..",
            ".....pg..Pq.pq..",
            "......g..pg.qP..",
            "......g...g..g..",
            "......g...g..g..",
            "......g..g..g...",
            "......gg.g..g...",
            ".......g.g.g....",
            ".......g.gg.....",
            ".......ggg......",
            "........g.......",
        ],
        "edelweiss": [
            "................",
            "................",
            "................",
            "......E.e.......",
            "....eeEyEee.....",
            ".....eyyye......",
            "....EeyyyeE.....",
            ".....eEyEe......",
            "......e.E.......",
            "........w.......",
            "....W...w..W....",
            ".....Ww.w.wW....",
            "......wWwWw.....",
            ".......www......",
            "........w.......",
            "........w.......",
        ],
        "lupine": [
            "........B.......",
            ".......BbB......",
            ".......bBb......",
            "......BbvbB.....",
            ".......vbv......",
            "......bvBvb.....",
            ".......vbv......",
            "......bvbvb.....",
            ".......vgv......",
            "........g.......",
            "........g.......",
            "...l.l..g..l.l..",
            "....lll.g.lll...",
            ".....lGlgGl.....",
            "......lGgG......",
            "........g.......",
        ],
        "hibiscus": [
            "................",
            "................",
            "................",
            ".....rr..rr.....",
            "....rRrrrrRr....",
            "....rrRYYRrr....",
            ".....rrYYrr.....",
            "....rrRrrRrr....",
            ".....rr.Grr.....",
            "........g.......",
            "....ll..g.......",
            ".....lllg..ll...",
            ".......lg.ll....",
            "........gl......",
            "........g.......",
            "........g.......",
        ],
        "desert_pea": [
            "................",
            "................",
            "...d........d...",
            "..dDd......dDd..",
            "..dkd..d...dkd..",
            "...dD.dDd..Dd...",
            "....s.dkd..s....",
            "....s..dD.s.....",
            ".....s..s.s.....",
            "..s..s..s.s..s..",
            "...s.s..ss..s...",
            "....sss.s..s....",
            "......s.s.s.....",
            ".......sss......",
            "........s.......",
            "........s.......",
        ],
    }
    for name, rows in art.items():
        grid(rows, pal).save(f"{B}/{name}.png")


def palm_door_full():
    """beach hut: bleached driftwood, louvred top half, darker weathered palm-wood frame"""
    r = random.Random(41)
    im = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    frame, frame_d = ["#8e5f40", "#83573a", "#99694a"], "#5e3b27"
    drift, drift_d, gap = WOOD["palm"]["planks"], "#7a4a32", "#4f2f22"
    for y in range(32):
        for x in range(16):
            if x in (0, 15) or y in (0, 31) or y in (15, 16):
                c = frame_d if (x == 15 or y == 31) else pick(r, frame)
            elif x in (1, 14):
                c = pick(r, frame)
            elif y < 15:
                c = gap if y % 2 == 0 else pick(r, drift)          # slanted louvres: slat, shadow, slat...
                if y % 2 == 1 and x == 2:
                    c = drift_d
            else:
                c = drift_d if x in (5, 10) else pick(r, drift)     # three vertical boards
                if r.random() < .08:
                    c = drift_d
            im.putpixel((x, y), hexc(c))
    for y in (19, 20):
        im.putpixel((12, y), hexc("#8a6a42"))                      # rope pull
    im.putpixel((11, 21), hexc("#8a6a42"))
    return im


def euc_door_full():
    """solid framed gum-wood door with carved grooves, warm red-ochre"""
    r = random.Random(42)
    im = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    p = WOOD["eucalyptus"]
    deep, light = "#5e2f1c", "#c7845f"
    for y in range(32):
        for x in range(16):
            c = pick(r, p["planks"], [5, 3, 2])
            if x in (0, 15) or y in (0, 31):
                c = p["seam"]
            elif x in (1, 14) or y in (1, 30, 15, 16):
                c = "#8e4d32"                                       # raised frame and middle rail
            im.putpixel((x, y), hexc(c))
    # carved grooves in each panel: an inset rectangle, light on the top-left edge, dark on the bottom-right
    for (y0, y1) in ((3, 13), (18, 28)):
        for x in range(3, 13):
            im.putpixel((x, y0), hexc(deep))
            im.putpixel((x, y1), hexc(light))
        for y in range(y0, y1 + 1):
            im.putpixel((3, y), hexc(deep))
            im.putpixel((12, y), hexc(light))
        # three vertical flutes inside
        for x in (6, 9):
            for y in range(y0 + 2, y1 - 1):
                im.putpixel((x, y), hexc(deep))
                im.putpixel((x + 1, y), hexc(light))
    im.putpixel((12, 17), hexc("#d9b36a"))                         # brass knob
    im.putpixel((12, 18), hexc("#a8833f"))
    return im


def palm_trapdoor():
    """boardwalk slats with gaps, darker rim"""
    r = random.Random(43)
    im = new()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = pick(r, ["#8e5f40", "#83573a"])
            elif y % 3 == 0:
                continue                                           # gaps between the slats
            else:
                c = pick(r, WOOD["palm"]["planks"])
            im.putpixel((x, y), hexc(c))
    return im


def euc_trapdoor():
    r = random.Random(44)
    p = WOOD["eucalyptus"]
    im = new()
    deep, light = "#5e2f1c", "#c7845f"
    for y in range(16):
        for x in range(16):
            c = p["seam"] if x in (0, 15) or y in (0, 15) else ("#8e4d32" if x in (1, 14) or y in (1, 14) else pick(r, p["planks"], [5, 3, 2]))
            im.putpixel((x, y), hexc(c))
    for k in range(3, 13):
        im.putpixel((k, 3), hexc(deep)); im.putpixel((k, 12), hexc(light))
        im.putpixel((3, k), hexc(deep)); im.putpixel((12, k), hexc(light))
    for k in range(5, 11):
        im.putpixel((k, 7), hexc(deep)); im.putpixel((k, 8), hexc(light))
    return im


DOORS = {"palm": (palm_door_full, palm_trapdoor), "eucalyptus": (euc_door_full, euc_trapdoor)}

for w, p in WOOD.items():
    bark(w, p).save(f"{B}/{w}_log.png")
    log_top(w, p, False).save(f"{B}/{w}_log_top.png")
    stripped(w, p).save(f"{B}/stripped_{w}_log.png")
    log_top(w, p, True).save(f"{B}/stripped_{w}_log_top.png")
    planks(w, p).save(f"{B}/{w}_planks.png")
    full = DOORS[w][0]()
    full.crop((0, 0, 16, 16)).save(f"{B}/{w}_door_top.png")
    full.crop((0, 16, 16, 32)).save(f"{B}/{w}_door_bottom.png")
    DOORS[w][1]().save(f"{B}/{w}_trapdoor.png")
    item = new()
    item.alpha_composite(full.resize((8, 16), Image.NEAREST), (4, 0))
    item.save(f"{I}/{w}_door.png")
    leaves(w).save(f"{B}/{w}_leaves.png")
grid(PALM_SAPLING, {"g": "#5fa236", "G": "#3d7524", "t": "#8a7356"}).save(f"{B}/palm_sapling.png")
grid(EUC_SAPLING, {"e": "#84ad99", "E": "#5f8a77", "s": "#c6ac9c"}).save(f"{B}/eucalyptus_sapling.png")
flowers()


def coconut():
    """a coconut: fibrous brown husk, a bit of green-brown"""
    r = random.Random(91)
    im = new()
    husk = ["#6b4a2b", "#7a5532", "#5e3f24", "#86603a"]
    for y in range(16):
        for x in range(16):
            c = pick(r, husk, [4, 3, 2, 1])
            if (x + y * 3) % 7 == 0:
                c = "#4a321d"                                     # fibres
            im.putpixel((x, y), hexc(c))
    im.save(f"{B}/coconut.png")
    # drawn like the vanilla apple: round body, darker outline in the same colour, lit from the top left
    item = grid([
        "................",
        "........s.......",
        ".......gs.......",
        ".....OOsOOO.....",
        "...OOLLMMMMOO...",
        "..OLHHLMMfMDDO..",
        "..OLHSLMMMMMDO..",
        ".OLLHLMfMMMMDDO.",
        ".OLLLMMMMMfMDDO.",
        ".OMLMMfMMMMDDDO.",
        ".OMMMMMMMfMDDDO.",
        "..OMMfMMMMDDDO..",
        "..OMMMMMDDDDDO..",
        "...OODDDDDDOO...",
        ".....OOOOOO.....",
        "................"], {"O": "#3b2412", "D": "#5a3a20", "M": "#7a5232", "L": "#9a6c42", "H": "#c09060",
                              "S": "#dcb581", "f": "#68452a", "s": "#6b5a2a", "g": "#5a7a2e"})
    item.save(f"{I}/coconut.png")


coconut()
print("ok")
