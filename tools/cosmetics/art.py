"""Vortex-Cosmetics: Pixel-Kunst fuer Bandanas, Face, Back, Auras und Shield Skins.

Erzeugt:
  src/main/resources/assets/vortexclient/cosmetics/designs.txt   (liest der Client)
  src/main/resources/assets/vortexclient/textures/cosmetics/shield/<id>.png
und optional Vorschaubilder:  python3 tools/cosmetics/art.py <vorschau-ordner>

Alle Designs sind eigene Entwuerfe. Koordinaten in designs.txt:
  head  -- Ursprung Mitte Kopfunterseite, y nach oben (0..8), Gesicht bei z = -4
  body  -- Ursprung Mitte Halsansatz, y nach oben (0..-12), Ruecken bei z = +2
  Kunst wird von vorne gesehen gezeichnet (Rucksaecke: von hinten gesehen).
"""
import math, os, sys, random

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'vortexclient')

# ---------------------------------------------------------------- Raster
class G:
    def __init__(s, w, h, fill='.'):
        s.w, s.h = w, h
        s.c = [[fill] * w for _ in range(h)]
    def set(s, x, y, ch):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < s.w and 0 <= y < s.h: s.c[y][x] = ch
    def get(s, x, y):
        return s.c[y][x] if 0 <= x < s.w and 0 <= y < s.h else '.'
    def rect(s, x0, y0, x1, y1, ch):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): s.set(x, y, ch)
    def disc(s, cx, cy, r, ch, r0=-1):
        for y in range(s.h):
            for x in range(s.w):
                d = math.hypot(x - cx, y - cy)
                if d <= r + 0.2 and d > r0: s.set(x, y, ch)
    def line(s, x0, y0, x1, y1, ch, dicke=0):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(n + 1):
            t = i / n
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            if dicke:
                for dy in range(-dicke, dicke + 1):
                    for dx in range(-dicke, dicke + 1):
                        if dx * dx + dy * dy <= dicke * dicke: s.set(x + dx, y + dy, ch)
            else: s.set(x, y, ch)
    def outline(s, ch, inner=None):
        """Rand um alles Nicht-Leere ziehen (nur auf leeren Feldern)."""
        add = []
        for y in range(s.h):
            for x in range(s.w):
                if s.c[y][x] != '.': continue
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    n = s.get(x + dx, y + dy)
                    if n != '.' and n != ch and (inner is None or n in inner):
                        add.append((x, y)); break
        for x, y in add: s.c[y][x] = ch
    def mirror_x(s):
        for y in range(s.h):
            for x in range(s.w // 2):
                if s.c[y][x] != '.': s.c[y][s.w - 1 - x] = s.c[y][x]
                elif s.c[y][s.w - 1 - x] != '.': s.c[y][x] = s.c[y][s.w - 1 - x]
    def rows(s): return [''.join(r) for r in s.c]

def from_rows(rows):
    g = G(max(len(r) for r in rows), len(rows))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r): g.c[y][x] = ch
    return g

# ---------------------------------------------------------------- Ausgabe
OUT = []

def design(kat, id_, name, **kw):
    OUT.append(dict(kat=kat, id=id_, name=name, **kw))

def part(art, colors, at=(0, 0, 0), px=0.5, depth=1, rot=(0, 0, 0), glow='', anim=''):
    if isinstance(art, G): art = art.rows()
    return dict(art=art, colors=colors, at=at, px=px, depth=depth, rot=rot, glow=glow, anim=anim)

# ======================================================================
# FACE
# ======================================================================
def face():
    # --- Pixel Shades: klassische schwarze Pixel-Sonnenbrille
    shades = [
        "kkkkkkkkkkkkkkkkkk",
        "kkwkkkkkkkkkwkkkkk",
        ".kkwkkkk..kkkwkkk.",
        "..kkkkkk...kkkkkk.",
    ]
    design('face', 'pixel_shades', 'Pixel Shades', arms='k', colors={'k': '15151C', 'w': 'F4F4FF'},
           parts=[part(shades, {}, at=(0, 4.0, -4.85))])

    # --- Heart Glasses
    herz = [".pp.pp.", "pwppppp", "plppppp", ".ppppp.", "..ppp..", "...p..."]
    g = G(18, 6)
    for y, r in enumerate(herz):
        for x, ch in enumerate(r):
            if ch != '.': g.set(1 + x, y, ch); g.set(10 + x, y, ch)
    g.rect(8, 1, 9, 1, 'f')
    design('face', 'heart_glasses', 'Heart Glasses', arms='f', colors={'p': 'FF3E8E', 'l': 'FF8CC0', 'w': 'FFFFFF', 'f': 'FFD1E6'},
           parts=[part(g, {}, at=(0, 3.7, -4.85))])

    # --- Neon Visor: durchgehendes Band, leuchtet
    g = G(18, 4)
    g.rect(0, 0, 17, 3, 'd')
    for x in range(1, 17):
        g.set(x, 1, 'C' if x % 4 else 'P'); g.set(x, 2, 'P' if x % 4 else 'C')
    g.rect(0, 0, 17, 0, 'd'); g.rect(0, 3, 17, 3, 'd')
    design('face', 'neon_visor', 'Neon Visor', arms='d', colors={'d': '20163A', 'C': '38E1FF', 'P': 'B26BFF'},
           parts=[part(g, {}, at=(0, 4.0, -4.85), glow='CP')])

    # --- Star Glasses
    stern = ["...y...", "..yyy..", "yyyoyyy", ".yoooy.", "..yyy..", ".yy.yy.", "y.....y"]
    g = G(18, 7)
    for y, r in enumerate(stern):
        for x, ch in enumerate(r):
            if ch != '.': g.set(1 + x, y, ch); g.set(10 + x, y, ch)
    g.rect(8, 2, 9, 2, 'y')
    design('face', 'star_glasses', 'Star Glasses', arms='y', colors={'y': 'FFD23F', 'o': 'FF8A1F'},
           parts=[part(g, {}, at=(0, 3.7, -4.85))])

    # --- 3D Glasses: weisser Rahmen, rot/cyan
    g = G(18, 5)
    g.rect(0, 0, 17, 4, 'w')
    g.rect(1, 1, 7, 3, 'r'); g.rect(10, 1, 16, 3, 'c')
    g.set(8, 4, '.'); g.set(9, 4, '.'); g.set(8, 3, '.'); g.set(9, 3, '.')
    design('face', 'retro_3d', '3D Glasses', arms='w', colors={'w': 'F2F2F2', 'r': 'E3343A', 'c': '29C6E8'},
           parts=[part(g, {}, at=(0, 3.9, -4.85))])

    # --- Monocle: Goldring + Kette
    g = G(8, 8)
    g.disc(3.5, 3.5, 3.4, 'g', 2.3)
    g.disc(3.5, 3.5, 2.3, 'l')
    g.set(2, 2, 'w')
    kette = G(1, 9)
    for y in range(9): kette.set(0, y, 'g' if y % 2 == 0 else 'h')
    design('face', 'monocle', 'Monocle', colors={'g': 'E8B53A', 'h': 'A8761A', 'l': 'BFE7FF', 'w': 'FFFFFF'},
           parts=[part(g, {}, at=(-2, 4.2, -4.85)),
                  part(kette, {}, at=(-0.2, 0.9, -4.85), rot=(0, 0, -20))])

    # --- Skull Mask: ganze Gesichtsmaske
    s = [
        "..bbbbbbbbbbbb..",
        ".bbbbbbbbbbbbbb.",
        "bbbbbbbbbbbbbbbb",
        "bbbbbbbbbbbbbbbb",
        "bbkkkkbbbbkkkkbb",
        "bkkkkkkbbkkkkkkb",
        "bkkrkkkbbkkkrkkb",
        "bkkkkkkbbkkkkkkb",
        "bbkkkkbbbbkkkkbb",
        "bbbbbbbkkbbbbbbb",
        "bbbbbbkkkkbbbbbb",
        ".bbbbbbbbbbbbbb.",
        ".bwbwbwbwbwbwbb.",
        ".bkbkbkbkbkbkbb.",
        "..bwbwbwbwbwbb..",
        "...bbbbbbbbbb...",
    ]
    design('face', 'skull_mask', 'Skull Mask', colors={'b': 'E9E4D4', 'k': '1A1714', 'w': 'FFFFFF', 'r': 'E33B3B'},
           parts=[part(s, {}, at=(0, 4.0, -4.85), glow='r')])

    # --- Ninja Mask: Tuch um die untere Kopfhaelfte (Ring)
    design('face', 'ninja_mask', 'Ninja Mask', ring=dict(rows=['kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk',
                                                             'kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk',
                                                             'rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr',
                                                             'kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk',
                                                             'kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk',
                                                             'kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk'],
                                                       y0=0.2, px=0.5, knot='k', tails='kr'),
           colors={'k': '22222B', 'r': 'C8243A'})

    # --- Fox Mask: Kitsune, weiss mit roten Zeichen, Ohren
    f = [
        "..e..........e....",
        ".eee........eee...",
        ".eRe........eRe...",
        "eeRee......eeRee..",
        "eeeeeeeeeeeeeeee..",
        "eeeeeeeeeeeeeeee..",
        "eRReeeeeeeeeeRRe..",
        "eekRRe.eeeeRRkee..",
        "eekkkReeeeRkkkee..",
        "eeeeeeeeeeeeeeee..",
        "eeeeeeeeeeeeeeee..",
        ".eeeeeeRReeeeee...",
        ".eeeeeekkeeeeee...",
        "..eeeeeeeeeeee....",
        "...eeRRRRRRee.....",
        "....eeeeeeee......",
        "......eeee........",
    ]
    f = [r[:16] for r in f]
    g = from_rows(f)
    design('face', 'fox_mask', 'Fox Mask', colors={'e': 'F7F3EC', 'R': 'D9283A', 'k': '18141C'},
           parts=[part(g, {}, at=(0, 4.6, -4.85))])

    # --- Bow: Schleife oben am Kopf
    b = [
        "pp......pp",
        "pPp....pPp",
        "pPPp..pPPp",
        "pPPPkkPPPp",
        "pPPPkkPPPp",
        "pPPp..pPPp",
        "pPp....pPp",
        "pp......pp",
    ]
    design('face', 'bow', 'Bow', colors={'p': 'FF5FA8', 'P': 'FF9BCB', 'k': 'D93A86'},
           parts=[part(b, {}, at=(2.6, 8.4, -1.5), depth=2, rot=(0, -20, 18))])

# ======================================================================
# BANDANAS -- Band (32 Spalten rund um den Kopf: vorne 8 | links 8 | hinten 8 | rechts 8)
# ======================================================================
def bandana(id_, name, colors, rows, tails, glow=''):
    design('bandana', id_, name, colors=colors, ring=dict(rows=rows, y0=5.0, px=0.5, knot=tails[0], tails=tails), glow=glow)

N = 64   # Spalten rund um den Kopf (je Seite 16, ein halber Pixel breit)

def band(fn, n=4):
    return [''.join(fn(x, y) for x in range(N)) for y in range(n)]

def bandanas():
    # Lightning: weisser Zickzack-Blitz auf schwarz
    def blitz(x, y):
        k = x % 8
        z = [0, 1, 2, 3, 2, 1, 0, 1][k]
        return 'w' if y == z or (y == z + 1 and k in (3, 6)) else 'k'
    bandana('lightning', 'Lightning', {'k': '17171E', 'w': 'F2F6FF'}, band(blitz), 'kw')
    # Flame: rot mit Flammenzungen
    def flamme(x, y):
        h = [0, 1, 2, 3, 2, 1, 2, 3, 3, 2, 1, 0][x % 12]
        if y >= 4 - h: return 'y' if y >= 4 - h + 2 else 'o'
        return 'r'
    bandana('flame', 'Flame', {'r': '6E0D10', 'o': 'F05A16', 'y': 'FFC23A'}, band(flamme), 'ro')
    # Rainbow: schraege Streifen
    bandana('rainbow', 'Rainbow', {'r': 'E8434B', 'o': 'F59A2C', 'y': 'F7D43C', 'g': '4CC25B', 'b': '3D8BE6', 'v': '8E5BE3'},
            band(lambda x, y: 'roygbv'[((x + y) // 2) % 6]), 'rb')
    # Camo: Flecken
    rnd = random.Random(4)
    cam = [[rnd.choice('ggdb') for _ in range(N)] for _ in range(4)]
    for _ in range(4):
        cam = [[max(set(w := [cam[y][x], cam[y][(x + 1) % N], cam[y][x - 1], cam[(y + 1) % 4][x]]), key=w.count) for x in range(N)] for y in range(4)]
    bandana('camo', 'Camo', {'g': '4B5E2E', 'd': '2F3B1E', 'b': '7A6A45'}, [''.join(r) for r in cam], 'gd')
    # Galaxy: dunkel mit Nebel und kleinen Sternen
    rnd = random.Random(9)
    sterne = {(rnd.randrange(N), rnd.randrange(4)) for _ in range(14)}
    def galaxy(x, y):
        if (x, y) in sterne: return 'S'
        v = math.sin(x * 0.35) + math.cos(y * 1.3 + x * 0.12)
        return 'p' if v > 0.9 else ('m' if v > 0 else 'n')
    bandana('galaxy', 'Galaxy', {'n': '140F38', 'm': '2B1C66', 'p': '6A3FC4', 'S': 'FFFFFF'}, band(galaxy), 'nm', glow='S')
    # Vortex: lila/blau mit hellem V-Muster
    def vortex(x, y):
        k = x % 8
        if (k, y) in ((1, 0), (7, 0), (2, 1), (6, 1), (3, 2), (5, 2), (4, 3)): return 'w'
        return 'v' if (x // 16) % 2 == 0 else 'b'
    bandana('vortex', 'Vortex', {'v': '7C3AED', 'b': '3B5BDB', 'w': 'D3A6FF'}, band(vortex), 'vb')
    # Racing: Schachbrett
    bandana('racing', 'Racing', {'k': '141418', 'w': 'F4F4F4'}, band(lambda x, y: 'k' if (x // 2 + y // 2) % 2 else 'w'), 'kw')
    # Sakura: hell mit kleinen Blueten
    def sakura(x, y):
        k, m = x % 10, (x // 10) % 2
        cy = 1 if m == 0 else 2
        if (k, y) == (4, cy): return 'Y'
        if (k in (3, 5) and y == cy) or (k == 4 and y in (cy - 1, cy + 1)): return 'p'
        return 'l'
    bandana('sakura', 'Sakura', {'l': 'FFE3EE', 'p': 'FF7FB0', 'Y': 'FFD94A'}, band(sakura), 'lp')
    # Ice: Eiskristalle
    def eis(x, y):
        k = x % 9
        if (k == 4 and y in (0, 1, 2, 3)) or (y in (1, 2) and k in (3, 5)): return 'w'
        return 'b' if (x // 3 + y) % 3 == 0 else 'c'
    bandana('ice', 'Ice', {'c': 'A8E6FF', 'b': '6CC6F0', 'w': 'FFFFFF'}, band(eis), 'cb')
    # Toxic: dunkel mit leuchtenden Tropfen
    def toxic(x, y):
        k = x % 7
        if k == 3 and y >= 1: return 'L'
        if k in (2, 4) and y == 3: return 'L'
        if k == 3 and y == 0: return 'l'
        return 'k'
    bandana('toxic', 'Toxic', {'k': '162016', 'l': '3E8F2A', 'L': '9BFF3A'}, band(toxic), 'kL', glow='L')

# ======================================================================
# BACK -- von hinten gesehen; Ruecken bei z = +2, Koerper y 0..-12
# ======================================================================
def back():
    # --- Teddy Backpack
    t = [
        "..bb........bb..",
        ".bBBb......bBBb.",
        ".bBkbbbbbbbbkBb.",
        "..bbbbbbbbbbbb..",
        "..bbbbbbbbbbbb..",
        ".bbbkkbbbbkkbbb.",
        ".bbbkwbbbbkwbbb.",
        ".bbbbbBBBBbbbbb.",
        ".bbbbBBnnBBbbbb.",
        "..bbbBBBBBBbbb..",
        "...bbbBkkBbbb...",
        "....bbbbbbbb....",
        "..bbbbbbbbbbbb..",
        ".bbbbBBBBBBbbbb.",
        "bbbbBBBBBBBBbbbb",
        "bbbbBBBBBBBBbbbb",
        "bbbbBBBBBBBBbbbb",
        ".bbbBBBBBBBBbbb.",
        ".bbbbBBBBBBbbbb.",
        "..bbbbbbbbbbbb..",
        "..BBB......BBB..",
        "..bbb......bbb..",
    ]
    design('back', 'teddy_backpack', 'Teddy Backpack', colors={'b': '8A5A35', 'B': 'C49067', 'k': '1D1410', 'w': 'FFFFFF', 'n': '3A241A'},
           parts=[part(t, {}, at=(0, -5.6, 3.1), px=0.5, depth=3, anim='bob')])

    # --- Explorer Backpack (mit Schlafrolle)
    p = G(16, 20)
    p.rect(1, 2, 14, 19, 'l')          # Koerper
    p.rect(1, 2, 14, 8, 'L')           # Klappe
    p.rect(1, 8, 14, 8, 'd')
    p.rect(6, 7, 9, 10, 'g')           # Schnalle
    p.rect(7, 8, 8, 9, 'd')
    p.rect(3, 12, 12, 17, 'L')         # Aussentasche
    p.rect(3, 12, 12, 12, 'd')
    p.rect(2, 2, 2, 19, 's'); p.rect(13, 2, 13, 19, 's')
    roll = G(18, 5)
    roll.rect(0, 0, 17, 4, 'r')
    for x in (3, 14): roll.rect(x, 0, x, 4, 's')
    roll.rect(0, 0, 17, 0, 'R'); roll.rect(0, 4, 17, 4, 'R')
    design('back', 'explorer_pack', 'Explorer Backpack', colors={'l': '6B4423', 'L': '845530', 'd': '3E2614', 'g': 'D9A93A', 's': '2F1D10', 'r': '2F6B4F', 'R': '24543E'},
           parts=[part(p, {}, at=(0, -6.2, 3.35), depth=4, anim='bob'),
                  part(roll, {}, at=(0, -1.6, 3.35), depth=4, anim='bob')])

    # --- Greatsword (diagonal)
    s = G(9, 46)
    s.rect(3, 0, 5, 1, 'p')            # Knauf
    s.rect(4, 2, 4, 8, 'h')            # Griff
    for y in range(2, 9, 2): s.set(4, y, 'H')
    s.rect(0, 9, 8, 10, 'g')           # Parierstange
    s.rect(4, 9, 4, 10, 'G')
    s.rect(3, 11, 5, 43, 'm')          # Klinge
    s.rect(4, 11, 4, 42, 'M')
    s.set(3, 43, '.'); s.set(5, 43, '.'); s.rect(4, 43, 4, 45, 'm')
    design('back', 'greatsword', 'Greatsword', colors={'p': 'D9A93A', 'h': '3B2416', 'H': '5A3A24', 'g': 'C8962E', 'G': 'F2D46B', 'm': 'B9C1CC', 'M': 'E9EEF4'},
           parts=[part(s, {}, at=(0, -5.5, 2.6), depth=1, rot=(0, 0, 38), anim='bob')])

    # --- Crystal Scythe
    sc = G(26, 40)
    sc.line(14, 6, 14, 39, 'w', 0)     # Stiel
    sc.line(15, 6, 15, 39, 'W', 0)
    for y in range(10, 39, 6): sc.rect(14, y, 15, y, 'b')
    # Klinge: Bogen nach links
    for i in range(0, 15):
        a = math.radians(180 + i * 6)
        for r in range(9, 14):
            x = 14 + math.cos(a) * r * 1.0 - 0
            y = 7 + math.sin(a) * r * 0.55
            sc.set(x, y, 'C' if r < 12 else 'c')
    sc.rect(13, 4, 16, 7, 'b')
    sc.outline('d', inner='Cc')
    design('back', 'crystal_scythe', 'Crystal Scythe', colors={'w': '4A3A6B', 'W': '6A568F', 'b': 'C9D3E6', 'C': '6FF3FF', 'c': '2BB8E6', 'd': '1C5E86'},
           parts=[part(sc, {}, at=(0, -5.2, 2.6), depth=1, rot=(0, 0, -28), glow='C', anim='bob')])

    # --- Guitar
    gt = G(14, 42)
    gt.disc(6.5, 33, 6.4, 'w'); gt.disc(6.5, 24, 5.0, 'w')
    gt.disc(6.5, 33, 5.4, 'W'); gt.disc(6.5, 24, 4.0, 'W')
    gt.disc(6.5, 28, 1.8, 'k')
    gt.rect(4, 37, 9, 37, 'k')
    gt.rect(5, 2, 8, 20, 'n'); gt.rect(6, 2, 7, 20, 'N')
    for y in range(4, 20, 3): gt.rect(5, y, 8, y, 'f')
    gt.rect(4, 0, 9, 3, 'h')
    for y in (0, 2): gt.set(3, y, 'f'); gt.set(10, y, 'f')
    for x in (6, 7): gt.line(x, 3, x, 37, 's')
    design('back', 'guitar', 'Guitar', colors={'w': 'B5672F', 'W': 'D8914E', 'k': '1E140E', 'n': '5A3A24', 'N': '7A5236', 'f': 'C9C9C9', 'h': '3A2416', 's': 'EDEDED'},
           parts=[part(gt, {}, at=(0, -6.0, 2.85), depth=2, rot=(0, 0, -32), anim='bob')])

    # --- Katana (in der Scheide)
    k = G(5, 46)
    k.rect(1, 0, 3, 9, 'h'); 
    for y in range(0, 10, 2): k.rect(1, y, 3, y, 'H')
    k.rect(0, 10, 4, 11, 'g')
    k.rect(1, 12, 3, 45, 's'); k.rect(2, 12, 2, 44, 'S')
    for y in (18, 30): k.rect(1, y, 3, y, 'g')
    design('back', 'katana', 'Katana', colors={'h': '1C1C24', 'H': 'E8E1D0', 'g': 'D9A93A', 's': '7E1620', 'S': 'A82432'},
           parts=[part(k, {}, at=(0, -5.5, 2.6), depth=1, rot=(0, 0, 40), anim='bob')])

    # --- Surfboard
    sb = G(12, 44)
    for y in range(44):
        t_ = y / 43
        w = 5.6 * math.sin(math.pi * min(1, (t_ * 1.05) + 0.02)) ** 0.6
        for x in range(12):
            if abs(x - 5.5) <= w: sb.set(x, y, 'w')
    for y in range(44):
        for x in range(12):
            if sb.get(x, y) == 'w' and abs(x - 5.5) < 1.2: sb.set(x, y, 'b')
            if sb.get(x, y) == 'w' and 14 < y < 20: sb.set(x, y, 'y')
    sb.outline('B')
    design('back', 'surfboard', 'Surfboard', colors={'w': 'F7F1E1', 'b': '2B8FE6', 'y': 'FFC93C', 'B': '1F6FB8'},
           parts=[part(sb, {}, at=(0, -5.5, 2.6), depth=1, rot=(0, 0, -14), anim='bob')])

    # --- Rocket Pack
    rp = G(16, 20)
    for cx in (3.5, 12.5):
        rp.rect(int(cx) - 2, 3, int(cx) + 2, 17, 'm')
        rp.rect(int(cx) - 1, 3, int(cx) - 1, 17, 'M')
        rp.rect(int(cx) - 1, 0, int(cx) + 1, 2, 'r')
        rp.rect(int(cx) - 2, 15, int(cx) + 2, 17, 'd')
    rp.rect(6, 5, 9, 14, 'd'); rp.rect(7, 7, 8, 8, 'L')
    rp.rect(0, 16, 1, 19, 'r'); rp.rect(14, 16, 15, 19, 'r')
    flamme = G(16, 8)
    for cx in (3, 12):
        flamme.rect(cx, 0, cx + 1, 1, 'Y'); flamme.rect(cx, 2, cx + 1, 4, 'O'); flamme.rect(cx, 5, cx + 1, 6, 'R')
    design('back', 'rocket_pack', 'Rocket Pack', colors={'m': 'B8BEC9', 'M': 'E6EAF0', 'r': 'D92E3A', 'd': '3A4150', 'L': '5EF2FF', 'Y': 'FFF36A', 'O': 'FF9A2E', 'R': 'FF4A2E'},
           parts=[part(rp, {}, at=(0, -6.0, 3.1), depth=3, anim='bob', glow='L'),
                  part(flamme, {}, at=(0, -12.6, 3.1), depth=1, glow='YOR', anim='flicker')])

    # --- Quiver
    q = G(10, 30)
    q.rect(2, 8, 7, 29, 'l'); q.rect(3, 8, 3, 29, 'L')
    q.rect(2, 12, 7, 12, 'd'); q.rect(2, 24, 7, 24, 'd')
    for i, x in enumerate((2, 4, 6)):
        q.rect(x + 1, 3 + i, x + 1, 8, 's')
        q.rect(x, i, x + 2, 2 + i, 'f' if i != 1 else 'F')
    design('back', 'quiver', 'Quiver', colors={'l': '7A4A26', 'L': '9A6236', 'd': '3E2614', 's': 'C9B28A', 'f': 'F2F2F2', 'F': 'D9283A'},
           parts=[part(q, {}, at=(1.5, -4.5, 2.85), depth=2, rot=(0, 0, -22), anim='bob')])

# ======================================================================
# AURAS -- kleine Sprites, die um den ganzen Koerper schweben
# ======================================================================
def aura(id_, name, motion, count, sprites, colors, px=0.7, glow='', spin=1.0, size=1.0, legacy=''):
    design('aura', id_, name, motion=motion, count=count, sprites=sprites, colors=colors, px=px, glow=glow, spin=spin, size=size, legacy=legacy)

def auras():
    aura('sakura', 'Sakura Aura', 'fall', 10,
         [[".p.", "pyp", ".p."], ["..p", ".pP", "pP."], [".pp", "pPp", "pp."]],
         {'p': 'FFB3D1', 'P': 'FF7FB0', 'y': 'FFE58A'}, legacy='cherry')
    aura('snow', 'Snow Aura', 'fall', 12,
         [["w.w.w", ".wcw.", "wcCcw", ".wcw.", "w.w.w"], [".w.", "wcw", ".w."]],
         {'w': 'FFFFFF', 'c': 'BFEAFF', 'C': '8FD8FF'}, glow='C', legacy='snow')
    aura('autumn', 'Autumn Aura', 'fall', 9,
         [["..o.", ".ooo", "oooo", ".rr.", "..b."], ["..y", ".yo", "yo.", "b.."], [".r.", "rRr", ".b."]],
         {'o': 'F07D1E', 'r': 'D2381F', 'R': 'A8221A', 'y': 'F5C036', 'b': '6B3A1E'})
    aura('butterfly', 'Butterfly Aura', 'flutter', 6,
         [["cc...cc", "cCc.cCc", "ccckccc", ".ccKcc.", ".cc.cc."], ["pp...pp", "pPp.pPp", "pppkppp", ".ppKpp.", ".pp.pp."]],
         {'c': '45E0C8', 'C': 'C9FFF5', 'p': 'FF6FA8', 'P': 'FFD0E3', 'k': '1D1D24', 'K': '3A3A44'})
    aura('flame', 'Flame Aura', 'rise', 10,
         [[".Y.", "YyY", "yoy", "oro", ".r."], ["Y.", "yY", "oy", "ro"]],
         {'Y': 'FFF6A0', 'y': 'FFD23A', 'o': 'FF8A1F', 'r': 'E8401C'}, glow='Yyor', legacy='flames')
    aura('soul', 'Soul Aura', 'rise', 10,
         [[".W.", "WcW", "cbc", "bBb", ".B."], ["W.", "cW", "bc", "Bb"]],
         {'W': 'E8FFFF', 'c': '7FF3FF', 'b': '2BC4E8', 'B': '1A7FAF'}, glow='WcbB', legacy='soul_flames')
    aura('yin_yang', 'Yin-Yang Aura', 'twin', 2,
         [["....wwwwww....", "..wwwwwwwwww..", ".www......www.", "ww..........ww", "w............w"],
          ["....kkkkkk....", "..kkkkkkkkkk..", ".kkk......kkk.", "kk..........kk", "k............k"]],
         {'w': 'F6F6F6', 'k': '18181E'}, px=0.8, spin=0)
    aura('neon_rings', 'Neon Rings', 'rings', 3,
         [[".pppppp.", "p......p", "p......p", "p......p", "p......p", ".pppppp."]],
         {'p': 'C06BFF'}, px=1.6, glow='p', spin=0, legacy='magic')
    aura('hearts', 'Heart Aura', 'orbit', 6,
         [[".r.r.", "rRrrr", "rrrrr", ".rrr.", "..r.."]],
         {'r': 'F2385F', 'R': 'FFB0C2'}, glow='', legacy='hearts')
    aura('stars', 'Star Aura', 'twinkle', 9,
         [["..y..", ".yYy.", "yYWYy", ".yYy.", "..y.."], [".y.", "yWy", ".y."]],
         {'y': 'FFC93A', 'Y': 'FFE58A', 'W': 'FFFFFF'}, glow='yYW', legacy='sparkles,totem,glow')
    aura('music', 'Music Aura', 'orbit', 5,
         [["..kk", "..kK", "..k.", "..k.", "kkk.", "kk.."], [".kkkk", ".k..k", ".k..k", "kk.kk", "kk.kk"]],
         {'k': 'B26BFF', 'K': 'E2C6FF'}, glow='kK', legacy='notes')
    aura('lightning', 'Lightning Aura', 'flicker', 7,
         [["....yy", "...yWy", "..yWy.", ".yWy..", "yWWWWy", "..yWy.", ".yWy..", ".yy...", "yy....", "y....."],
          ["..yy", ".yW.", "yWWy", ".yWy", "yW..", "y..."]],
         {'y': '7FD8FF', 'W': 'FFFFFF'}, glow='yW', px=0.6, legacy='electric')
    aura('bubbles', 'Bubble Aura', 'rise', 10,
         [[".bb.", "bWcb", "bccb", ".bb."], [".b.", "bWb", ".b."]],
         {'b': '7FD4FF', 'c': 'CDEFFF', 'W': 'FFFFFF'}, spin=0)
    aura('emerald', 'Emerald Aura', 'orbit', 7,
         [[".g.", "gGg", ".g."], ["..g..", ".gGg.", "gGWGg", ".gGg.", "..g.."]],
         {'g': '2FD06A', 'G': '8BFFB4', 'W': 'FFFFFF'}, glow='gGW', legacy='emerald,enchant')

# ======================================================================
# SHIELD SKINS -- Texturen 128x128 (Schild-Modell: Platte vorne bei (2,2) 24x44)
# ======================================================================
def shields():
    from PIL import Image
    out = os.path.join(RES, 'textures', 'cosmetics', 'shield')
    os.makedirs(out, exist_ok=True)
    W, H = 24, 44

    def hexc(h): return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
    def mix(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)

    def skin(id_, name, front, rim, back, handle):
        img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
        px = img.load()
        grund = back
        def back(x, y):
            # Rueckseite (sieht man in der Ich-Perspektive): Rahmen + Bretter mit Fugen
            if x < 2 or y < 2 or x >= W - 2 or y >= H - 2: return rim
            c = grund(x, y)
            if (y - 2) % 7 == 6: return mix(c, (0, 0, 0, 255), 0.35)
            if x == (11 if ((y - 2) // 7) % 2 else 5) or x == (17 if ((y - 2) // 7) % 2 else 19): return mix(c, (0, 0, 0, 255), 0.2)
            return mix(c, (255, 255, 255, 255), 0.06) if (x * 3 + y) % 9 == 0 else c
        for y in range(H):
            for x in range(W):
                c = front(x, y)
                px[2 + x, 2 + y] = c                     # vorne
                px[28 + (W - 1 - x), 2 + y] = back(x, y)  # hinten
        # Rand (oben/unten/seiten) in Rahmenfarbe
        for x in range(W):
            for y in range(2):
                px[2 + x, y] = rim; px[26 + x, y] = rim
        for y in range(H):
            for x in range(2):
                px[x, 2 + y] = rim; px[26 + x, 2 + y] = rim
        # Griff
        for y in range(24):
            for x in range(52, 84):
                px[x, y] = handle if (x + y) % 5 else mix(handle, (0, 0, 0, 255), 0.25)
        img.save(os.path.join(out, id_ + '.png'))
        design('shield', id_, name)

    def rahmen(fn, rimc, breite=2):
        def f(x, y):
            if x < breite or y < breite or x >= W - breite or y >= H - breite: return rimc
            return fn(x, y)
        return f

    rnd = random.Random(3)
    # Obsidian
    cr = set()
    for _ in range(9):
        x, y = rnd.randrange(W), rnd.randrange(H)
        for _ in range(10):
            cr.add((x, y)); x += rnd.choice((-1, 0, 1)); y += rnd.choice((-1, 0, 1, 1))
    ob1, ob2, ob3 = hexc('120B1E'), hexc('2A1748'), hexc('7A4FD6')
    skin('obsidian', 'Obsidian Shield', rahmen(lambda x, y: ob3 if (x, y) in cr else (ob2 if (x * 7 + y * 3) % 11 < 3 else ob1), hexc('3B2A57')),
         hexc('3B2A57'), lambda x, y: ob1, hexc('2A1748'))
    # Ender: dunkles Tuerkis mit Auge
    e1, e2, e3, e4 = hexc('0E2A2A'), hexc('1F4F4A'), hexc('3FD9B4'), hexc('0A0A0A')
    def ender(x, y):
        d = math.hypot((x - 11.5) / 1.0, (y - 21.5) / 1.4)
        if d < 2.2: return e4
        if d < 5.2: return e3 if d > 3.6 else hexc('9BFFE6')
        return e2 if (x + y) % 6 < 2 else e1
    skin('ender', 'Ender Shield', rahmen(ender, hexc('0A1414')), hexc('0A1414'), lambda x, y: e1, hexc('1F4F4A'))
    # Nether Portal: lila Wirbel
    def portal(x, y):
        a = math.atan2(y - 21.5, x - 11.5); r = math.hypot(x - 11.5, (y - 21.5) * 0.6)
        v = math.sin(a * 3 + r * 0.9)
        return mix(hexc('3A0F7A'), hexc('C26BFF'), (v + 1) / 2)
    skin('nether_portal', 'Portal Shield', rahmen(portal, hexc('140A22')), hexc('140A22'), lambda x, y: hexc('2A0A55'), hexc('140A22'))
    # Frost: Eis mit Schneeflocke
    def frost(x, y):
        dx, dy = x - 11.5, y - 21.5
        on = (abs(dx) < 0.8 and abs(dy) < 14) or (abs(dy - dx * 1.7) < 1.2 and abs(dx) < 8) or (abs(dy + dx * 1.7) < 1.2 and abs(dx) < 8)
        if on: return hexc('FFFFFF')
        return mix(hexc('BFEAFF'), hexc('6CC6F0'), (y / H))
    skin('frost', 'Frost Shield', rahmen(frost, hexc('E9F8FF')), hexc('E9F8FF'), lambda x, y: hexc('8FD8FF'), hexc('C9EEFF'))
    # Sunflower
    def sonne(x, y):
        dx, dy = x - 11.5, (y - 21.5) * 0.62
        r = math.hypot(dx, dy); a = math.atan2(dy, dx)
        if r < 4.2: return hexc('4A2A12') if (x + y) % 2 else hexc('6B3A1A')
        if r < 9.5 + 1.8 * math.cos(a * 8): return hexc('FFD23A') if r < 7 else hexc('F5B21E')
        return hexc('3E8A2E') if (x * 5 + y) % 9 < 4 else hexc('2F6B24')
    skin('sunflower', 'Sunflower Shield', rahmen(sonne, hexc('2F6B24')), hexc('2F6B24'), lambda x, y: hexc('2F6B24'), hexc('6B4423'))
    # Sakura
    def sak(x, y):
        dx, dy = x - 11.5, (y - 21.5) * 0.62
        r = math.hypot(dx, dy); a = math.atan2(dy, dx)
        if r < 2.2: return hexc('FFE58A')
        if r < 8 * abs(math.cos(a * 2.5)) + 2.5: return hexc('FF8FBF') if r > 5 else hexc('FFC2DA')
        return hexc('FFF0F6') if (x + y * 2) % 7 else hexc('FFD6E7')
    skin('sakura', 'Sakura Shield', rahmen(sak, hexc('C8417E')), hexc('C8417E'), lambda x, y: hexc('FFD6E7'), hexc('6B3A2A'))
    # Vortex: dunkel mit dem V
    def vtx(x, y):
        u, v = (x - 2) / 20 * 200, (y - 8) / 30 * 200
        l = (26 <= u <= 112) and v >= 38 and v <= 174 and abs((u - 26) - (v - 38) * (86 / 136) * 0.5) < 22 and u <= 26 + (v - 38) * 0.63 + 44
        if (abs(u - (48 + (v - 38) * 0.38)) < 22) and 38 <= v <= 170: return mix(hexc('D3A6FF'), hexc('7C3AED'), v / 200)
        if (abs(u - (152 - (v - 38) * 0.38)) < 22) and 38 <= v <= 170: return mix(hexc('5CC8FF'), hexc('4338CA'), v / 200)
        return hexc('120E24') if (x + y) % 4 else hexc('1A1433')
    skin('vortex', 'Vortex Shield', rahmen(vtx, hexc('7C3AED')), hexc('7C3AED'), lambda x, y: hexc('120E24'), hexc('3B2A57'))
    # Lava: schwarze Kruste, orange Risse
    lav = set()
    for _ in range(7):
        x, y = rnd.randrange(W), rnd.randrange(H)
        for _ in range(14):
            lav.add((x, y)); lav.add((x + 1, y)); x += rnd.choice((-1, 0, 1)); y += rnd.choice((-1, 1, 1))
    skin('lava', 'Lava Shield', rahmen(lambda x, y: (hexc('FFB02E') if (x + y) % 3 == 0 else hexc('F05A16')) if (x, y) in lav else (hexc('1C1412') if (x * 3 + y) % 5 else hexc('2E211C')), hexc('3A2A24')),
         hexc('3A2A24'), lambda x, y: hexc('1C1412'), hexc('3A2A24'))
    # Prism: Regenbogen-Diagonalen
    farben = ['E8434B', 'F59A2C', 'F7D43C', '4CC25B', '3D8BE6', '8E5BE3']
    skin('prism', 'Prism Shield', rahmen(lambda x, y: hexc(farben[((x + y) // 4) % 6]), hexc('F4F4F4')), hexc('F4F4F4'), lambda x, y: hexc('2A2A33'), hexc('8E5BE3'))
    # Royal: rot mit Goldrand und Krone
    krone = ["y...y...y", "yy.yyy.yy", "yyyyyyyyy", "yRyyByyRy", "yyyyyyyyy"]
    def royal(x, y):
        if x < 4 or x >= W - 4 or y < 4 or y >= H - 4: return hexc('E8B53A') if (x + y) % 2 else hexc('C8962E')
        kx, ky = x - 7, y - 12
        if 0 <= ky < len(krone) and 0 <= kx < 9 and krone[ky][kx] != '.':
            return {'y': hexc('FFD23F'), 'R': hexc('E8434B'), 'B': hexc('3D8BE6')}[krone[ky][kx]]
        return hexc('9E1B2A') if (x + y) % 6 else hexc('B8243A')
    skin('royal', 'Royal Shield', rahmen(royal, hexc('E8B53A'), 1), hexc('E8B53A'), lambda x, y: hexc('7A1420'), hexc('C8962E'))

# ======================================================================
def schreiben():
    p = os.path.join(RES, 'cosmetics', 'designs.txt')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    L = ["# Erzeugt von tools/cosmetics/art.py -- nicht von Hand aendern.", ""]
    for d in OUT:
        L.append(f"== {d['kat']} {d['id']}")
        L.append(f"name: {d['name']}")
        if d.get('colors'): L.append('colors: ' + ' '.join(f"{k}={v}" for k, v in d['colors'].items()))
        if d.get('glow'): L.append(f"glow: {d['glow']}")
        if d.get('arms'): L.append(f"arms: {d['arms']}")
        for k in ('motion', 'count', 'px', 'spin', 'size', 'legacy'):
            if k in d and d['kat'] == 'aura' and d[k] != '': L.append(f"{k}: {d[k]}")
        if d.get('ring'):
            r = d['ring']
            L.append(f"ring: y0={r['y0']} px={r['px']} knot={r['knot']} tails={r['tails']}")
            L.extend('  ' + row for row in r['rows'])
            L.append('end')
        for pt in d.get('parts', []):
            L.append('part: at=%s,%s,%s px=%s depth=%s rot=%s,%s,%s%s%s' % (*pt['at'], pt['px'], pt['depth'], *pt['rot'],
                     (' glow=' + pt['glow']) if pt['glow'] else '', (' anim=' + pt['anim']) if pt['anim'] else ''))
            if pt['colors']: L.append('colors: ' + ' '.join(f"{k}={v}" for k, v in pt['colors'].items()))
            L.extend('  ' + row for row in pt['art'])
            L.append('end')
        for sp in d.get('sprites', []):
            L.append('sprite:')
            L.extend('  ' + row for row in sp)
            L.append('end')
        L.append('')
    open(p, 'w').write('\n'.join(L))
    return p

def vorschau(ordner):
    from PIL import Image, ImageDraw
    os.makedirs(ordner, exist_ok=True)
    def col(h): return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    for kat in ('face', 'bandana', 'back', 'aura'):
        ds = [d for d in OUT if d['kat'] == kat]
        tiles = []
        for d in ds:
            S = 8
            img = Image.new('RGB', (260, 260), (40, 38, 52))
            dr = ImageDraw.Draw(img)
            cols = dict(d.get('colors') or {})
            arts = []
            if d.get('ring'): arts.append(d['ring']['rows'])
            for pt in d.get('parts', []): arts.append(pt['art'])
            for sp in d.get('sprites', []): arts.append(sp)
            y0 = 6
            for art in arts:
                w = max(len(r) for r in art); s = max(2, min(S, 240 // max(w, 1), 200 // max(len(art), 1)))
                x0 = (260 - w * s) // 2
                for y, r in enumerate(art):
                    for x, ch in enumerate(r):
                        if ch != '.' and ch in cols: dr.rectangle([x0 + x * s, y0 + y * s, x0 + x * s + s - 1, y0 + y * s + s - 1], fill=col(cols[ch]))
                y0 += len(art) * s + 6
                if y0 > 230: break
            dr.text((6, 246), d['name'], fill=(255, 255, 255))
            tiles.append(img)
        n = len(tiles); cols_n = 5; rows_n = (n + cols_n - 1) // cols_n
        sheet = Image.new('RGB', (260 * cols_n, 260 * rows_n), (20, 18, 28))
        for i, t in enumerate(tiles): sheet.paste(t, ((i % cols_n) * 260, (i // cols_n) * 260))
        sheet.save(os.path.join(ordner, kat + '.png'))
    # Schilde
    sd = os.path.join(RES, 'textures', 'cosmetics', 'shield')
    fs = sorted(f for f in os.listdir(sd) if f.endswith('.png'))
    sheet = Image.new('RGB', (len(fs) * 110, 200), (20, 18, 28))
    for i, f in enumerate(fs):
        im = Image.open(os.path.join(sd, f)).crop((2, 2, 26, 46)).resize((96, 176), Image.NEAREST)
        sheet.paste(im, (i * 110 + 7, 12), im)
    sheet.save(os.path.join(ordner, 'shield.png'))

if __name__ == '__main__':
    face(); bandanas(); back(); auras(); shields()
    print(schreiben(), len(OUT))
    if len(sys.argv) > 1: vorschau(sys.argv[1])
