#!/usr/bin/env python3
"""Compone un PDF (A4) con las capturas de PictoKeyboard y sus explicaciones.

El texto se genera como texto real (seleccionable); solo las capturas van
incrustadas como imagen. Requiere fpdf2.
"""
import os
from fpdf import FPDF

HERE = os.path.dirname(os.path.abspath(__file__))
IMG_DIR = os.path.join(HERE, "images")
OUT = os.path.join(HERE, "PictoKeyboard-guia-visual.pdf")

# A4 en mm
W, H = 210, 297
MARGIN = 16

TEAL  = (14, 124, 110)
INK   = (28, 32, 36)
GREY  = (120, 126, 132)
RULE  = (224, 228, 230)
WHITE = (255, 255, 255)

FB = "/usr/share/fonts/noto/NotoSans-Bold.ttf"
FR = "/usr/share/fonts/noto/NotoSans-Regular.ttf"

IMG_RATIO = 899 / 2048  # ancho/alto de las capturas

pdf = FPDF(orientation="P", unit="mm", format="A4")
pdf.set_auto_page_break(False)
pdf.add_font("Noto", "", FR)
pdf.add_font("Noto", "B", FB)
pdf.set_title("PictoKeyboard - Guía visual")
pdf.set_author("PictoKeyboard")


def para(x, w, text, size=11, font="", color=INK, lh=5.6, gap=2.0):
    pdf.set_left_margin(x)
    pdf.set_xy(x, pdf.get_y())
    pdf.set_font("Noto", font, size)
    pdf.set_text_color(*color)
    pdf.multi_cell(w, lh, text)
    pdf.set_left_margin(MARGIN)
    if gap:
        pdf.ln(gap)


def bullet(x, w, text, size=11, lh=5.6):
    y0 = pdf.get_y()
    pdf.set_fill_color(*TEAL)
    pdf.ellipse(x + 0.3, y0 + 1.7, 1.7, 1.7, style="F")
    para(x + 5, w - 5, text, size=size, lh=lh, gap=1.4)


def header(kicker, title):
    pdf.set_font("Noto", "B", 9)
    tw = pdf.get_string_width(kicker) + 7
    pdf.set_fill_color(*TEAL)
    pdf.rect(MARGIN, 15, tw, 7, style="F")
    pdf.set_text_color(*WHITE)
    pdf.set_xy(MARGIN, 15.4)
    pdf.cell(tw, 6.2, kicker, align="C")
    pdf.set_xy(MARGIN, 25)
    pdf.set_font("Noto", "B", 21)
    pdf.set_text_color(*INK)
    pdf.multi_cell(W - 2 * MARGIN, 9.5, title)
    y = pdf.get_y() + 2.5
    pdf.set_draw_color(*TEAL)
    pdf.set_line_width(0.7)
    pdf.line(MARGIN, y, W - MARGIN, y)
    return y + 6


def footer(n):
    pdf.set_draw_color(*RULE)
    pdf.set_line_width(0.3)
    pdf.line(MARGIN, 283, W - MARGIN, 283)
    pdf.set_font("Noto", "", 8)
    pdf.set_text_color(*GREY)
    pdf.set_xy(MARGIN, 285)
    pdf.cell(60, 5, "PictoKeyboard · Guía visual")
    pdf.set_xy(W - MARGIN - 20, 285)
    pdf.cell(20, 5, str(n), align="R")


def content_blocks(x, w, blocks):
    for kind, text in blocks:
        if kind == "b":
            bullet(x, w, text)
        elif kind == "gap":
            pdf.ln(2)
        else:
            para(x, w, text)


def img_page(n, kicker, title, blocks, image_file):
    pdf.add_page()
    y0 = header(kicker, title)
    img_w = 64
    img_x = W - MARGIN - img_w
    pdf.image(os.path.join(IMG_DIR, image_file), x=img_x, y=y0, w=img_w)
    img_h = img_w / IMG_RATIO
    pdf.set_draw_color(205, 210, 213)
    pdf.set_line_width(0.3)
    pdf.rect(img_x, y0, img_w, img_h)
    text_w = img_x - MARGIN - 7
    pdf.set_xy(MARGIN, y0)
    content_blocks(MARGIN, text_w, blocks)
    footer(n)


def img_page2(n, kicker, title, blocks, files):
    pdf.add_page()
    y0 = header(kicker, title)
    pdf.set_xy(MARGIN, y0)
    content_blocks(MARGIN, W - 2 * MARGIN, blocks)
    each_w = 70
    gap = 8
    img_h = each_w / IMG_RATIO
    total = each_w * 2 + gap
    x0 = (W - total) / 2
    y = pdf.get_y() + 4
    if y + img_h > 281:
        y = 281 - img_h
    for i, fn in enumerate(files):
        x = x0 + i * (each_w + gap)
        pdf.image(os.path.join(IMG_DIR, fn), x=x, y=y, w=each_w)
        pdf.set_draw_color(205, 210, 213)
        pdf.set_line_width(0.3)
        pdf.rect(x, y, each_w, img_h)
    footer(n)


# ------------------- PORTADA -------------------
pdf.add_page()
split = H * 0.60
pdf.set_fill_color(*TEAL)
pdf.rect(0, 0, W, split, style="F")
pdf.set_text_color(*WHITE)
pdf.set_xy(MARGIN, 62)
pdf.set_font("Noto", "B", 40)
pdf.cell(0, 18, "PictoKeyboard")
pdf.set_xy(MARGIN, 88)
pdf.set_font("Noto", "", 18)
pdf.cell(0, 9, "Guía visual de la aplicación")
pdf.set_draw_color(*WHITE)
pdf.set_line_width(1.2)
pdf.line(MARGIN, 102, MARGIN + 42, 102)

pdf.set_xy(MARGIN, split + 14)
para(MARGIN, W - 2 * MARGIN,
     "PictoKeyboard es un teclado para Android cuyas teclas son pictogramas "
     "(de ARASAAC). Al pulsar un picto, escribe su palabra en cualquier app y, "
     "además, la pronuncia en voz alta (texto a voz). Está pensado como "
     "herramienta de comunicación aumentativa: bilingüe español/inglés, con "
     "tablero y voz totalmente configurables.",
     size=12, lh=6.4, gap=4)
para(MARGIN, W - 2 * MARGIN,
     "Este documento recorre las pantallas de configuración y el teclado en "
     "uso, capturadas en un dispositivo real.",
     size=11, font="B", lh=6.0, gap=0)
pdf.set_xy(MARGIN, 280)
pdf.set_font("Noto", "", 8.5)
pdf.set_text_color(*GREY)
pdf.cell(0, 5, "Pictogramas: Sergio Palao / ARASAAC / Gobierno de Aragón "
                "(CC BY-NC-SA 4.0).")

# ------------------- PÁGINAS -------------------
img_page(2, "AJUSTES", "Configuración general",
    [("p", "Pantalla principal de Ajustes. Desde aquí se adapta el teclado a "
           "cada persona usuaria:"),
     ("b", "Idioma por defecto: Español o English (afecta al texto del picto y a la voz)."),
     ("b", "Columnas del grid (4) y filas visibles / alto (4): cuántos pictos se ven a la vez."),
     ("b", "Mostrar texto bajo los pictos: muestra la palabra debajo de cada imagen."),
     ("b", "Añadir un espacio tras cada palabra al escribir."),
     ("b", "Leer cada picto en voz alta: activa el texto a voz (TTS)."),
     ("b", "Velocidad (1.0x) y tono (1.0) de la voz, ajustables con deslizadores.")],
    "WhatsApp Image 2026-06-05 at 16.27.24 (1).jpeg")

img_page(3, "AJUSTES", "Modo ciego, PIN y copias",
    [("p", "Continuación de Ajustes, con las opciones avanzadas:"),
     ("b", "Modo ciego (teclado por gestos): teclado sin necesidad de vista, "
           "manejado con deslizamientos. Un doble toque con dos dedos lo activa "
           "o desactiva en cualquier momento."),
     ("b", "PIN de administrador: protege la configuración para que solo la "
           "persona cuidadora pueda cambiarla."),
     ("b", "Copia de seguridad: Exportar e Importar el tablero en formato JSON, "
           "para guardarlo o pasarlo a otro dispositivo.")],
    "WhatsApp Image 2026-06-05 at 16.27.24 (2).jpeg")

img_page(4, "TABLERO", "Lista de categorías",
    [("p", "El tablero se organiza en categorías. Vienen unas predefinidas: "
           "Personas, Acciones, Comida, Sentimientos, Lugares, Objetos y Tiempo, "
           "además de Suggested (sugeridos)."),
     ("b", "Cada categoría tiene su color de marco, siguiendo el código de "
           "colores habitual en comunicación aumentativa (AAC)."),
     ("b", "Iconos de lápiz (editar) y papelera (eliminar) por categoría, y "
           "acceso a sus Pictos."),
     ("b", "Reorder permite reordenarlas arrastrando."),
     ("b", "El botón + (abajo a la derecha) crea una categoría nueva.")],
    "WhatsApp Image 2026-06-05 at 16.27.25.jpeg")

img_page(5, "TABLERO", "Editar una categoría",
    [("p", "Diálogo para editar una categoría (aquí, «Suggested»):"),
     ("b", "Nombre de la categoría."),
     ("b", "Color del marco: paleta amplia de 26 colores."),
     ("b", "Estilo del marco: sólido, discontinuo o punteado."),
     ("b", "Grosor del marco: de fino a grueso."),
     ("p", "Así cada categoría queda visualmente distinta y reconocible de un "
           "vistazo.")],
    "WhatsApp Image 2026-06-05 at 16.27.24 (3).jpeg")

img_page(6, "TABLERO", "Crear una categoría nueva",
    [("p", "Al pulsar +, se elige un punto de partida (todo es editable después):"),
     ("b", "Sugeridos: tus palabras más usadas, en orden de uso."),
     ("b", "Categoría en blanco: empezar vacía y añadir tus propios pictos."),
     ("b", "Plantillas listas para usar: Bebidas (7 pictos), Animales (8 pictos), "
           "Ropa (7 pictos)…"),
     ("p", "Las plantillas dan un arranque rápido sin partir de cero.")],
    "WhatsApp Image 2026-06-05 at 16.40.09.jpeg")

img_page(7, "TABLERO", "Pictos de una categoría",
    [("p", "Contenido de la categoría «Suggested», con los pictos ordenados "
           "por frecuencia de uso:"),
     ("b", "yo, galleta, pan, comer, mañana, mamá y atardecer."),
     ("b", "«atardecer» es una imagen propia importada (no de ARASAAC); el "
           "resto son pictogramas ARASAAC."),
     ("b", "Cada picto lleva el marco verde de esta categoría."),
     ("p", "Reorder reordena los pictos y el botón + añade más.")],
    "WhatsApp Image 2026-06-05 at 16.27.26 (1).jpeg")

img_page(8, "TABLERO", "Añadir un picto con imagen propia",
    [("p", "Diálogo «New image picto» para crear un picto a partir de una foto "
           "o imagen del dispositivo (aquí, un atardecer):"),
     ("b", "Texto a escribir y pronunciar: lo que el picto inserta y dice."),
     ("b", "Caption: el texto que se muestra debajo del picto."),
     ("b", "Idioma del picto: Español o English."),
     ("b", "Color del marco para encuadrarlo."),
     ("p", "Permite personalizar el tablero con personas, objetos o lugares "
           "reales de la vida de cada usuario.")],
    "WhatsApp Image 2026-06-05 at 16.27.26.jpeg")

img_page(9, "EN USO", "El teclado dentro de otra app",
    [("p", "Aquí el teclado de pictos está abierto dentro de WhatsApp, igual "
           "que cualquier teclado del sistema:"),
     ("b", "A la izquierda, las categorías (Personas, Acciones, Comida…)."),
     ("b", "A la derecha, los pictos de Comida: agua, pan, leche, manzana, "
           "plátano, galleta, fruta, naranja, zumo, huevo, queso, arroz."),
     ("b", "En la barra de texto se ha compuesto la frase «yo comer galleta» "
           "pulsando pictos."),
     ("b", "El picto «galleta» se ha enviado al chat como imagen/sticker.")],
    "WhatsApp Image 2026-06-05 at 16.27.24.jpeg")

img_page(10, "EN USO", "Enviar un picto como sticker",
    [("p", "El picto propio «atardecer» enviado al chat como sticker, sin salir "
           "del teclado:"),
     ("b", "Mantener pulsado un picto lo envía como imagen (sticker en WhatsApp; "
           "imagen con su texto en otras apps)."),
     ("b", "En el teclado se ve la fila «Suggested» con los pictos más usados."),
     ("b", "Así se combina comunicación escrita, hablada y visual en la misma "
           "conversación.")],
    "WhatsApp Image 2026-06-05 at 16.27.26 (2).jpeg")

img_page2(11, "ACCESIBILIDAD", "Modo ciego: anuncio a pantalla completa",
    [("p", "El modo ciego convierte el teclado en una superficie azul manejada "
           "por gestos, sin necesidad de mirar la pantalla. Al recorrer el "
           "tablero, cada selección se muestra EN GRANDE y se lee en voz alta: "
           "arriba la categoría y, debajo, el picto actual."),
     ("p", "En estos dos ejemplos se anuncian «médico» (categoría Personas) y "
           "«Atardecer» (categoría Suggested). El gesto vertical cambia de "
           "categoría, el horizontal de picto, y el doble toque lo escribe.")],
    ["WhatsApp Image 2026-06-05 at 16.27.26 (4).jpeg",
     "WhatsApp Image 2026-06-05 at 16.27.26 (5).jpeg"])

pdf.output(OUT)
print("OK", OUT, pdf.page_no(), "páginas")
