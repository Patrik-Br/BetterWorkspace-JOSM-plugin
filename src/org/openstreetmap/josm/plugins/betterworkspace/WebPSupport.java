package org.openstreetmap.josm.plugins.betterworkspace;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;

import org.openstreetmap.josm.tools.Logging;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;

/**
 * Built-in WebP imagery support, so .webp tile layers display in JOSM.
 * <p>
 * Java has no WebP decoder of its own, so TwelveMonkeys' decoder is bundled inside BetterWorkspace.jar
 * (see lib/twelvemonkeys/, merged in by build.bat/build.sh) and registered here at startup. Nothing is
 * downloaded at runtime, so it works offline and doesn't depend on any server.
 * <p>
 * Only the WebP reader is registered - deliberately not via the jars' {@code META-INF/services} files
 * (stripped by the build), which would also install TwelveMonkeys' replacement image-stream providers
 * globally and change how every other image in JOSM is read.
 */
final class WebPSupport {

    private WebPSupport() {
    }

    static void install() {
        // Returns false if this exact class was registered already. A WebP reader added by another
        // plugin is a different class (other class loader) - both coexisting is harmless, Java just
        // uses whichever it finds first.
        boolean added = IIORegistry.getDefaultInstance()
                .registerServiceProvider(new WebPImageReaderSpi(), ImageReaderSpi.class);
        if (added) {
            Logging.info("BetterWorkspace: WebP imagery support enabled (bundled TwelveMonkeys decoder)");
        }
    }
}
