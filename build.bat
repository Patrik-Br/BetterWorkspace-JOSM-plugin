@echo off
REM Builds BetterWorkspace.jar from src/, compiled against lib/josm-tested.jar
REM Run from within the BetterWorkspace/ folder.
REM lib/josm-tested.jar is gitignored - download it from https://josm.openstreetmap.de/josm-tested.jar
REM lib/twelvemonkeys/*.jar (WebP decoder, BSD-3-Clause) is committed and bundled into the plugin jar.

if not exist lib\josm-tested.jar (
    echo lib\josm-tested.jar not found. Download it from https://josm.openstreetmap.de/josm-tested.jar
    exit /b 1
)
if not exist lib\twelvemonkeys\imageio-webp-*.jar (
    echo lib\twelvemonkeys\ jars not found - they are needed for the built-in WebP support.
    exit /b 1
)

if exist build rmdir /s /q build
mkdir build
xcopy /s /e /i /y images build\images >nul

echo Compiling...
dir /s /b src\*.java > sources.txt
javac --release 17 -cp "lib\josm-tested.jar;lib\twelvemonkeys\*" -d build @sources.txt
if errorlevel 1 (
    del sources.txt
    echo Compilation failed.
    exit /b 1
)
del sources.txt

echo Bundling TwelveMonkeys WebP decoder...
pushd build
for %%J in (..\lib\twelvemonkeys\*.jar) do jar xf "%%J"
popd
REM Their manifests would clash with ours; their META-INF/services files would make Java auto-register
REM extra stream providers globally - WebPSupport.java registers just the WebP reader instead.
del /q build\META-INF\MANIFEST.MF
rmdir /s /q build\META-INF\services
copy /y lib\twelvemonkeys\LICENSE.txt build\META-INF\LICENSE-TwelveMonkeys.txt >nul

echo Packaging...
jar cfm BetterWorkspace.jar MANIFEST.MF -C build .
if errorlevel 1 (
    echo Packaging failed.
    exit /b 1
)

echo Done. Copy BetterWorkspace.jar to %%APPDATA%%\JOSM\plugins\ and restart JOSM.
