# TrapBuilder Assistant

Client-side **Fabric** mod for **Minecraft 1.21.11** (Java 21, Yarn) that loads `.litematic` / `.schem`
files and shows a color-coded, step-by-step build overlay for traps. It only renders; it never places or
breaks blocks for you.

## Features
- Reads `.litematic` and Sponge `.schem` (v2/v3)
- Zones: dig (green), drop shaft (red), triggers (gold), redstone (purple), camouflage ghost blocks
- Smart Auto-Step: dig, then redstone and triggers, then camouflage
- "Next block" marker
- Flexible block substitution (Stone/Cobble, Dirt/Grass, Iron/Copper, Stairs/Slabs)
- GUI on **R**: file list, X/Y/Z offset, rotate 90 degrees, hex colors, render modes

## Usage
1. Download the jar from **Releases** (or the **Actions** artifact) and put it in `.minecraft/mods/` with Fabric API.
2. Put schematics in `.minecraft/schematics/`.
3. In game press **R**, click a file, then adjust position and rotation.

## Build
```
./gradlew build
```
The jar is written to `build/libs/`.

## Setup note
Copy `gradlew`, `gradlew.bat` and the `gradle/` folder from any working Fabric project, and set the version
numbers in `gradle.properties` (see https://fabricmc.net/develop).

## Release
Push a tag such as `v1.0.0` and GitHub Actions attaches the jar to a Release.

## License
MIT
