Loconautics issue regression checks

Run from the repository root with Java 21:

    .\gradlew.bat compileJava build -I verification/issues.init.gradle runGameTestServer --console=plain

The init script adds these tests only to the game-test run. They are not part of
the normal release JAR. No separate test framework or external test mod is needed.
The test world is created under build/issue-verification/run.

The 17 server tests cover:
  - transmission stress caching, including stopping the output;
  - clutch blocking, open-clutch propagation and gearshift reversal through bogeys;
  - portable interface reach, lateral alignment and forward direction;
  - dashboard assembly with translation, clockwise/counterclockwise/half rotation;
  - preservation of label, inversion, frequencies and a stationary external target;
  - velocity gauges with away/towards sensor ranges and both movement directions.

The panel tests use Sable's actual moveBlocks and AssemblyTransform APIs. They
tick the destination block entity explicitly to avoid depending on chunk-ticker
registration in the test world. The sensor tests set a known measured velocity
to isolate gauge normalization from a moving physical body.

Results from this change:
  Original Indev Java sources: 17 tests completed, 10 failed.
  Patched Java sources:        all 17 required tests passed.
  compileJava and build:       successful.

Both comparisons use the corrected development dependency setup so that the
dedicated game-test server can start. The baseline failures cover stress caching,
clutch/gearshift behavior, interface reach, four dashboard moves and two negative
sensor-range cases. The original Java sources were restored to the patched
versions after the comparison, and the final build ran against the patched code.

These are server regressions. They do not verify client graphics/Ponder playback,
Steam 'n' Rails gauge integration, repeated physical rerailing, moving rollers,
long-running unloaded travel, or the Lithium/Deployer API incompatibility.
