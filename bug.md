# Bug: imgui-java native library fails to load (JDK 25 + JPMS)

## Status: RESOLVED — replaced imgui-java with Nuklear (LWJGL built-in)

## Resolution

Replaced the `imgui-java` dependency entirely with LWJGL's bundled Nuklear binding.

### Architecture

```
UIRenderer (interface — HAL)
  └── NuklearUIRenderer  (abstract base)
        NK context, input, all widget calls
        Abstract hooks: uploadFontTexture(), createNullTexture(),
                        initRenderer(), renderCommands(), disposeRenderer()
        └── NuklearOpenGLRenderer  ← OpenGL 4.1 implementation
```

Future Vulkan support only requires a `NuklearVulkanRenderer` extending the same base.

### Files changed

- `core/build.gradle.kts` — removed `imgui-java-*`, added `lwjgl-nuklear` + natives
- `sandbox/build.gradle.kts` — removed `--enable-native-access=imgui.binding`
- `core/src/main/java/module-info.java` — swapped imgui requires for `org.lwjgl.nuklear`
- `core/…/ui/ImGuiUIRenderer.java` — deleted
- `core/…/ui/NuklearUIRenderer.java` — new abstract base
- `core/…/ui/NuklearOpenGLRenderer.java` — new OpenGL implementation
- `sandbox/…/SandboxApplication.java` — uses `NuklearOpenGLRenderer`

### Known quirk: native DLL extraction under Debug mode

`-Dorg.lwjgl.util.Debug=true` causes LWJGL's `SharedLibraryLoader` to skip JAR extraction.
LWJGL only finds natives from `org.lwjgl.librarypath` (the temp cache folder).
All previously used LWJGL DLLs were already in the cache; `lwjgl_nuklear.dll` was not.

**One-time fix** — manually extracted `lwjgl_nuklear.dll` from the natives JAR to the cache:
```powershell
$jar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.lwjgl\lwjgl-nuklear\3.4.0" -Filter "*natives-windows.jar" -Recurse | Select-Object -First 1
$dest = "C:\Users\Sandor\AppData\Local\Temp\lwjgl_Sandor\3.4.0+20\x64"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
$entry = $zip.Entries | Where-Object { $_.FullName -like "*lwjgl_nuklear.dll" } | Select-Object -First 1
[System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, "$dest\lwjgl_nuklear.dll", $true)
$zip.Dispose()
```

If the temp folder is cleared, re-run the above script.


## Symptom

```
Exception in thread "main" java.lang.UnsatisfiedLinkError: no imgui-java64 in java.library.path
    at imgui.binding@1.90.0/imgui.ImGui.<clinit>(ImGui.java:42)
    at hu.mudlee.core.ui.ImGuiUIRenderer.initialize(ImGuiUIRenderer.java:24)
```

## Environment

- JDK 25 (`Eclipse Adoptium jdk-25.0.2.10-hotspot`)
- JPMS modular project (via `org.javamodularity.moduleplugin 2.0.0`)
- imgui-java `1.90.0`
- `RenderBackend.OPENGL` (already switched from VULKAN for this plan step)

## Root Cause (partially diagnosed)

`ImGui.<clinit>` (line 42) calls `System.loadLibrary("imgui-java64")` directly. This is either:

1. **The only call** — the `imgui-java-binding` JAR does NOT bundle the native DLL, and a separate
   `imgui-java-natives-windows` artifact is required (like LWJGL's classifier-based natives).
2. **A fallback** — the JAR does bundle the DLL as a resource, but the in-JAR extraction silently
   fails in a JPMS module context (e.g. `getResourceAsStream` restricted, or `System.load`
   blocked by JDK 25's native-access enforcement), and the fallback `System.loadLibrary` then
   also fails because the DLL is not on `java.library.path`.

The `--enable-native-access=imgui.binding` JVM arg was added to `sandbox/build.gradle.kts` but
did **not** resolve the error, suggesting the native DLL is simply not available on the classpath/
module path at all (case 1 above).

## What was already tried

- Added `--enable-native-access=imgui.binding` to `engineJvmArgs` in `sandbox/build.gradle.kts` — **no effect**.

## Next steps to investigate / try (in order)

1. **Inspect the JAR** — confirm whether `imgui-java64.dll` is bundled inside
   `imgui-java-binding-1.90.0.jar` or not:
   ```powershell
   $jar = Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Filter "imgui-java-binding-*.jar" -Recurse | Select-Object -First 1
   Add-Type -AssemblyName System.IO.Compression.FileSystem
   $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
   $zip.Entries | Select-Object FullName | Sort-Object
   $zip.Dispose()
   ```
   Also check the `MANIFEST.MF` for `Automatic-Module-Name`.

2. **If DLL is NOT bundled** — add the platform-specific natives artifact to
   `core/build.gradle.kts` (alongside the existing imgui deps):
   ```kotlin
   val imguiNatives = when {
       lwjglNatives.startsWith("natives-windows") -> "imgui-java-natives-windows"
       lwjglNatives.startsWith("natives-linux") && lwjglNatives.contains("arm64") -> "imgui-java-natives-linux-arm64"
       lwjglNatives.startsWith("natives-linux")  -> "imgui-java-natives-linux"
       lwjglNatives.startsWith("natives-macos") && lwjglNatives.contains("arm64") -> "imgui-java-natives-macos-arm64"
       lwjglNatives.startsWith("natives-macos")  -> "imgui-java-natives-macos"
       else -> throw GradleException("Unsupported platform for imgui-java natives: $lwjglNatives")
   }
   runtimeOnly("io.github.spair", imguiNatives, imguiVersion)
   ```
   Then figure out how to expose the DLL to the JVM (either via `java.library.path` or
   `--patch-module imgui.binding=<natives-jar>`).

3. **If DLL IS bundled but extraction fails** — the JPMS loader cannot access the resource.
   Consider pre-loading the native in `ImGuiUIRenderer.initialize()` before any `ImGui.*` call,
   by getting the resource stream directly and extracting to a temp file:
   ```java
   var url = ImGuiUIRenderer.class.getClassLoader().getResource("imgui-java64.dll"); // adjust path
   // extract → System.load(tempPath)
   ```
   Also add `--enable-native-access=hu.mudlee.core` to JVM args.

4. **Nuclear option** — replace `imgui-java-binding` + `imgui-java-lwjgl3` with
   `imgui-java-app` (all-in-one artifact). This bundles everything and may have a more
   robust loader. Update `module-info.java` requires directive to match the new module name
   (inspect the app JAR's manifest first to find `Automatic-Module-Name`).
