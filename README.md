# BetterWorkspace – JOSM Plugin

Workspace tweaks for JOSM. All menu-driven features live under **More tools → BetterWorkspace**
(each item is also a separate, shortcut-bindable, toolbar-registerable action — assign a key or add
it to your toolbar via JOSM's own Preferences → Shortcuts / toolbar customization), in this order:
- Arrange the docked side panels, remembered across restarts.
- Load a HOT Tasking Manager project's task grid as a data layer — including **private and draft
  projects you have access to** — via your personal TM API token.
- Toggle the visibility of the currently active layer - handy as keyboard shortcut
- Multi-validation prep — adding tasks into todolist. (select all ways in the layer below the active one and add them to the todo plugin's list, for paging through task borders during validation)
- Quick TMS — Quickly load TMS link as imagery layer without the need of storing it in your settings
- Secondary view-only map window that tracks the main view, with its own independent set of active layers.
- Rotate the whole map view (data + imagery) clockwise/counter-clockwise

Separately, it also adds a **"Select objects"** entry to the right-click menu of JOSM's built-in
**Authors** panel (which otherwise only offers "Copy"). 

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


## HOT Tasking Manager task grid loading (private/draft projects)

- Get your personal token from the TM website: **tasks.hotosm.org → Settings → enable "Expert
  mode" → API Key** card. Pasting the copied "Token xxx" text or just the token itself both work.
  It's stored via JOSM's own preferences, the same mechanism JOSM uses for its own OSM OAuth token.
- The token expires roughly 7 days after your last TM login — re-copy it periodically.
- **Load HOT TM Task Grid...** works for public projects too (no token needed), so it's a drop-in
  replacement for the Ctrl+L workflow either way, and remembers the last project ID you entered.

## Quick TMS

**Quick TMS...** previews a TMS layer without going through JOSM's own **Preferences → Imagery → +TMS**, which
always writes the new entry into your persisted imagery list whether you wanted to keep it or not.

- By default the layer only lives in the current session — closing it or quitting JOSM just drops
  it, nothing touches your saved imagery list. Checking **Pin to my imagery list** before clicking
  Add Layer adds it to your list of imagery same as if you would go Preferences → Imagery → +TMS.
- The URL field requires a zoom placeholder (`{zoom}` or `{z}`) plus both `{x}` and `{y}` — checked
  live as you type, with the "Add Layer" button disabled until it's satisfied.
- Name is optional — leaving it blank uses the URL as the layer's name.
- A generic TMS URL has no known coverage area, so JOSM's own right-click **Zoom to layer** would
  otherwise zoom out to the whole world. Quick TMS captures your current map view as the new
  layer's bounds when you click Add Layer, so a later **Zoom to layer** returns you to roughly where
  you were looking instead — so add the layer while already looking at roughly the right place.

## Secondary Map View

A second, view-only map window with its own checkbox list of layers, opened/closed via
**Secondary Map View**. It stays in sync with the main view's position/zoom, but has its own independent set of which layers are shown — checking a
box here never changes what's shown in the main Layers panel, and vice versa.

## Multi-validation prep and the todo-plugin bridge

**Multi-validation prep** looks at the layer directly below the currently active one in the Layers
panel, switches to it, selects all its ways, hands them to the todo plugin, then switches back. Works with the standard "todo" plugin or any compatible fork; if no matching todo dialog is found, it throws a warning instead of failing silently.

## Credits

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

