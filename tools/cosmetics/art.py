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
def scale2x(rows):
    """EPX/Scale2x: doppelte Aufloesung mit geglaetteten Diagonalen."""
    h, w = len(rows), max(len(r) for r in rows)
    g = lambda x, y: rows[y][x] if 0 <= y < h and 0 <= x < len(rows[y]) else '.'
    out = [[''] * (w * 2) for _ in range(h * 2)]
    for y in range(h):
        for x in range(w):
            p, a, b, c, d = g(x, y), g(x, y - 1), g(x + 1, y), g(x - 1, y), g(x, y + 1)
            e0 = e1 = e2 = e3 = p
            if c == a and c != d and a != b: e0 = a
            if a == b and a != c and b != d: e1 = b
            if d == c and d != b and c != a: e2 = c
            if b == d and b != a and d != c: e3 = d
            out[2 * y][2 * x], out[2 * y][2 * x + 1], out[2 * y + 1][2 * x], out[2 * y + 1][2 * x + 1] = e0, e1, e2, e3
    return [''.join(r) for r in out]

def raster(w, h, fn):
    """Bild w x h aus einer Funktion fn(x, y) -> Zeichen; x, y = Pixelmitte (y nach unten)."""
    return [''.join(fn(x + 0.5, y + 0.5) for x in range(w)) for y in range(h)]

def herz_sdf(x, y):
    """< 0 innerhalb eines Herzens. Spitze bei (0, 0.55), Boegen oben bis y ~ -0.6 (y nach unten)."""
    px, py = abs(x), -y + 0.55
    if py + px > 1.0:
        return math.hypot(px - 0.25, py - 0.75) - math.sqrt(2) / 4
    m = 0.5 * max(px + py, 0.0)
    d = math.sqrt(min((px) ** 2 + (py - 1.0) ** 2, (px - m) ** 2 + (py - m) ** 2))
    return d * (1 if px - py > 0 else -1)

def stern_sdf(x, y, r1=1.0, r2=0.45):
    a = math.atan2(x, -y); r = math.hypot(x, y)
    k = (a % (2 * math.pi / 5)) / (2 * math.pi / 5)
    grenze = r2 + (r1 - r2) * abs(1 - 2 * k) ** 1.6
    return r - grenze

def face():
    F = 0.25   # feine Brillen: halbe Pixel der alten Version
    # --- Pixel Shades: schwarze Sonnenbrille, feiner, mit Glanzstreifen
    def shades(x, y):
        u, v = x / 2, y / 2                     # in alten Pixeln (18 x 4)
        if v < 1.0: return 'k'                  # oberer Steg
        for x0 in (0.3, 9.7):                   # zwei Glaeser, unten schraeg
            lx = u - x0
            if 0 <= lx <= 8.0 and v <= 4.0 - max(0, (lx - 5.2) * 0.9) - max(0, (1.6 - lx) * 0.9):
                if 1.2 < v < 3.0 and abs(lx - (v - 1.2) - 1.3) < 0.55: return 'w'
                if 1.2 < v < 2.4 and abs(lx - (v - 1.2) - 2.6) < 0.3: return 'w'
                return 'K' if v > 3.0 else 'k'
        if 8.2 <= u <= 9.8 and v < 1.6: return 'k'
        return '.'
    design('face', 'pixel_shades', 'Pixel Shades', arms='k', colors={'k': '101016', 'K': '24242E', 'w': 'E6E8FF'},
           parts=[part(raster(36, 8, shades), {}, at=(0, 4.0, -4.85), px=F),
                  part(["W.", "WW", ".W"], {'W': 'FFFFFF'}, at=(0, 4.2, -5.0), px=0.35, depth=0.3, rot=(0, 0, -20), glow='W', anim='glint:3.6')])

    # --- Heart Glasses: glatte Herzen mit Glanz, duenner Rahmen
    def herzen(x, y):
        for cx in (8.6, 27.4):
            d = herz_sdf((x - cx) / 9.0, (y - 8.4) / 9.0)
            if d < -0.11:
                hx, hy = (x - cx) / 9.0, (y - 8.4) / 9.0
                if math.hypot(hx + 0.45, hy + 0.45) < 0.17: return 'w'
                return 'l' if hy < -0.25 and hx < 0 else 'p'
            if d < 0.0: return 'r'
        if 13.0 <= x <= 23.0 and 4.6 <= y <= 5.8: return 'r'
        return '.'
    design('face', 'heart_glasses', 'Heart Glasses', arms='r', colors={'p': 'FF3E8E', 'l': 'FF86BC', 'w': 'FFFFFF', 'r': 'C2185B'},
           parts=[part(raster(36, 17, herzen), {}, at=(0, 3.7, -4.85), px=F, anim='pulse')])

    # --- Neon Visor: Band mit runden Enden, Lauflicht faehrt hin und her
    def visor(x, y):
        rx = min(x, 36 - x)
        unten = 10 - max(0, 2.2 - abs(x - 18) * 0.55)      # Nasen-Aussparung unten in der Mitte
        if y < 1 or y > unten or (rx < 2.5 and math.hypot(2.5 - rx, max(0, abs(y - 5.5) - 2)) > 2.6): return '.'
        if y < 2 or y > unten - 1 or rx < 1.4: return 'd'
        if int(y) % 2 == 1 and rx > 3: return 's'               # feine Scanlines
        t = (y - 2) / 7
        return 'C' if t < 0.34 else ('B' if t < 0.67 else 'P')
    design('face', 'neon_visor', 'Neon Visor', arms='d', colors={'d': '1A1230', 's': '2A1F55', 'C': '38E1FF', 'B': '7C8CFF', 'P': 'B26BFF'},
           parts=[part(raster(36, 11, visor), {}, at=(0, 4.0, -4.85), px=F, glow='CBP'),
                  part(["W", "W", "W", "W", "W", "W"], {'W': 'F2FDFF'}, at=(0, 4.0, -4.97), px=F, depth=0.5, glow='W', anim='scan:3.6')])

    # --- Star Glasses: glatte Sterne mit Verlauf, Funkeln
    def sterne(x, y):
        for cx in (8.6, 27.4):
            d = stern_sdf((x - cx) / 8.4, (y - 8.6) / 8.4)
            if d < -0.12:
                r = math.hypot(x - cx, y - 8.6) / 8.4
                return 'o' if r < 0.28 else ('y' if r < 0.62 else 'Y')
            if d < 0.0: return 'b'
        if 16.6 <= x <= 19.4 and 6.8 <= y <= 8.0: return 'b'
        return '.'
    design('face', 'star_glasses', 'Star Glasses', arms='b', colors={'Y': 'FFE680', 'y': 'FFD23F', 'o': 'FF8A1F', 'b': 'C77A12'},
           parts=[part(raster(36, 17, sterne), {}, at=(0, 3.7, -4.85), px=F),
                  part(["..W..", ".WWW.", "WWWWW", ".WWW.", "..W.."], {'W': 'FFFDE8'}, at=(3.9, 5.6, -5.0), px=0.18, depth=0.4, glow='W', anim='twinkle'),
                  part(["..W..", ".WWW.", "WWWWW", ".WWW.", "..W.."], {'W': 'FFFDE8'}, at=(-3.3, 2.4, -5.0), px=0.14, depth=0.4, glow='W', anim='twinkle:0.5')])

    # --- 3D Glasses: duenner weisser Rahmen, rot/cyan mit Glanz
    def brille3d(x, y):
        if y < 0 or y > 10: return '.'
        for x0, ch, hl in ((1.5, 'r', 'R'), (19.5, 'c', 'C')):
            if x0 + 1 <= x <= x0 + 14 and 1 <= y <= 9:
                if x0 + 1 <= x <= x0 + 14 and 2 <= y <= 8 and x0 + 2 <= x <= x0 + 13:
                    return hl if (x - x0) + y < 7 and (x - x0) + y > 5.2 else ch
                return 'w'
        if 15.5 <= x <= 20.5 and y <= 3: return 'w'
        return '.'
    design('face', 'retro_3d', '3D Glasses', arms='w', colors={'w': 'F2F2F2', 'r': 'E3343A', 'R': 'FF8A8E', 'c': '29C6E8', 'C': '9BEFFF'},
           parts=[part(raster(36, 11, brille3d), {}, at=(0, 3.9, -4.85), px=F)])

    # --- Monocle: feiner Goldring, Glas mit Glanz, Kette schwingt
    def monokel(x, y):
        r = math.hypot(x - 8, y - 8)
        if r > 7.6: return '.'
        if r > 6.2: return 'G' if (x - 8) + (y - 8) < -2 else 'g'
        if math.hypot(x - 5.6, y - 5.4) < 1.3: return 'w'
        return 'L' if (x - 8) + (y - 8) < -3 else 'l'
    kette = ['g' if y % 3 != 2 else 'h' for y in range(22)]
    design('face', 'monocle', 'Monocle', colors={'g': 'E8B53A', 'G': 'FFE08A', 'h': 'A8761A', 'l': 'BFE7FF', 'L': 'E6F7FF', 'w': 'FFFFFF'},
           parts=[part(raster(16, 16, monokel), {}, at=(-2, 4.2, -4.85), px=F),
                  part(kette, {}, at=(-0.4, 0.95, -4.85), px=F, rot=(0, 0, -18), anim='swing:7')])

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
    s = scale2x(s)
    # Schattierung: Wangen und Stirn etwas dunkler, Augen-Glut
    s = [''.join('B' if ch == 'b' and ((y > 18 and (x < 5 or x > 26)) or y < 2) else ch for x, ch in enumerate(r)) for y, r in enumerate(s)]
    design('face', 'skull_mask', 'Skull Mask', colors={'b': 'E9E4D4', 'B': 'CFC8B4', 'k': '1A1714', 'w': 'FFFFFF', 'r': 'E33B3B'},
           parts=[part(s, {}, at=(0, 4.0, -4.85), px=F, glow='r'),
                  part(["rr", "rr"], {'r': 'FF5A4A'}, at=(-2.25, 4.75, -5.03), px=0.25, depth=0.3, glow='r', anim='pulse:0.35'),
                  part(["rr", "rr"], {'r': 'FF5A4A'}, at=(2.25, 4.75, -5.03), px=0.25, depth=0.3, glow='r', anim='pulse:0.35')])

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
           parts=[part(scale2x(g.rows()), {}, at=(0, 4.6, -4.85), px=F)])

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
           parts=[part(scale2x(b), {}, at=(2.6, 8.4, -1.5), px=F, depth=4, rot=(0, -20, 18), anim='sway:6')])

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
RUECKEN = 2.12   # Ruecken-Oberflaeche (z) plus etwas Luft

def hinten(tiefe, px=0.5, extra=0.0):
    """z-Mitte eines Teils, das mit seiner Tiefe direkt am Ruecken anliegt."""
    return round(RUECKEN + extra + tiefe * px / 2, 3)

def rel(at, rotz, ox, oy):
    """Mitte eines Unterteils, das im (um rotz gedrehten) Raster des Hauptteils um ox/oy versetzt liegt."""
    c, si = math.cos(math.radians(rotz)), math.sin(math.radians(rotz))
    return (round(at[0] + ox * c - oy * si, 3), round(at[1] + ox * si + oy * c, 3))

def gurte(farbe_k, schnalle=None):
    """Schultergurte: vorne ueber die Brust und oben ueber die Schulter."""
    vorne = ['g'] * 13
    if schnalle: vorne[9] = 's'
    oben = ['g'] * 9
    teile = []
    for x in (-2.2, 2.2):
        teile.append(part(vorne, {'g': farbe_k, 's': schnalle or farbe_k}, at=(x, -3.1, -2.18), px=0.5, depth=0.5, anim='strap'))
        teile.append(part(oben, {'g': farbe_k}, at=(x, 0.13, 0.0), px=0.5, depth=0.5, rot=(90, 0, 0), anim='strap'))
    return teile

def back():
    # --- Teddy Backpack: Kopf und Koerper mit Volumen, Schnauze steht vor, Arme, Gurte
    kopf = [
        "..bb........bb..",
        ".bBBb......bBBb.",
        ".bBkbbbbbbbbkBb.",
        "..bbbbbbbbbbbb..",
        "..bbbbbbbbbbbb..",
        ".bbbkkbbbbkkbbb.",
        ".bbbkwbbbbkwbbb.",
        ".bbbbbbbbbbbbbb.",
        ".bbbbbbbbbbbbbb.",
        "..bbbbbbbbbbbb..",
        "...bbbbbbbbbb...",
        "....bbbbbbbb....",
    ]
    koerper = [
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
    schnauze = ["BBBB", "BnnB", "BBBB", ".kk."]
    arm = [".b", "bb", "bb", "bb", "BB"]
    tc = {'b': '8A5A35', 'B': 'C49067', 'k': '1D1410', 'w': 'FFFFFF', 'n': '3A241A'}
    design('back', 'teddy_backpack', 'Teddy Backpack', colors=tc,
           parts=[part(koerper, {}, at=(0, -8.6, hinten(6)), px=0.5, depth=6, anim='bob'),
                  part(kopf, {}, at=(0, -3.1, hinten(5)), px=0.5, depth=5, anim='bob'),
                  part(schnauze, {}, at=(0, -4.3, hinten(5) + 1.25 + 0.4), px=0.5, depth=1.6, anim='bob'),
                  part(arm, {}, at=(-3.3, -8.0, hinten(6) + 1.0), px=0.5, depth=3, rot=(0, 0, -25), anim='bob'),
                  part([r[::-1] for r in arm], {}, at=(3.3, -8.0, hinten(6) + 1.0), px=0.5, depth=3, rot=(0, 0, 25), anim='bob')]
                 + gurte('6B3F22'))

    # --- Explorer Backpack: Koerper mit Tiefe, Deckel und Aussentasche stehen vor, Schlafrolle oben
    p = G(16, 18)
    p.rect(1, 0, 14, 17, 'l')
    p.rect(2, 0, 2, 17, 's'); p.rect(13, 0, 13, 17, 's')
    klappe = G(16, 7)
    klappe.rect(1, 0, 14, 5, 'L'); klappe.rect(1, 6, 14, 6, 'd')
    klappe.rect(6, 4, 9, 6, 'g'); klappe.rect(7, 5, 8, 5, 'd')
    tasche = G(10, 6)
    tasche.rect(0, 0, 9, 5, 'L'); tasche.rect(0, 0, 9, 0, 'd'); tasche.rect(4, 1, 5, 2, 'g')
    roll = G(18, 5)
    roll.rect(0, 0, 17, 4, 'r')
    for x in (3, 14): roll.rect(x, 0, x, 4, 's')
    roll.rect(0, 0, 17, 0, 'R'); roll.rect(0, 4, 17, 4, 'R')
    ec = {'l': '6B4423', 'L': '845530', 'd': '3E2614', 'g': 'D9A93A', 's': '2F1D10', 'r': '2F6B4F', 'R': '24543E'}
    design('back', 'explorer_pack', 'Explorer Backpack', colors=ec,
           parts=[part(p, {}, at=(0, -6.6, hinten(7)), depth=7, anim='bob'),
                  part(klappe, {}, at=(0, -3.4, hinten(8)), depth=8, anim='bob'),
                  part(tasche, {}, at=(0, -8.6, hinten(7) + 1.75 + 0.6), depth=2.4, anim='bob'),
                  part(roll, {}, at=(0, -1.0, hinten(5)), depth=5, anim='bob')]
                 + gurte('2F1D10', 'D9A93A'))

    # --- Greatsword (diagonal) -- Klinge flach, Griff und Parierstange dicker
    s = G(9, 46)
    s.rect(3, 11, 5, 43, 'm')          # Klinge
    s.rect(4, 11, 4, 42, 'M')
    s.set(3, 43, '.'); s.set(5, 43, '.'); s.rect(4, 43, 4, 45, 'm')
    griff = G(9, 11)
    griff.rect(3, 0, 5, 1, 'p'); griff.rect(4, 2, 4, 8, 'h')
    for y in range(2, 9, 2): griff.set(4, y, 'H')
    griff.rect(0, 9, 8, 10, 'g'); griff.rect(4, 9, 4, 10, 'G')
    glanz = ["W", "W", "W", "W"]
    gc = {'p': 'D9A93A', 'h': '3B2416', 'H': '5A3A24', 'g': 'C8962E', 'G': 'F2D46B', 'm': 'B9C1CC', 'M': 'E9EEF4'}
    # Teile liegen im gedrehten Schwert-Raster: gleiche Mitte, Griff oben
    design('back', 'greatsword', 'Greatsword', colors=gc,
           parts=[part(s, {}, at=(0, -5.5, hinten(1.5)), depth=1.5, rot=(0, 0, 38), anim='bob'),
                  part(griff, {}, at=(*rel((0, -5.5), 38, 0, 8.75), hinten(3)), depth=3, rot=(0, 0, 38), anim='bob'),
                  part(glanz, {'W': 'FFFFFF'}, at=(0, -5.5, hinten(1.5) + 0.45), px=0.5, depth=0.2, rot=(0, 0, 38), glow='W', anim='glint:5')])

    # --- Crystal Scythe -- Kristallklinge pulsiert
    sc = G(26, 40)
    sc.line(14, 6, 14, 39, 'w', 0)     # Stiel
    sc.line(15, 6, 15, 39, 'W', 0)
    for y in range(10, 39, 6): sc.rect(14, y, 15, y, 'b')
    for i in range(0, 15):
        a_ = math.radians(180 + i * 6)
        for r in range(9, 14):
            x = 14 + math.cos(a_) * r * 1.0
            y = 7 + math.sin(a_) * r * 0.55
            sc.set(x, y, 'C' if r < 12 else 'c')
    sc.rect(13, 4, 16, 7, 'b')
    sc.outline('d', inner='Cc')
    design('back', 'crystal_scythe', 'Crystal Scythe', colors={'w': '4A3A6B', 'W': '6A568F', 'b': 'C9D3E6', 'C': '6FF3FF', 'c': '2BB8E6', 'd': '1C5E86'},
           parts=[part(sc, {}, at=(0, -5.2, hinten(2)), depth=2, rot=(0, 0, -28), glow='C', anim='bob'),
                  part(["..W..", ".WWW.", "WWWWW", ".WWW.", "..W.."], {'W': 'C8FBFF'}, at=(-3.9, -0.6, hinten(2) + 0.6), px=0.22, depth=0.4, glow='W', anim='twinkle')])

    # --- Guitar -- Korpus mit Volumen, Hals duenner, Saiten
    gt = G(14, 42)
    gt.disc(6.5, 33, 6.4, 'w'); gt.disc(6.5, 24, 5.0, 'w')
    gt.disc(6.5, 33, 5.4, 'W'); gt.disc(6.5, 24, 4.0, 'W')
    gt.disc(6.5, 28, 1.8, 'k')
    gt.rect(4, 37, 9, 37, 'k')
    hals = G(14, 21)
    hals.rect(5, 2, 8, 20, 'n'); hals.rect(6, 2, 7, 20, 'N')
    for y in range(4, 20, 3): hals.rect(5, y, 8, y, 'f')
    hals.rect(4, 0, 9, 3, 'h')
    for y in (0, 2): hals.set(3, y, 'f'); hals.set(10, y, 'f')
    saiten = G(14, 42)
    for x in (6, 7): saiten.line(x, 3, x, 37, 's')
    gc2 = {'w': 'B5672F', 'W': 'D8914E', 'k': '1E140E', 'n': '5A3A24', 'N': '7A5236', 'f': 'C9C9C9', 'h': '3A2416', 's': 'EDEDED'}
    design('back', 'guitar', 'Guitar', colors=gc2,
           parts=[part(gt, {}, at=(0, -6.0, hinten(5)), depth=5, rot=(0, 0, -32), anim='bob'),
                  part(hals, {}, at=(*rel((0, -6.0), -32, 0, 5.25), hinten(2)), depth=2, rot=(0, 0, -32), anim='bob'),
                  part(saiten, {}, at=(0, -6.0, hinten(5) + 1.3), depth=0.2, rot=(0, 0, -32), anim='bob')])

    # --- Katana (in der Scheide)
    k = G(5, 46)
    k.rect(1, 0, 3, 9, 'h')
    for y in range(0, 10, 2): k.rect(1, y, 3, y, 'H')
    k.rect(0, 10, 4, 11, 'g')
    k.rect(1, 12, 3, 45, 's'); k.rect(2, 12, 2, 44, 'S')
    for y in (18, 30): k.rect(1, y, 3, y, 'g')
    design('back', 'katana', 'Katana', colors={'h': '1C1C24', 'H': 'E8E1D0', 'g': 'D9A93A', 's': '7E1620', 'S': 'A82432'},
           parts=[part(k, {}, at=(0, -5.5, hinten(3)), depth=3, rot=(0, 0, 40), anim='bob')])

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
           parts=[part(sb, {}, at=(0, -5.5, hinten(2)), depth=2, rot=(0, 0, -14), anim='bob')])

    # --- Rocket Pack -- zwei runde Tanks, Mittelteil, flackernde Flammen
    tank = G(5, 18)
    tank.rect(0, 3, 4, 17, 'm'); tank.rect(1, 3, 1, 17, 'M'); tank.rect(4, 3, 4, 17, 'n')
    tank.rect(1, 0, 3, 2, 'r'); tank.rect(0, 2, 4, 2, 'r')
    tank.rect(0, 15, 4, 17, 'd')
    mitte = G(6, 11)
    mitte.rect(0, 0, 5, 10, 'd'); mitte.rect(2, 2, 3, 3, 'L'); mitte.rect(1, 6, 4, 6, 'm'); mitte.rect(1, 8, 4, 8, 'm')
    flamme = G(3, 7)
    flamme.rect(0, 0, 2, 1, 'Y'); flamme.rect(0, 2, 2, 4, 'O'); flamme.rect(1, 5, 1, 6, 'R')
    rc = {'m': 'B8BEC9', 'M': 'E6EAF0', 'n': '8A909C', 'r': 'D92E3A', 'd': '3A4150', 'L': '5EF2FF', 'Y': 'FFF36A', 'O': 'FF9A2E', 'R': 'FF4A2E'}
    design('back', 'rocket_pack', 'Rocket Pack', colors=rc,
           parts=[part(tank, {}, at=(-2.25, -6.0, hinten(5)), depth=5, anim='bob'),
                  part(tank, {}, at=(2.25, -6.0, hinten(5)), depth=5, anim='bob'),
                  part(mitte, {}, at=(0, -5.6, hinten(3)), depth=3, anim='bob', glow='L'),
                  part(flamme, {}, at=(-2.25, -12.4, hinten(5)), depth=3, glow='YOR', anim='flicker'),
                  part(flamme, {}, at=(2.25, -12.4, hinten(5)), depth=3, glow='YOR', anim='flicker')]
                 + gurte('3A4150'))

    # --- Quiver -- runder Koecher mit Pfeilen
    q = G(10, 30)
    q.rect(2, 8, 7, 29, 'l'); q.rect(3, 8, 3, 29, 'L')
    q.rect(2, 12, 7, 12, 'd'); q.rect(2, 24, 7, 24, 'd')
    pfeile = G(10, 9)
    for i, x in enumerate((2, 4, 6)):
        pfeile.rect(x + 1, 3 + i, x + 1, 8, 's')
        pfeile.rect(x, i, x + 2, 2 + i, 'f' if i != 1 else 'F')
    qc = {'l': '7A4A26', 'L': '9A6236', 'd': '3E2614', 's': 'C9B28A', 'f': 'F2F2F2', 'F': 'D9283A'}
    design('back', 'quiver', 'Quiver', colors=qc,
           parts=[part(q, {}, at=(1.5, -4.5, hinten(5)), depth=5, rot=(0, 0, -22), anim='bob'),
                  part(pfeile, {}, at=(*rel((1.5, -4.5), -22, 0, 5.25), hinten(5)), depth=2, rot=(0, 0, -22), anim='bob')])

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
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import schilde
    schilde.erzeugen(os.path.join(RES, 'textures', 'cosmetics', 'shield'), design)

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
        for k in ('frames', 'fps'):
            if k in d and d['kat'] == 'shield': L.append(f"{k}: {d[k]}")
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
