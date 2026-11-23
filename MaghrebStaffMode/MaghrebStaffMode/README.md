# StaffMode (Maghreb SMP developer test)

Paper 1.21.11 · Java 21 · Maven

## Build
    mvn clean package
The jar is created at `target/StaffMode-1.0.0.jar`. Drop it in `plugins/`.

## Commands / permissions
| Command | Permission |
|---|---|
| /staff | staffmode.use |
| /staff reload | staffmode.admin |
| /vanish (/v) | staffmode.vanish |
| /freeze <player> | staffmode.freeze |
| /invsee <player> | staffmode.invsee |

## Design notes (inventory safety)
- The player's inventory, armor, offhand, gamemode, health, food, XP and flight are written to
  `plugins/StaffMode/data/<uuid>.yml` (atomic write) BEFORE anything is cleared. If the write fails,
  Staff Mode is not enabled.
- State is restored on `/staff`, on quit, on plugin disable and (crash recovery) on next join.
- Staff-tool items are tagged with PersistentDataContainer; the staff inventory is locked,
  drops/pickups/swaps are blocked, damage is cancelled.

## Test checklist (for the demo video, 2 players)
1. Player A holds items+armor, runs /staff -> items replaced by tools; run /staff -> items identical.
2. Vanish: Player B (no permission) cannot see A / A absent from B's player list.
3. Random-teleport tool teleports A to B.
4. Inspect tool / /invsee B -> read-only view of B's inventory.
5. Freeze tool / /freeze B -> B cannot move; repeat to unfreeze.
6. Try dropping tools, taking damage -> blocked.
7. Log out while in Staff Mode, rejoin -> inventory is intact.
8. Edit config.yml, /staff reload.
