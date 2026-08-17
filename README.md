# BetterWorkspace – JOSM Plugin

Workspace tweaks for JOSM. All menu-driven features live under **More tools → BetterWorkspace**
(each item is also a separate, shortcut-bindable, toolbar-registerable action — assign a key or add
it to your toolbar via JOSM's own Preferences → Shortcuts / toolbar customization), in this order:
- Load a HOT Tasking Manager project's task grid as a data layer — including **private and draft
  projects you have access to** — via your personal TM API token.
- Toggle the visibility of the currently active layer - handy as keyboard shortcut
- Multivalidation prep — adding tasks into todolist (select all ways in the layer below the active one and add them to the todo plugin's list, for paging through task borders during validation) - also handy as keyboard shortcut
- Manage validation rules — additional validator checks that wouldn't be possible using only validator rules (landuse/place/highway QA), individually toggled on/off, split into fast "Regular" and heavier "Possibly slow" groups.
- Quick TMS — Quickly load TMS link as imagery layer without the need of storing it in your settings
- Load Esri Imagery Date Grid — loads Esri World Imagery's real per-tile acquisition dates for the current view as a data layer 
  - First time you run this feature it will create "BetterWorkspace: Esri Imagery Dates" map paint style. You can right click it in the  Map Paint Styles window and change the colors in Style settings
- Secondary view-only map window that tracks the main view, with its own independent set of active layers.
- Arrange the docked side panels, remembered across restarts.

Separately, it also adds a **"Select objects"** entry to the right-click menu of JOSM's built-in
**Authors** panel (which otherwise only offers "Copy"). If you also have the standard **todo**
plugin (or a compatible fork) installed, marking an item done there keeps it visible in the list
instead of removing it — see [Keeping completed todo items visible](#keeping-completed-todo-items-visible)
below.

## Menu structure

```
BetterWorkspace
├── Load HOT TM Task Grid...
├── Set HOT TM API Token...
├── Toggle active layer visibility
├── Multivalidation prep (add task borders to todo)
├── Manage validation rules...
├── ───────────────
├── Quick TMS...
├── Load Esri Imagery Date Grid...
├── Secondary Map View
├── ───────────────
└── Arrange side panels...
```


## External services & responsible use

This plugin talks to two external HTTP APIs - the HOT Tasking Manager API and Esri's World Imagery
"Citations" service. Both are called **only in direct response to a menu click**: nothing in this
plugin polls, auto-refreshes, retries in a loop, or runs on any kind of timer or schedule.

|  | HOT Tasking Manager | Esri World Imagery (Citations) |
|---|---|---|
| Triggered by | **Load HOT TM Task Grid...** | **Load Esri Imagery Date Grid...** |
| Requests per click | 1 | 1 |
| Query scope | One project's task grid | Current map view only |
| Client-side size cap | n/a (a task grid is already small) | refused outright above 50 km across the view |
| Server-side cap | n/a | Esri caps every response at 100 features (`maxRecordCount`) regardless - detected via `exceededTransferLimit` and reported to the user rather than silently shown as an incomplete grid |
| Fields requested | n/a (task grid GeoJSON as-is) | only the 6 fields actually used, via `outFields=...` - never `outFields=*` |
| Timeouts | 15s connect / 30s read | 15s connect / 30s read |
| Identifies itself | `User-Agent: BetterWorkspace-JOSMPlugin/<version>` | same |

The 50 km cap on the Esri query is doing two jobs at once: it keeps each request small, and it avoids
the alternative failure mode of a large view silently coming back truncated at Esri's 100-feature limit
and being misread as "no recent imagery here" - see [Esri Imagery Date Grid](#esri-imagery-date-grid)
below for the full reasoning.

Your HOT TM API token (see below) is the only credential this plugin ever sends anywhere, and it's only
ever sent to the HOT Tasking Manager API itself, over HTTPS.

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

## Esri Imagery Date Grid

**Load Esri Imagery Date Grid...** queries Esri's own "Citations" footprint
layer (the same metadata source [esri-imagery-date-finder](https://martinedoesgis.github.io/esri-imagery-date-finder/app.html)
uses) for the current map view, and adds the footprints as a data layer - click any polygon to see its
real acquisition date (`date`, e.g. `2025-09-06`) plus resolution, accuracy and source in the tags panel.

- Scoped to the current view and capped at 50 km across - Esri's own server already caps each query at
  100 footprints, so a much larger area would silently come back incomplete rather than heavier to fetch.
  Zoom in and retry if asked.
- If a (still <50 km) view is dense enough to hit that 100-footprint cap anyway, you're warned that the
  grid is incomplete rather than shown a silently partial one.
- Each footprint's date is also drawn directly on the map (not just in the tags panel), via a small
  map paint style the plugin registers the first time this runs (**Preferences → Map Paint Styles** as
  "BetterWorkspace: Esri Imagery Dates"). It visualise attribute "date" for any object with attribute "SRC_RES" so should be safe to leave enabled permanently
- Text and outline color are both user-adjustable from Map Paint Styles window → right click → Style settings
  (defaults: white `#FFFFFF` text, orange `#FF9933` outline)

## Secondary Map View

A second, view-only map window with its own checkbox list of layers, opened/closed via
**Secondary Map View**. It stays in sync with the main view's position/zoom, but has its own independent set of which layers are shown — checking a
box here never changes what's shown in the main Layers panel, and vice versa.

## Multivalidation prep and the todo-plugin bridge

**Multivalidation prep** looks at the layer directly below the currently active one in the Layers
panel, switches to it, selects all its ways, hands them to the todo plugin, then switches back. Works with the standard "todo" plugin or any compatible fork; if no matching todo dialog is found, it throws a warning instead of failing silently.

### Keeping completed todo items visible

Normally, marking an item done in the todo plugin removes it from the list entirely. This plugin
changes that so a marked-done item instead:

- stays in the list, at its original position, grayed out
- has the list automatically select and scroll to the next item
- keeps the "done/total" count in the todo list's title accurate

You can turn this feature off by changing status of `betterworkspace.todo.keepdone` preference (on by default) in JOSM's own
**Preferences → Advanced Preferences** 

## Validation rules

**Manage validation rules...** opens a dialog to individually enable/disable extra validator
checks this plugin adds, grouped into:

- **Regular** (fast, always cheap to run): *Residential with multiple place nodes*, *Hamlet/village
  building count mismatch*.
- **Possibly slow** (heavier geometry checks over many objects at once - may take noticeably longer
  on large downloads or slower machines; handy for third-pass validation, but not limited to it):
  *Highway classification mismatch*, *Residential area without a highway*, *Overlapping landuse
  areas*.

All of them are **off by default** - turn on whichever you want from the dialog, one checkbox each,
applied immediately, no separate Apply step. Hover a rule for its full description. 

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
| `TodoBehaviorSync.java` | Keeps marked-done todo items visible instead of removed - see [Keeping completed todo items visible](#keeping-completed-todo-items-visible) |
| `QuickTmsAction.java` / `QuickTmsDialog.java` | Preview a session-only TMS imagery layer |
| `LoadEsriImageryDatesAction.java` | Loads Esri World Imagery's real acquisition-date footprints for the current view |
| `ProgressDialog.java` | Shared "please wait" dialog used by both HTTP-loading actions above |
| `SecondaryMapViewAction.java` / `SecondaryMapViewFrame.java` | A second, view-only map window |
| `AuthorSelectHook.java` | Adds "Select objects" to the built-in Authors panel's right-click menu |
| `ManageValidationRulesAction.java` / `ValidationRulesDialog.java` | Dialog to toggle the validation rules below on/off |
| `validation/BwValidationConfig.java` / `BwTest.java` | Registry, on/off persistence, and shared base class for the validation rules |
| `validation/ResidentialMultiplePlaceNodes.java`, `HamletVillageTaggingMismatch.java`, `HighwayClassificationMismatch.java`, `ResidentialWithoutHighway.java`, `OverlappingLanduseAreas.java` | The five validation rules themselves |
| `validation/BwResidentialArea.java` / `BwLanduseArea.java` | Shared geometry helpers (ring-stitching, point-in-polygon, hole-aware overlap) used by the rules above |

