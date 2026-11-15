#!/usr/bin/env python3
"""Genera el icono adaptativo de Sazón a partir de docs/branding/sazon-icono-original.png.

Uso (desde la raíz del repositorio; necesita Python 3, Pillow y numpy):

    python3 scripts/branding/make-icon.py                      # escribe los recursos del icono
    python3 scripts/branding/make-icon.py --previews DIR       # y además vistas previas con máscaras en DIR

Escribe en app/src/main/res/:
  - mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_{background,foreground,monochrome}.png
    (108 dp: 108, 162, 216, 324 y 432 px);
  - mipmap-anydpi/ic_launcher.xml e ic_launcher_round.xml (icono adaptativo con las tres capas).

Cómo sale cada capa del original (1254×1254: un cuadrado redondeado sobre esquinas negras que NO son del icono):
  - Fondo: se dibuja de nuevo, de borde a borde, sin objetos. La ola es la curva que separa la crema del melocotón
    (medida en las columnas donde se ve; el tramo oculto tras la campana se une con una curva de Hermite) y cada zona
    lleva el degradado que se ajusta a sus píxeles. Las esquinas negras nunca se usan.
  - Primer plano: campana, bandeja y vapores recortados del original con bordes suaves (el alfa de cada borde se
    calcula frente al fondo que lo rodea; la sombra de la bandeja no se lleva). Su recuadro va al centro del lienzo y
    se escala para que ningún punto quede a más de 31,3 dp del centro (el tamaño del motivo en el original); después
    se comprueba a cada densidad que todo queda dentro de 32 dp (zona segura: círculo de 66 dp) y, si no, el script
    falla. Se generan y comprueban todas las densidades antes de escribir ninguna: un fallo no deja un juego mezclado.
  - Monocromo: la misma silueta en blanco sobre transparente (iconos temáticos de Android 13+).

El resultado es reproducible: no hay nada aleatorio y siempre se parte del mismo original. Los recursos del
repositorio se generaron con Python 3.13, Pillow 12.3.0 y numpy 2.5.3 (otras versiones pueden cambiar algún byte
de los PNG por el redondeo de los filtros, no el dibujo).
"""

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ORIGINAL = ROOT / "docs/branding/sazon-icono-original.png"
RES = ROOT / "app/src/main/res"

CANVAS_DP = 108.0  # lienzo del icono adaptativo
VIEWPORT_DP = 72.0  # lo que se ve tras la máscara del lanzador
SAFE_RADIUS_DP = 33.0  # zona segura: círculo de 66 dp
MAX_RADIUS_DP = 32.0  # margen: nada del primer plano más allá de este radio
FG_RADIUS_DP = 31.3  # radio del dibujo del primer plano (antes de suavizar los bordes a cada densidad)
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por scripts/branding/make-icon.py a partir de docs/branding/sazon-icono-original.png. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""


# ---------------------------------------------------------------- utilidades de máscaras (solo numpy)

def box_sum(a, r):
    """Suma de cada ventana (2r+1)×(2r+1), con ceros fuera de la imagen."""
    c = np.pad(a, ((r + 1, r), (r + 1, r))).cumsum(0).cumsum(1)
    return c[2 * r + 1:, 2 * r + 1:] - c[:-2 * r - 1, 2 * r + 1:] - c[2 * r + 1:, :-2 * r - 1] + c[:-2 * r - 1, :-2 * r - 1]


def dilate(mask, r):
    return box_sum(mask.astype(np.int32), r) > 0


def _spread_rows(reach, allowed):
    """Cada tramo horizontal de `allowed` que toca `reach` pasa a estar entero en `reach`."""
    h, w = allowed.shape
    starts = allowed & ~np.pad(allowed, ((0, 0), (1, 0)))[:, :w]
    ids = np.cumsum(starts.ravel()).reshape(h, w) * allowed
    hit = np.zeros(ids.max() + 1, bool)
    hit[ids[reach & allowed]] = True
    hit[0] = False
    return hit[ids]


def flood(seed, allowed):
    """Píxeles de `allowed` conectados (4 vecinos) con `seed`, por barridos de filas y columnas."""
    reach = seed & allowed
    while True:
        new = _spread_rows(reach, allowed)
        new = _spread_rows(new.T, allowed.T).T | new
        if (new == reach).all():
            return reach
        reach = new


def border(shape):
    b = np.zeros(shape, bool)
    b[0, :] = b[-1, :] = b[:, 0] = b[:, -1] = True
    return b


# ---------------------------------------------------------------- análisis del original

class Original:
    def __init__(self, path):
        self.rgb = np.asarray(Image.open(path).convert("RGB")).astype(np.float64)
        h, w, _ = self.rgb.shape
        self.h, self.w = h, w
        lum = self.rgb.mean(axis=2)
        self.lum = lum
        warm = self.rgb[..., 0] - self.rgb[..., 2]  # crema ≈ 5–15, melocotón ≈ 25–60, vapor > 120

        # Esquinas negras y su borde suavizado: lo oscuro conectado con el borde de la imagen. Nunca se usa.
        self.outside = flood(border((h, w)), lum < 200)
        rim = dilate(self.outside, 10)
        ys, xs = np.nonzero(~self.outside)
        self.box = (xs.min(), xs.max(), ys.min(), ys.max())  # el cuadrado redondeado
        self.cx = (xs.min() + xs.max()) / 2
        self.cy = (ys.min() + ys.max()) / 2
        # px del original por dp: el cuadrado redondeado ocupa la ventana visible de 72 dp.
        self.px_per_dp = ((xs.max() - xs.min()) + (ys.max() - ys.min())) / 2 / VIEWPORT_DP

        # Objetos: campana y bandeja (oscuras) y los tres vapores (naranjas), dentro del cuadrado.
        self.dark = (lum < 90) & ~self.outside
        self.flame = (warm > 120) & ~self.outside
        self.core = self.dark | self.flame
        # Huecos encerrados en un objeto (el brillo de la campana) son parte de él.
        self.interior = self.core | ~flood(border((h, w)), ~self.core)

        cream = (warm < 18) & (lum > 235)
        peach = (warm >= 22) & (warm <= 75) & (lum > 195)
        self.wave_x, self.wave_y = self._wave(cream, peach)
        if np.abs(np.diff(self.wave_at(np.arange(-w // 2, w * 3 // 2)))).max() > 3:
            sys.exit("La ola sale con saltos: revisa cómo se mide en el original.")

        # Degradados: píxeles lejos de los objetos (y de su sombra) y del borde.
        sample = ~dilate(self.core, 110) & ~rim
        yy, xx = np.mgrid[0:h, 0:w]
        boundary = self.wave_at(xx[0])[None, :]
        self.cream_fit = self._fit(sample & (yy < boundary - 4), xx, yy)
        self.peach_fit = self._fit(sample & (yy > boundary + 4), xx, yy)

    def _wave(self, cream, peach):
        """Puntos de la ola donde se ve: 6 px de crema encima y 6 px de melocotón debajo."""
        above = np.ones_like(cream)
        below = np.ones_like(peach)
        for k in range(1, 7):
            above &= np.roll(cream, k, axis=0)
            below &= np.roll(peach, -k, axis=0)
        hits = above & below & ~dilate(self.core, 8)
        xs, ys = [], []
        for x in range(self.w):
            col = np.nonzero(hits[:, x])[0]
            if len(col):
                xs.append(x)
                ys.append(col[0])
        xs, ys = np.array(xs, float), np.array(ys, float)
        # Fuera los sueltos (bordes de los vapores): cada punto debe seguir a sus vecinos.
        keep = np.array([
            (np.abs(xs - x) <= 8).sum() >= 10 and abs(y - np.median(ys[np.abs(xs - x) <= 8])) < 6
            for x, y in zip(xs, ys)
        ])
        xs, ys = xs[keep], ys[keep]
        if len(xs) < 200:
            sys.exit(f"No se encuentra la ola en el original ({len(xs)} puntos).")
        # Suaviza el ruido del original, cada tramo visible por separado (sin mezclar los dos lados de un hueco).
        pieces = np.split(np.arange(len(xs)), np.nonzero(np.diff(xs) > 20)[0] + 1)
        ys = np.concatenate([np.convolve(np.pad(ys[i], 4, mode="edge"), np.ones(9) / 9, mode="valid") for i in pieces])
        return xs, ys

    def wave_at(self, x):
        """Altura de la ola en x (px del original): medida, unida con Hermite donde no se ve y recta fuera."""
        xs, ys = self.wave_x, self.wave_y
        x = np.asarray(x, float)
        y = np.interp(x, xs, ys)

        def slope(sel):
            return np.polyfit(xs[sel], ys[sel], 1)[0]

        for i in np.nonzero(np.diff(xs) > 20)[0]:  # tramos ocultos
            x0, x1, y0, y1 = xs[i], xs[i + 1], ys[i], ys[i + 1]
            m0 = slope((xs <= x0) & (xs > x0 - 25))
            m1 = slope((xs >= x1) & (xs < x1 + 25))
            sel = (x > x0) & (x < x1)
            t = (x[sel] - x0) / (x1 - x0)
            d = x1 - x0
            y[sel] = ((2 * t**3 - 3 * t**2 + 1) * y0 + (t**3 - 2 * t**2 + t) * d * m0
                      + (-2 * t**3 + 3 * t**2) * y1 + (t**3 - t**2) * d * m1)
        left, right = xs[0], xs[-1]
        y = np.where(x < left, ys[0] + (x - left) * slope(xs < left + 30), y)
        y = np.where(x > right, ys[-1] + (x - right) * slope(xs > right - 30), y)
        return y

    def _uv(self, x, y):
        x0, x1, y0, y1 = self.box
        u = (np.asarray(x, float) - self.cx) / ((x1 - x0) / 2)
        v = (np.asarray(y, float) - self.cy) / ((y1 - y0) / 2)
        # Fuera del cuadrado del original el degradado no sigue: se prolonga su color.
        return np.clip(u, -1.05, 1.05), np.clip(v, -1.05, 1.05)

    @staticmethod
    def _terms(u, v):
        return np.stack([np.ones_like(u), u, v, u * u, u * v, v * v], axis=-1)

    def _fit(self, mask, xx, yy):
        u, v = self._uv(xx[mask], yy[mask])
        coef, *_ = np.linalg.lstsq(self._terms(u, v), self.rgb[mask], rcond=None)
        return coef

    def background_at(self, x, y):
        u, v = self._uv(x, y)
        terms = self._terms(u, v)
        below = (np.asarray(y) > self.wave_at(x))[..., None]
        return np.clip(np.where(below, terms @ self.peach_fit, terms @ self.cream_fit), 0, 255)

    def foreground(self):
        """Campana, bandeja y vapores en RGBA (float, alfa 0–1) al tamaño del original, con bordes suaves."""
        rgb, core, interior = self.rgb, self.core, self.interior
        band = dilate(core, 4) & ~interior
        # Fondo local de cada borde: media de los píxeles de fondo cercanos (incluye la sombra de la bandeja). Donde
        # casi no hay fondo a la vista (las muescas junto al pomo de la campana), el fondo dibujado.
        known = (~dilate(core, 6) & ~dilate(self.outside, 2)).astype(np.float64)
        weight = box_sum(known, 12)
        nearby = np.stack([box_sum(rgb[..., c] * known, 12) for c in range(3)], axis=-1) / np.maximum(weight, 1)[..., None]
        yy, xx = np.mgrid[0:self.h, 0:self.w]
        local_bg = np.where((weight >= 20)[..., None], nearby, self.background_at(xx, yy))
        # Color del objeto en el borde: el del objeto más cercano, propagado hacia fuera.
        color = rgb * core[..., None]
        have = core.copy()
        for _ in range(6):
            total = np.zeros_like(color)
            count = np.zeros(have.shape)
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    if dy or dx:
                        total += np.roll(color, (dy, dx), axis=(0, 1))
                        count += np.roll(have, (dy, dx), axis=(0, 1))
            grow = ~have & (count > 0)
            color[grow] = total[grow] / count[grow][:, None]
            have |= grow
        diff = color - local_bg
        alpha = ((rgb - local_bg) * diff).sum(-1) / np.maximum((diff * diff).sum(-1), 1e-6)
        alpha = np.where(band & have, np.clip(alpha, 0, 1), 0)
        alpha[interior] = 1
        out_rgb = np.where(interior[..., None], rgb, color)
        return np.dstack([out_rgb, alpha])


# ---------------------------------------------------------------- capas a cada densidad

def render_background(orig, size, supersample=4):
    n = size * supersample
    dp = (np.arange(n) + 0.5) * CANVAS_DP / n
    x = orig.cx + (dp - CANVAS_DP / 2) * orig.px_per_dp
    y = orig.cy + (dp - CANVAS_DP / 2) * orig.px_per_dp
    xx, yy = np.meshgrid(x, y)
    img = orig.background_at(xx, yy)
    img = img.reshape(size, supersample, size, supersample, 3).mean(axis=(1, 3))
    return Image.fromarray(np.round(img).astype(np.uint8))


def placement(orig, fg):
    """Centro (px del original) y escala del primer plano: su recuadro, en el centro del lienzo; todo a FG_RADIUS_DP."""
    ys, xs = np.nonzero(fg[..., 3] > 0)
    cx, cy = (xs.min() + xs.max() + 1) / 2, (ys.min() + ys.max() + 1) / 2
    radius = np.hypot(xs + 0.5 - cx, ys + 0.5 - cy).max() / orig.px_per_dp
    return cx, cy, min(1.0, FG_RADIUS_DP / radius)


def render_foreground(fg, orig, size, place):
    """Reduce el primer plano (premultiplicado, Lanczos) a `size` px, centrado y escalado según `place`."""
    cx, cy, scale = place
    half = CANVAS_DP / 2 * orig.px_per_dp / scale
    # El lienzo de 108 dp se sale del original: se rellena con transparente lo que haga falta.
    pad = int(np.ceil(max(0.0, half - min(cx, cy, orig.w - cx, orig.h - cy)))) + 2
    box = (cx - half + pad, cy - half + pad, cx + half + pad, cy + half + pad)
    alpha = fg[..., 3]
    channels = []
    for c in range(4):
        plane = fg[..., c] * alpha if c < 3 else alpha
        plane = np.pad(plane, pad).astype(np.float32)
        channels.append(np.asarray(Image.fromarray(plane).resize((size, size), Image.LANCZOS, box=box)))
    a = np.clip(channels[3], 0, 1)
    a = np.where(a < 4 / 255, 0, a)  # las colas del filtro (invisibles) no cuentan como dibujo
    rgb = np.clip(np.stack(channels[:3], -1) / np.maximum(a, 1e-6)[..., None], 0, 255)
    rgba = np.dstack([np.where(a[..., None] > 0, rgb, 0), a * 255])
    return Image.fromarray(np.round(rgba).astype(np.uint8))


def monochrome(foreground):
    a = np.asarray(foreground)[..., 3]
    white = np.full(a.shape + (3,), 255, np.uint8)
    return Image.fromarray(np.dstack([white * (a[..., None] > 0), a]).astype(np.uint8))


def max_radius_dp(image):
    a = np.asarray(image)[..., 3]
    size = a.shape[0]
    ys, xs = np.nonzero(a)
    d = np.hypot((xs + 0.5) * CANVAS_DP / size - CANVAS_DP / 2, (ys + 0.5) * CANVAS_DP / size - CANVAS_DP / 2)
    return d.max()


# ---------------------------------------------------------------- vistas previas (solo para revisar a ojo)

def mask_image(shape, size, k=4):
    n = size * k
    img = Image.new("L", (n, n), 0)
    draw = ImageDraw.Draw(img)
    if shape == "circulo":
        draw.ellipse((0, 0, n - 1, n - 1), fill=255)
    elif shape == "cuadrado-redondeado":
        draw.rounded_rectangle((0, 0, n - 1, n - 1), radius=n * 0.18, fill=255)
    elif shape == "lagrima":
        draw.ellipse((0, 0, n - 1, n - 1), fill=255)
        draw.rectangle((n // 2, n // 2, n - 1, n - 1), fill=255)
    elif shape == "squircle":
        t = (np.arange(n) + 0.5) / n * 2 - 1
        u, v = np.meshgrid(t, t)
        return Image.fromarray(((np.abs(u) ** 5 + np.abs(v) ** 5 <= 1) * 255).astype(np.uint8)).resize((size, size), Image.BOX)
    return img.resize((size, size), Image.BOX)


def viewport(layer, size):
    """La ventana visible de 72 dp, como la recorta el lanzador."""
    full = layer.size[0]
    inset = full * (CANVAS_DP - VIEWPORT_DP) / 2 / CANVAS_DP
    return layer.resize((size, size), Image.LANCZOS, box=(inset, inset, full - inset, full - inset))


def write_previews(outdir, background, foreground, mono, orig_path, size=192):
    outdir.mkdir(parents=True, exist_ok=True)
    icon = Image.alpha_composite(background.convert("RGBA"), foreground)
    shown = viewport(icon, size)
    safe = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    r = size * SAFE_RADIUS_DP / VIEWPORT_DP
    ImageDraw.Draw(safe).ellipse((size / 2 - r, size / 2 - r, size / 2 + r, size / 2 + r), outline=(0, 160, 255, 255))
    sheet = Image.new("RGB", (size * 4 + 50, size * 2 + 30), (120, 120, 120))
    tiles = []
    for i, shape in enumerate(["circulo", "squircle", "cuadrado-redondeado", "lagrima"]):
        tile = Image.new("RGBA", (size, size), (120, 120, 120, 255))
        tile.paste(shown, (0, 0), mask_image(shape, size))
        tile.convert("RGB").save(outdir / f"{shape}.png")
        tiles.append(tile)
    # Icono temático (Android 13+): el sistema tiñe el monocromo y pone un fondo de su paleta.
    themed = Image.new("RGBA", (size, size), (32, 40, 48, 255))
    tint = Image.new("RGBA", (size, size), (40, 56, 72, 255))
    mono_view = viewport(mono, size)
    glyph = Image.new("RGBA", (size, size), (214, 230, 246, 255))
    tint.paste(glyph, (0, 0), mono_view.getchannel("A"))
    themed.paste(tint, (0, 0), mask_image("circulo", size))
    themed.convert("RGB").save(outdir / "monocromo-oscuro.png")
    guide = Image.alpha_composite(shown, safe)
    guide.convert("RGB").save(outdir / "zona-segura.png")
    original = Image.open(orig_path).convert("RGB").resize((size, size), Image.LANCZOS)
    original.save(outdir / "original.png")
    for i, tile in enumerate(tiles):
        sheet.paste(tile.convert("RGB"), (10 + i * (size + 10), 10))
    for i, extra in enumerate([original, guide.convert("RGB"), themed.convert("RGB")]):
        sheet.paste(extra, (10 + i * (size + 10), size + 20))
    sheet.save(outdir / "todas.png")


# ---------------------------------------------------------------- principal

def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--previews", type=Path, help="carpeta para las vistas previas (fuera del repositorio)")
    args = parser.parse_args()

    orig = Original(ORIGINAL)
    fg = orig.foreground()
    place = placement(orig, fg)
    # Primero se generan y comprueban todas las densidades; solo si todas valen se escribe alguna.
    layers = {}
    for density, size in DENSITIES.items():
        background = render_background(orig, size)
        foreground = render_foreground(fg, orig, size, place)
        mono = monochrome(foreground)
        radius = max_radius_dp(foreground)
        if radius > MAX_RADIUS_DP:
            sys.exit(f"{density}: el primer plano llega a {radius:.2f} dp del centro (máximo {MAX_RADIUS_DP} dp). No se ha escrito nada.")
        if np.asarray(background).mean(axis=2).min() < 150:
            sys.exit(f"{density}: el fondo tiene píxeles oscuros: ¿se han colado las esquinas negras? No se ha escrito nada.")
        layers[density] = (background, foreground, mono, radius)

    for density, (background, foreground, mono, radius) in layers.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        background.save(folder / "ic_launcher_background.png", optimize=True)
        foreground.save(folder / "ic_launcher_foreground.png", optimize=True)
        mono.save(folder / "ic_launcher_monochrome.png", optimize=True)
        print(f"mipmap-{density}: {DENSITIES[density]} px, primer plano hasta {radius:.2f} dp del centro")
    background, foreground, mono, _ = layers[max(DENSITIES, key=DENSITIES.get)]
    largest = {"background": background, "foreground": foreground, "mono": mono}

    anydpi = RES / "mipmap-anydpi"
    anydpi.mkdir(parents=True, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (anydpi / name).write_text(ADAPTIVE_XML, encoding="utf-8")

    if args.previews:
        write_previews(args.previews, largest["background"], largest["foreground"], largest["mono"], ORIGINAL)
        print(f"Vistas previas en {args.previews}")


if __name__ == "__main__":
    main()
