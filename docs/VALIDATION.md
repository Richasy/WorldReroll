# Validation evidence

Validated on 2026-10-10 against Minecraft 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3 and Java 25. The implementation worktree is
`WorldReroll-implementation`, branch `codex/world-reroll`, based on the clean
bootstrap commit `70c427898b6502cc5b185760626108659de803a4`.

- `gradlew.bat build runClientGameTest --no-daemon`: passed.
- Seven JUnit tests: passed; cancellation, duplicate preparation, loading gates,
  path traversal, active-world exclusion and replaced directory identity.
  Readable directory reservation also covers collision preservation, Unicode,
  invalid path characters, Windows device names and long-name shortening.
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
- `gradlew.bat runClientGameTest --no-daemon` passed again after documentation
  capture changes. The actual client window was resized to 1920 x 1080 before
  GUI initialization; renderer-only screenshot resizing is not used. All five
  published screenshots were individually reviewed for centered layout, complete
  English/Chinese text, hidden HUD and autosave indicator, and clear composition.
  The village illustration is explicitly captioned as a spectator overview.
- The resumed feature adds a settings panel with separate random/village choices,
  visible selection and an unavailable reason when structures are off. The native
  suite exercises panel cancellation and the disabled choice, plus a normal
  terrain replacement whose player and world spawn are on safe ground near a
  real village. An enabled datapack removing all village tags forces a locate
  failure and verifies source preservation. English and Chinese panel screenshots
  are captured for visual review.
- Complete source/resource/build-input diff reviewed; `git diff --check` passed.

The user explicitly authorized PR integration, public repository visibility and
the first GitHub Release. Full acceptance of third-party compatibility in the
personal modded survival instance remains outside the executed test evidence.
Third-party world-loading overrides, arbitrary per-save mod state and multiplayer
server connections have not been exercised. No personal world was used in tests.
