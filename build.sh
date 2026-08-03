#!/bin/bash
# Builds BetterWorkspace.jar from src/, compiled against lib/josm-tested.jar
# Run from within the BetterWorkspace/ folder.
# lib/josm-tested.jar is gitignored - download it from https://josm.openstreetmap.de/josm-tested.jar
set -e

if [ ! -f lib/josm-tested.jar ]; then
    echo "lib/josm-tested.jar not found. Download it from https://josm.openstreetmap.de/josm-tested.jar"
    exit 1
fi

rm -rf build
mkdir -p build
cp -r images build/images

echo "Compiling..."
find src -name "*.java" > .sources.txt
javac --release 17 -cp lib/josm-tested.jar -d build @.sources.txt
rm -f .sources.txt

echo "Packaging..."
jar cfm BetterWorkspace.jar MANIFEST.MF -C build .

echo "Done. Copy BetterWorkspace.jar to ~/.local/share/JOSM/plugins/ and restart JOSM."
