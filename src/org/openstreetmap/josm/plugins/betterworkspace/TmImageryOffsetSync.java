package org.openstreetmap.josm.plugins.betterworkspace;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.OffsetBookmark;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

/**
 * Auto-applies a HOT Tasking Manager (TM) project's imagery offset (set by the project manager;
 * today only iD honors it automatically - see hotosm/tasking-manager#6873) when a mapper locks a
 * task and opens it in JOSM via TM's own "open in JOSM" remote-control sequence. Stopgap until
 * JOSM core's {@code /imagery} remote-control handler grows an {@code offset} param.
 *
 * <p>TM's launch sends, among other remote-control calls, a {@code /load_and_zoom} with
 * {@code changeset_comment=<project's changeset comment>} - which JOSM's own
 * {@code LoadAndZoomHandler} applies via the public {@link DataSet#addChangeSetTag}, and which TM
 * defaults to {@code #hotosm-project-<id>}. This deliberately reads THAT (a structured, public
 * API) rather than the separate boundary layer TM also adds (named e.g. "Boundary for task: 12 of
 * TM Project #1234"), which is free-text UI wording that could change with JOSM's own
 * translations or a TM wording tweak.
 *
 * <p>Handles both possible arrival orders of the data layer (carrying the project id) and the
 * imagery layer (which the offset needs to attach to) by keeping a single "pending" offset slot,
 * applied as soon as both are known - matching a mapper working one task at a time.
 */
final class TmImageryOffsetSync {

    private static final String TM_API = "https://tasking-manager-production-api.hotosm.org/api/v2";
    private static final Pattern PROJECT_HASHTAG = Pattern.compile("#hotosm-project-(\\d+)", Pattern.CASE_INSENSITIVE);
    /** Matches an "offset=east,north" appended as a URL fragment - see {@link #applyEmbeddedUrlOffsetIfPresent}. */
    private static final Pattern URL_OFFSET = Pattern.compile("offset=(-?[\\d.]+),(-?[\\d.]+)", Pattern.CASE_INSENSITIVE);

    static final String PREF_ENABLED = "betterworkspace.tmimageryoffset.enabled";

    /** Newest fetched-but-not-yet-applied offset, or null. Single slot - one task open at a time is the normal workflow. */
    private static PendingOffset pending;

    private TmImageryOffsetSync() {
    }

    static void install() {
        MainApplication.getLayerManager().addLayerChangeListener(new LayerManager.LayerChangeListener() {
            @Override
            public void layerAdded(LayerManager.LayerAddEvent e) {
                Layer layer = e.getAddedLayer();
                if (layer instanceof OsmDataLayer) {
                    checkForProjectComment((OsmDataLayer) layer, 20);
                } else if (layer instanceof AbstractTileSourceLayer<?>) {
                    onImageryLayerAdded((AbstractTileSourceLayer<?>) layer);
                }
            }

            @Override
            public void layerRemoving(LayerManager.LayerRemoveEvent e) {
            }

            @Override
            public void layerOrderChanged(LayerManager.LayerOrderChangeEvent e) {
            }
        });
    }

    // =====================================================================
    //  Data layer side: find the project id from the changeset "comment" tag
    // =====================================================================

    /**
     * JOSM's LoadAndZoomHandler attaches the changeset tags via a background worker task AFTER
     * the layer already exists, so the tag may not be there yet the instant layerAdded fires -
     * same "wait for it" shape as this plugin's other hooks (e.g. TodoBehaviorSync).
     */
    private static void checkForProjectComment(OsmDataLayer layer, int retriesLeft) {
        if (!Config.getPref().getBoolean(PREF_ENABLED, true)) {
            return;
        }
        if (!MainApplication.getLayerManager().containsLayer(layer)) {
            return; // closed again already
        }
        DataSet ds = layer.getDataSet();
        String comment = ds == null ? null : ds.getChangeSetTags().get("comment");
        Matcher m = comment == null ? null : PROJECT_HASHTAG.matcher(comment);
        if (m != null && m.find()) {
            int projectId = Integer.parseInt(m.group(1));
            fetchOffset(projectId, taskCenterBounds(ds));
            return;
        }
        if (retriesLeft <= 0) {
            return; // not a TM-opened layer (or the tag never arrived) - nothing to do
        }
        Timer timer = new Timer(250, null);
        timer.addActionListener(e -> {
            timer.stop();
            checkForProjectComment(layer, retriesLeft - 1);
        });
        timer.setRepeats(false);
        timer.start();
    }

    /** The task's own downloaded extent when available, falling back to the current map view. */
    private static Bounds taskCenterBounds(DataSet ds) {
        List<Bounds> sourceBounds = ds.getDataSourceBounds();
        if (!sourceBounds.isEmpty()) {
            return sourceBounds.get(0);
        }
        return currentViewBounds();
    }

    private static Bounds currentViewBounds() {
        MapFrame map = MainApplication.getMap();
        return map != null && map.mapView != null ? map.mapView.getRealBounds() : null;
    }

    // =====================================================================
    //  TM API fetch: offset (from extraIdParams) + which imagery to attach it to
    // =====================================================================

    private static void fetchOffset(int projectId, Bounds taskBounds) {
        MainApplication.worker.submit(() -> {
            try {
                JsonObject project = fetchJson(TM_API + "/projects/" + projectId + "/");
                String extraIdParams = project.isNull("extraIdParams") ? null : project.getString("extraIdParams", null);
                String imageryRef = project.isNull("imagery") ? null : project.getString("imagery", null);
                double[] offsetMeters = parseOffsetMeters(extraIdParams);
                if (offsetMeters == null || imageryRef == null || imageryRef.isEmpty()) {
                    return; // no offset configured for this project (the common case) - nothing to do
                }
                Bounds bounds = taskBounds;
                if (bounds == null) {
                    return; // no usable center to convert the meters offset against
                }
                LatLon center = bounds.getCenter();
                EastNorth displacement = metersToDisplacement(offsetMeters);
                PendingOffset p = new PendingOffset(projectId, imageryRef, displacement, center);
                SwingUtilities.invokeLater(() -> onOffsetReady(p));
            } catch (IOException | RuntimeException ex) {
                Logging.warn("BetterWorkspace: could not fetch TM project #" + projectId + " for its imagery offset: " + ex);
            }
        });
    }

    /** Parses {@code offset=east,north} (meters) out of a query-string-like extraIdParams value, e.g. {@code disabled_features=buildings&offset=-10,5}. */
    private static double[] parseOffsetMeters(String extraIdParams) {
        if (extraIdParams == null || extraIdParams.isEmpty()) {
            return null;
        }
        for (String part : extraIdParams.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0 || !"offset".equals(part.substring(0, eq))) {
                continue;
            }
            String[] xy = part.substring(eq + 1).split(",");
            if (xy.length != 2) {
                return null;
            }
            try {
                return new double[] {Double.parseDouble(xy[0].trim()), Double.parseDouble(xy[1].trim())};
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * TM/iD's "offset=east,north" is defined directly in Web Mercator meters (iD is always
     * Mercator-based internally), NOT geodesic ground meters - so this is just the displacement,
     * no reprojection needed.
     *
     * <p>An earlier version of this converted the meters into a geodesic lat/lon shift (correctly
     * applying cos(latitude) once, for the east/longitude component) and then reprojected that
     * through the current projection - but Mercator is conformal, so it applies its OWN
     * 1/cos(latitude) scale factor again, on BOTH axes equally. Two corrections stacked into one,
     * uniformly inflating the result away from the equator - confirmed empirically (consistently
     * ~2.7% off on both east and north, matching 1/cos(lat) for the test latitude).
     */
    private static EastNorth metersToDisplacement(double[] offsetMeters) {
        return new EastNorth(offsetMeters[0], offsetMeters[1]);
    }

    private static JsonObject fetchJson(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "BetterWorkspace-JOSMPlugin/1.1.2");
        String auth = TmApiToken.authorizationHeader();
        if (auth != null) {
            conn.setRequestProperty("Authorization", auth);
        }
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("TM API returned HTTP " + code);
        }
        try (InputStream in = conn.getInputStream(); JsonReader reader = Json.createReader(in)) {
            return reader.readObject();
        }
    }

    // =====================================================================
    //  Imagery layer side: apply once both the offset and the matching layer are known
    // =====================================================================

    private static void onOffsetReady(PendingOffset p) {
        pending = p;
        AbstractTileSourceLayer<?> existing = findMatchingImageryLayer(p.imageryRef);
        if (existing != null) {
            applyOffset(existing, p);
        }
        // else: the /imagery remote-control call hasn't landed yet - onImageryLayerAdded picks it up.
    }

    private static void onImageryLayerAdded(AbstractTileSourceLayer<?> layer) {
        if (applyEmbeddedUrlOffsetIfPresent(layer)) {
            return; // handled straight from the URL - no TM API call, so no token needed either
        }
        PendingOffset p = pending;
        if (p != null && matchesImagery(layer.getInfo(), p.imageryRef)) {
            applyOffset(layer, p);
        }
    }

    /**
     * Zero-auth alternative to the TM-API flow above: if the imagery URL itself (set in TM's
     * Imagery tab) ends with {@code #offset=east,north}, apply it directly - no TM API call, so
     * it works for private projects without anyone's 7-day-expiring API token. The fragment is
     * inert for actual tile loading: JOSM builds a real {@code java.net.URL} from the template
     * (confirmed via TMSCachedTileLoaderJob) and a URL fragment is never sent to the tile server,
     * only ever read client-side - and template placeholder substitution only touches
     * {@code {z}}/{@code {x}}/{@code {y}}-style tokens, leaving a trailing fragment untouched.
     *
     * <p>Checked before the API-based path, but doesn't replace it - projects that use the
     * "Additional iD URL parameters" field instead still work the other way.
     */
    private static boolean applyEmbeddedUrlOffsetIfPresent(AbstractTileSourceLayer<?> layer) {
        if (!Config.getPref().getBoolean(PREF_ENABLED, true)) {
            return false;
        }
        String url = layer.getInfo().getUrl();
        int hash = url == null ? -1 : url.indexOf('#');
        if (hash < 0) {
            return false;
        }
        Matcher m = URL_OFFSET.matcher(url.substring(hash + 1));
        if (!m.find()) {
            return false;
        }
        Bounds bounds = currentViewBounds();
        if (bounds == null) {
            return false;
        }
        try {
            double[] offsetMeters = {Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))};
            LatLon center = bounds.getCenter();
            EastNorth displacement = metersToDisplacement(offsetMeters);
            applyOffset(layer, new PendingOffset(null, url, displacement, center));
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static AbstractTileSourceLayer<?> findMatchingImageryLayer(String imageryRef) {
        for (Layer l : MainApplication.getLayerManager().getLayers()) {
            if (l instanceof AbstractTileSourceLayer<?> tsl && matchesImagery(tsl.getInfo(), imageryRef)) {
                return tsl;
            }
        }
        return null;
    }

    /**
     * TM's {@code project.imagery} is either a named provider's id (matches
     * {@link ImageryInfo#getId()} or its display name), or - for a custom TMS/WMS URL - the URL
     * itself (matches {@link ImageryInfo#getUrl()}). Checking all three avoids needing to
     * pre-classify which kind of string it is.
     */
    private static boolean matchesImagery(ImageryInfo info, String imageryRef) {
        if (info == null || imageryRef == null) {
            return false;
        }
        return imageryRef.equals(info.getId()) || imageryRef.equalsIgnoreCase(info.getName()) || imageryRef.equals(info.getUrl());
    }

    private static void applyOffset(AbstractTileSourceLayer<?> layer, PendingOffset p) {
        pending = null;
        String projectLabel = p.projectId == null ? I18n.tr("TM imagery offset") : I18n.tr("TM #{0} offset", String.valueOf(p.projectId));
        OffsetBookmark bookmark = new OffsetBookmark(
                ProjectionRegistry.getProjection().toCode(),
                layer.getInfo().getId(), layer.getInfo().getName(),
                projectLabel, p.displacement, p.center);
        layer.getDisplaySettings().setOffsetBookmark(bookmark);
        String message = p.projectId == null
                ? I18n.tr("Applied the imagery''s embedded offset.")
                : I18n.tr("Applied HOT Tasking Manager project #{0}''s imagery offset.", String.valueOf(p.projectId));
        new Notification(message)
                .setIcon(JOptionPane.INFORMATION_MESSAGE)
                .setDuration(Notification.TIME_SHORT)
                .show();
        Logging.info("BetterWorkspace: applied imagery offset ({0}) - {1}", p.displacement, projectLabel);
    }

    private static final class PendingOffset {
        final Integer projectId;
        final String imageryRef;
        final EastNorth displacement;
        final LatLon center;

        PendingOffset(Integer projectId, String imageryRef, EastNorth displacement, LatLon center) {
            this.projectId = projectId;
            this.imageryRef = imageryRef;
            this.displacement = displacement;
            this.center = center;
        }
    }
}
