"""Shield Skins (seit 4.30): weiche Schild-Texturen in 4-facher Aufloesung.

Textur 256x256 im Layout des Vanilla-Schilds (64x64) mal 4:
  Vorderseite (4,4) 48x88, Rueckseite (56,4) 48x88, Kanten, Griff ab (104,0).
Jedes Motiv wird 4x ueberabgetastet gerechnet und dann gemittelt -> glatte
Kanten und Verlaeufe statt grober Pixel. Animierte Schilde bekommen 8 Bilder
(<id>_0.png .. <id>_7.png, Schleife), dazu <id>.png = Bild 0.

Alle Motive sind eigene Entwuerfe.
"""
import math, os
import numpy as np
from PIL import Image

W, H = 48, 88          # Vorderseite in Texturpixeln
SS = 4                 # Ueberabtastung
RAND = 4.0             # Metallrahmen (= 1 Vanilla-Pixel)
BILDER = 8


# ---------------------------------------------------------------- Werkzeug
def hexc(h):
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], dtype=np.float64)

def mix(a, b, t):
    t = np.clip(t, 0, 1)[..., None] if np.ndim(t) else np.clip(t, 0, 1)
    return a * (1 - t) + b * t

def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)

def gitter():
    """Koordinaten der Abtastpunkte (in Texturpixeln, y nach unten)."""
    xs = (np.arange(W * SS) + 0.5) / SS
    ys = (np.arange(H * SS) + 0.5) / SS
    return np.meshgrid(xs, ys)

def _hash(ix, iy, seed):
    h = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0

def rauschen(x, y, seed=0):
    """Glattes Wertrauschen 0..1."""
    ix, iy = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    fx, fy = x - ix, y - iy
    ux, uy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = _hash(ix, iy, seed), _hash(ix + 1, iy, seed)
    c, d = _hash(ix, iy + 1, seed), _hash(ix + 1, iy + 1, seed)
    return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy

def fbm(x, y, seed=0, okt=4):
    s, amp, f, norm = 0, 0.5, 1.0, 0
    for o in range(okt):
        s = s + amp * rauschen(x * f, y * f, seed + o * 17)
        norm += amp; amp *= 0.5; f *= 2.0
    return s / norm

def zellen(x, y, n=7, seed=1):
    """Voronoi: Abstand zum naechsten und zweitnaechsten Punkt (Facetten, Adern)."""
    rnd = np.random.default_rng(seed)
    px = rnd.uniform(0, W, n * 3); py = rnd.uniform(0, H, n * 3)
    d = np.stack([np.hypot(x - a, y - b) for a, b in zip(px, py)])
    d.sort(axis=0)
    return d[0], d[1]

def kreis_aa(r, radius):
    return 1 - smooth(radius - 0.35, radius + 0.35, r)


# ---------------------------------------------------------------- Rahmen
def rahmen(rgb, x, y, metall, tief=None):
    """Metallrahmen mit Fase (oben links hell, unten rechts dunkel), Glanz, Vignette."""
    rand = np.minimum.reduce([x, y, W - x, H - y])
    innen = smooth(RAND - 0.5, RAND + 0.5, rand)
    # Fase: Licht von oben links
    licht = np.clip(0.55 + 0.45 * ((W - x) / W * 0.5 + (H - y) / H * 0.5) - 0.25, 0, 1)
    kante = np.where((x < RAND) | (y < RAND), 1.0, 0.0) * 0.25 - np.where((W - x < RAND) | (H - y < RAND), 1.0, 0.0) * 0.25
    m = metall[None, None, :] * (0.72 + 0.36 * licht[..., None] + 0.7 * kante[..., None])
    # schmale dunkle Fuge innen am Rahmen
    fuge = 1 - 0.45 * np.exp(-((rand - RAND - 0.3) ** 2) / 0.25)
    # Vignette und Glanz auf der Flaeche
    dx, dy = (x - W / 2) / (W / 2), (y - H / 2) / (H / 2)
    vign = 1 - 0.18 * np.clip(dx * dx * 0.6 + dy * dy * 0.6, 0, 1)
    glanz = 0.10 * smooth(0.0, 1.0, 1 - np.abs((x / W + y / H * 0.55) - 0.42) * 6) * (y < H * 0.6)
    flaeche = rgb * (vign * fuge)[..., None] + glanz[..., None]
    out = mix(m, flaeche, innen)
    return np.clip(out, 0, 1)

def runter(img):
    """Ueberabtastung mitteln -> W x H."""
    return img.reshape(H, SS, W, SS, 3).mean(axis=(1, 3))


# ---------------------------------------------------------------- Motive
# Jedes Motiv: f(x, y, t) -> rgb (t = 0..1 Schleife; bei statischen ignoriert)

def obsidian(x, y, t):
    d1, d2 = zellen(x, y, 9, 4)
    facette = (d1 / 9) % 1
    grund = mix(hexc('0C0716'), hexc('2B1650'), np.clip(0.35 + 0.65 * (1 - facette) * 0.8, 0, 1))
    ader = np.exp(-((d2 - d1) ** 2) / 0.35)
    puls = 0.65 + 0.35 * math.sin(t * 2 * math.pi)
    lila = hexc('B48CFF')
    rgb = grund + ader[..., None] * lila * puls * 0.9
    # kleine Funken
    funke = (rauschen(x * 0.9, y * 0.9, 77) > 0.86) * (0.5 + 0.5 * math.sin(t * 2 * math.pi + 2))
    return rgb + funke[..., None] * hexc('E9DDFF') * 0.35

def ender(x, y, t):
    cx, cy = W / 2, H / 2
    dx, dy = x - cx, (y - cy) * 0.78
    r = np.hypot(dx, dy); a = np.arctan2(dy, dx)
    grund = mix(hexc('06201E'), hexc('0E3A36'), fbm(x / 9, y / 9, 3))
    ringe = 0.5 + 0.5 * np.cos(r * 0.9 - t * 2 * math.pi)
    rgb = grund + (ringe * np.exp(-r / 22))[..., None] * hexc('1E7F70') * 0.35
    # Auge
    auge = kreis_aa(r, 13.5)
    iris = mix(hexc('3FE0B8'), hexc('0E8C75'), smooth(4, 13, r)) * (0.85 + 0.15 * np.cos(a * 12 + t * 2 * math.pi))[..., None]
    rgb = mix(rgb, iris, auge)
    puls = 0.85 + 0.15 * math.sin(t * 2 * math.pi)
    pupille = 1 - smooth(1.6 * puls, 2.4 * puls, np.hypot(dx * 2.2, dy * 0.75))
    rgb = mix(rgb, hexc('020606'), pupille)
    licht = kreis_aa(np.hypot(dx + 4, dy + 4), 2.2)
    rgb = mix(rgb, hexc('E8FFF8'), licht * 0.9)
    rand = smooth(12.8, 13.8, r) * (1 - smooth(13.8, 15.5, r))
    return rgb + rand[..., None] * hexc('9BFFE6') * 0.5

def portal(x, y, t):
    cx, cy = W / 2, H / 2
    dx, dy = x - cx, (y - cy) * 0.55
    r = np.hypot(dx, dy) + 1e-6; a = np.arctan2(dy, dx)
    w = np.sin(a * 3 + r * 0.42 - t * 2 * math.pi + fbm(x / 10, y / 10, 5) * 2.2)
    v = (w + 1) / 2
    rgb = mix(hexc('2A0868'), hexc('C47CFF'), v ** 1.4)
    kern = np.exp(-r / 7)
    rgb = rgb + kern[..., None] * hexc('F3E3FF') * 0.55
    funken = (rauschen(x * 0.7 + t * 6, y * 0.7, 9) > 0.88)
    return rgb + funken[..., None] * hexc('FFFFFF') * 0.25

def frost(x, y, t):
    grund = mix(hexc('D4F1FF'), hexc('5DB8EA'), smooth(0, H, y))
    grund = grund + (fbm(x / 6, y / 6, 11) - 0.5)[..., None] * 0.12
    cx, cy = W / 2, H / 2
    dx, dy = x - cx, y - cy
    r = np.hypot(dx, dy); a = np.arctan2(dy, dx)
    # Schneeflocke: 6 Arme mit Seitenzweigen
    flocke = np.zeros_like(x)
    for k in range(6):
        w = k * math.pi / 3
        ux, uy = math.cos(w), math.sin(w)
        s = dx * ux + dy * uy; q = -dx * uy + dy * ux
        arm = (np.abs(q) < 1.0) & (s > 0) & (s < 20)
        flocke = np.maximum(flocke, arm * 1.0)
        for p, l in ((8, 6), (14, 4.5)):
            for sg in (1, -1):
                vx, vy = math.cos(w + sg * 0.75), math.sin(w + sg * 0.75)
                bx, by = dx - ux * p, dy - uy * p
                s2 = bx * vx + by * vy; q2 = -bx * vy + by * vx
                flocke = np.maximum(flocke, ((np.abs(q2) < 0.85) & (s2 > 0) & (s2 < l)) * 1.0)
    flocke = np.maximum(flocke, kreis_aa(r, 3.0))
    rgb = mix(grund, hexc('FFFFFF'), flocke * 0.95)
    # Glitzern (animiert)
    g = np.zeros_like(x)
    rnd = np.random.default_rng(5)
    for i in range(10):
        px, py = rnd.uniform(6, W - 6), rnd.uniform(6, H - 6)
        ph = (t + i / 10) % 1
        st = max(0.0, math.sin(ph * 2 * math.pi)) ** 3
        d = np.hypot(x - px, y - py)
        kreuz = np.exp(-np.minimum(np.abs(x - px), np.abs(y - py)) ** 2 * 3) * np.exp(-d / 2.2)
        g = np.maximum(g, kreuz * st)
    return rgb + g[..., None] * 0.9

def sonnenblume(x, y, t):
    grund = mix(hexc('5BA846'), hexc('25591E'), smooth(0, H, y)) + (fbm(x / 7, y / 7, 21) - 0.5)[..., None] * 0.1
    cx, cy = W / 2, H * 0.47
    dx, dy = x - cx, y - cy
    r = np.hypot(dx, dy); a = np.arctan2(dy, dx)
    rad = 16 + 3.2 * np.abs(np.cos(a * 7))
    blatt = kreis_aa(r / rad * 16, 16)
    bl = mix(hexc('FFE34D'), hexc('F09A12'), smooth(9, rad.max(), r)) * (0.9 + 0.1 * np.cos(a * 14))[..., None]
    rgb = mix(grund, bl, blatt)
    # Samen: Fibonacci-Spirale
    korb = kreis_aa(r, 9.0)
    samen = np.zeros_like(x)
    gold = math.pi * (3 - math.sqrt(5))
    for i in range(110):
        rr = 0.85 * math.sqrt(i); ww = i * gold
        samen = np.maximum(samen, kreis_aa(np.hypot(dx - rr * math.cos(ww), dy - rr * math.sin(ww)), 0.55))
    k = mix(hexc('5A3214'), hexc('2A160A'), smooth(0, 9, r))
    k = mix(k, hexc('8A5A2A'), samen * 0.8)
    rgb = mix(rgb, k, korb)
    # Stiel
    stiel = (np.abs(x - cx - (y - cy) * 0.05) < 1.1) & (y > cy + 16)
    return mix(rgb, hexc('2E7A22'), stiel * 1.0)

def sakura(x, y, t):
    grund = mix(hexc('FFF3F8'), hexc('FFD3E4'), smooth(0, H, y))
    def bluete(cx, cy, s, rot):
        dx, dy = (x - cx) / s, (y - cy) / s
        r = np.hypot(dx, dy); a = np.arctan2(dy, dx) + rot
        blatt = 1 - smooth(-0.05, 0.05, r - (0.62 + 0.38 * np.abs(np.cos(a * 2.5))) * (1 - 0.18 * (np.cos(a * 5 * 2) > 0.95)))
        farbe = mix(hexc('FFC6DC'), hexc('FF6FA8'), smooth(0.2, 1.0, r))
        mitte = kreis_aa(r * s, 0.22 * s)
        return blatt, farbe, mitte
    rgb = grund
    for cx, cy, s, rot in ((W * 0.5, H * 0.42, 17, 0.3), (W * 0.25, H * 0.8, 7, 1.1), (W * 0.78, H * 0.16, 6, 2.0), (W * 0.8, H * 0.74, 5, 0.5)):
        b, f, m = bluete(cx, cy, s, rot)
        rgb = mix(rgb, f, b)
        rgb = mix(rgb, hexc('FFE07A'), m)
    # Bluetenblaetter fallen (animiert)
    rnd = np.random.default_rng(8)
    for i in range(7):
        px = rnd.uniform(6, W - 6); y0 = rnd.uniform(0, H)
        py = (y0 + t * H) % H
        px2 = px + 3 * math.sin((t + i) * 2 * math.pi)
        d = np.hypot((x - px2) * 1.0, (y - py) * 1.7)
        rgb = mix(rgb, hexc('FF9CC4'), kreis_aa(d, 1.5) * 0.9)
    return rgb

def vortex(x, y, t):
    grund = mix(hexc('120E26'), hexc('07060F'), smooth(0, H, y))
    cx, cy = W / 2, H / 2
    dx, dy = x - cx, (y - cy) * 0.6
    r = np.hypot(dx, dy); a = np.arctan2(dy, dx)
    spirale = 0.5 + 0.5 * np.sin(a * 2 - r * 0.35 + t * 2 * math.pi)
    rgb = grund + (spirale * np.exp(-r / 18))[..., None] * hexc('3A2A8A') * 0.45
    # V aus zwei Klingen (wie das Logo): links lila, rechts blau
    def klinge(x0, y0, x1, y1, breite):
        vx, vy = x1 - x0, y1 - y0; l2 = vx * vx + vy * vy
        s = np.clip(((x - x0) * vx + (y - y0) * vy) / l2, 0, 1)
        d = np.hypot(x - (x0 + s * vx), y - (y0 + s * vy))
        return 1 - smooth(breite * (1 - s * 0.55) - 0.5, breite * (1 - s * 0.55) + 0.5, d), s
    puls = 0.75 + 0.25 * math.sin(t * 2 * math.pi)
    l, sl = klinge(W * 0.18, H * 0.2, W * 0.5, H * 0.8, 5.5)
    r_, sr = klinge(W * 0.82, H * 0.2, W * 0.5, H * 0.8, 5.5)
    hl = mix(hexc('E2C2FF'), hexc('7C3AED'), sl)
    hr = mix(hexc('A6E4FF'), hexc('3B5BDB'), sr)
    rgb = mix(rgb, hl, l)
    rgb = mix(rgb, hr, r_ * (1 - l * 0.5))
    halo_l, _ = klinge(W * 0.18, H * 0.2, W * 0.5, H * 0.8, 10)
    halo_r, _ = klinge(W * 0.82, H * 0.2, W * 0.5, H * 0.8, 10)
    halo = np.clip(halo_l + halo_r, 0, 1) * (1 - np.clip(l + r_, 0, 1))
    return rgb + halo[..., None] * hexc('6A4CFF') * 0.35 * puls

def lava(x, y, t):
    ph = t * 2 * math.pi
    n = fbm(x / 9 + math.cos(ph) * 0.6, y / 9 - t * 3 + math.sin(ph) * 0.6, 31)
    fluss = 1 - np.abs(n - 0.5) * 7
    fluss = np.clip(fluss, 0, 1) ** 1.5
    kruste = mix(hexc('171010'), hexc('3A2620'), fbm(x / 5, y / 5, 7))
    gl = mix(hexc('D2300C'), hexc('FFD24A'), fluss)
    rgb = mix(kruste, gl, smooth(0.05, 0.6, fluss))
    return rgb + (fluss ** 3)[..., None] * hexc('FFF2B0') * 0.4

def prisma(x, y, t):
    farben = [hexc(h) for h in ('E8434B', 'F59A2C', 'F7D43C', '4CC25B', '3D8BE6', '8E5BE3', 'E8434B')]
    u = ((x / W) * 0.8 + (y / H) * 0.9 + t) % 1 * 6
    i = np.floor(u).astype(int); f = u - i
    a = np.stack(farben)[i]; b = np.stack(farben)[np.minimum(i + 1, 6)]
    rgb = a * (1 - f[..., None]) + b * f[..., None]
    rgb = rgb * 0.82 + 0.18
    # Glanzstreifen, der durchlaeuft
    s = ((x / W) - (y / H) * 0.6 - (t * 1.6 - 0.3))
    glanz = np.exp(-(s * 9) ** 2)
    return rgb + glanz[..., None] * 0.45

def royal(x, y, t):
    samt = mix(hexc('A3162A'), hexc('5C0A16'), smooth(0, H, y)) * (0.9 + 0.1 * fbm(x / 3, y / 3, 41))[..., None]
    rgb = samt
    gold = hexc('F0C24A'); gold_d = hexc('A67A1E')
    # innere Goldlinie mit runden Ecken
    ix, iy = np.abs(x - W / 2) - (W / 2 - 9), np.abs(y - H / 2) - (H / 2 - 9)
    d = np.hypot(np.maximum(ix, 0), np.maximum(iy, 0)) + np.minimum(np.maximum(ix, iy), 0)
    linie = np.exp(-(d ** 2) / 0.6)
    rgb = mix(rgb, gold, linie)
    # Lilien-Muster schwach
    muster = (np.sin(x * 0.9) * np.sin(y * 0.9) > 0.85) * 0.15
    rgb = rgb + muster[..., None] * gold * 0.4
    # Krone
    cx, cy = W / 2, H * 0.42
    kx, ky = x - cx, y - cy
    band = (np.abs(kx) < 12) & (ky > 2) & (ky < 7)
    zacken = (np.abs(kx) < 12) & (ky <= 2) & (ky > -9 + 7 * np.abs(np.cos(kx * math.pi / 8)))
    krone = (band | zacken) * 1.0
    kc = mix(gold, gold_d, smooth(-9, 7, ky))
    rgb = mix(rgb, kc, krone)
    for jx, jy, c in ((0, 4.5, '3D8BE6'), (-7, 4.5, 'E8434B'), (7, 4.5, 'E8434B')):
        rgb = mix(rgb, hexc(c), kreis_aa(np.hypot(kx - jx, ky - jy), 1.3))
    for jx in (-12, -4, 4, 12):
        rgb = mix(rgb, hexc('FFF2C0'), kreis_aa(np.hypot(kx - jx, ky + 9.5 - 7 * abs(math.cos(jx * math.pi / 8))), 1.2))
    # Schriftband darunter
    sb = (np.abs(kx) < 14) & (ky > 13) & (ky < 17)
    return mix(rgb, gold_d * 1.1, sb * 1.0)


# ---------------------------------------------------------------- Liste
# id, Name, Motiv, Rahmen, Rueckseite, Griff, animiert
SKINS = [
    ('obsidian', 'Obsidian Shield', obsidian, '3B2A57', '1A1128', '2A1748', True),
    ('ender', 'Ender Shield', ender, '1E3B38', '0E2422', '0A1414', True),
    ('nether_portal', 'Portal Shield', portal, '2A1A44', '1C0E33', '140A22', True),
    ('frost', 'Frost Shield', frost, 'E6F6FF', '7FC6EE', 'C9EEFF', True),
    ('sunflower', 'Sunflower Shield', sonnenblume, '6B4423', '3E2A16', '6B4423', False),
    ('sakura', 'Sakura Shield', sakura, 'C8417E', 'F4C2D6', '6B3A2A', True),
    ('vortex', 'Vortex Shield', vortex, '7C3AED', '15102B', '3B2A57', True),
    ('lava', 'Lava Shield', lava, '4A3A34', '201614', '3A2A24', True),
    ('prism', 'Prism Shield', prisma, 'EDEDF3', '2A2A33', '8E5BE3', True),
    ('royal', 'Royal Shield', royal, 'E8B53A', '5C0A16', 'C8962E', False),
]


def rueckseite(farbe, metall):
    x, y = gitter()
    holz = hexc(farbe) * (0.85 + 0.25 * fbm(x / 14, y / 1.6, 3))[..., None]
    for gy in (H * 0.3, H * 0.7):
        riemen = smooth(-3.5, -2.5, y - gy) * (1 - smooth(2.5, 3.5, y - gy))
        holz = mix(holz, hexc('3A2416'), riemen * 0.9)
        niete = sum(kreis_aa(np.hypot(x - nx, y - gy), 1.3) for nx in (RAND + 4, W - RAND - 4))
        holz = mix(holz, hexc('C9C9D0'), np.clip(niete, 0, 1))
    return runter(rahmen(holz, x, y, hexc(metall)))


def bild(motiv, t, metall):
    x, y = gitter()
    return runter(rahmen(np.clip(motiv(x, y, t), 0, 1), x, y, hexc(metall)))


def textur(vorne, hinten, metall, griff):
    img = np.zeros((256, 256, 4))
    m = hexc(metall)
    img[4:4 + H, 4:4 + W, :3] = vorne
    img[4:4 + H, 56:56 + W, :3] = hinten[:, ::-1]
    # Kanten: oben/unten und Seiten in Rahmenfarbe (mit leichtem Verlauf)
    img[0:4, 4:52, :3] = m * 1.1; img[0:4, 52:100, :3] = m * 0.8
    img[4:4 + H, 0:4, :3] = m * 0.95; img[4:4 + H, 52:56, :3] = m * 0.85
    # Griff (Leder)
    g = hexc(griff)
    yy, xx = np.mgrid[0:48, 104:168]
    img[0:48, 104:168, :3] = g * (0.85 + 0.15 * np.sin(xx * 0.8 + yy * 0.3))[..., None]
    belegt = np.zeros((256, 256), bool)
    belegt[0:4, 4:100] = belegt[4:4 + H, 0:104] = belegt[0:48, 104:168] = True
    img[..., 3] = belegt
    return Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA')


def erzeugen(ordner, design):
    os.makedirs(ordner, exist_ok=True)
    for f in os.listdir(ordner):
        if f.endswith('.png'): os.remove(os.path.join(ordner, f))
    for id_, name, motiv, metall, hinten_farbe, griff, anim in SKINS:
        hinten = rueckseite(hinten_farbe, metall)
        if anim:
            for k in range(BILDER):
                textur(bild(motiv, k / BILDER, metall), hinten, metall, griff).save(os.path.join(ordner, f'{id_}_{k}.png'), optimize=True)
        textur(bild(motiv, 0.0, metall), hinten, metall, griff).save(os.path.join(ordner, id_ + '.png'), optimize=True)
        design('shield', id_, name, frames=BILDER if anim else 1, fps=8)
