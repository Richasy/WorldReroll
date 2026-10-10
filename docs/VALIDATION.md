# Validation evidence

Validated on 2026-10-10 against Minecraft 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3 and Java 25. The implementation worktree is
`WorldReroll-implementation`, branch `codex/world-reroll`, based on the clean
bootstrap commit `70c427898b6502cc5b185760626108659de803a4`.

- `gradlew.bat build runClientGameTest --no-daemon`: passed.
- Five JUnit tests: passed; cancellation, duplicate preparation, loading gates,
  path traversal, active-world exclusion and replaced directory identity.
- Native client scenario: passed using disposable saves in the test run directory.
  The pause-menu entry and confirmation were exercised, including cancellation
  and duplicate activation. Survival mode, locked hard difficulty, game rules,
  flat generator, structure and bonus-chest options were retained with a new seed.
  A copied enabled world datapack produced its custom crafting result.
  Old inventory and a placed terrain marker were absent. The source existed before
  replacement entry, was deleted afterward, and an unrelated folder survived.
  Publishing the replacement to LAN hid the entry and rejected activation.
  An intentionally malformed enabled world-generation datapack then caused
  recreation to fail while retaining its source. The resulting registry error
  in the test log is expected.
- Pause-menu and confirmation screenshots were visually reviewed.
- Complete source/resource/build-input diff reviewed; `git diff --check` passed.

Actual user acceptance in the personal modded survival instance is pending.
Third-party world-loading overrides, arbitrary per-save mod state and multiplayer
server connections have not been exercised. No personal world was used in tests.
