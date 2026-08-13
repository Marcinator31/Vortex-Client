# Vortex Client

A Fabric client for **Minecraft 1.21.11** with HUD modules, ESP, a waypoint
system, a skin wardrobe and performance tools. Everything is configurable
in-game, and every setting is saved with your preset.

---

## Features

**HUD** — FPS, ping, CPS, coordinates, potion effects, armour with durability,
totem count, radar, saturation, player list, keystrokes, totem popper counter
and session stats. Every element can be dragged into place in the HUD editor,
with snapping to edges and to other elements.

**Combat** — hitboxes, shield status, toggle sprint, health indicator and
target info (an opponent's gear plus whether they are actually in attack
range). Projectile paths preview where a pearl, potion or arrow will land,
with bow charge taken into account.

**ESP** — mobs, blocks, containers, spawners and dropped items, each with its
own selection screen, colours and draw distance.

**Waypoints** — a system of its own rather than a module. Markers are drawn as
small rings with the initial of their name; aim at one and it grows and shows
the full name and distance. Markers can carry groups of marked blocks, which
appear only when you are nearby. Markers are scoped per world, with named
profiles for proxy networks where every server shares one address. Four
assignable keys handle adding, marking blocks, marking an area and opening the
manager.

**Skins** — a wardrobe that fetches skins by player name, imports your own PNG
files and previews every entry. Skins can be applied client-side or uploaded
to your account so that everyone sees them.

**Base hunting** — stash finder, suspicious chunk detection, tunnel detector
and chunk borders.

**Performance** — potato mode and anti-render for hiding entity types you do
not need to see.

Three presets can be switched at any time; each holds its own modules,
colours, favourites, window layout and waypoints. Presets can be exported to a
text file and imported again, which makes them easy to share.

---

## Building

### Locally
```
./gradlew build
```
The finished mod appears in `build/libs/` — the file **without** `-sources`.

### Via GitHub Actions
Every push builds automatically. The `.jar` is available as an artifact in the
Actions tab. The workflow caches Minecraft and retries up to three times, so a
short outage of Mojang's or Fabric's servers does not fail the build.

---

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **0.18.1** or newer
- Fabric API
- Java **21**

---

## Commands

| Command | What it does |
| --- | --- |
| `/wp add \| del \| list` | Manage waypoints |
| `/export <name>` | Save the active preset to a file |
| `/import <name>` | Load a saved preset |
| `/presets` | List saved presets |
| `/errors` | Show what went wrong and how often |
| `/lag` | Show which part of the client uses how much time |
| `/relaunch` | Restart the game |

Default keys: **Right Shift** opens the menu, **Right Ctrl** the HUD editor,
**F4** freecam. Waypoint keys are unassigned by default so that nothing is
taken away from you — assign them once in the Waypoints section.

---

## A note on fair play

Some modules — aimbot, auto hit, auto totem, fly, no fall and the crystal
macro — automate combat. They are detected reliably by most anti-cheat
systems, and using them on a server that forbids them will very likely get you
banned. They are marked accordingly in the module list. Everything else in
this client only displays information the game has already sent you.

---

## Licence

MIT
