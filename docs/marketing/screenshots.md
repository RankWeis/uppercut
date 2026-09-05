# Marketplace screenshots

Three images, each showing exactly one thing, all 1600x1000. You capture the
*region* that matters; `compose_screenshots.py` puts every capture on an
identical canvas with a caption, so they can't drift out of sync.

## Why not a full-window capture

A full IDE window is ~3000x1900. Fitted into the Marketplace carousel (~1000px
wide) every character drops to 4-5px and the image says nothing except "this is
an IDE" - which is what the current screenshot 2 does. Capture the panel the
feature lives in and nothing else. A gutter icon deserves the editor gutter plus
20-30 lines of code beside it, not the toolbar, the project tree, the status bar
and the macOS menu bar.

Rule of thumb: if a reader can't read the code at a glance, the capture is too
wide. Aim for a region roughly 16:10 - the canvas is that shape, so a 3:1
ultrawide capture will sit in a band with dead space above and below it.

## IDE setup (do this once, keep it for all five)

- **Theme:** Dark (Darcula). Pick one and never mix - two themes across five
  images looks like two different products.
- **Editor font:** Settings > Editor > Font > Size **18**, line height 1.2.
  This is the single biggest legibility win.
- **Hide chrome:** close the Project tool window (Cmd+1), close irrelevant
  editor tabs, collapse anything you aren't demonstrating.
- **Clean the sample:** no `TODO`, no `Feature: Test`, no `google.com`. Use a
  realistic feature - an auth header, a POST with a JSON body, a `match` against
  a nested response. It should look like the reader's own test suite.
- **Check for private data** before every capture: project name, absolute paths,
  hostnames, tokens, your username in the title bar.

## Capturing on macOS

- `Cmd+Shift+4`, then drag the region. Hold **Space** mid-drag to reposition the
  selection, **Esc** to cancel.
- On a Retina display the file is 2x what you dragged, so a ~800x500 drag lands
  at ~1600x1000 - already the right size.
- Turn off the drop shadow if you ever capture a window
  (`Cmd+Shift+4` then `Space`):
  `defaults write com.apple.screencapture disable-shadow -bool true` then
  `killall SystemUIServer`.
- `Cmd+Shift+5` > Options > uncheck **Show Floating Thumbnail** so the preview
  doesn't land in the next capture.

## The images

Name the files with a leading number - it sets the carousel order.

| File | What to capture |
| --- | --- |
| `01-run-from-gutter.png` | Editor with the gutter run arrow on a scenario, plus the run window below showing the test tree with steps expanded. The money shot: it's the main reason people install. |
| `02-go-to-definition.png` | Split Down: `orders.feature` on top with the caret on `auth.token`, `auth.feature` below with the `* def token = ...` line it resolves to. Resolving a variable across a `call` boundary is the part nothing else in the ecosystem does - a plain path jump is much weaker. No breakpoints in the gutter, and end both panes on whole lines. |
| `03-karate-completion.png` | `* karate.` on a step line with the completion popup open, listing the whole `karate.*` API. The list is matched to the Karate major version on the module's classpath. Keep the code above the popup fully visible and let the list drop into empty space below. |

## Composing

1. Drop the raw captures in `docs/marketing/raw/`.
2. Optionally add `docs/marketing/raw/captions.tsv` with `filename<TAB>caption`
   lines. Without it the caption is derived from the filename
   (`01-run-from-gutter.png` -> "Run from gutter"), which is usually worse -
   write real captions, they're the only words on the image.
3. `python3 docs/marketing/compose_screenshots.py`

   The script needs [Pillow](https://pypi.org/project/Pillow/). macOS system python doesn't have it, and
   `pip install pillow` there usually fails with `externally-managed-environment`, so use a venv:

   ```
   python3 -m venv ~/.venvs/marketing && ~/.venvs/marketing/bin/pip install pillow
   ~/.venvs/marketing/bin/python docs/marketing/compose_screenshots.py
   ```

4. Results land in `docs/marketing/out/`, all exactly 1600x1000. Upload those to
   the Marketplace listing, in filename order.

The script never upscales a capture - a blown-up screenshot reads as blurry - so
it warns instead and tells you to recapture larger.

The debugger and Karate 2 are deliberately not shown: debugging is experimental and does not pause a Karate 2 run at all, and a screenshot cannot carry that caveat. A formatting before/after is the obvious fourth if one is ever wanted - stable, easy to stage, and nothing to overstate.

## Also worth doing

A 60-second demo video. The Marketplace embeds a YouTube link above the
screenshots and almost nothing in this niche has one. No narration needed: open
a feature file, click the gutter, tests run, click a failing step, jump to the
source. It will convert better than all five screenshots together.
