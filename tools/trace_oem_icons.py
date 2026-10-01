"""Usage: python3 trace_oem_icons.py <hvac_default dir> app/src/main/res/drawable

Needs `pip install potracer numpy pillow`. Traces the OEM raster HVAC icons into two-layer VectorDrawables.

Bright parts stay white; parts drawn at ~30% alpha in the source (unlit levels, inactive
directions) become a second path with fillAlpha 0.302, like the Atlas icons, so the widget's
tint keeps them dim.
"""
import os
import sys

import numpy as np
import potrace
from PIL import Image

SRC = sys.argv[1]
OUT = sys.argv[2]
SCALE = 8
DIM_ALPHA = 0.302

# Source file per drawable, relative to the hvac_default folder. Levels go from off to full.
PLAN = {
    'ic_oem_auto': 'Кондиционер/uG.png',
    'ic_oem_ac': 'Кондиционер/bj.png',
    'ic_oem_ac_max': 'Кондиционер/seq_0_3.png',
    'ic_oem_eco': 'Кондиционер/uM.webp',
    'ic_oem_recirculation': 'Рецеркуляция/48.png',
    'ic_oem_windshield_heat': 'Электро обогрев/wZ.png',
    'ic_oem_front_defrost': 'Обогрев обдув лобового/G9.png',
    'ic_oem_rear_defrost': 'Обогрев заднего и зеркал/seq_0_1.png',
    'ic_oem_fan': 'Вентиляция водитель/l9.png',
    'ic_oem_blow_window': 'стекло ноги голова/8q.png',
    'ic_oem_blow_face': 'стекло ноги голова/yO.png',
    'ic_oem_blow_legs': 'стекло ноги голова/Bd.png',
    'ic_oem_rear_seat_left': 'Задний диван/DN1.png',
    'ic_oem_rear_seat_right': 'Задний диван/DN.png',
    'ic_oem_wheel_heat_0': 'Руль/pf.png',
    'ic_oem_wheel_heat_1': 'Руль/l-.png',
    'ic_oem_wheel_heat_2': 'Руль/Rn.png',
    'ic_oem_wheel_heat_3': 'Руль/seq_0_14.png',
    'ic_oem_seat_heat_driver_0': 'Обогрев водитель/BY.png',
    'ic_oem_seat_heat_driver_1': 'Обогрев водитель/9t.png',
    'ic_oem_seat_heat_driver_2': 'Обогрев водитель/n9.png',
    'ic_oem_seat_heat_driver_3': 'Обогрев водитель/nj_1.png',
    'ic_oem_seat_heat_passenger_0': 'Обогрев пассажир/Rn1.png',
    'ic_oem_seat_heat_passenger_1': 'Обогрев пассажир/pc.png',
    'ic_oem_seat_heat_passenger_2': 'Обогрев пассажир/oC_1.png',
    'ic_oem_seat_heat_passenger_3': 'Обогрев пассажир/_y.png',
    'ic_oem_seat_vent_driver_0': 'Вентиляция водитель/W4.png',
    'ic_oem_seat_vent_driver_1': 'Вентиляция водитель/Q8.png',
    'ic_oem_seat_vent_driver_2': 'Вентиляция водитель/dg.png',
    'ic_oem_seat_vent_driver_3': 'Вентиляция водитель/QB.png',
    'ic_oem_seat_vent_passenger_0': 'Вентиляция пассажир/Gz.png',
    'ic_oem_seat_vent_passenger_1': 'Вентиляция пассажир/ua.png',
    'ic_oem_seat_vent_passenger_2': 'Вентиляция пассажир/7Z.png',
    'ic_oem_seat_vent_passenger_3': 'Вентиляция пассажир/kD.png',
}



def fmt(v):
    s = ('%.2f' % v).rstrip('0').rstrip('.')
    return '0' if s == '-0' else s


def trace(alpha, offset, canvas):
    """alpha: float array 0..1 of one layer; returns path data in canvas units."""
    if alpha.max() < 0.5:
        return ''
    img = Image.fromarray((alpha * 255).astype(np.uint8))
    big = img.resize((img.width * SCALE, img.height * SCALE), Image.BICUBIC)
    # potracer traces the False pixels.
    bits = np.array(big) < 128
    path = potrace.Bitmap(bits).trace(turdsize=SCALE * SCALE // 2, alphamax=1.0,
                                      opticurve=True, opttolerance=0.2)
    ox, oy = offset

    def p(pt):
        return '%s,%s' % (fmt(pt.x / SCALE + ox), fmt(pt.y / SCALE + oy))

    parts = []
    for curve in path:
        parts.append('M ' + p(curve.start_point))
        for seg in curve:
            if seg.is_corner:
                parts.append('L ' + p(seg.c) + ' L ' + p(seg.end_point))
            else:
                parts.append('C ' + p(seg.c1) + ' ' + p(seg.c2) + ' ' + p(seg.end_point))
        parts.append('Z')
    return ' '.join(parts)


def load(path):
    image = Image.open(path).convert('RGBA')
    side = max(image.size)
    canvas = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    canvas.paste(image, ((side - image.width) // 2, (side - image.height) // 2))
    return np.array(canvas).astype(int), side


def layers(rgba):
    """Splits into (bright, dim): the sources draw unlit parts in #EBEBF5 at 30% alpha, so
    the bluish tint marks them pixel by pixel, even where they join a lit part."""
    alpha = rgba[..., 3] / 255.0
    dim_mask = (alpha > 0.02) & (rgba[..., 2] - rgba[..., 0] >= 6)
    bright = np.where(dim_mask, 0.0, alpha)
    dim = np.where(dim_mask, np.clip(alpha / DIM_ALPHA, 0, 1), 0.0)
    return bright, dim, None


def write(name, rgba, side):
    bright, dim, _ = layers(rgba)
    lines = ['<vector xmlns:android="http://schemas.android.com/apk/res/android" '
             'android:width="24dp" android:height="24dp" '
             'android:viewportWidth="%d" android:viewportHeight="%d">' % (side, side)]
    dim_path = trace(dim, (0, 0), side)
    bright_path = trace(bright, (0, 0), side)
    if dim_path:
        lines.append('    <path android:fillColor="#FFFFFF" android:fillAlpha="%s" '
                     'android:fillType="evenOdd" android:pathData="%s"/>'
                     % (DIM_ALPHA, dim_path))
    if bright_path:
        lines.append('    <path android:fillColor="#FFFFFF" android:fillType="evenOdd" '
                     'android:pathData="%s"/>' % bright_path)
    lines.append('</vector>')
    with open(os.path.join(OUT, name + '.xml'), 'w') as handle:
        handle.write('\n'.join(lines) + '\n')


if __name__ == '__main__':
    for name, path in PLAN.items():
        rgba, side = load(os.path.join(SRC, path))
        write(name, rgba, side)
