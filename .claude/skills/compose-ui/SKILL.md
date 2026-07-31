---
name: compose-ui
description: Build Jetpack Compose UI to a consistent, accessible, Material 3 standard — layout, theming, state, and performance conventions. Use when writing or reviewing Composables, adding a screen, or restyling the app.
---

# Compose UI conventions

Design decisions here are accessibility decisions. Size, contrast, and spacing are not taste — they determine whether the app is usable by someone with low vision, a tremor, or a switch device. Build to the accessible standard first; it is cheaper than retrofitting, and the `a11y-reviewer` agent will find the gap anyway.

## Structure

- **Stateless Composables.** State hoisted to the caller or a ViewModel; a Composable takes data and lambdas, returns Unit, and owns nothing it does not draw. This is what makes previews and tests possible.
- Every Composable takes `modifier: Modifier = Modifier` as its **first optional parameter** and applies it to the root — the universal Compose convention; violating it makes a component uncomposable.
- Collect state with `collectAsStateWithLifecycle()`, not `collectAsState()`, so collection stops when the UI is not visible.
- One screen = one Composable that wires ViewModel to a stateless content Composable. The stateless one gets the `@Preview`s.
- No business logic, I/O, or `Context` reaching into a Composable.

## Theming

- All colors from `MaterialTheme.colorScheme`, all type from `MaterialTheme.typography`, all shapes from `MaterialTheme.shapes`. **No hardcoded `Color(0xFF...)` in a Composable** — it will be wrong in dark mode and invisible to a theme change.
- Define the palette once, verify contrast there (4.5:1 body text, 3:1 large text and UI affordances), in both light and dark schemes.
- Spacing from a small named scale (4/8/12/16/24/32dp), not ad-hoc numbers. Inconsistent spacing reads as "unfinished" more than any other single thing.

## Sizing and touch

- Interactive elements: **minimum 48dp touch target**, via `Modifier.minimumInteractiveComponentSize()` or explicit padding. The icon may be 24dp; the target may not.
- Text in **sp**; everything else in **dp**. Never a text size in dp.
- Layouts must survive a 200% font scale: no fixed heights around text, no `maxLines = 1` on content the user must read. Add a `@Preview(fontScale = 2f)` to any screen with substantial text.
- Design for the smallest supported screen first; a grid that works at 320dp works everywhere.

## Accessibility (non-negotiable)

- `contentDescription` on every meaningful icon and image; explicit `null` on decorative ones.
- Use `Modifier.toggleable`/`selectable`/`Role` for state-bearing controls rather than a bare `clickable` — that is what tells a screen reader it is a switch, not a button.
- `Modifier.semantics(mergeDescendants = true)` on groups that should read as one item.
- Never encode meaning in color alone; pair it with an icon, label, or shape.
- Provide a discrete alternative for every gesture-only action.

## Performance

- Keep state reads low in the tree — read the value where it is drawn, not in a parent that then recomposes wholesale. Prefer lambda-based reads for frequently-changing values.
- `remember` for expensive computation; `derivedStateOf` for state computed from other state.
- Parameters should be **stable**; an unstable type (a raw `List`, a class with `var` fields) forces recomposition. Prefer `ImmutableList`/persistent collections or immutable data classes.
- In `LazyColumn`/`LazyRow`/`LazyVerticalGrid`, always supply a stable `key`, and never allocate or decode inside an item body.
- Constrain image loads to their display size.

## Previews

Every non-trivial Composable gets `@Preview`s covering: default state, empty state, error state, dark theme, and `fontScale = 2f`. Previews are the cheapest regression test Compose offers and they cost nothing at runtime.

## Review checklist

Before calling UI work done: themed colors only, 48dp targets, sp text, labels present, previews render in light and dark at 2x font scale, no logic in the Composable, stable parameters, keys on lazy items.
