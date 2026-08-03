# BetterWorkspace – JOSM Plugin

Workspace tweaks for JOSM. All menu-driven features live under **More tools → BetterWorkspace**
(each item is also a separate, shortcut-bindable, toolbar-registerable action — assign a key or add
it to your toolbar via JOSM's own Preferences → Shortcuts / toolbar customization), in this order:
- Arrange the docked side panels, remembered across restarts.
- Load a HOT Tasking Manager project's task grid as a data layer — including **private and draft**
  projects you have access to, not just public ones — via your personal TM API token.
- Set/update that HOT TM API token.
- Toggle the visibility of the currently active layer.
- Multi-validation prep — select all ways in the layer below the active one and add them to the
  todo plugin's list, for paging through task borders during a second/third mapping pass.
- Quick TMS — preview a TMS imagery layer for this session only, without touching your saved
  imagery list (optionally pin it permanently instead).
- Open/close a secondary, view-only map window that always tracks the main view's position and
  zoom, with its own independent set of which layers are shown.
- Rotate the whole map view (data + imagery) clockwise/counter-clockwise, or reset it.

**Note on "More tools":** this top-level menu isn't part of JOSM core's own default UI — it starts
out empty and hidden, and normally only appears if another plugin (like `utilsplugin2` or
`buildings_tools`) populates it. BetterWorkspace makes it visible itself, so its submenu shows up
whether or not those other plugins are installed.

Separately, it also adds a **"Select objects"** entry to the right-click menu of JOSM's built-in
**Authors** panel (which otherwise only offers "Copy"). This hooks into JOSM internals with no
public extension point, so it's inherently version-fragile — if a future JOSM release changes that
panel internally, the hook just fails silently and the panel goes back to only offering "Copy".

## HOT Tasking Manager task grid loading (private/draft projects)

JOSM's built-in **File → Open Location** can load a public TM project's task grid, but the TM API
returns HTTP 403 for private or draft projects even if you have access, since Open Location has no
way to send an `Authorization` header.

**Set HOT TM API Token...** and **Load HOT TM Task Grid...** work around that:

- Get your personal token from the TM website: **tasks.hotosm.org → Settings → enable "Expert
  mode" → API Key** card. Pasting the copied "Token xxx" text or just the token itself both work.
  It's stored via JOSM's own preferences, the same mechanism JOSM uses for its own OSM OAuth token.
- The token expires roughly 7 days after your last TM login — re-copy it periodically.
- **Load HOT TM Task Grid...** works for public projects too (no token needed), so it's a drop-in
  replacement for the Ctrl+L workflow either way, and remembers the last project ID you entered.

## Quick TMS

**Quick TMS...** previews a TMS layer without going through JOSM's own **Imagery → Add...**, which
always writes the new entry into your persisted imagery list whether you wanted to keep it or not.

- By default the layer only lives in the current session — closing it or quitting JOSM just drops
  it, nothing touches your saved imagery list. Checking **Pin to my imagery list** before clicking
  Add Layer adds it permanently instead, so it shows up under the normal Imagery menu next time.
- The URL field requires a zoom placeholder (`{zoom}` or `{z}`) plus both `{x}` and `{y}` — checked
  live as you type, with the "Add Layer" button disabled until it's satisfied.
- The last 10 name/URL pairs used are kept as an in-session (not saved, cleared on restart) history,
  offered as the URL field's dropdown; picking one also fills in its matching name if the Name field
  is still empty. Name itself is optional — leaving it blank uses the URL as the layer's name.
- A generic TMS URL has no known coverage area, so JOSM's own right-click **Zoom to layer** would
  otherwise zoom out to the whole world. Quick TMS captures your current map view as the new
  layer's bounds when you click Add Layer, so a later **Zoom to layer** returns to roughly where
  you were looking instead — add the layer while already looking at roughly the right place.

## Secondary Map View

A second, view-only map window with its own checkbox list of layers, opened/closed via
**Secondary Map View**. It stays pixel-perfect in sync with the main view's position/zoom for free
(no manual tracking code), but has its own independent set of which layers are shown — checking a
box here never changes what's shown in the main Layers panel, and vice versa.

**Align to Main View** button: since this window has its own sidebar (of a different width than the
main window's toolbar/panels), lining up the two *windows'* edges doesn't line up the two
*canvases* — the actual map content ends up offset even though both show the identical
position/zoom. This button snaps the secondary window so its canvas sits pixel-adjacent to the main
view's, on whichever side it's currently closer to.

## Multi-validation prep and the todo-plugin bridge

**Multi-validation prep** looks at the layer directly below the currently active one in the Layers
panel, switches to it, selects all its ways, hands them to the todo plugin, then switches back —
one click instead of four to set up "page through every task border with the todo dialog" for a
second/third mapping pass. Works with the standard "todo" plugin or any compatible fork; if no
matching todo dialog is found, it shows a warning instead of failing silently.

## Credits

`ToggleActiveLayerAction.java` and `MultiValidationPrepAction.java` are ported from
[3rdPassJOSMPlugin](https://github.com/MissingMaps/3rdPassJOSMPlugin) ("ThirdPassMM").

This plugin was built with the help of Claude, Anthropic's AI chatbot, used throughout for design,
implementation, and debugging.

## Architecture

| File | Purpose |
|---|---|
| `BetterWorkspacePlugin.java` | Entry point, builds the "More tools → BetterWorkspace" menu |
| `ArrangePanelsDialog.java` / `PanelReorderer.java` | Reorder the docked side panels; order remembered across restarts |
| `LoadTmTaskGridAction.java` | Loads a HOT TM project's task grid, including private/draft projects |
| `SetTmApiTokenAction.java` / `TmApiToken.java` | Save/use your personal HOT TM API token |
| `ToggleActiveLayerAction.java` | Toggle the visibility of the currently active layer |
| `MultiValidationPrepAction.java` / `TodoBridge.java` | Select the layer-below's ways and hand them to the todo plugin |
| `QuickTmsAction.java` / `QuickTmsDialog.java` | Preview a session-only TMS imagery layer |
| `SecondaryMapViewAction.java` / `SecondaryMapViewFrame.java` | A second, view-only map window |
| `RotatingProjection.java` | Backs the rotate/reset view actions |
| `AuthorSelectHook.java` | Adds "Select objects" to the built-in Authors panel's right-click menu |

## Menu structure

```
BetterWorkspace
├── Arrange side panels...
├── ───────────────
├── Load HOT TM Task Grid...
├── Set HOT TM API Token...
├── Toggle active layer visibility
├── Multi-validation prep (add task borders to todo)
├── ───────────────
├── Quick TMS...
├── Secondary Map View
├── Rotate view clockwise
├── Rotate view counter-clockwise
└── Reset view rotation
```
