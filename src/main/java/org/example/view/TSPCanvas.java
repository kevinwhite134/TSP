package org.example.view;

import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import org.example.model.PointNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class TSPCanvas extends Pane {

    private final List<Circle> pointCircles = new ArrayList<>();
    private final List<Text> pointLabels = new ArrayList<>();
    private final List<Line> tourLines = new ArrayList<>();
    private final List<Line> rayLines = new ArrayList<>();

    // circles centered at COM, radius = dist(point, COM)
    private final List<Circle> comCenteredCircles = new ArrayList<>();

    // NEW: angle labels on canvas
    private final List<Text> angleTexts = new ArrayList<>();

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
        clearAngleLabels();

        points.clear();

        clearTourLines();
        clearRays();

        if (comDot != null) { getChildren().remove(comDot); comDot = null; }
        if (outerCircle != null) { getChildren().remove(outerCircle); outerCircle = null; }
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

    // -------- NEW: ANGLES ON CANVAS --------

    public void clearAngleLabels() {
        for (Text t : angleTexts) getChildren().remove(t);
        angleTexts.clear();
    }

    /**
     * Draws:
     *  - internal angle at each node u: angle(prev-u-next), shown near node u
     *  - COM angle for each step u->next: angle(COM->u , COM->next), shown near COM (slightly offset)
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

            PointNode A = points.get(prev);
            PointNode B = points.get(u);
            PointNode C = points.get(next);

            double ang = angleAtVertexDeg(A, B, C);
            if (!Double.isFinite(ang)) continue;

            Text t = new Text(B.x() + 10, B.y() + 16, "ang=" + (int) Math.round(ang) + " deg");
            t.setFill(Color.web("#e6e6e6"));
            t.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
            angleTexts.add(t);
        }

        // COM angles between consecutive rays (u -> next)
        // Place each label near COM, offset in the direction of the bisector.
        for (int k = 0; k < n; k++) {
            int u = order.get(k);
            int v = order.get((k + 1) % n);
            if (!validIndex(u) || !validIndex(v)) continue;

            PointNode U = points.get(u);
            PointNode V = points.get(v);

            double comAng = angleBetweenFromComDeg(com, U, V);
            if (!Double.isFinite(comAng)) continue;

            // bisector direction (normalized sum of unit vectors)
            double ux = U.x() - com.getX();
            double uy = U.y() - com.getY();
            double vx = V.x() - com.getX();
            double vy = V.y() - com.getY();

            double um = Math.sqrt(ux * ux + uy * uy);
            double vm = Math.sqrt(vx * vx + vy * vy);
            if (um == 0 || vm == 0) continue;

            ux /= um; uy /= um;
            vx /= vm; vy /= vm;

            double bx = ux + vx;
            double by = uy + vy;
            double bm = Math.sqrt(bx * bx + by * by);
            if (bm == 0) continue;
            bx /= bm; by /= bm;

            double px = com.getX() + bx * 26;
            double py = com.getY() + by * 26;

            Text t = new Text(px, py, "com=" + (int) Math.round(comAng) + " deg");
            t.setFill(Color.web("#cfcfcf"));
            t.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            angleTexts.add(t);
        }

        // Add angle text above tour lines so it stays readable
        getChildren().addAll(angleTexts);
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
        rayLines.clear();
        comCenteredCircles.clear();
        angleTexts.clear();

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