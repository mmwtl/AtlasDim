#!/usr/bin/env python3
"""Процедурные фоны для приборки G636 (фоновое окно dpy-2, 1920×532).

Все сцены в одной перспективе: горизонт за спидометром, точка схода над
машиной. Рядом с каждым фоном — эскиз со схемой штатного интерфейса.

    uv run --with numpy --with pillow python render_backgrounds.py
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

W, H = 1920, 532
CX, HZ = 960, 118          # точка схода
K = 400.0                  # глубина: d = y - HZ = K / z
V = 4.0                    # путь по z за цикл анимации
OUT = os.path.dirname(os.path.abspath(__file__))
MARKINGS = True            # дорожная разметка (на приборке её рисует штатный ADAS)


YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)
D = YY - HZ
GROUND = np.clip(D + 0.5, 0, 1)            # 1 — земля, 0 — небо (1px AA)
DS = np.maximum(D, 0.5)
Z = K / DS
U = (XX - CX) / DS                          # поперечная мировая координата


# ---------------------------------------------------------------- утилиты

def hexc(s):
    s = s.lstrip('#')
    return np.array([int(s[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def vgrad(stops):
    ys = [s[0] for s in stops]
    cs = np.stack([hexc(s[1]) for s in stops])
    y = np.arange(H, dtype=np.float32)
    out = np.stack([np.interp(y, ys, cs[:, i]) for i in range(3)], -1)
    return np.broadcast_to(out[:, None, :], (H, W, 3)).astype(np.float32)


def _box(a, r, axis):
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(a, pad, mode='edge'), axis=axis, dtype=np.float64)
    n = a.shape[axis]
    hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
    lo = np.take(c, np.arange(0, n), axis=axis)
    return ((hi - lo) / (2 * r + 1)).astype(np.float32)


def blur(a, r):
    """Три прохода box-фильтра ≈ гаусс."""
    r = max(1, int(round(r)))
    for _ in range(3):
        a = _box(_box(a, r, 0), r, 1)
    return a


def ellipse(cx, cy, rx, ry):
    r = np.sqrt(((XX - cx) / rx) ** 2 + ((YY - cy) / ry) ** 2)
    return 1 - smooth(0.55, 1.0, r)


def mask(draw_fn, ss=2):
    im = Image.new('L', (W * ss, H * ss), 0)
    draw_fn(ImageDraw.Draw(im), ss)
    return np.asarray(im.resize((W, H), Image.LANCZOS), np.float32) / 255


def rgba_layer(draw_fn, ss=2):
    im = Image.new('RGBA', (W * ss, H * ss), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im), ss)
    a = np.asarray(im.resize((W, H), Image.LANCZOS), np.float32) / 255
    return a[..., :3], a[..., 3:]


def over(img, rgb, alpha):
    return img * (1 - alpha) + rgb * alpha


def add(img, m, color, k=1.0):
    return img + m[..., None] * hexc(color) * k


def noise2(seed, cell, octaves=3):
    rng = np.random.default_rng(seed)
    acc = np.zeros((H, W), np.float32)
    amp, tot = 1.0, 0.0
    for _ in range(octaves):
        g = rng.random((H // cell + 2, W // cell + 2)).astype(np.float32)
        im = Image.fromarray(g, 'F').resize((W + 2 * cell, H + 2 * cell), Image.BICUBIC)
        acc += amp * np.asarray(im)[cell:cell + H, cell:cell + W]
        tot += amp
        amp *= 0.5
        cell = max(2, cell // 2)
    return acc / tot


def noise1(seed, n, cell):
    rng = np.random.default_rng(seed)
    g = rng.random(n // cell + 3).astype(np.float32)
    x = np.arange(n, dtype=np.float32) / cell
    return np.interp(x, np.arange(len(g)), g)


def stars(seed, count, ymax, bright=1.0):
    rng = np.random.default_rng(seed)
    m = np.zeros((H, W), np.float32)
    xs = rng.integers(0, W, count)
    ys = (rng.random(count) ** 1.4 * ymax).astype(int)
    m[ys, xs] = (rng.random(count) ** 3) * bright
    return np.maximum(m, blur(m, 1) * 2.5)


def road_line(u0, w, t=0.0, dash=None, near=4, far=60):
    """Линия разметки на земле: u0 — поперечная позиция, w — полуширина."""
    cos = 1 / np.sqrt(1 + u0 * u0)          # пологие линии: меряем поперёк линии
    px = (np.abs(XX - (CX + u0 * DS)) - w * DS) * cos
    a = np.clip(0.5 - px, 0, 1) * np.minimum(1, 2 * w * DS * cos + 0.3)
    if dash:
        period, duty = dash
        ph = ((Z + t * V) / period) % 1.0
        dpdy = K / (DS * DS) / period
        edge = np.minimum(ph, duty - ph) / dpdy
        a *= np.where(ph < duty, np.clip(edge + 0.5, 0, 1), 0)
    return a * smooth(HZ + near, HZ + far, YY) * GROUND


def moving(spacing, t, zmin, zmax, seed, per_period, extra=None):
    """Объекты вдоль дороги: узор с периодом `spacing` по z, сдвиг V*t.

    Узор одинаков в каждом периоде, поэтому сдвиг на V (кратное spacing)
    даёт ту же картинку — цикл замкнут.
    """
    rng = np.random.default_rng(seed)
    base = [(rng.random() * spacing,) + tuple(extra(rng) if extra else ())
            for _ in range(per_period)]
    items = []
    shift = (t * V) % spacing
    k = 0
    while k * spacing < zmax + spacing:
        for b in base:
            z = k * spacing + b[0] - shift
            if zmin < z < zmax:
                items.append((z,) + b[1:])
        k += 1
    items.sort(key=lambda it: -it[0])
    return items


# ------------------------------------------------- чёрные зоны и читаемость

# Боковые панели: ручник и колонка Eco/R/N/D/P лежат на чёрном, но чёрное —
# не пятно под значком, а панель с тонкой световой кромкой (как подсветка
# рамки приборки). Погашенный ручник оставляет панель цельной.
_y = np.arange(H, dtype=np.float32)
PANEL_L = 46 + 120 * smooth(40, 340, _y)                 # внутренняя кромка слева
PANEL_R = W - 118 - 34 * smooth(0, 70, _y)               # внутренняя кромка справа
_cut = 1 - smooth(446, 470, _y)                           # кромка уходит под нижнюю панель
EDGE_L = (smooth(60, 260, _y) * _cut)[:, None] * np.exp(-((XX - PANEL_L[:, None]) / 1.1) ** 2)
EDGE_R = (smooth(0, 90, _y) * _cut)[:, None] * np.exp(-((XX - PANEL_R[:, None]) / 1.1) ** 2)
EDGE = EDGE_L + EDGE_R
PANELS = np.maximum(smooth(PANEL_L[:, None] + 34, PANEL_L[:, None] - 4, XX),
                    smooth(PANEL_R[:, None] - 34, PANEL_R[:, None] + 4, XX))


def finish(img, accent='#4a8cff', dither=True):
    m = np.ones((H, W), np.float32)
    m *= 1 - 0.995 * PANELS
    m *= 1 - 0.35 * ellipse(CX, 66, 150, 72)     # цифры скорости
    m *= 1 - 0.25 * ellipse(CX, 280, 125, 175)   # машина
    m *= 1 - 0.45 * smooth(430, 500, YY)         # под нижней панелью
    img = np.clip(img, 0, None) * m[..., None]
    img = add(img, blur(EDGE, 3), accent, 0.5)
    img = add(img, blur(EDGE, 12), accent, 0.25)
    img = add(img, EDGE, accent, 0.55)
    if dither:
        img = img + (np.random.default_rng(7).random((H, W, 1)) - 0.5) / 255
    return np.clip(img, 0, 1)


def bloom(img, m, color, r1=4, r2=16, k1=0.5, k2=0.3):
    return add(add(img, blur(m, r1), color, k1), blur(m, r2), color, k2)


# ------------------------------------------------------------------ сцены

def night_highway(t):
    """Мокрая ночная трасса: огни города на горизонте, фонари и разметка."""
    img = vgrad([(0, '#03050c'), (70, '#0a1430'), (HZ, '#1d2b4f')])
    img = add(img, np.exp(-((YY - HZ) / 26) ** 2) * np.exp(-((XX - CX) / 650) ** 2),
              '#ff9a4a', 0.30)
    img = add(img, np.exp(-((YY - HZ) / 60) ** 2), '#4a7bd5', 0.10)

    rng = np.random.default_rng(11)
    bld = []
    x = 150
    while x < 1770:
        w = int(rng.integers(10, 42))
        far = 0.45 + 0.55 * min(1, abs(x - CX) / 700)
        bld.append((x, int(HZ - rng.integers(4, 34) * far), x + w))
        x += w + int(rng.integers(0, 6))
    sil = mask(lambda d, s: [d.rectangle([a * s, b * s, c * s, HZ * s], fill=255)
                             for a, b, c in bld])
    img = over(img, hexc('#060a16'), sil[..., None])
    win = np.zeros((H, W), np.float32)
    for a, b, c in bld:
        for _ in range(int(rng.integers(0, 5)) if HZ - b > 6 else 0):
            wx, wy = int(rng.integers(a + 2, c - 1)), int(rng.integers(b + 2, HZ - 1))
            win[wy, wx] = rng.random() * 0.8 + 0.2
    img = add(img, win + blur(win, 2) * 3, '#ffc070', 0.7)

    lights = np.zeros(W, np.float32)
    for lx in rng.integers(250, 1670, 40):
        lights += np.exp(-((np.arange(W) - lx) / 3.0) ** 2) * rng.random()
    refl = lights[None, :] * np.exp(-DS / 150) * GROUND
    img = img * (1 - GROUND[..., None])
    img = img + vgrad([(0, '#121722'), (HZ, '#121722'), (300, '#0a0d14'), (H, '#06080c')]) \
        * GROUND[..., None]
    img = add(img, (noise2(3, 2, 2) - 0.5) * GROUND, '#ffffff', 0.05)
    img = add(img, blur(refl, 2), '#ff9a4a', 0.35)
    img = add(img, np.exp(-D.clip(0) / 20) * GROUND * np.exp(-((XX - CX) / 650) ** 2),
              '#ff9a4a', 0.18)

    # Фонари: голова на высоте 2.2 над дорогой, световое пятно на асфальте.
    glow = np.zeros((H, W), np.float32)
    pool = np.zeros((H, W), np.float32)
    for (z,) in moving(2.0, t, 1.2, 70, 5, 1):
        d = K / z
        for side in (-1, 1):
            lx, ly = CX + side * 4.6 * d, HZ + d - 2.2 * d
            r = 1.2 + d * 0.012
            glow += np.exp(-(((XX - lx) ** 2 + (YY - ly) ** 2) / (2 * r * r))) * min(1, 30 / d)
            px, py = CX + side * 3.2 * d, HZ + d
            pool += np.exp(-(((XX - px) / (1.4 * d)) ** 2 + ((YY - py) / (0.35 * d)) ** 2))
    img = add(img, pool * GROUND, '#ff9a4a', 0.10)
    img = bloom(img, glow, '#ffb060', 6, 20, 0.9, 0.5)
    img = add(img, glow, '#ffe2b0', 1.0)

    if not MARKINGS:
        return img
    lines = np.maximum.reduce([
        road_line(-1.0, 0.03, t, (0.5, 0.45)), road_line(1.0, 0.03, t, (0.5, 0.45)),
        road_line(-3.0, 0.035), road_line(3.0, 0.035)])
    img = over(img, hexc('#c8cdd8') * 0.85, lines[..., None])
    return bloom(img, lines, '#9fb4d8', 4, 14, 0.25, 0.15)


def neon_grid(t):
    """Неоновый горизонт: полосатое солнце, горы-силуэты, бегущая сетка."""
    img = vgrad([(0, '#04020c'), (60, '#12052a'), (HZ, '#3e0c52')])
    img = add(img, stars(21, 260, HZ - 10) * (1 - GROUND), '#d8c8ff', 0.8)

    r = 150
    sy = HZ + 6
    dist = np.sqrt((XX - CX) ** 2 + (YY - sy) ** 2)
    disc = np.clip(r - dist + 0.5, 0, 1) * (1 - GROUND)
    gap = np.clip((YY - (sy - 95)) / 95, 0, 1) * 0.6
    stripes = ((YY - (sy - 95)) / 13) % 1.0
    disc *= np.where(YY > sy - 95, np.clip((stripes - gap) * 13, 0, 1), 1)
    sun = np.clip((YY - (sy - r)) / r, 0, 1)[..., None]
    sun_col = hexc('#ffc85a') * (1 - sun) + hexc('#ff2d78') * sun
    img = img + disc[..., None] * sun_col * 0.6
    img = add(img, blur(disc, 30) * (1 - GROUND), '#ff2d78', 0.45)

    rng = np.random.default_rng(4)

    def ridge(x0, x1, hmax, hmin):
        pts, x = [], x0
        while x <= x1:
            k = abs(x - CX) / 960
            pts.append((x, HZ - (hmin + (hmax - hmin) * k) * (0.35 + 0.65 * rng.random())))
            x += int(rng.integers(30, 90))
        return [(x0, HZ)] + pts + [(x1, HZ)]
    hills = [ridge(-20, 760, 95, 18), ridge(1160, 1940, 95, 18)]
    fill = mask(lambda d, s: [d.polygon([(a * s, b * s) for a, b in p], fill=255) for p in hills])
    rim = mask(lambda d, s: [d.line([(a * s, b * s) for a, b in p], fill=255, width=2 * s)
                             for p in hills])
    img = over(img, hexc('#0a0320'), fill[..., None])
    img = bloom(img, rim, '#21e6ff', 3, 10, 0.4, 0.2)
    img = add(img, rim, '#7ff4ff', 0.5)

    img = img * (1 - GROUND[..., None]) + vgrad(
        [(0, '#0b0220'), (HZ, '#0b0220'), (H, '#05010d')]) * GROUND[..., None]
    img = add(img, np.exp(-D.clip(0) / 22) * GROUND, '#ff2d78', 0.30)

    hw = 0.7 + 0.004 * DS
    uk = np.round(U)
    cos = 1 / np.sqrt(1 + uk * uk)
    long_px = np.abs(U - uk) * DS * cos
    a_long = np.clip(hw + 0.5 - long_px, 0, 1) * smooth(HZ + 4, HZ + 90, YY) \
        * smooth(5, 14, DS * cos)
    p = (Z + t * V) / 2.0
    tr_px = np.abs(p - np.round(p)) * 2.0 * DS * DS / K
    a_tr = np.clip(hw + 0.5 - tr_px, 0, 1) * smooth(6, 22, 2.0 * DS * DS / K)
    grid = np.maximum(a_long, a_tr) * GROUND
    k = smooth(HZ, H, YY)[..., None]
    col = hexc('#ff3ad0') * (1 - k) + hexc('#21e6ff') * k
    img = img + blur(grid, 4)[..., None] * col * 0.6 + blur(grid, 16)[..., None] * col * 0.35
    return img + grid[..., None] * col * 0.85


def warp(t):
    """Гиперпрыжок: звёзды тянутся лучами от точки схода."""
    rr = np.sqrt((XX - CX) ** 2 + ((YY - HZ) * 1.6) ** 2)
    k = smooth(0, 1000, rr)[..., None]
    img = hexc('#0e2148') * (1 - k) + hexc('#010208') * k
    n1, n2 = noise2(31, 64, 4), noise2(32, 96, 4)
    img = add(img, smooth(0.45, 0.85, n1), '#3a1a7a', 0.35)
    img = add(img, smooth(0.5, 0.9, n2), '#0a4a6a', 0.30)
    img = add(img, stars(33, 700, H, 0.8), '#cfe0ff', 0.6)

    rng = np.random.default_rng(34)
    n = 900
    th = rng.random(n) * 2 * np.pi
    rho = 0.2 + rng.random(n) ** 0.7 * 3.0
    ph = rng.random(n)
    tint = rng.random(n)
    F = 60.0

    def draw(d, s, which):
        for i in range(n):
            if (tint[i] < 0.18) != which:
                continue
            z = 0.04 + 6.0 * ((ph[i] - t) % 1.0)
            r0, r1 = F * rho[i] / (z + 0.35), F * rho[i] / z
            b = min(0.75, 0.55 / z)
            if r1 < 4 or b < 0.03:
                continue
            c, sn = np.cos(th[i]), np.sin(th[i]) * 0.62
            w = max(1, int((0.5 + 0.35 / z) * s))
            d.line([((CX + c * r0) * s, (HZ + sn * r0) * s),
                    ((CX + c * r1) * s, (HZ + sn * r1) * s)],
                   fill=int(255 * min(1, b)), width=w)
    white = mask(lambda d, s: draw(d, s, False))
    violet = mask(lambda d, s: draw(d, s, True))
    img = bloom(img, white, '#5aa0ff', 3, 14, 0.5, 0.35)
    img = bloom(img, violet, '#a060ff', 3, 14, 0.6, 0.4)
    img = add(img, white, '#e8f2ff', 0.7)
    img = add(img, violet, '#d9b8ff', 0.7)
    return add(img, np.exp(-(rr / 90) ** 2), '#9cc4ff', 0.25)


def aurora(t):
    """Северное сияние над тундрой: снежная дорога и столбики с катафотами."""
    img = vgrad([(0, '#010309'), (80, '#04101e'), (HZ, '#0b2232')])
    img = add(img, stars(41, 650, HZ, 1.0) * (1 - GROUND), '#e6f0ff', 0.9)

    x = np.arange(W, dtype=np.float32)
    cur = np.zeros((H, W, 3), np.float32)
    ribbons = ((0.0, 78, 22, 0.0042, 0.011, 1.0), (2.1, 56, 18, 0.0031, 0.017, 0.75),
               (4.0, 92, 14, 0.0055, 0.008, 0.55))
    for i, (p, yb0, amp, f1, f2, inten) in enumerate(ribbons):
        yb = yb0 + amp * np.sin(x * f1 + p + 2 * np.pi * t) + amp * 0.45 * np.sin(x * f2 + 2 * p)
        dy = yb[None, :] - YY
        curtain = np.where(dy < 0, np.exp(-(dy / 5) ** 2), np.exp(-dy / 42))
        rays = 0.35 + 0.65 * noise1(50 + i, W + 400, 7)[200:200 + W]
        rays *= 0.6 + 0.4 * noise1(60 + i, W + 400, 37)[200:200 + W]
        env = 0.4 + 0.6 * noise1(70 + i, W, 420)
        v = curtain * (rays * env * inten)[None, :]
        k = smooth(0, 70, dy)[..., None]
        cur += v[..., None] * (hexc('#2dff96') * (1 - k) + hexc('#8a5cff') * k)
    cur *= (1 - GROUND)[..., None]
    img = img + blur(cur, 1) * 0.55 + blur(cur, 18) * 0.35

    rng = np.random.default_rng(42)

    def ridge(hmax, hmin, step):
        pts, x0 = [(-10, HZ)], -10
        while x0 < W + 10:
            k = min(1, abs(x0 - CX) / 900)
            pts.append((x0, HZ - (hmin + (hmax - hmin) * k ** 1.5) * (0.4 + 0.6 * rng.random())))
            x0 += int(rng.integers(*step))
        return pts + [(W + 10, HZ)]
    far, near = ridge(48, 10, (25, 70)), ridge(30, 2, (40, 110))
    m_far = mask(lambda d, s: d.polygon([(a * s, b * s) for a, b in far], fill=255))
    m_near = mask(lambda d, s: d.polygon([(a * s, b * s) for a, b in near], fill=255))
    img = over(img, hexc('#0d1c2c'), m_far[..., None])
    img = over(img, hexc('#060c15'), m_near[..., None])

    snow = vgrad([(0, '#1a2c3e'), (HZ, '#1a2c3e'), (260, '#122030'), (H, '#0a121c')])
    snow = snow + (noise2(43, 3, 2) - 0.5)[..., None] * 0.05
    img = img * (1 - GROUND[..., None]) + snow * GROUND[..., None]
    img = add(img, np.exp(-D.clip(0) / 45) * GROUND, '#2dff96', 0.06)
    road = np.clip(1.8 * DS - np.abs(XX - CX) + 0.5, 0, 1) * GROUND
    img = over(img, hexc('#0a1018'), road[..., None])
    berm = np.clip(np.minimum(2.15 * DS - np.abs(XX - CX), np.abs(XX - CX) - 1.8 * DS) + 0.5,
                   0, 1) * GROUND
    img = add(img, berm, '#5a7088', 0.25)
    for u in (-0.95, 0.95):
        img = over(img, hexc('#070b11'), road_line(u, 0.16, near=2)[..., None] * 0.6)

    posts = moving(1.0, t, 0.9, 90, 44, 1)
    cols, alpha = rgba_layer(lambda d, s: [
        _post(d, s, z, side) for (z,) in posts for side in (-1, 1)])
    img = over(img, cols, alpha)
    refl = np.zeros((H, W), np.float32)
    for (z,) in posts:
        dd = K / z
        for side in (-1, 1):
            rx, ry, rad = CX + side * 2.45 * dd, HZ + dd - 0.30 * dd, 0.6 + dd * 0.008
            fade = smooth(18, 70, np.float32(dd)).item()
            refl += np.exp(-(((XX - rx) ** 2 + (YY - ry) ** 2) / (2 * rad * rad))) * fade
    img = bloom(img, refl, '#ff2a2a', 4, 14, 0.8, 0.4)
    return add(img, refl, '#ff6a5a', 0.9)


def _post(d, s, z, side):
    dd = K / z
    x = CX + side * 2.45 * dd
    w = max(0.5, 0.018 * dd)
    fog = min(1, z / 40)
    c = tuple(int(v) for v in (6 + 20 * fog, 10 + 32 * fog, 16 + 44 * fog))
    d.rectangle([(x - w) * s, (HZ + dd - 0.34 * dd) * s, (x + w) * s, (HZ + dd) * s], fill=c + (255,))


def forest(t):
    """Сумеречный лес: тёплая заря, луна, ели вдоль дороги."""
    img = vgrad([(0, '#071126'), (55, '#1b2a48'), (95, '#4a3a52'), (HZ, '#9a5236')])
    img = add(img, stars(51, 160, 60, 0.8) * (1 - GROUND), '#e8eeff', 0.7)
    img = add(img, np.exp(-((YY - HZ) / 18) ** 2) * np.exp(-((XX - CX) / 520) ** 2),
              '#ff9a50', 0.25)
    mx, my = 1500, 40
    disc = np.clip(15 - np.sqrt((XX - mx) ** 2 + (YY - my) ** 2) + 0.5, 0, 1)
    cut = np.clip(14 - np.sqrt((XX - mx - 7) ** 2 + (YY - my + 4) ** 2) + 0.5, 0, 1)
    moon = disc * (1 - cut)
    img = add(img, blur(disc, 18), '#a0b0d0', 0.25)
    img = add(img, moon, '#f2e8cc', 0.95)

    rng = np.random.default_rng(52)
    tops, x = [], -10
    while x < W + 10:
        h = 5 + 14 * rng.random() * (0.4 + 0.6 * min(1, abs(x - CX) / 500))
        w = 4 + 6 * rng.random()
        tops += [(x, HZ), (x + w / 2, HZ - h), (x + w, HZ)]
        x += w * (0.5 + 0.5 * rng.random())
    line = mask(lambda d, s: d.polygon([(a * s, b * s) for a, b in tops + [(W + 10, HZ + 2),
                                                                         (-10, HZ + 2)]],
                                       fill=255))
    img = over(img, hexc('#121a2c'), line[..., None])

    floor = vgrad([(0, '#0a0e14'), (HZ, '#141822'), (H, '#05070a')])
    img = img * (1 - GROUND[..., None]) + floor * GROUND[..., None]
    road = np.clip(1.6 * DS - np.abs(XX - CX) + 0.5, 0, 1) * GROUND
    asphalt = vgrad([(0, '#1a1c24'), (HZ, '#1a1c24'), (H, '#0c0d11')])
    img = img * (1 - road[..., None]) + asphalt * road[..., None]
    img = add(img, np.exp(-D.clip(0) / 40) * road, '#ff9a50', 0.20)
    if MARKINGS:
        lines = np.maximum.reduce([
            road_line(-0.8, 0.025, t, (0.5, 0.5)), road_line(0.8, 0.025, t, (0.5, 0.5)),
            road_line(-1.45, 0.02), road_line(1.45, 0.02)])
        img = over(img, hexc('#d8d2c4') * 0.75, lines[..., None])

    def extra(r):
        side = -1 if r.random() < 0.5 else 1
        return (side * (2.2 + 4.8 * r.random() ** 1.3), 1.3 + 1.3 * r.random(),
                0.3 + 0.12 * r.random(), r.integers(0, 1 << 30))
    trees = moving(4.0, t, 0.9, 90, 53, 30, extra)
    haze = hexc('#3a3448')
    cols, alpha = rgba_layer(lambda d, s: [_pine(d, s, *tr, haze) for tr in trees])
    return over(img, cols, alpha)


def _pine(d, s, z, u, h, wr, seed, haze):
    dd = K / z
    xb, yb = CX + u * dd, HZ + dd
    hp, wp = h * dd, h * dd * wr
    fog = smooth(3, 55, np.float32(z)).item()
    c = tuple(int(v) for v in (np.array([3, 4, 6]) * (1 - fog) + haze * 255 * fog * 0.75))
    r = np.random.default_rng(int(seed))
    n = 7
    left, right = [], []
    for k in range(n + 1):
        f = k / n
        y = yb - hp * 0.12 - hp * 0.88 * f
        half = wp * 0.5 * (1 - f) ** 1.1 * (0.85 + 0.3 * r.random())
        inner = half * 0.55
        left += [(xb - half, y), (xb - inner, y - hp * 0.88 / n * 0.35)]
        right += [(xb + half, y), (xb + inner, y - hp * 0.88 / n * 0.35)]
    pts = left + [(xb, yb - hp)] + right[::-1]
    d.polygon([(a * s, b * s) for a, b in pts], fill=c + (255,))
    tw = max(0.5, wp * 0.05)
    d.rectangle([(xb - tw) * s, (yb - hp * 0.14) * s, (xb + tw) * s, yb * s], fill=c + (255,))


SCENES = [
    ('01-night-highway', 'Ночная трасса', night_highway, '#4a8cff'),
    ('02-neon-horizon', 'Неоновый горизонт', neon_grid, '#21e6ff'),
    ('03-warp', 'Гиперпрыжок', warp, '#6a9cff'),
    ('04-aurora', 'Северное сияние', aurora, '#2dd890'),
    ('05-dusk-forest', 'Сумеречный лес', forest, '#e08a4a'),
]


# ---------------------------------------- схема штатного интерфейса (эскиз)

_FONTS = {}


def font(size, bold=True):
    key = (size, bold)
    if key not in _FONTS:
        path = '/System/Library/Fonts/HelveticaNeue.ttc'
        f = ImageFont.truetype(path, size)
        for i in range(16):
            try:
                c = ImageFont.truetype(path, size, index=i)
            except OSError:
                break
            if c.getname()[1] == ('Bold' if bold else 'Regular'):
                f = c
                break
        _FONTS[key] = f
    return _FONTS[key]


def draw_ui(d, s, brake=True):
    """Положение элементов снято с фото приборки (пересчёт в 1920×532)."""
    def S(*v):
        return [x * s for x in v]

    def text(x, y, txt, size, fill, bold=True, anchor='mm'):
        d.text((x * s, y * s), txt, font=font(size * s, bold), fill=fill, anchor=anchor)

    white, grey = (240, 242, 248, 255), (150, 152, 175, 255)
    green, red = (60, 220, 90, 255), (235, 50, 50, 255)

    text(CX, 70, '0', 112, white)
    text(1060, 82, 'km/h', 28, grey, False, 'lm')

    # Машина сверху.
    d.rounded_rectangle(S(915, 152, 1005, 404), radius=34 * s, fill=(226, 229, 236, 255))
    d.ellipse(S(902, 222, 922, 238), fill=(200, 204, 214, 255))
    d.ellipse(S(998, 222, 1018, 238), fill=(200, 204, 214, 255))
    d.rounded_rectangle(S(925, 205, 995, 380), radius=18 * s, fill=(70, 74, 88, 255))
    for i, (sx, sy) in enumerate(((933, 232), (965, 232), (933, 290), (958, 290), (976, 290))):
        d.rounded_rectangle(S(sx, sy, sx + 22 if i < 2 else sx + 16, sy + 34), radius=6 * s,
                            fill=(235, 70, 70, 255) if i == 0 else (175, 178, 190, 255))
    d.rounded_rectangle(S(930, 160, 990, 200), radius=14 * s, fill=(240, 242, 246, 255))

    # Контрольные лампы сверху слева.
    d.ellipse(S(390, 22, 402, 34), outline=red, width=3 * s)
    d.line(S(396, 34, 388, 66), fill=red, width=4 * s)
    d.line(S(384, 40, 410, 62), fill=red, width=3 * s)
    d.arc(S(470, 26, 506, 62), 270, 90, fill=green, width=4 * s)
    for k in range(3):
        d.line(S(458, 34 + k * 10, 476, 34 + k * 10), fill=green, width=3 * s)
    d.ellipse(S(552, 30, 584, 58), outline=green, width=3 * s)
    for k in range(3):
        d.line(S(538, 34 + k * 10, 550, 34 + k * 10), fill=green, width=3 * s)
        d.line(S(586, 34 + k * 10, 598, 34 + k * 10), fill=green, width=3 * s)

    # Ручник, обороты.
    if brake:
        d.rectangle(S(62, 358, 110, 402), fill=(0, 0, 0, 255))
        d.ellipse(S(70, 364, 102, 396), outline=red, width=3 * s)
        text(86, 380, 'P', 20, red)
    text(258, 432, '0.7', 52, white, anchor='lm')
    text(368, 446, 'x1000rpm', 16, (110, 100, 130, 255), False, 'lm')

    # Eco и колонка передач.
    text(1853, 80, 'Eco', 22, white)
    d.rectangle(S(1823, 132, 1884, 318), fill=(0, 0, 0, 255))
    for k, g in enumerate('RND'):
        text(1853, 162 + k * 58, g, 34, white, False)
    d.rounded_rectangle(S(1827, 324, 1879, 372), radius=8 * s, fill=(0, 0, 0, 255),
                        outline=white, width=3 * s)
    text(1853, 348, 'P', 32, white)

    # Маленькая машина справа.
    d.rounded_rectangle(S(1674, 404, 1736, 436), radius=8 * s, fill=(170, 172, 182, 255))
    d.polygon(S(1684, 404, 1692, 388, 1718, 388, 1726, 404), fill=(170, 172, 182, 255))

    # Нижняя панель с выступом по центру.
    rise = [0.5 - 0.5 * np.cos(np.pi * k / 12) for k in range(13)]
    band = ([(0, 468)] + [(630 + 60 * a, 468 - 34 * a) for a in rise]
            + [(1290 - 60 * a, 468 - 34 * a) for a in rise[::-1]]
            + [(W, 468), (W, H), (0, H)])
    d.polygon([(a * s, b * s) for a, b in band], fill=(9, 11, 28, 255))
    d.line([(a * s, b * s) for a, b in band[:-2]], fill=(70, 80, 140, 255), width=2 * s)
    text(34, 504, '17:55', 30, white, anchor='lm')
    d.rounded_rectangle(S(660, 486, 800, 494), radius=4 * s, fill=(80, 82, 96, 255))
    d.rounded_rectangle(S(660, 486, 672, 494), radius=4 * s, fill=(230, 80, 40, 255))
    text(668, 470, 'E', 16, white)
    text(792, 470, 'F', 16, white)
    d.rectangle(S(818, 466, 834, 494), fill=(240, 220, 40, 255))
    text(CX, 480, 'G-Pilot', 24, (230, 232, 240, 255), False)
    d.polygon(S(890, 480, 902, 472, 902, 488), fill=(230, 232, 240, 255))
    d.polygon(S(1030, 480, 1018, 472, 1018, 488), fill=(230, 232, 240, 255))
    d.rounded_rectangle(S(1130, 486, 1290, 494), radius=4 * s, fill=(80, 82, 96, 255))
    d.rounded_rectangle(S(1130, 486, 1220, 494), radius=4 * s, fill=(70, 170, 230, 255))
    text(1136, 470, 'C', 16, white)
    text(1282, 470, 'H', 16, white)
    d.rounded_rectangle(S(1545, 484, 1565, 520), radius=4 * s, outline=green, width=3 * s)
    d.ellipse(S(1600, 486, 1630, 516), outline=(200, 200, 210, 255), width=3 * s)
    text(1615, 501, 'A', 16, (200, 200, 210, 255))
    text(1880, 504, '10 °C', 26, white, anchor='rm')


UI = {}


def sketch(bg, brake=True):
    if brake not in UI:
        ui = Image.new('RGBA', (W * 2, H * 2), (0, 0, 0, 0))
        draw_ui(ImageDraw.Draw(ui), 2, brake)
        UI[brake] = ui.resize((W, H), Image.LANCZOS)
    out = bg.convert('RGBA')
    out.alpha_composite(UI[brake])
    return out.convert('RGB')


def to_image(a):
    return Image.fromarray((a * 255 + 0.5).astype(np.uint8), 'RGB')


def main():
    for name, title, fn, accent in SCENES:
        bg = to_image(finish(fn(0.0), accent))
        bg.save(os.path.join(OUT, f'{name}-bg.png'), optimize=True)
        sketch(bg).save(os.path.join(OUT, f'{name}-sketch.png'), optimize=True)
        print('ok', name, flush=True)


if __name__ == '__main__':
    main()
