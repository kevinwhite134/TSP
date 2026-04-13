package org.example.view;

import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import org.example.model.PointNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class TSPCanvas extends Pane {

    private static final double INTERSECTION_EPS = 1e-9;
    private static final double DEDUP_EPS_PX = 0.75;

    private final List<Circle> pointCircles = new ArrayList<>();
    private final List<Text> pointLabels = new ArrayList<>();
    private final List<Line> tourLines = new ArrayList<>();
    private final List<Line> hullLines = new ArrayList<>();
    private final List<Circle> hullMarkers = new ArrayList<>();
    private final List<Line> rayLines = new ArrayList<>();

    // circles centered at COM, radius = dist(point, COM)
    private final List<Circle> comCenteredCircles = new ArrayList<>();
    private Circle averageComCircle = null;
    private Circle rmsComCircle = null;

    private final List<Circle> averageIntersectionMarkers = new ArrayList<>();
    private final List<Circle> rmsIntersectionMarkers = new ArrayList<>();

    // angle labels/arcs on canvas
    private final List<Text> angleTexts = new ArrayList<>();
    private final List<Arc> angleArcs = new ArrayList<>();

    private Circle comDot = null;
    private Circle outerCircle = null;

    private final List<PointNode> points = new ArrayList<>();

    private Consumer<Point2D> onLeftClickAdd = null;
    private Consumer<Point2D> onRightClick = null;

    public TSPCanvas(double w, double h) {
        setPrefSize(w, h);
        setMinSize(w, h);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setStyle("-fx-background-color: #111; -fx-border-color: #2b2b2b; -fx-border-width: 1;");

        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                if (onLeftClickAdd != null) onLeftClickAdd.accept(new Point2D(e.getX(), e.getY()));
            } else if (e.getButton() == MouseButton.SECONDARY) {
                if (onRightClick != null) onRightClick.accept(new Point2D(e.getX(), e.getY()));
            }
        });
    }

    public void setOnLeftClickAdd(Consumer<Point2D> handler) {
        this.onLeftClickAdd = handler;
    }

    public void setOnRightClick(Consumer<Point2D> handler) {
        this.onRightClick = handler;
    }

    public List<PointNode> getPoints() {
        return new ArrayList<>(points);
    }

    public void setPoints(List<PointNode> pts) {
        points.clear();
        points.addAll(pts);
        redrawPoints();
    }

    public void addPoint(PointNode p) {
        points.add(p);
        addPointVisual(points.size() - 1, p);
    }

    public void clearPointsOnly() {
        for (Circle c : pointCircles) getChildren().remove(c);
        pointCircles.clear();

        for (Text t : pointLabels) getChildren().remove(t);
        pointLabels.clear();

        clearComCenteredCircles();
        clearAverageComCircle();
        clearRmsComCircle();
        clearAverageIntersections();
        clearRmsIntersections();
        clearAngleLabels();
        clearConvexHullOverlay();

        points.clear();

        clearTourLines();
        clearRays();

        if (comDot != null) {
            getChildren().remove(comDot);
            comDot = null;
        }
        if (outerCircle != null) {
            getChildren().remove(outerCircle);
            outerCircle = null;
        }
    }

    public void setComDot(Point2D com) {
        if (comDot != null) getChildren().remove(comDot);

        comDot = new Circle(com.getX(), com.getY(), 4);
        comDot.setFill(Color.web("#ffd166"));
        getChildren().add(comDot);
    }

    public void setOuterCircle(Point2D com, double radius) {
        if (outerCircle != null) getChildren().remove(outerCircle);

        outerCircle = new Circle(com.getX(), com.getY(), radius);
        outerCircle.setFill(Color.TRANSPARENT);
        outerCircle.setStroke(Color.web("#444"));
        outerCircle.setStrokeWidth(2);

        getChildren().add(0, outerCircle);
    }

    public void setRaysFromCom(Point2D com, double radius) {
        setRaysFromCom(com, radius, false);
    }

    public void setRaysFromCom(Point2D com, double radius, boolean extendThroughCom) {
        clearRays();
        if (points.isEmpty()) return;

        for (PointNode p : points) {
            double dx = p.x() - com.getX();
            double dy = p.y() - com.getY();
            double mag = Math.sqrt(dx * dx + dy * dy);
            if (mag == 0) continue;

            double ux = dx / mag;
            double uy = dy / mag;

            double startX, startY, endX, endY;

            if (extendThroughCom) {
                startX = com.getX() - ux * radius;
                startY = com.getY() - uy * radius;
                endX = com.getX() + ux * radius;
                endY = com.getY() + uy * radius;
            } else {
                startX = com.getX();
                startY = com.getY();
                endX = com.getX() + ux * radius;
                endY = com.getY() + uy * radius;
            }

            Line ray = new Line(startX, startY, endX, endY);
            ray.setStroke(Color.web("#666"));
            ray.setStrokeWidth(1.3);
            ray.setOpacity(0.85);

            rayLines.add(ray);
        }

        if (outerCircle != null) {
            int circleIndex = getChildren().indexOf(outerCircle);
            int insertIndex = Math.max(0, circleIndex + 1);
            getChildren().addAll(insertIndex, rayLines);
        } else {
            getChildren().addAll(0, rayLines);
        }
    }

    public void clearRays() {
        for (Line l : rayLines) getChildren().remove(l);
        rayLines.clear();
    }

    // COM-centered circles (center = COM, radius = dist(point, COM))
    public void showComCenteredCircles(Point2D com) {
        clearComCenteredCircles();
        if (points.isEmpty()) return;

        for (PointNode p : points) {
            double dx = p.x() - com.getX();
            double dy = p.y() - com.getY();
            double r = Math.sqrt(dx * dx + dy * dy);

            Circle c = new Circle(com.getX(), com.getY(), r);
            c.setFill(Color.TRANSPARENT);
            c.setStroke(Color.web("#2f2f2f"));
            c.setStrokeWidth(1.0);
            c.setOpacity(0.55);

            comCenteredCircles.add(c);
        }

        int idx = 0;
        if (outerCircle != null) idx = Math.max(0, getChildren().indexOf(outerCircle) + 1);
        getChildren().addAll(idx, comCenteredCircles);
    }

    public void clearComCenteredCircles() {
        for (Circle c : comCenteredCircles) getChildren().remove(c);
        comCenteredCircles.clear();
    }

    public void showAverageComCircle(Point2D com, double radius) {
        clearAverageComCircle();
        if (!Double.isFinite(radius) || radius <= 0) return;

        averageComCircle = new Circle(com.getX(), com.getY(), radius);
        averageComCircle.setFill(Color.TRANSPARENT);
        averageComCircle.setStroke(Color.web("#1e3a8a"));
        averageComCircle.setStrokeWidth(2.0);
        averageComCircle.setOpacity(0.95);

        int idx = 0;
        if (outerCircle != null) idx = Math.max(0, getChildren().indexOf(outerCircle) + 1);
        getChildren().add(idx, averageComCircle);
    }

    public void clearAverageComCircle() {
        if (averageComCircle != null) {
            getChildren().remove(averageComCircle);
            averageComCircle = null;
        }
    }

    public void showRmsComCircle(Point2D com, double radius) {
        clearRmsComCircle();
        if (!Double.isFinite(radius) || radius <= 0) return;

        rmsComCircle = new Circle(com.getX(), com.getY(), radius);
        rmsComCircle.setFill(Color.TRANSPARENT);
        rmsComCircle.setStroke(Color.web("#ff4fa3"));
        rmsComCircle.setStrokeWidth(2.0);
        rmsComCircle.setOpacity(0.95);

        int idx = 0;
        if (outerCircle != null) idx = Math.max(0, getChildren().indexOf(outerCircle) + 1);
        getChildren().add(idx, rmsComCircle);
    }

    public void clearRmsComCircle() {
        if (rmsComCircle != null) {
            getChildren().remove(rmsComCircle);
            rmsComCircle = null;
        }
    }

    public void showAverageIntersections(List<Integer> order, boolean closedLoop, Point2D com, double radius) {
        clearAverageIntersections();
        showIntersections(order, closedLoop, com, radius, Color.web("#1e3a8a"), averageIntersectionMarkers);
    }

    public void clearAverageIntersections() {
        for (Circle m : averageIntersectionMarkers) getChildren().remove(m);
        averageIntersectionMarkers.clear();
    }

    public void showRmsIntersections(List<Integer> order, boolean closedLoop, Point2D com, double radius) {
        clearRmsIntersections();
        showIntersections(order, closedLoop, com, radius, Color.web("#ff4fa3"), rmsIntersectionMarkers);
    }

    public void clearRmsIntersections() {
        for (Circle m : rmsIntersectionMarkers) getChildren().remove(m);
        rmsIntersectionMarkers.clear();
    }

    private void showIntersections(List<Integer> order,
                                   boolean closedLoop,
                                   Point2D com,
                                   double radius,
                                   Color color,
                                   List<Circle> outMarkers) {
        if (order == null || order.size() < 2 || points.isEmpty()) return;
        if (!Double.isFinite(radius) || radius <= 0) return;

        List<Point2D> hits = new ArrayList<>();

        int limit = order.size() - 1;
        for (int i = 0; i < limit; i++) {
            addSegmentCircleIntersections(order.get(i), order.get(i + 1), com, radius, hits);
        }
        if (closedLoop && order.size() > 2) {
            addSegmentCircleIntersections(order.get(order.size() - 1), order.get(0), com, radius, hits);
        }

        dedupeIntersections(hits);

        for (Point2D h : hits) {
            Circle marker = new Circle(h.getX(), h.getY(), 4.0);
            marker.setFill(color);
            marker.setStroke(color);
            marker.setStrokeWidth(1.0);
            outMarkers.add(marker);
        }

        getChildren().addAll(outMarkers);
    }

    private void addSegmentCircleIntersections(int ia, int ib, Point2D com, double radius, List<Point2D> out) {
        if (!validIndex(ia) || !validIndex(ib)) return;

        PointNode a = points.get(ia);
        PointNode b = points.get(ib);

        double ax = a.x() - com.getX();
        double ay = a.y() - com.getY();
        double bx = b.x() - com.getX();
        double by = b.y() - com.getY();

        double dx = bx - ax;
        double dy = by - ay;

        double A = dx * dx + dy * dy;
        if (A <= INTERSECTION_EPS) return;

        double B = 2.0 * (ax * dx + ay * dy);
        double C = ax * ax + ay * ay - radius * radius;

        double disc = B * B - 4.0 * A * C;
        if (disc < -INTERSECTION_EPS) return;

        if (Math.abs(disc) <= INTERSECTION_EPS) {
            double t = -B / (2.0 * A);
            if (t >= -INTERSECTION_EPS && t <= 1.0 + INTERSECTION_EPS) {
                out.add(new Point2D(a.x() + t * (b.x() - a.x()), a.y() + t * (b.y() - a.y())));
            }
            return;
        }

        double sqrt = Math.sqrt(Math.max(0.0, disc));
        double t1 = (-B - sqrt) / (2.0 * A);
        double t2 = (-B + sqrt) / (2.0 * A);

        if (t1 >= -INTERSECTION_EPS && t1 <= 1.0 + INTERSECTION_EPS) {
            out.add(new Point2D(a.x() + t1 * (b.x() - a.x()), a.y() + t1 * (b.y() - a.y())));
        }
        if (t2 >= -INTERSECTION_EPS && t2 <= 1.0 + INTERSECTION_EPS) {
            out.add(new Point2D(a.x() + t2 * (b.x() - a.x()), a.y() + t2 * (b.y() - a.y())));
        }
    }

    private void dedupeIntersections(List<Point2D> points2d) {
        List<Point2D> unique = new ArrayList<>();
        for (Point2D p : points2d) {
            boolean seen = false;
            for (Point2D q : unique) {
                if (p.distance(q) <= DEDUP_EPS_PX) {
                    seen = true;
                    break;
                }
            }
            if (!seen) unique.add(p);
        }
        points2d.clear();
        points2d.addAll(unique);
    }

    public void clearAngleLabels() {
        for (Arc a : angleArcs) getChildren().remove(a);
        angleArcs.clear();

        for (Text t : angleTexts) getChildren().remove(t);
        angleTexts.clear();
    }

    /**
     * Draws:
     *  - internal angle at each node u: angle(prev-u-next), shown inside its arc
     *  - COM angle for each step u->next: angle(COM->u , COM->next), shown inside its arc
     *
     * order must be a tour order (size >= 3). closedLoop assumed.
     */
    public void showAngles(Point2D com, List<Integer> order) {
        clearAngleLabels();
        if (order == null || order.size() < 3 || points.isEmpty()) return;

        int n = order.size();

        // Internal angles at nodes
        for (int k = 0; k < n; k++) {
            int prev = order.get((k - 1 + n) % n);
            int u = order.get(k);
            int next = order.get((k + 1) % n);

            if (!validIndex(prev) || !validIndex(u) || !validIndex(next)) continue;

            PointNode a = points.get(prev);
            PointNode b = points.get(u);
            PointNode c = points.get(next);

            double ang = angleAtVertexDeg(a, b, c);
            if (!Double.isFinite(ang)) continue;

            double bax = a.x() - b.x();
            double bay = a.y() - b.y();
            double bcx = c.x() - b.x();
            double bcy = c.y() - b.y();

            double a1 = normalizeDeg(Math.toDegrees(Math.atan2(-bay, bax)));
            double a2 = normalizeDeg(Math.toDegrees(Math.atan2(-bcy, bcx)));
            double delta = shortestDeltaDeg(a1, a2);

            double arcRadius = 20;
            Arc arc = new Arc(b.x(), b.y(), arcRadius, arcRadius, a1, delta);
            arc.setType(ArcType.OPEN);
            arc.setFill(Color.TRANSPARENT);
            arc.setStroke(Color.web("#d7d7d7"));
            arc.setStrokeWidth(1.6);
            angleArcs.add(arc);

            double ux = bax;
            double uy = bay;
            double vx = bcx;
            double vy = bcy;
            double um = Math.sqrt(ux * ux + uy * uy);
            double vm = Math.sqrt(vx * vx + vy * vy);
            if (um == 0 || vm == 0) continue;
            ux /= um;
            uy /= um;
            vx /= vm;
            vy /= vm;

            double bx = ux + vx;
            double by = uy + vy;
            double bm = Math.sqrt(bx * bx + by * by);
            if (bm == 0) continue;
            bx /= bm;
            by /= bm;

            double tx = b.x() + bx * (arcRadius - 8);
            double ty = b.y() + by * (arcRadius - 8);

            Text t = new Text(tx - 6, ty + 4, String.valueOf((int) Math.round(ang)));
            t.setFill(Color.web("#e6e6e6"));
            t.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
            angleTexts.add(t);
        }

        // COM angles between consecutive rays (u -> next)
        for (int k = 0; k < n; k++) {
            int u = order.get(k);
            int v = order.get((k + 1) % n);
            if (!validIndex(u) || !validIndex(v)) continue;

            PointNode up = points.get(u);
            PointNode vp = points.get(v);

            double comAng = angleBetweenFromComDeg(com, up, vp);
            if (!Double.isFinite(comAng)) continue;

            double ux = up.x() - com.getX();
            double uy = up.y() - com.getY();
            double vx = vp.x() - com.getX();
            double vy = vp.y() - com.getY();

            double a1 = normalizeDeg(Math.toDegrees(Math.atan2(-uy, ux)));
            double a2 = normalizeDeg(Math.toDegrees(Math.atan2(-vy, vx)));
            double delta = shortestDeltaDeg(a1, a2);

            double arcRadius = 28;
            Arc arc = new Arc(com.getX(), com.getY(), arcRadius, arcRadius, a1, delta);
            arc.setType(ArcType.OPEN);
            arc.setFill(Color.TRANSPARENT);
            arc.setStroke(Color.web("#bbbbbb"));
            arc.setStrokeWidth(1.4);
            angleArcs.add(arc);

            double um = Math.sqrt(ux * ux + uy * uy);
            double vm = Math.sqrt(vx * vx + vy * vy);
            if (um == 0 || vm == 0) continue;

            ux /= um;
            uy /= um;
            vx /= vm;
            vy /= vm;

            double bx = ux + vx;
            double by = uy + vy;
            double bm = Math.sqrt(bx * bx + by * by);
            if (bm == 0) continue;
            bx /= bm;
            by /= bm;

            double px = com.getX() + bx * (arcRadius - 8);
            double py = com.getY() + by * (arcRadius - 8);

            Text t = new Text(px - 6, py + 4, String.valueOf((int) Math.round(comAng)));
            t.setFill(Color.web("#cfcfcf"));
            t.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            angleTexts.add(t);
        }

        // Arcs first, then values.
        getChildren().addAll(angleArcs);
        getChildren().addAll(angleTexts);
    }

    private double normalizeDeg(double d) {
        double out = d % 360.0;
        if (out < 0) out += 360.0;
        return out;
    }

    // Signed shortest delta from a1 to a2 in [-180, 180].
    private double shortestDeltaDeg(double a1, double a2) {
        return (a2 - a1 + 540.0) % 360.0 - 180.0;
    }

    private boolean validIndex(int i) {
        return i >= 0 && i < points.size();
    }

    // Angle at B formed by A-B-C, in degrees (0..180)
    private double angleAtVertexDeg(PointNode a, PointNode b, PointNode c) {
        double bax = a.x() - b.x();
        double bay = a.y() - b.y();
        double bcx = c.x() - b.x();
        double bcy = c.y() - b.y();

        double magBA = Math.sqrt(bax * bax + bay * bay);
        double magBC = Math.sqrt(bcx * bcx + bcy * bcy);
        if (magBA == 0 || magBC == 0) return Double.NaN;

        double dot = bax * bcx + bay * bcy;
        double cos = dot / (magBA * magBC);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    // Angle at COM between COM->p and COM->q, in degrees (0..180)
    private double angleBetweenFromComDeg(Point2D com, PointNode p, PointNode q) {
        double ux = p.x() - com.getX();
        double uy = p.y() - com.getY();
        double vx = q.x() - com.getX();
        double vy = q.y() - com.getY();

        double magU = Math.sqrt(ux * ux + uy * uy);
        double magV = Math.sqrt(vx * vx + vy * vy);
        if (magU == 0 || magV == 0) return Double.NaN;

        double dot = ux * vx + uy * vy;
        double cos = dot / (magU * magV);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    // -------- TOURS --------

    public void showConvexHullOverlay(List<Integer> hullOrder, boolean highlightPoints, boolean connectPoints) {
        clearConvexHullOverlay();
        if (hullOrder == null || hullOrder.isEmpty()) return;

        if (connectPoints && hullOrder.size() >= 2) {
            int edgeCount = hullOrder.size() == 2 ? 1 : hullOrder.size();
            for (int i = 0; i < edgeCount; i++) {
                int u = hullOrder.get(i);
                int v = hullOrder.get((i + 1) % hullOrder.size());
                if (!validIndex(u) || !validIndex(v)) continue;

                PointNode a = points.get(u);
                PointNode b = points.get(v);
                Line line = new Line(a.x(), a.y(), b.x(), b.y());
                line.setStroke(Color.web("#ffd166"));
                line.setStrokeWidth(3.0);
                line.setOpacity(0.9);
                line.getStrokeDashArray().addAll(8.0, 5.0);
                line.setMouseTransparent(true);
                hullLines.add(line);
            }
            getChildren().addAll(hullLines);
        }

        if (highlightPoints) {
            for (int idx : hullOrder) {
                if (!validIndex(idx)) continue;

                PointNode p = points.get(idx);
                Circle marker = new Circle(p.x(), p.y(), 10);
                marker.setFill(Color.TRANSPARENT);
                marker.setStroke(Color.web("#ffd166"));
                marker.setStrokeWidth(3.0);
                marker.setMouseTransparent(true);
                hullMarkers.add(marker);
            }
            getChildren().addAll(hullMarkers);
        }
    }

    public void clearConvexHullOverlay() {
        for (Line line : hullLines) getChildren().remove(line);
        hullLines.clear();

        for (Circle marker : hullMarkers) getChildren().remove(marker);
        hullMarkers.clear();
    }

    public void setTourOrder(List<Integer> order, boolean closedLoop) {
        setTourOrderColor(order, closedLoop, Color.web("#ff4d6d"));
    }

    public void setTourOrderColor(List<Integer> order, boolean closedLoop, Color color) {
        clearTourLines();
        if (order == null || order.size() < 2) return;

        for (int i = 0; i < order.size() - 1; i++) {
            PointNode a = points.get(order.get(i));
            PointNode b = points.get(order.get(i + 1));
            addTourLine(a, b, color);
        }

        if (closedLoop && order.size() > 2) {
            PointNode last = points.get(order.get(order.size() - 1));
            PointNode first = points.get(order.get(0));
            addTourLine(last, first, color);
        }
    }

    public void setTourOrderCompare(List<Integer> order,
                                    boolean closedLoop,
                                    BiPredicate<Integer, Integer> edgeMatches,
                                    Color matchColor,
                                    Color diffColor) {
        clearTourLines();
        if (order == null || order.size() < 2) return;

        for (int i = 0; i < order.size() - 1; i++) {
            int u = order.get(i);
            int v = order.get(i + 1);

            PointNode a = points.get(u);
            PointNode b = points.get(v);

            Color c = edgeMatches.test(u, v) ? matchColor : diffColor;
            addTourLine(a, b, c);
        }

        if (closedLoop && order.size() > 2) {
            int u = order.get(order.size() - 1);
            int v = order.get(0);

            PointNode a = points.get(u);
            PointNode b = points.get(v);

            Color c = edgeMatches.test(u, v) ? matchColor : diffColor;
            addTourLine(a, b, c);
        }
    }

    public void clearAll() {
        getChildren().clear();

        pointCircles.clear();
        pointLabels.clear();
        tourLines.clear();
        hullLines.clear();
        hullMarkers.clear();
        rayLines.clear();
        comCenteredCircles.clear();
        averageIntersectionMarkers.clear();
        rmsIntersectionMarkers.clear();
        averageComCircle = null;
        rmsComCircle = null;
        angleTexts.clear();
        angleArcs.clear();

        comDot = null;
        outerCircle = null;
        points.clear();
    }

    private void redrawPoints() {
        for (Circle c : pointCircles) getChildren().remove(c);
        pointCircles.clear();

        for (Text t : pointLabels) getChildren().remove(t);
        pointLabels.clear();

        for (int i = 0; i < points.size(); i++) {
            addPointVisual(i, points.get(i));
        }
    }

    private void addPointVisual(int index, PointNode p) {
        Circle c = new Circle(p.x(), p.y(), 6);
        c.setFill(Color.web("#8ecae6"));
        c.setStroke(Color.web("#0b0b0b"));
        c.setStrokeWidth(1);
        pointCircles.add(c);

        Text t = new Text(p.x() + 8, p.y() - 8, String.valueOf(index + 1));
        t.setFill(Color.web("#e6e6e6"));
        t.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        pointLabels.add(t);

        getChildren().add(c);
        getChildren().add(t);
    }

    private void clearTourLines() {
        for (Line l : tourLines) getChildren().remove(l);
        tourLines.clear();
    }

    private void addTourLine(PointNode a, PointNode b, Color color) {
        Line l = new Line(a.x(), a.y(), b.x(), b.y());
        l.setStroke(color);
        l.setStrokeWidth(2.5);
        tourLines.add(l);
        getChildren().add(0, l);
    }
}
