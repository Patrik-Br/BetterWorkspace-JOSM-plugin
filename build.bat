@echo off
REM Builds BetterWorkspace.jar from src/, compiled against lib/josm-tested.jar
REM Run from within the BetterWorkspace/ folder.
REM lib/josm-tested.jar is gitignored - download it from https://josm.openstreetmap.de/josm-tested.jar

if not exist lib\josm-tested.jar (
    echo lib\josm-tested.jar not found. Download it from https://josm.openstreetmap.de/josm-tested.jar
    exit /b 1
)

if exist build rmdir /s /q build
mkdir build
xcopy /s /e /i /y images build\images >nul

echo Compiling...
dir /s /b src\*.java > sources.txt
javac --release 17 -cp lib\josm-tested.jar -d build @sources.txt
if errorlevel 1 (
    del sources.txt
    echo Compilation failed.
    exit /b 1
)
del sources.txt

echo Packaging...
jar cfm BetterWorkspace.jar MANIFEST.MF -C build .

echo Done. Copy BetterWorkspace.jar to %%APPDATA%%\JOSM\plugins\ and restart JOSM.
