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

## Project structure

```
.
├── README.md
├── bin/                       # compiled .class output
├── saves/                     # slot_N.sav files (created on first save)
└── src/
    ├── Main/                  # bootstrap, game loop, sound, input, collision
    │   ├── Main.java
    │   ├── GamePanel.java     # game state machine + 60 Hz loop
    │   ├── KeyHandler.java
    │   ├── Sound.java
    │   └── CollisionChecker.java
    ├── entity/
    │   ├── Entity.java
    │   └── Player.java
    ├── tile/
    │   ├── Tile.java
    │   └── TileManager.java
    ├── object/                # static map objects (SuperObject subclasses)
    │   ├── SuperObject.java
    │   ├── OBJ_Pokeball.java
    │   ├── OBJ_BushRoute1.java
    │   ├── OBJ_Boss.java      # the "????" trainer tile
    │   └── AssetSetter.java
    ├── Pokemon/               # data layer: species, moves, types, EXP curves
    │   ├── Pokemon.java
    │   ├── GetPokemon.java
    │   ├── GetPokemonImages.java
    │   ├── Move.java
    │   ├── Moves.java
    │   ├── TypeChart.java
    │   └── ExpCurves.java
    ├── PokemonEncounters/     # wild encounters + battle screen
    │   ├── PlayerObjectInteraction.java
    │   ├── PokemonEncounter.java
    │   └── BattleSystem.java  # phase machine, HP eases, ball throw, faint anim
    ├── PlayerInventory/
    │   ├── PlayerInventory.java
    │   ├── PlayerPokemon.java
    │   └── OpenPlayerInventory.java  # party UI + selection mode
    ├── PokemonCenter/
    │   ├── PokemonCenter.java        # heal + PC viewer
    │   └── Blackout.java             # wipe-out cutscene
    ├── MoveTutor/
    │   └── MoveTutor.java
    ├── BossIntro/
    │   └── BossIntro.java
    ├── Animations/
    │   └── Animations.java
    ├── Items/                 # placeholder item classes (stubbed)
    │   ├── PokemonItems.java
    │   ├── LifeOrb.java
    │   └── ChoiceScarf.java
    ├── Save/
    │   ├── TitleScreen.java          # save-slot picker
    │   ├── NameEntry.java
    │   ├── StarterSelection.java
    │   └── SaveManager.java          # plain-text save files
    ├── tests/                 # main()-style regression suites
    │   ├── MoveSwapTest.java
    │   └── SpawnRateTest.java
    └── res/                   # all assets (loaded by relative path)
        ├── PokemonData/
        │   ├── PokemonStats.csv      # 368 species, 25 columns each
        │   └── learnsets.csv         # ~6,100 species/move rows
        ├── movesData/
        │   └── moves.csv             # 182 moves (type,name,power,acc,phys,minLvl)
        ├── PokemonFrontImages/       # ~368 enemy sprites
        ├── PokemonBackImages/        # ~368 ally back sprites
        ├── EncounterAssets/          # HP bars, trainer poses, transitions
        ├── EncounterBackgrounds/     # 14 battle backdrops
        ├── tiles/                    # map tiles (266-tile palette)
        ├── maps/Route1.txt           # ASCII grid, 49 cols x 103 rows
        ├── objects/                  # bush + pokeball sprites
        ├── player/                   # 3-frame walk cycle, 4 directions
        ├── trainers/                 # trainer sprites
        ├── music/                    # 28 .wav tracks, indexed by prefix
        ├── SoundEffects/             # 0_pokeballOpen.wav
        └── Font/MaruMonica.ttf
```

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

## Asset pipeline

The game loads everything from disk at startup using **filename conventions**.
No external manifest or atlas — drop a file in the right folder with the right
prefix and it's wired in.

### Adding a pokemon

1. Add a row to `src/res/PokemonData/PokemonStats.csv` (column order is
   documented in the header line; see `GetPokemon.assignPokemonInfo` for the
   parser).
2. Drop `pokemonname.png` in `src/res/PokemonFrontImages/` (file name is the
   species name with a lowercased first letter).
3. Drop a back-sprite at `src/res/PokemonBackImages/pokemonname.png`.
4. Optional: add level-up / TM moves to
   `src/res/PokemonData/learnsets.csv` (`Species,Move` per line) so the Move
   Tutor offers a realistic list.

### Adding a move

1. Add a row to `src/res/movesData/moves.csv`:
   `type,name,power,accuracy,physical,min_level`. Accuracy `Always Hits` is a
   valid token; the parser maps it to `-1`.
2. If you want a species to use it, add `Species,Move` to `learnsets.csv`.
3. If it's a signature move, add the name to `Moves.SIGNATURE_MOVES` so it
   force-includes on high-level rolls.

### Adding music / sound

- **Music** — drop `<index>_<label>.wav` in `src/res/music/` (e.g.
  `28_VictoryFanfare.wav`). `Sound.java` indexes by the leading number.
  Bump `musicFiles` array size in `Sound.java` if you exceed 30.
- **Sound effects** — same convention in `src/res/SoundEffects/` (currently
  only `0_pokeballOpen.wav`). Bump `seFiles` array size to extend.

### Maps & tiles

- **Tiles:** `src/res/tiles/<n>.png` for collidable tiles, `<n>_false.png` for
  walkable tiles. The number is the tile id used in the map file.
- **Maps:** `src/res/maps/Route1.txt` is a space-separated grid of tile
  numbers, 49 cols x 103 rows. Tile `5` in the grid is also where bushes get
  spawned (see `AssetSetter.loadBushes`).
- Special tiles wired into `Player.java`: `193` = Pokemon Center auto-trigger,
  `173` = Move Tutor auto-trigger.

### Battle backgrounds / assets

- **Encounter backgrounds** — `src/res/EncounterBackgrounds/<idx>_<label>.png`.
  `PokemonEncounter` currently uses index `12`.
- **Encounter assets** — `src/res/EncounterAssets/<idx>_<label>.png` for
  HP bars, trainer poses, transition flashes. Indexes are hardcoded in
  `PokemonEncounter` and `BattleSystem`.

---

## Contributing

This is a personal-archive project, but if you fork:

### Code style

- **Java 8 source level** — no `var`, no records, no text blocks. Lambdas are
  fine and used throughout the menu system.
- **Package per system** (`Main`, `Pokemon`, `PokemonEncounters`, etc.).
- **Comment the WHY**, not the WHAT — most existing comments explain
  why a constant is what it is, why an edge case exists, or what
  invariant a block preserves. Match that.
- **Avoid premature abstraction.** The codebase prefers a switch-on-state and
  a stack of phase enums over deep class hierarchies.
- **Edge-detect held keys.** Every menu uses `justX = keyH.xPressed && !prevX`
  rather than reading raw key state.

### Branch strategy

- `main` is the only long-lived branch.
- Feature branches: `feature/<short-name>`. Bug fixes: `fix/<short-name>`.

### PR process

1. Build cleanly: `javac -d bin -sourcepath src $(find src -name "*.java")`
2. Run both tests: `java -cp bin tests.MoveSwapTest && java -cp bin tests.SpawnRateTest`
3. Manually verify the loop you touched (new game → starter → encounter →
   center → tutor → boss is the canonical smoke test).
4. PR description: one-paragraph why, then a bulleted summary of files
   touched.

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

## Known issues

- **`bin/.DS_Store`** modifications appear in `git status` on macOS — safe to
  ignore.
- **Image loading is eager and synchronous** at startup (~700 pokemon
  sprites). First boot can take a few seconds on slow disks.
- **Single map** — the world is one big Route 1 grid; there are no
  teleport/door tiles, only the Pokemon Center blackout-respawn.
- **Save format is line-based plain text** — sufficient and diffable, but
  not robust against partial writes if the game crashes mid-save.

---

## Credits

- **Code:** Edward — handwritten Java + Swing.
- **Pokemon data (`PokemonStats.csv`, `learnsets.csv`):** scraped / curated
  from public Bulbapedia-style sources.
- **Sprites:** front + back pokemon sprites from the official mainline games
  (Gen 1-6 style).
- **Font:** `MaruMonica.ttf` — Pokemon-style pixel font.
- **Music & SFX:** Pokemon mainline-game inspired (`.wav` rips and
  remixes — indexed `0..27`).
- **Inspiration:** every Pokemon game from Red/Blue through X/Y, plus
  RyiSnow's YouTube series for the Swing game-loop scaffolding pattern.

---

## License

This is a non-commercial personal/learning project. **Pokemon, all species
names, sprites, and the audio tracks belong to Nintendo / Game Freak / The
Pokemon Company.** The original Java source code in this repository is
provided as-is for educational reference. Do not redistribute or sell.

---

*"Walk through grass. Throw ball. Don't ask what `????` knows about you."*
