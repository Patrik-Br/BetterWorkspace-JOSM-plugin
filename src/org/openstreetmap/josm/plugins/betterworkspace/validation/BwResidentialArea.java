package org.openstreetmap.josm.plugins.betterworkspace.validation;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;

/**
 * A landuse=residential area from either a closed way or a multipolygon relation
 * (outer/blank-role members stitched into rings; inner/hole members ignored).
 * Ported from MapathonQA-JOSM-plugin's ResidentialArea; geometry helpers inlined.
 */
final class BwResidentialArea {

    final OsmPrimitive primitive;
    final List<List<Node>> outerRings;

    private BwResidentialArea(OsmPrimitive primitive, List<List<Node>> outerRings) {
        this.primitive = primitive;
        this.outerRings = outerRings;
    }

    static List<BwResidentialArea> collectFromDataSet(DataSet ds) {
        List<BwResidentialArea> result = new ArrayList<>();

        for (Way w : ds.getWays()) {
            if (w.isDeleted() || w.isIncomplete()) continue;
            if (!w.isClosed()) continue;
            if (!"residential".equals(w.get("landuse"))) continue;
            List<List<Node>> rings = new ArrayList<>();
            rings.add(w.getNodes());
            result.add(new BwResidentialArea(w, rings));
        }

        for (Relation r : ds.getRelations()) {
            if (r.isDeleted() || r.isIncomplete()) continue;
            if (!"multipolygon".equals(r.get("type"))) continue;
            if (!"residential".equals(r.get("landuse"))) continue;
            List<Way> outerWays = new ArrayList<>();
            for (RelationMember m : r.getMembers()) {
                if (!m.isWay()) continue;
                String role = m.getRole();
                if (!"outer".equals(role) && !"".equals(role)) continue;
                Way mw = m.getWay();
                if (mw == null || mw.isDeleted() || mw.isIncomplete()) continue;
                outerWays.add(mw);
            }
            List<List<Node>> rings = stitchWaysIntoRings(outerWays);
            if (!rings.isEmpty()) result.add(new BwResidentialArea(r, rings));
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

    boolean containsNode(Node n) {
        if (n == null || n.getCoor() == null) return false;
        for (List<Node> ring : outerRings) {
            if (nodeInsidePolygon(n.lat(), n.lon(), ring)) return true;
        }
        return false;
    }

    boolean intersectsSegment(Node a, Node b) {
        for (List<Node> ring : outerRings) {
            for (int i = 0; i < ring.size() - 1; i++) {
                if (segmentsIntersect(a, b, ring.get(i), ring.get(i + 1))) return true;
            }
        }
        return false;
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

    // Ray-casting point-in-polygon (ported from MapathonQA GeometryUtil)
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

    // Cross-product segment intersection (ported from MapathonQA GeometryUtil)
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
