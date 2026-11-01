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
        bark=["#8a7356", "#7d6749", "#958061"], bark_dark="#5b4934", bark_light="#a8926d",
        inner=["#d0bb8f", "#c4ad7f", "#dac7a0"], inner_dark="#a88f62",
        planks=["#c9ae7c", "#bfa271", "#d3b98a"], seam="#8c7149", grain="#b0935f"),
    "eucalyptus": dict(
        bark=["#bdb8aa", "#d6cfba", "#a9a596", "#c6ac9c"], bark_dark="#86836f", bark_light="#e2dcc9",
        inner=["#c98e6c", "#bf805f", "#d49c7b"], inner_dark="#9c5e40",
        planks=["#a35f40", "#984f35", "#b06d4b"], seam="#6a3822", grain="#8a4a2e"),
}


def bark(w, p):
    r = random.Random(sum(map(ord, w)))
    im = new()
    if w == "palm":
        # fibrous trunk with ring scars where old fronds fell off
        for y in range(16):
            for x in range(16):
                c = pick(r, p["bark"])
                if y % 4 == 3:
                    c = p["bark_dark"] if (x + y // 4) % 5 != 0 else p["bark"][0]
                elif y % 4 == 2 and r.random() < .3:
                    c = p["bark_light"]
                im.putpixel((x, y), hexc(c))
    else:
        # smooth bark peeling in long patches: grey, cream and pinkish
        cols = [r.randrange(len(p["bark"])) for _ in range(16)]
        for x in range(16):
            run = 0
            ci = cols[x]
            for y in range(16):
                if run <= 0:
                    ci = r.randrange(len(p["bark"])) if r.random() < .5 else ci
                    run = r.randint(3, 8)
                run -= 1
                c = p["bark"][ci]
                if r.random() < .08:
                    c = p["bark_dark"]
                elif r.random() < .06:
                    c = p["bark_light"]
                im.putpixel((x, y), hexc(c))
    return im


def log_top(w, p, stripped):
    r = random.Random(sum(map(ord, w)) + 7)
    im = new()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge and not stripped:
                c = pick(r, p["bark"])
            else:
                d = max(abs(x - 7.5), abs(y - 7.5))
                if w == "palm":
                    c = pick(r, p["inner"]) if r.random() < .8 else p["inner_dark"]      # fibres, no rings
                else:
                    c = p["inner_dark"] if int(d) % 3 == 0 else pick(r, p["inner"])     # growth rings
                if edge:
                    c = p["inner_dark"]
            im.putpixel((x, y), hexc(c))
    return im


def stripped(w, p):
    r = random.Random(sum(map(ord, w)) + 3)
    im = new()
    for x in range(16):
        base = pick(r, p["inner"])
        for y in range(16):
            c = base if r.random() < .7 else pick(r, p["inner"] + [p["inner_dark"]])
            im.putpixel((x, y), hexc(c))
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
            elif (x == (5 + board * 7) % 16) and y % 4 != 3:
                c = p["seam"]                                   # board ends, staggered
            elif r.random() < .12:
                c = p["grain"]
            im.putpixel((x, y), hexc(c))
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
        greens = ["#6f9a86", "#5f8a77", "#84ad99", "#4f7565"]
        for y in range(16):
            for x in range(16):
                # narrow hanging leaves: vertical strokes, sparse
                if (x % 3 == 2 and r.random() < .6) or r.random() < .12:
                    continue
                im.putpixel((x, y), hexc(pick(r, greens, [4, 3, 2, 1])))
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


for w, p in WOOD.items():
    bark(w, p).save(f"{B}/{w}_log.png")
    log_top(w, p, False).save(f"{B}/{w}_log_top.png")
    stripped(w, p).save(f"{B}/stripped_{w}_log.png")
    log_top(w, p, True).save(f"{B}/stripped_{w}_log_top.png")
    planks(w, p).save(f"{B}/{w}_planks.png")
    door(w, p, "top").save(f"{B}/{w}_door_top.png")
    door(w, p, "bottom").save(f"{B}/{w}_door_bottom.png")
    trapdoor(w, p).save(f"{B}/{w}_trapdoor.png")
    door_item(w, p).save(f"{I}/{w}_door.png")
    leaves(w).save(f"{B}/{w}_leaves.png")
grid(PALM_SAPLING, {"g": "#5fa236", "G": "#3d7524", "t": "#8a7356"}).save(f"{B}/palm_sapling.png")
grid(EUC_SAPLING, {"e": "#84ad99", "E": "#5f8a77", "s": "#c6ac9c"}).save(f"{B}/eucalyptus_sapling.png")
flowers()
print("ok")
