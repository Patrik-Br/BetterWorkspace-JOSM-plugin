package org.openstreetmap.josm.plugins.betterworkspace;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Projecting;
import org.openstreetmap.josm.data.projection.Projection;

/**
 * Wraps another {@link Projection} and rotates every east/north coordinate it
 * produces or consumes around a fixed pivot point by a fixed angle. Backs
 * {@link BetterWorkspacePlugin}'s rotate/reset view actions: installing an
 * instance of this as the active projection makes JOSM draw the whole map
 * (data + imagery) rotated, since everything JOSM paints goes through the
 * active projection's east/north conversion.
 */
public final class RotatingProjection implements Projection {

    private final Projection base;
    private final double theta;
    private final EastNorth pivot;
    private final double cos;
    private final double sin;

    public RotatingProjection(Projection base, double theta, EastNorth pivot) {
        this.base = base;
        this.theta = theta;
        this.pivot = pivot;
        this.cos = Math.cos(theta);
        this.sin = Math.sin(theta);
    }

    public Projection getUnderlyingProjection() {
        return base;
    }

    public double getTheta() {
        return theta;
    }

    private EastNorth rotate(EastNorth p) {
        double dx = p.east() - pivot.east();
        double dy = p.north() - pivot.north();
        return new EastNorth(
                pivot.east() + dx * cos - dy * sin,
                pivot.north() + dx * sin + dy * cos);
    }

    private EastNorth unrotate(EastNorth p) {
        double dx = p.east() - pivot.east();
        double dy = p.north() - pivot.north();
        return new EastNorth(
                pivot.east() + dx * cos + dy * sin,
                pivot.north() - dx * sin + dy * cos);
    }

    @Override
    public EastNorth latlon2eastNorth(ILatLon ll) {
        return rotate(base.latlon2eastNorth(ll));
    }

    @Override
    public LatLon eastNorth2latlon(EastNorth en) {
        return base.eastNorth2latlon(unrotate(en));
    }

    @Override
    public LatLon eastNorth2latlonClamped(EastNorth en) {
        return base.eastNorth2latlonClamped(unrotate(en));
    }

    @Override
    public Projection getBaseProjection() {
        return this;
    }

    @Override
    public Map<ProjectionBounds, Projecting> getProjectingsForArea(ProjectionBounds area) {
        return Collections.singletonMap(area, this);
    }

    @Override
    public double getDefaultZoomInPPD() {
        return base.getDefaultZoomInPPD();
    }

    @Override
    public double getMetersPerUnit() {
        return base.getMetersPerUnit();
    }

    @Override
    public boolean switchXY() {
        return base.switchXY();
    }

    @Override
    public String toCode() {
        return base.toCode() + "-rot" + Math.round(Math.toDegrees(theta));
    }

    @Override
    public String toString() {
        return base.toString() + " ↻ " + Math.round(Math.toDegrees(theta)) + "°";
    }

    @Override
    public Bounds getWorldBoundsLatLon() {
        return base.getWorldBoundsLatLon();
    }

    @Override
    public ProjectionBounds getWorldBoundsBoxEastNorth() {
        ProjectionBounds bounds = new ProjectionBounds();
        visitOutline(getWorldBoundsLatLon(), bounds::extend);
        return bounds;
    }

    @Override
    public Bounds getLatLonBoundsBox(ProjectionBounds area) {
        ProjectionBounds unrotated = new ProjectionBounds();
        unrotated.extend(unrotate(new EastNorth(area.minEast, area.minNorth)));
        unrotated.extend(unrotate(new EastNorth(area.minEast, area.maxNorth)));
        unrotated.extend(unrotate(new EastNorth(area.maxEast, area.minNorth)));
        unrotated.extend(unrotate(new EastNorth(area.maxEast, area.maxNorth)));
        return base.getLatLonBoundsBox(unrotated);
    }

    @Override
    public ProjectionBounds getEastNorthBoundsBox(ProjectionBounds area, Projection fromProjection) {
        ProjectionBounds result = new ProjectionBounds();
        EastNorth[] corners = {
                new EastNorth(area.minEast, area.minNorth),
                new EastNorth(area.minEast, area.maxNorth),
                new EastNorth(area.maxEast, area.minNorth),
                new EastNorth(area.maxEast, area.maxNorth),
        };
        for (EastNorth corner : corners) {
            result.extend(latlon2eastNorth(fromProjection.eastNorth2latlon(corner)));
        }
        return result;
    }

    @Override
    public void visitOutline(Bounds area, Consumer<EastNorth> consumer) {
        base.visitOutline(area, p -> consumer.accept(rotate(p)));
    }
}
