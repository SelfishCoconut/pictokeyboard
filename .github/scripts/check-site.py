#!/usr/bin/env python3
"""Everything that must be true of site/ before it is published.

These pages are the privacy policy and the account-deletion route: the two
documents Play checks, and the only way to delete an account without the app.
They are also plain HTML with no framework, which means nothing else would ever
notice if one of these went wrong -- there is no compiler, no type checker, and
no test that loads them.

Run locally with:  python3 .github/scripts/check-site.py

Each check exists because of a specific way this could fail quietly:

  links     A dead link in a privacy policy is a compliance problem, and a dead
            link between languages strands somebody on a page they cannot read.
  messages  The deletion page's logic is shared by every language and reads its
            wording from `window.PK_MESSAGES`. A key defined in English but not
            in Spanish does not render as a blank -- it renders as the word
            "undefined", on the page someone is using to delete their account.
  structure Unlabelled inputs and skipped heading levels are invisible to
            someone reading the page and decisive for someone using a screen
            reader. This app exists for people who rely on assistive technology.
  contrast  The palette is hand-written and used in both light and dark themes,
            so a colour can pass in one and fail in the other.
"""

import re
import sys
from pathlib import Path

SITE = Path('site')
MODULE = SITE / 'assets' / 'delete-account.js'
STYLESHEET = SITE / 'assets' / 'style.css'

problems: list[str] = []


def report(check: str, page, message: str) -> None:
    problems.append(f'{check}: {page}: {message}')


def pages():
    return sorted(SITE.rglob('*.html'))


def check_links() -> None:
    for page in pages():
        for href in re.findall(r'(?:href|src)="([^"#]+)"', page.read_text()):
            if href.startswith(('http://', 'https://', 'mailto:', 'data:')):
                continue
            target = (page.parent / href).resolve()
            if not (target.exists() or (target / 'index.html').exists()):
                report('links', page, f'broken link {href!r}')


def check_messages() -> None:
    used = set(re.findall(r'\btext\.(\w+)', MODULE.read_text()))
    if not used:
        report('messages', MODULE, 'no messages found -- has the module changed shape?')
        return
    for page in pages():
        block = re.search(r'window\.PK_MESSAGES\s*=\s*\{(.*?)\n\s*\}', page.read_text(), re.S)
        if not block:
            continue
        # Keys only. A colon inside a translated sentence is not a key.
        defined = set(re.findall(r'^\s*(\w+)\s*:', block.group(1), re.M))
        for missing in sorted(used - defined):
            report('messages', page, f'missing message {missing!r}')


def check_structure() -> None:
    for page in pages():
        text = page.read_text()

        if not re.search(r'<html lang="(en|es)"', text):
            report('structure', page, 'no lang on <html>')
        if '<title>' not in text:
            report('structure', page, 'no <title>')

        ids = set(re.findall(r'<input[^>]*\bid="([^"]+)"', text))
        labelled = set(re.findall(r'<label[^>]*\bfor="([^"]+)"', text))
        for missing in sorted(ids - labelled):
            report('structure', page, f'input #{missing} has no <label for>')

        levels = [int(h) for h in re.findall(r'<h([1-6])[ >]', text)]
        if levels and levels[0] != 1:
            report('structure', page, f'first heading is h{levels[0]}, not h1')
        for previous, current in zip(levels, levels[1:]):
            if current > previous + 1:
                report('structure', page, f'heading jumps h{previous} to h{current}')

        for button in re.findall(r'<button[^>]*>(.*?)</button>', text, re.S):
            if not re.sub(r'<[^>]+>', '', button).strip():
                report('structure', page, 'button with no text')

        if 'id="status"' in text and 'aria-live' not in text:
            report('structure', page, 'status box is not a live region')


def relative_luminance(colour: str) -> float:
    value = colour.lstrip('#')
    if len(value) == 3:
        value = ''.join(c * 2 for c in value)

    def channel(pair: str) -> float:
        v = int(pair, 16) / 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4

    r, g, b = channel(value[0:2]), channel(value[2:4]), channel(value[4:6])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a: str, b: str) -> float:
    la, lb = relative_luminance(a), relative_luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


# WCAG AA for body text. Everything here is body-sized or a control label, so
# the 3.0 large-text allowance deliberately is not used.
AA = 4.5

# (foreground var, background var, what it is)
COMBINATIONS = [
    ('fg', 'bg', 'body text'),
    ('muted', 'bg', 'muted text'),
    ('muted', 'card', 'muted text on a card'),
    ('accent', 'bg', 'links'),
    ('danger', 'bg', 'the delete button'),
    ('danger', 'card', 'the delete button on a card'),
    ('ok', 'bg', 'the success message'),
    ('ok', 'card', 'the success message on a card'),
]


def check_contrast() -> None:
    css = STYLESHEET.read_text()
    blocks = re.findall(r':root[^{]*\{(.*?)\}', css, re.S)
    if len(blocks) < 2:
        report('contrast', STYLESHEET, 'expected a light and a dark :root block')
        return

    themes = {
        'light': dict(re.findall(r'--([\w-]+):\s*(#[0-9a-fA-F]{3,6})', blocks[0])),
        'dark': dict(re.findall(r'--([\w-]+):\s*(#[0-9a-fA-F]{3,6})', blocks[1])),
    }

    for name, theme in themes.items():
        for fg, bg, what in COMBINATIONS:
            if fg not in theme or bg not in theme:
                report('contrast', STYLESHEET, f'{name}: missing --{fg} or --{bg}')
                continue
            ratio = contrast(theme[fg], theme[bg])
            if ratio < AA:
                report('contrast', STYLESHEET,
                       f'{name}: {what} is {ratio:.2f}:1, below {AA}:1')

        # The primary button inverts its text colour per theme.
        on_primary = '#ffffff' if name == 'light' else '#0b1220'
        ratio = contrast(on_primary, theme['accent'])
        if ratio < AA:
            report('contrast', STYLESHEET,
                   f'{name}: primary button text is {ratio:.2f}:1, below {AA}:1')


def main() -> int:
    if not SITE.is_dir():
        print('site/ not found -- run this from the repository root', file=sys.stderr)
        return 2

    for check in (check_links, check_messages, check_structure, check_contrast):
        check()

    if problems:
        print(f'{len(problems)} problem(s) in site/:')
        for problem in problems:
            print(f'  - {problem}')
        return 1

    print(f'site/ is clean: {len(pages())} pages checked '
          '(links, messages, structure, contrast)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
