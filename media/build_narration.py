#!/usr/bin/env python3
"""Genera una narración en español (edge-tts) y la mezcla sobre demo.mp4.

Salida: demo_narrado.mp4  (no modifica demo.mp4).
Requiere: edge-tts (pip), ffmpeg/ffprobe. Necesita internet para la TTS.
"""
import asyncio
import os
import subprocess
import edge_tts

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "demo.mp4")
OUT = os.path.join(REPO, "demo_narrado.mp4")
CLIPDIR = "/tmp/narr"
VOICE = "es-ES-ElviraNeural"
RATE = "+8%"

# (inicio_segundos, texto) — alineado con lo que se ve en pantalla
SEGMENTS = [
    (0.5,  "PictoTeclado es un teclado para Android pensado para comunicarse mediante pictogramas."),
    (6.5,  "Al abrir la aplicación, la pantalla de inicio guía la puesta en marcha en dos pasos: activar el teclado y seleccionarlo como teclado actual."),
    (16.0, "El tablero viene con siete categorías y más de cien pictogramas listos para usar."),
    (22.0, "Ahora abrimos WhatsApp. El teclado de pictogramas aparece igual que cualquier otro teclado del sistema."),
    (30.0, "A la izquierda están las categorías. Al pulsar un pictograma, su palabra se escribe en el mensaje y, además, se pronuncia en voz alta."),
    (40.0, "Así se compone una frase completa, como «yo comer pan»."),
    (45.0, "Y manteniendo pulsado un pictograma, se envía directamente como sticker en la conversación."),
    (51.0, "Volvemos a la aplicación para preparar el tablero. Desde Categorías creamos una nueva."),
    (58.0, "Elegimos un punto de partida: los pictogramas sugeridos, una categoría en blanco, o una plantilla como Bebidas, Animales o Ropa."),
    (67.0, "Dentro de cada categoría podemos añadir nuestros propios pictogramas."),
    (78.0, "Buscamos en el banco de pictogramas de ARASAAC; por ejemplo, «correr»."),
    (85.0, "Podemos seleccionar varios a la vez y añadirlos al tablero de una sola vez."),
    (93.0, "Cada pictograma es personalizable: su texto, su idioma y el color del marco."),
    (104.0, "Una vez añadidos, los pictogramas quedan disponibles al instante en el teclado."),
    (114.0, "De vuelta en el chat, ya se han enviado el mensaje «yo comer pan» y el pictograma de la manzana."),
    (123.0, "PictoTeclado: una forma sencilla y visual de dar voz a quien más lo necesita."),
]


def probe_dur(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "default=noprint_wrappers=1:nokey=1", path],
        capture_output=True, text=True).stdout.strip()
    return float(out)


async def gen_all():
    os.makedirs(CLIPDIR, exist_ok=True)
    paths = []
    for i, (_, text) in enumerate(SEGMENTS):
        p = os.path.join(CLIPDIR, f"seg_{i:02d}.mp3")
        await edge_tts.Communicate(text, VOICE, rate=RATE).save(p)
        paths.append(p)
    return paths


def main():
    vdur = probe_dur(SRC)
    paths = asyncio.run(gen_all())

    # comprobar solapes
    print(f"video: {vdur:.2f}s")
    ok = True
    for i, (start, _) in enumerate(SEGMENTS):
        d = probe_dur(paths[i])
        end = start + d
        nxt = SEGMENTS[i + 1][0] if i + 1 < len(SEGMENTS) else vdur
        flag = "" if end <= nxt + 0.05 else "  <-- SOLAPA"
        if flag:
            ok = False
        print(f"  seg {i:02d}  {start:6.2f}s  dur {d:4.2f}s  fin {end:6.2f}s  (sig {nxt:.2f}){flag}")
    if not ok:
        print("AVISO: hay segmentos que se solapan; ajusta tiempos o RATE.")

    # construir filtro: retardar cada clip y mezclar
    inputs = ["-i", SRC]
    for p in paths:
        inputs += ["-i", p]
    parts = []
    labels = []
    for i, (start, _) in enumerate(SEGMENTS):
        ms = int(round(start * 1000))
        parts.append(f"[{i+1}:a]adelay={ms}:all=1[d{i}]")
        labels.append(f"[d{i}]")
    mix = ("".join(labels) +
           f"amix=inputs={len(paths)}:normalize=0:dropout_transition=0[mx];"
           f"[mx]apad=whole_dur={vdur + 0.1:.3f}[aout]")
    filtergraph = ";".join(parts) + ";" + mix

    cmd = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", *inputs,
           "-filter_complex", filtergraph,
           "-map", "0:v", "-c:v", "copy",
           "-map", "[aout]", "-c:a", "aac", "-b:a", "160k",
           "-movflags", "+faststart", OUT]
    subprocess.run(cmd, check=True)
    print("OK", OUT, f"{probe_dur(OUT):.2f}s")


if __name__ == "__main__":
    main()
