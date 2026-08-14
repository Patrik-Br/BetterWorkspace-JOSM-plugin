package org.openstreetmap.josm.plugins.betterworkspace.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;

/**
 * A landuse=* area (any value, unlike {@link BwResidentialArea} which is residential-only)
 * from either a closed way or a multipolygon relation, keeping both outer AND inner (hole)
 * rings.
 *
 * <p>Holes matter for overlap detection: a common real-world pattern is a large area (e.g.
 * landuse=farmland) with a hole cut out exactly where a separately-tagged area (e.g. a
 * landuse=residential village) sits - often reusing the same way as both the hole's inner
 * ring and the inner area's own outline. A check that only looks at outer rings sees the
 * inner area's nodes as "inside" the outer shape and misreports it as overlapping, when the
 * hole means there's no real overlap at all.
 */
final class BwLanduseArea {

    final OsmPrimitive primitive;
    final String landuse;
    final List<List<Node>> outerRings;
    final List<List<Node>> innerRings;
    /** Every node used by any outer or inner ring, for shared-boundary (touching, not overlapping) detection. */
    final Set<Node> boundaryNodes;

    private BwLanduseArea(OsmPrimitive primitive, String landuse,
            List<List<Node>> outerRings, List<List<Node>> innerRings) {
        this.primitive = primitive;
        this.landuse = landuse;
        this.outerRings = outerRings;
        this.innerRings = innerRings;
        this.boundaryNodes = new HashSet<>();
        for (List<Node> ring : outerRings) boundaryNodes.addAll(ring);
        for (List<Node> ring : innerRings) boundaryNodes.addAll(ring);
    }

    static List<BwLanduseArea> collectFromDataSet(DataSet ds) {
        List<BwLanduseArea> result = new ArrayList<>();

        for (Way w : ds.getWays()) {
            if (w.isDeleted() || w.isIncomplete()) continue;
            if (!w.isClosed()) continue;
            String landuse = w.get("landuse");
            if (landuse == null) continue;
            List<List<Node>> rings = new ArrayList<>();
            rings.add(w.getNodes());
            result.add(new BwLanduseArea(w, landuse, rings, Collections.emptyList()));
        }

        for (Relation r : ds.getRelations()) {
            if (r.isDeleted() || r.isIncomplete()) continue;
            if (!"multipolygon".equals(r.get("type"))) continue;
            String landuse = r.get("landuse");
            if (landuse == null) continue;
            List<Way> outerWays = new ArrayList<>();
            List<Way> innerWays = new ArrayList<>();
            for (RelationMember m : r.getMembers()) {
                if (!m.isWay()) continue;
                Way mw = m.getWay();
                if (mw == null || mw.isDeleted() || mw.isIncomplete()) continue;
                String role = m.getRole();
                if ("inner".equals(role)) {
                    innerWays.add(mw);
                } else if ("outer".equals(role) || "".equals(role)) {
                    outerWays.add(mw);
                }
            }
            List<List<Node>> outerRings = stitchWaysIntoRings(outerWays);
            List<List<Node>> innerRings = stitchWaysIntoRings(innerWays);
            if (!outerRings.isEmpty()) {
                result.add(new BwLanduseArea(r, landuse, outerRings, innerRings));
            }
        }
        return result;
    }

    /** Chains way node-lists end-to-end (matching by node identity) into closed rings. Open/unstitchable chains are dropped. */
    private static List<List<Node>> stitchWaysIntoRings(List<Way> ways) {
        List<List<Node>> rings = new ArrayList<>();
        List<Way> remaining = new ArrayList<>(ways);
        while (!remaining.isEmpty()) {
            Way w = remaining.remove(0);
            List<Node> ring = new ArrayList<>(w.getNodes());
            if (w.isClosed()) { rings.add(ring); continue; }

            boolean changed = true;
            while (changed && !remaining.isEmpty()) {
                changed = false;
                Node ringLast = ring.get(ring.size() - 1);
                for (int i = 0; i < remaining.size(); i++) {
                    Way cand = remaining.get(i);
                    List<Node> cn = cand.getNodes();
                    Node candFirst = cn.get(0);
                    Node candLast  = cn.get(cn.size() - 1);
                    if (candFirst == ringLast) {
                        for (int k = 1; k < cn.size(); k++) ring.add(cn.get(k));
                        remaining.remove(i); changed = true; break;
                    } else if (candLast == ringLast) {
                        for (int k = cn.size() - 2; k >= 0; k--) ring.add(cn.get(k));
                        remaining.remove(i); changed = true; break;
                    }
                }
                if (ring.get(0) == ring.get(ring.size() - 1)) break;
            }
            if (ring.size() >= 3 && ring.get(0) == ring.get(ring.size() - 1)) {
                rings.add(ring);
            }
        }
        return rings;
    }

    BBox getBBox() {
        BBox bbox = new BBox();
        for (List<Node> ring : outerRings) {
            for (Node n : ring) {
                if (n != null && n.getCoor() != null) bbox.add(n.getCoor());
            }
        }
        return bbox;
    }

    /** Hole-aware containment: inside an outer ring AND not inside any inner (hole) ring. */
    boolean containsPoint(double lat, double lon) {
        boolean insideOuter = false;
        for (List<Node> ring : outerRings) {
            if (nodeInsidePolygon(lat, lon, ring)) { insideOuter = true; break; }
        }
        if (!insideOuter) return false;
        for (List<Node> ring : innerRings) {
            if (nodeInsidePolygon(lat, lon, ring)) return false;
        }
        return true;
    }

    /**
     * True if any boundary vertex of {@code other} is genuinely inside this area's true
     * (hole-subtracted) extent. Vertices that are also one of this area's own boundary nodes
     * are skipped - those are shared-boundary points (e.g. {@code other} exactly fills a hole
     * cut into this area), not real overlap, and exact-boundary point-in-polygon tests are
     * numerically unreliable anyway.
     */
    boolean strictlyContainsAnyVertexOf(BwLanduseArea other) {
        for (List<Node> ring : other.allRings()) {
            for (Node n : ring) {
                if (n == null || n.getCoor() == null) continue;
                if (boundaryNodes.contains(n)) continue;
                if (containsPoint(n.lat(), n.lon())) return true;
            }
        }
        return false;
    }

    /** True if any edge of this area's rings genuinely crosses (not merely touches or runs along) any edge of {@code other}'s rings. */
    boolean strictlyCrosses(BwLanduseArea other) {
        for (List<Node> ringA : allRings()) {
            for (int i = 0; i < ringA.size() - 1; i++) {
                Node a1 = ringA.get(i), a2 = ringA.get(i + 1);
                for (List<Node> ringB : other.allRings()) {
                    for (int j = 0; j < ringB.size() - 1; j++) {
                        if (segmentsIntersect(a1, a2, ringB.get(j), ringB.get(j + 1))) return true;
                    }
                }
            }
        }
        return false;
    }

    List<List<Node>> allRings() {
        List<List<Node>> all = new ArrayList<>(outerRings);
        all.addAll(innerRings);
        return all;
    }

    // Ray-casting point-in-polygon (ported from MapathonQA GeometryUtil, as in BwResidentialArea)
    private static boolean nodeInsidePolygon(double lat, double lon, List<Node> ring) {
        int n = ring.size();
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Node ni = ring.get(i);
            Node nj = ring.get(j);
            if (ni == null || nj == null) continue;
            double xi = ni.lon(), yi = ni.lat(), xj = nj.lon(), yj = nj.lat();
            if (((yi > lat) != (yj > lat)) && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi))
                inside = !inside;
        }
        return inside;
    }

    // Cross-product segment intersection (ported from MapathonQA GeometryUtil, as in BwResidentialArea).
    // Strict inequalities (> / <) mean touching endpoints or collinear/overlapping shared edges
    // are NOT reported as an intersection - only a genuine crossing is.
    private static boolean segmentsIntersect(Node a1, Node a2, Node b1, Node b2) {
        if (a1 == null || a2 == null || b1 == null || b2 == null) return false;
        double ax1 = a1.lon(), ay1 = a1.lat(), ax2 = a2.lon(), ay2 = a2.lat();
        double bx1 = b1.lon(), by1 = b1.lat(), bx2 = b2.lon(), by2 = b2.lat();
        double d1 = cross(bx2-bx1, by2-by1, ax1-bx1, ay1-by1);
        double d2 = cross(bx2-bx1, by2-by1, ax2-bx1, ay2-by1);
        double d3 = cross(ax2-ax1, ay2-ay1, bx1-ax1, by1-ay1);
        double d4 = cross(ax2-ax1, ay2-ay1, bx2-ax1, by2-ay1);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double cross(double ux, double uy, double vx, double vy) {
        return ux * vy - uy * vx;
    }
}
