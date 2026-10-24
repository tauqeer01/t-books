# Reader annotation: tool dial and shared pens

Date: 2026-10-24 · Status: proposed

## Why

PDF annotation in the reader still draws with its own single pen whose default width (3.5 in reader units) is about 1.8 mm on an A4 page. Notes was just changed to fine pens (0.3 mm default, down to 0.1 mm) drawn as real pens. The two places should feel like one app: the same pens, the same thickness, and a tool picker that is quick with one thumb and doesn't cover the page.

The current reader tool strip is a tall vertical column on the right edge that covers the page (see the screenshot that prompted this: the strip runs most of the screen height beside the text).

## What we're building

### 1. Shared pens

- The reader uses **the same saved pens as Notes** (`NotePenPreferences`: presets, and the active pen index). Changing, adding or removing a pen in either place changes it in both.
- Thickness, color and pen type come from the active pen. The reader's own pen color / pen width preferences (`UserReadingPreferences.penColor`, `penWidth`) stop being used for new ink. Their controls are removed from Reading Preferences. The stored values are left alone (no migration needed).
- **Width conversion.** Notes sizes are in 1/1000 of the page width; reader `DrawingStroke.strokeWidth` is in 1/400 of the page width (`DrawingComponents` renders `strokeWidth * pageW / 400`, and the exporter uses the same scale). New reader strokes store `strokeWidth = notesSize * 0.4`, divided by 2.2 for highlighters because the reader renders highlighters at 2.2× width. This makes a 0.3 mm Notes pen draw 0.3 mm on a PDF page too.
- **Pen types on PDFs (decided with the user: plain lines are fine for now).** The reader's renderer draws smooth constant-width lines. A Pencil draws at reduced opacity (as in Notes). A Highlighter uses the existing `isHighlighter` path. Ballpoint, Fountain, Marker and Dashed draw as solid lines in their color and thickness. Fountain-pen pressure and the dashed pattern are Notes-only for now.
- **Existing annotations are untouched.** They keep their stored widths and colors and render exactly as before.

### 2. The tool dial (decided with the user)

- Tapping the annotate button opens a **half-circle dial on the right edge, vertically centered**. The flat side is against the screen edge and the round side faces the page.
- Tools sit around the arc. You **spin the dial by dragging up or down along it**; tools roll past a **highlighted slot in the middle** (the 9 o'clock position, pointing at the page). The slot shows the tool's name beside it.
- **Picking a tool:** spinning a tool into the middle slot and lifting selects it. Tapping any visible tool selects it directly.
- **After picking, the dial shrinks into a small bar** on the right edge so the page is free. The bar shows the current tool (for a pen, its pen drawing in its color), plus **Undo, Redo and Done (✓)**. Tapping the current-tool button reopens the dial with that tool in the middle slot.
- **Done** leaves annotation mode, as today.

### 3. What's on the dial, in order

1. Scroll (hand)
2. Select text
3. Clip to note
4. **Each saved pen as its own slot**, drawn with `PenGlyph` exactly as in Notes (includes highlighter pens)
5. Text marker (highlight / underline / strikethrough)
6. Shapes (line / arrow / rectangle / ellipse)
7. Sticky note
8. Eraser
9. Add pen (+), which adds a pen exactly as Notes' "Add pen" does

Selecting the **already-selected** pen opens the same pen editor as Notes (`PenEditor`: type, color, thickness in mm, Remove pen). Selecting the already-selected Text marker or Shapes opens its sub-choices (the existing style flyouts), and for Text marker and Sticky note a color choice using the existing palette.

### 4. Moved controls

- **Clear page drawings** and **Stylus only** move off the main surface into a small overflow (⋯) on the collapsed bar.
- The separate Color and Thickness rail buttons go away. Pens carry their own color and thickness; Text marker and Sticky note color live in their sub-choices.

### 5. Details settled during design review (my calls)

- The annotate button stays where it is today (bottom-right, clear of the page edges). Tapping it enters annotation mode **with the active shared pen selected** and opens the dial on the right edge, middle.
- Picking a highlighter pen sets the reader's highlighter tool; picking any other pen sets the pen tool. Both take color and width from the pen.
- Text marker and Sticky note keep their own color (the reader's existing drawing color state), separate from pens.
- Reader "Stylus only" stays separate from Notes' "Draw with finger" setting.
- When the notes panel is open beside the book, the dial and bar sit on the right edge of the reader pane, not the whole screen.

## Goals

- Ink in PDFs matches Notes in thickness and pen choice.
- Picking a tool takes one drag or one tap, with one thumb, without leaving the page.
- The page stays visible: the collapsed bar is small, and the dial only shows while choosing.

## Non-goals

- Rendering PDF ink with the Notes ink engine (pressure, textures, dashed lines). That's a later change.
- Changing how existing annotations are stored or migrating them.
- Changing Notes' own toolbars.

## Anti-goals (would be a failure even if it works)

- Existing highlights, drawings or exports change appearance.
- The dial or bar covers the tap-to-turn page edges or blocks writing while collapsed.
- Pens drift apart between Notes and the reader.
- Spinning feels sticky or imprecise: it must follow the finger, fling and snap to a slot.

## Constraints

- Kotlin, Jetpack Compose, existing reader architecture (`ReaderViewModel`, `ReaderUiState`, `DrawingComponents.kt`).
- Keep the test tags existing tests use where the control still exists: `annotation_rail_button`, `drawing_toolbar` (the dial or bar container), `drawing_undo_button`, `drawing_redo_button`, `drawing_done_button`, `tool_hand`, `tool_select_text`, `tool_clip`, `tool_text`, `tool_shapes`, `tool_sticky_note`, `tool_eraser`. Pen slots are tagged `tool_pen_<index>`.
- Accessibility: every slot is a button with a content description ("Pen 1", "Eraser"…) and a selected state, so TalkBack users can pick tools without spinning.
- Works in portrait and landscape, phone and tablet; light and dark themes.

## Left to the builder

- Dial geometry: radius, how many slots show at once, spacing, slot size (≥ 48 dp touch targets), snap and fling physics, and the open/collapse animation.
- Exact look of the collapsed bar and the middle-slot highlight, within the app's existing style.
- How the reader observes shared pen changes (re-read prefs on resume or a shared flow).
- Updating tests that assert the old reader pen color/width preferences (e.g. `pinchDrawingAndUndoRedoCoexist` expects `#EF4444` / `8f` from Reading Preferences) to the shared-pen behavior.
