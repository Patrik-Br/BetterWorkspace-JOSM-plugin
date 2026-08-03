/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.openstreetmap.josm.data.Bounds
 *  org.openstreetmap.josm.data.ProjectionBounds
 *  org.openstreetmap.josm.data.coor.EastNorth
 *  org.openstreetmap.josm.data.coor.ILatLon
 *  org.openstreetmap.josm.data.coor.LatLon
 *  org.openstreetmap.josm.data.projection.Projecting
 *  org.openstreetmap.josm.data.projection.Projection
 */
package org.openstreetmap.josm.plugins.betterworkspace;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Projecting;
import org.openstreetmap.josm.data.projection.Projection;

public final class RotatingProjection
implements Projection {
    private final Projection base;
    private final double theta;
    private final EastNorth pivot;
    private final double cos;
    private final double sin;

    public RotatingProjection(Projection projection, double d, EastNorth eastNorth) {
        this.base = projection;
        this.theta = d;
        this.pivot = eastNorth;
        this.cos = Math.cos(d);
        this.sin = Math.sin(d);
    }

    public Projection getUnderlyingProjection() {
        return this.base;
    }

    public double getTheta() {
        return this.theta;
    }

    private EastNorth rotate(EastNorth eastNorth) {
        double d = eastNorth.east() - this.pivot.east();
        double d2 = eastNorth.north() - this.pivot.north();
        return new EastNorth(this.pivot.east() + d * this.cos - d2 * this.sin, this.pivot.north() + d * this.sin + d2 * this.cos);
    }

    private EastNorth unrotate(EastNorth eastNorth) {
        double d = eastNorth.east() - this.pivot.east();
        double d2 = eastNorth.north() - this.pivot.north();
        return new EastNorth(this.pivot.east() + d * this.cos + d2 * this.sin, this.pivot.north() - d * this.sin + d2 * this.cos);
    }

    public EastNorth latlon2eastNorth(ILatLon iLatLon) {
        return this.rotate(this.base.latlon2eastNorth(iLatLon));
    }

    public LatLon eastNorth2latlon(EastNorth eastNorth) {
        return this.base.eastNorth2latlon(this.unrotate(eastNorth));
    }

    public LatLon eastNorth2latlonClamped(EastNorth eastNorth) {
        return this.base.eastNorth2latlonClamped(this.unrotate(eastNorth));
    }

    public Projection getBaseProjection() {
        return this;
    }

    public Map<ProjectionBounds, Projecting> getProjectingsForArea(ProjectionBounds projectionBounds) {
        HashMap<ProjectionBounds, Projecting> hashMap = new HashMap<ProjectionBounds, Projecting>();
        hashMap.put(projectionBounds, (Projecting)this);
        return hashMap;
    }

    public double getDefaultZoomInPPD() {
        return this.base.getDefaultZoomInPPD();
    }

    public double getMetersPerUnit() {
        return this.base.getMetersPerUnit();
    }

    public boolean switchXY() {
        return this.base.switchXY();
    }

    public String toCode() {
        return this.base.toCode() + "-rot" + Long.toString(Math.round(Math.toDegrees(this.theta)));
    }

    public String toString() {
        return this.base.toString() + " \u21bb " + Math.round(Math.toDegrees(this.theta)) + "\u00b0";
    }

    public Bounds getWorldBoundsLatLon() {
        return this.base.getWorldBoundsLatLon();
    }

    public ProjectionBounds getWorldBoundsBoxEastNorth() {
        ProjectionBounds projectionBounds = new ProjectionBounds();
        this.visitOutline(this.getWorldBoundsLatLon(), arg_0 -> ((ProjectionBounds)projectionBounds).extend(arg_0));
        return projectionBounds;
    }

    public Bounds getLatLonBoundsBox(ProjectionBounds projectionBounds) {
        ProjectionBounds projectionBounds2 = new ProjectionBounds();
        projectionBounds2.extend(this.unrotate(new EastNorth(projectionBounds.minEast, projectionBounds.minNorth)));
        projectionBounds2.extend(this.unrotate(new EastNorth(projectionBounds.minEast, projectionBounds.maxNorth)));
        projectionBounds2.extend(this.unrotate(new EastNorth(projectionBounds.maxEast, projectionBounds.minNorth)));
        projectionBounds2.extend(this.unrotate(new EastNorth(projectionBounds.maxEast, projectionBounds.maxNorth)));
        return this.base.getLatLonBoundsBox(projectionBounds2);
    }

    public ProjectionBounds getEastNorthBoundsBox(ProjectionBounds projectionBounds, Projection projection) {
        EastNorth[] eastNorthArray;
        ProjectionBounds projectionBounds2 = new ProjectionBounds();
        for (EastNorth eastNorth : eastNorthArray = new EastNorth[]{new EastNorth(projectionBounds.minEast, projectionBounds.minNorth), new EastNorth(projectionBounds.minEast, projectionBounds.maxNorth), new EastNorth(projectionBounds.maxEast, projectionBounds.minNorth), new EastNorth(projectionBounds.maxEast, projectionBounds.maxNorth)}) {
            projectionBounds2.extend(this.latlon2eastNorth(projection.eastNorth2latlon(eastNorth)));
        }
        return projectionBounds2;
    }

    public void visitOutline(Bounds bounds, Consumer<EastNorth> consumer) {
        this.base.visitOutline(bounds, eastNorth -> consumer.accept(this.rotate((EastNorth)eastNorth)));
    }
}

