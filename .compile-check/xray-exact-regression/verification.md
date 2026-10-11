# Exact x-ray matching verification

Date: 2026-10-04

The regression harnesses extract the actual production Java methods and compile them against small Minecraft fixtures. No Minecraft instance or GPU rendering is started. Outline verification records the arguments passed to `LevelRenderer.renderLineBox`; it verifies block inclusion, colors and full-block outline coordinates, not the final on-screen appearance.

## Results

- `XrayScanRegression.java`: the 1.2.31 source snapshot reproduces seven failures against the requested exact-only behavior; the updated source passes all 24 assertions.
- Each before/after scan run observes 379 ticks, never exceeding two chunk probes or scans per tick.
- Scan coverage includes descent within the same chunk, late chunk loading, new/removed ores, world switching, mode switching, clearing the selection and rescanning after invalidation.
- Matching/render coverage includes selecting only regular uranium, selecting both regular and Nether uranium with separate colors, excluding unselected deepslate uranium, skipping stale cached blocks after selection removal, and skipping a cached block replaced by an unselected variant.
- `XrayColorSelectionRegression.java`: all 25 assertions pass using real Gson and an isolated temporary config directory. Coverage includes nine slot positions, saved empty-slot colors, JSON reload, duplicate exact blocks using the leftmost slot color, distinct variants using independent colors, replacing/removing exact targets and the nine-slot limit.

## Source fingerprints

- Before snapshot: `E8B2321E898462AC37A5507BF508B98BF576D806AAE851C5A6A22F37C731B577`
- Tested production `XrayClientState.java`: `4E98CF6196D620FE5E32590AE512733E9D2B3AE354E35F6E973ACE2EE38903F1`

## Commands

Run from `D:\VeinMinerPLUS` with Java 21:

```powershell
java .compile-check\XrayScanRegression.java .compile-check\xray-exact-regression\XrayClientState.before.java src\main\java\com\extrarawstyle\veinminerplus\XrayClientState.java
java .compile-check\XrayColorSelectionRegression.java src\main\java\com\extrarawstyle\veinminerplus\XrayClientState.java src\main\java\com\extrarawstyle\veinminerplus\OreFamily.java C:\Users\wtc27\.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson\2.10.1\b3add478d4382b78ea20b1671390a858002feb6c\gson-2.10.1.jar
```
