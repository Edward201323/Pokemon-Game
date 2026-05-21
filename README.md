# Pokemon Bronze Brick

## Screenshots

| Title screen | Starter selection |
|---|---|
| ![Title](docs/title.png) | ![Starter](docs/starter.png) |

| Overworld | Wild encounter |
|---|---|
| ![Overworld](docs/overworld.png) | ![Encounter](docs/encounter.png) |

| PC viewer |
|---|
| ![PC](docs/pc.png) |

---

## Game overview

- **Genre:** 2D top-down monster-collector RPG.
- **Premise:** You wake up in Mitis Town, pick a starter from any of the first
  six generations, and walk north through Route 1 / Route 2-3. Wild pokemon
  ambush you from the grass. In the top-right of the map, a mysterious trainer
  named `????` is waiting with a six-pokemon team of level-100 legendaries.
- **Core loop:** rustle grass → catch / KO wild pokemon → grind exp →
  level up → heal at the Pokemon Center → swap moves at the Move Tutor →
  fight stronger spawns → eventually beat `????` → cap your collection by
  catching everything on the way.
- **Target audience:** Pokemon fans who want a small offline sandbox of the
  classic mechanics, plus Java/Swing learners who want a single mid-size 2D
  game to read end-to-end.

---

## Features

### Mechanics

- **Full 18-type matchup chart** with proper dual-type stacking (`2x * 2x = 4x`,
  `0.5 * 0.5 = 0.25x`, immunities zero out).
- **Standard Pokemon stat formula** with base stats, IVs, EVs, and level scaling.
  Stats re-derive on level-up via `Pokemon.recalcStats()`; HP grows by the
  delta so leveling up at 1 HP doesn't leave you fainted.
- **Experience curves** approximated as `growth_max * (level / 100)^3` — exact
  for Fast / Medium Fast / Slow.
- **Catch math** that scales the pokeball bonus from normal-ball strength at
  low levels up to ultra-ball at level 100, plus the classic shake-count
  resolution (0-3 wobbles = break, 4 = caught).
- **Wild-encounter level scaling**: wilds always spawn within `partyMax - 5`
  to `partyMax` (capped at 80). Evolution-stage buckets bias the species pool
  by your strongest mon (base forms early, fully-evolved late).
- **Pseudo-legendary downweighting** (Dratini / Bagon / Gible / etc. lines at
  0.75x weight).
- **Legendary roll**: 2% of bush encounters draw from the legendary/mythical
  pool. Mythicals get their own theme; regular legendaries get another.

### Modes / activities

- **3 save slots** — independent worlds, picked from the title screen.
- **Starter selection** — 18 starters, Gen 1 through Gen 6.
- **Wild encounters** triggered by walking through tall grass on Route 1.
- **Trainer boss battle** — six level-100 legendaries (Darkrai, Groudon,
  Kyogre, Rayquaza, Mewtwo, Arceus) with hand-picked signature movesets. No
  catching, no running. Beat them and you're awarded a level-100 Arceus.
- **Pokemon Center** — Nurse Joy heals your party, lets you browse / release
  PC-stored pokemon.
- **Move Tutor** — teaches species-legal moves; lets you swap into any of your
  current four move slots.
- **Party management** — 2x3 card grid, swap members, view stats / EXP, switch
  during battle (counts as a turn against trainers).
- **Auto-PC overflow** — catching with a full party sends the new pokemon to
  the PC (fully healed).
- **Blackout** — party wipe fades out, shows the "blacked out" line,
  teleports you next to the Pokemon Center, heals everyone.

### Progression systems

- **EXP / level-up** with stat recalc and animated level-up dialogs.
- **Signature moves** force-included on legendaries (e.g., wild Kyogre always
  shows up with Origin Pulse).
- **Species learnsets** (`learnsets.csv`, ~6,000 entries) gate Move Tutor
  options to mainline-accurate move pools.
- **Boss reward**: defeating `????` awards a level-100 Arceus.

### Controls

| Action | Keys |
|---|---|
| Move | `W` `A` `S` `D` / Arrow keys |
| Confirm | `Enter` (primary) / `Z` (some menus) |
| Back / cancel | `Esc` |
| Open party menu | `P` |
| Save (in overworld) | `Shift` + `S` |
| Delete slot (title screen) | `Shift` + `D` |
| Name entry (new game) | Type — `Backspace` to delete, `Enter` to confirm |
| Starter scroll | `Left` / `Right` to scroll, `Up` / `Down` jumps generation |
| Battle action menu | Arrows + `Enter` (2x2: FIGHT / CATCH / POKEMON / RUN) |
| Battle move menu | Arrows + `Enter` to pick a move, `Esc` to back out |

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java (no language extensions; works on JDK 8+, tested on 17+) |
| Engine | Hand-rolled `javax.swing.JPanel` game loop at 60 FPS |
| Graphics | `java.awt.Graphics2D` (sprites, gradient panels, alpha overlays) |
| Audio | `javax.sound.sampled.Clip` (one clip per Sound instance) |
| Data | CSVs for pokemon stats, moves, learnsets; plain-text save files |
| Build | `javac` — no Maven / Gradle / dependencies |
| Tests | Plain `public static void main` test classes under `src/tests/` |

### Architecture

`Main.java` constructs a `JFrame` and a `GamePanel`. `GamePanel` extends
`JPanel implements Runnable`, runs a 60 Hz game loop (`update()` then
`repaint()`), and acts as the central registry for every subsystem:

```
GamePanel  (FPS=60, gameState machine)
├── TileManager        — loads Route1.txt + tiles/*.png
├── KeyHandler         — WASD/arrows + Z/X/Enter/Esc/Shift edge tracking
├── Sound (music + se) — one Clip per instance, indexed by filename prefix
├── CollisionChecker   — tile & object collision
├── AssetSetter        — spawns pokeballs, bushes, OBJ_Boss
├── Player             — overworld movement, music-zone detection, Shift+S save
├── PokemonEncounter   — wild/trainer encounter transitions
│   └── BattleSystem   — phase machine: ENTRY → CHOOSE_ACTION → CHOOSE_MOVE → …
├── OpenPlayerInventory— party menu + reusable selection mode
├── PokemonCenter      — heal + PC viewer
├── Blackout           — wipe-out / teleport / heal / fade
├── MoveTutor          — teach + swap
├── BossIntro          — overworld cutscene before the boss fight
├── TitleScreen        — slot picker
├── NameEntry          — typed name capture
├── StarterSelection   — Gen 1-6 carousel
└── SaveManager        — slot_N.sav serializer / loader
```

Game state is a flat int (`gameState`) — `update()` and `paintComponent()`
dispatch on it. Subsystems edge-detect their own keys so held buttons don't
auto-repeat through menus.

---

## Installation

### Prerequisites

- **JDK 8 or newer** (any vendor; tested on Temurin 17 / Oracle 21).
  `java --version` and `javac --version` should both work.
- A graphical desktop session (the game opens a Swing window).
- **macOS / Linux / Windows** — pure Java, no native deps.

### No external libraries

The project ships with everything it needs inside `src/res/`. No package
manager, no `pom.xml`, no `build.gradle`. The `.vscode/settings.json` points
at a `lib/` folder for optional jars, but the game itself uses only the JDK.

### Build (all platforms)

From the project root (the directory containing `src/`):

```bash
# 1. Make sure bin/ exists
mkdir -p bin

# 2. Compile everything
javac -d bin -sourcepath src $(find src -name "*.java")
```

Windows (PowerShell) equivalent:

```powershell
New-Item -ItemType Directory -Force -Path bin | Out-Null
$files = Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName }
javac -d bin -sourcepath src $files
```

---

## How to run

> The game uses **relative paths** like `./src/res/...` to load assets, fonts,
> sounds, and maps. **Always run from the project root** or the loaders will
> come up empty.

### Run normally

```bash
java -cp bin Main.Main
```

### Run the regression tests

```bash
java -cp bin tests.MoveSwapTest    # move tutor swap mutates the moves list
java -cp bin tests.SpawnRateTest   # 2% legendary rate, bucket filters hold
```

Both tests exit `0` on success and `1` on the first failure.

### Debug / inspection

- **Save inspection** — saves are plain key=value text at `./saves/slot_N.sav`,
  diff-friendly. Open them in any editor.
- **Recompile + run, one line:**
  ```bash
  javac -d bin -sourcepath src $(find src -name "*.java") && java -cp bin Main.Main
  ```
- **Watch the console** — encounter pipeline, save/load, and asset loaders
  print stack traces on failure rather than swallowing them.

---

## Gameplay guide

### Objectives

- Pick a starter, leave Mitis Town, head north.
- Walk through tall grass to find wild pokemon. Catch them, faint them, or run.
- Heal at the Pokemon Center. Tweak movesets at the Move Tutor.
- Find the `????` trainer at `(col=38, row=11)` on Route 2-3 and defeat all
  six of their legendaries.

### Controls (quick reference)

| Context | Keys |
|---|---|
| Walk | Arrows / WASD |
| Save | `Shift+S` (overworld only) |
| Open party | `P` |
| Action menu (battle) | Arrows + `Enter`, `Esc` to cancel in submenus |
| Confirm dialog | `Enter` (or `Z` in dialog boxes) |
| Name entry | Just type, `Backspace` to fix, `Enter` to confirm |

### Tips

- **Wild levels scale to your strongest party member**, so a stronger lead
  means stronger wilds. Stash overlevelled mons in the PC if you want easier
  spawns for catching.
- **Walking onto a tile adjacent to tile `193`** (Pokemon Center) auto-opens
  the heal menu. Adjacency to tile `173` opens the Move Tutor.
- **Saving mid-encounter is disabled** on purpose. Run to the overworld first.
- **Boss battles disable CATCH and RUN.** Bring revives... oh, wait. Bring
  numbers — switching takes a turn but the enemy can't catch you off-guard.
- **Saves are plain text.** Open `saves/slot_1.sav` in any editor if you want
  to peek (or hand-edit a level back to where it was when you mistakenly
  poured Rare Candies into your Magikarp).

---

## Configuration

### Save files

- **Location:** `./saves/slot_N.sav` (`N` ∈ `1..3`)
- **Format:** plain-text `key=value`, one per line. Lines starting with `#`
  are comments.
- **What's persisted:** player name + position + facing, party + PC pokemon
  (name, level, current HP, total EXP, IVs, EVs, gender, ability, moves),
  surviving map objects, boss-defeated flag, save timestamp.

### Tunable constants

| What | Where | Default |
|---|---|---|
| FPS | `src/Main/GamePanel.java` — `FPS` | `60` |
| Screen size (tiles) | `GamePanel.maxScreenCol/Row` | `18 x 14` |
| World size | `GamePanel.maxWorldCol/Row` | `49 x 103` |
| Wild encounter rate | `PlayerObjectInteraction.touchBushRoute1` | `1.5%` per grass step |
| Legendary roll rate | `PlayerObjectInteraction.LEGENDARY_ENCOUNTER_RATE` | `0.02` (2%) |
| Pseudo-legendary weight | `GetPokemon.PSEUDO_WEIGHT` | `0.75` |
| Save slot count | `SaveManager.SLOT_COUNT` | `3` |
| Starter level | `StarterSelection.STARTER_LEVEL` | `5` |
| Boss roster | `object/AssetSetter.setObject` | 6 legendaries @ Lv100 |
| Music boundary row | `GamePanel.ROUTE_BOUNDARY_ROW` | `61` |
| Party limit | `PlayerPokemon.PARTY_LIMIT` | `6` |
| Name max length | `Save/NameEntry.MAX_NAME_LEN` | `12` |

There are no environment variables or settings files — change constants in
source and recompile.

---

## Roadmap

- [ ] More routes / maps (currently a single Route1.txt covers the whole world).
- [ ] Real items (`src/Items/` is stubbed — `LifeOrb`, `ChoiceScarf`,
      `PokemonItems` exist as placeholders).
- [ ] Status conditions (burn / paralysis / poison / sleep / freeze).
- [ ] Multi-hit moves, recoil, and stat-stage modifiers (current battle is
      damage + accuracy only).
- [ ] Animations module beyond the FadeIn/FadeOut helpers.
- [ ] More trainers; the boss is the only scripted fight.
- [ ] Audio mixer (currently each `Sound` owns one `Clip`; SFX can clobber
      music on rapid trigger).

---

## License

This is a non-commercial personal/learning project. **Pokemon, all species
names, sprites, and the audio tracks belong to Nintendo / Game Freak / The
Pokemon Company.** The original Java source code in this repository is
provided as-is for educational reference. Do not redistribute or sell.

---

*"Walk through grass. Throw ball. Don't ask what `????` knows about you."*
