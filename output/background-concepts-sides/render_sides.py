#!/usr/bin/env python3
"""Фоны с симметричными чёрными полосами по бокам (ручник слева, R/N/D/P справа).

Вместо штатного контура со световой кромкой — сплошной чёрный SOLID px от края
и плавный переход ещё FADE px. Сцены c01–c05 перерисовываются генератором из
background-concepts-claude. У v01–v05 чёрные края запечены в исходник (сцена
x≈200…1720), поэтому они кадрируются (V_CROP) и увеличиваются без искажения
пропорций, затем затемняются той же маской.

Дорожной разметки на фонах нет: на приборке полосы рисует штатный ADAS, и
нарисованные на фоне легко принять за настоящие. У c01–c05 она выключена в
генераторе, v01 и v03 берутся из перегенерированных исходников *-nomarks.png
(промпты — background-concepts-v4/prompts.md, раздел «Без разметки»).

    uv run --with numpy --with pillow python render_sides.py
"""
import glob
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'background-concepts-claude'))
import render_backgrounds as rb  # noqa: E402

SOLID, FADE = 60, 90
W, H = rb.W, rb.H

x = np.arange(W, dtype=np.float32)
dist = np.minimum(x, W - 1 - x)
side = 1 - rb.smooth(SOLID, SOLID + FADE, dist)          # 1 — чёрное, 0 — сцена
V_CROP = (149, 0, 1771, 449)                              # 1622×449 → ×1.184

SIDES = np.broadcast_to(side[None, :], (H, W)).astype(np.float32)

# Подменяем штатные панели, убираем кромку и разметку.
rb.PANELS = SIDES
rb.EDGE = np.zeros((H, W), np.float32)
rb.MARKINGS = False

def save(name, bg):
    bg.save(os.path.join(HERE, f'{name}-bg.png'), optimize=True)
    rb.sketch(bg).save(os.path.join(HERE, f'{name}-sketch.png'), optimize=True)
    print('ok', name, flush=True)


def main():
    for name, _title, fn, accent in rb.SCENES:
        save('c' + name, rb.to_image(rb.finish(fn(0.0), accent)))
    keep = ((1 - 0.995 * SIDES) * (1 - 0.45 * rb.smooth(430, 500, rb.YY)))[..., None]
    sources = {}
    for path in sorted(glob.glob(os.path.join(HERE, '..', 'background-concepts-v4',
                                              '*-background-1920x532*.png'))):
        name = 'v' + os.path.basename(path).split('-background')[0]
        if name not in sources or path.endswith('-nomarks.png'):
            sources[name] = path                      # версия без разметки важнее
    for name, path in sorted(sources.items()):
        img = Image.open(path).convert('RGB').crop(V_CROP).resize((W, H), Image.LANCZOS)
        img = np.asarray(img, np.float32) / 255
        save(name, rb.to_image(np.clip(img * keep, 0, 1)))


if __name__ == '__main__':
    main()
