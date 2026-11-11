#!/bin/bash
# Builds BetterWorkspace.jar from src/, compiled against lib/josm-tested.jar
# Run from within the BetterWorkspace/ folder.
# lib/josm-tested.jar is gitignored - download it from https://josm.openstreetmap.de/josm-tested.jar
# lib/twelvemonkeys/*.jar (WebP decoder, BSD-3-Clause) is committed and bundled into the plugin jar.
set -e

if [ ! -f lib/josm-tested.jar ]; then
    echo "lib/josm-tested.jar not found. Download it from https://josm.openstreetmap.de/josm-tested.jar"
    exit 1
fi
if ! ls lib/twelvemonkeys/imageio-webp-*.jar >/dev/null 2>&1; then
    echo "lib/twelvemonkeys/ jars not found - they are needed for the built-in WebP support."
    exit 1
fi

rm -rf build
mkdir -p build
cp -r images build/images

echo "Compiling..."
find src -name "*.java" > .sources.txt
javac --release 17 -cp "lib/josm-tested.jar:lib/twelvemonkeys/*" -d build @.sources.txt
rm -f .sources.txt

echo "Bundling TwelveMonkeys WebP decoder..."
for j in lib/twelvemonkeys/*.jar; do
    (cd build && jar xf "../$j")
done
# Their manifests would clash with ours; their META-INF/services files would make Java auto-register
# extra stream providers globally - WebPSupport.java registers just the WebP reader instead.
rm -f build/META-INF/MANIFEST.MF
rm -rf build/META-INF/services
cp lib/twelvemonkeys/LICENSE.txt build/META-INF/LICENSE-TwelveMonkeys.txt

echo "Packaging..."
jar cfm BetterWorkspace.jar MANIFEST.MF -C build .

echo "Done. Copy BetterWorkspace.jar to ~/.local/share/JOSM/plugins/ and restart JOSM."
