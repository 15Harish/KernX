# Linux Command Center (Java Desktop Edition)

A dark, terminal-styled JavaFX desktop app for running Linux commands over SSH,
with preset command buttons and a live output console. This is the Java port
of the original Next.js "Linux Command Center" project.

## Requirements (to build)

- **JDK 17 or newer** (JDK 21 recommended) — https://adoptium.net
- **Maven** — https://maven.apache.org/download.cgi
- Internet access (to download JavaFX + JSch dependencies from Maven Central
  the first time you build)

## 1. Run it during development

```
cd linux-console
mvn javafx:run
```

This opens the app window directly — fastest way to test changes.

## 2. Build a runnable fat JAR

```
mvn clean package
```

This produces `target/linux-console.jar` — a single file containing your app
+ all dependencies. You can run it with:

```
java -jar target/linux-console.jar
```

(Note: because JavaFX modules need to be on the module path for some setups,
if double-clicking the jar doesn't launch the UI, run it via the command
above instead — this is normal for JavaFX fat jars.)

## 3. Turn it into a Windows `.exe`

This step must be run **on a Windows machine** with JDK 17+ installed, since
`jpackage` bundles a native Windows launcher + a private JRE.

```
mvn clean package

jpackage ^
  --input target ^
  --name "LinuxCommandCenter" ^
  --main-jar linux-console.jar ^
  --main-class com.linuxconsole.Main ^
  --type exe ^
  --win-console ^
  --icon icon.ico
```

- `--type exe` produces an installer `.exe` (uses the WiX Toolset — install
  it first from https://wixtoolset.org if `jpackage` complains it's missing).
- Drop `--icon icon.ico` if you don't have a custom icon yet.
- Output appears in the current folder as `LinuxCommandCenter-1.0.0.exe`.
- Double-clicking that installer installs the app with its own bundled Java
  runtime — the end user does **not** need Java installed separately.

If you just want a **portable folder** (no installer, just an `.exe` you can
zip and share) instead of an installer package, use `--type app-image`
instead of `--type exe` — that produces a folder containing
`LinuxCommandCenter.exe` you can run directly.

## Project structure

```
src/main/java/com/linuxconsole/
  Main.java                  → JavaFX entry point
  ui/DashboardView.java       → window layout, buttons, output console
  ssh/SshService.java         → mock mode + real SSH (JSch), same contract
                                 as the original lib/ssh/sshService.ts
  ssh/ConnectionConfig.java   → host/port/username/password holder
  ssh/CommandResult.java      → success/output/error contract
```

## Mock mode vs real SSH

- The **"Mock mode"** checkbox in the top bar toggles `SshService.MOCK_MODE`
  at runtime — no server needed, useful for demoing the UI.
- Uncheck it, fill in Host/Port/Username/Password, and commands run for real
  over SSH via JSch.
- Same trade-off as the original project: host key verification is disabled
  (`StrictHostKeyChecking=no`) for easy setup. For production use, replace
  this with proper host key checking against a known_hosts store.

## Adding more preset command buttons

Edit the `presetCommands` array in `DashboardView.java`:

```java
private final String[][] presetCommands = {
        {"Disk Usage", "df -h"},
        {"Memory", "free -m"},
        // add more here, e.g.:
        {"Docker PS", "docker ps"},
};
```
