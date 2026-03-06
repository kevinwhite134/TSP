package org.example.controller;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.control.*; // includes SplitPane
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.model.PointNode;
import org.example.model.TSPBruteForce;
import org.example.model.TSPHeuristic;
import org.example.view.TSPCanvas;

import java.util.*;

public class TSPController {

    private final BorderPane root = new BorderPane();

    private final TSPCanvas left = new TSPCanvas(520, 520);   // brute
    private final TSPCanvas right = new TSPCanvas(520, 520);  // heuristic

    private final Slider nSlider = new Slider(3, 20, 8);
    private final Label nLabel = new Label();

    private final CheckBox manualMode = new CheckBox("Manual placement");

    private final CheckBox showComCenteredCircles = new CheckBox("Show COM-centered circles");
    private final CheckBox extendRays = new CheckBox("Extend rays through COM");

    // NEW: show angles on the actual display
    private final CheckBox showAngles = new CheckBox("Show angles");

    private final Button solveBtn = new Button("Solve");
    private final Button clearBtn = new Button("Clear points");
    private final Button regenBtn = new Button("Regenerate");

    private final Label bruteLabel = new Label("Brute: -");
    private final Label heurLabel = new Label("Heuristic: -");

    private final Label bruteMeta = new Label("");
    private final Label heurMeta = new Label("");

    private final TextArea bruteDetails = new TextArea();
    private final TextArea heurDetails = new TextArea();

    private final Random rng = new Random();
    private List<PointNode> points = new ArrayList<>();

    public TSPController() {
        buildUI();
        wireManualPlacement();
    }

    public Parent getRoot() {
        return root;
    }

    public void init() {
        regenerate();
    }

    private void buildUI() {
        nSlider.setMajorTickUnit(1);
        nSlider.setMinorTickCount(0);
        nSlider.setSnapToTicks(true);
        nSlider.setShowTickMarks(true);
        nSlider.setShowTickLabels(true);

        nSlider.valueProperty().addListener((obs, oldV, newV) -> nLabel.setText("Points: " + newV.intValue()));
        nLabel.setText("Points: " + (int) nSlider.getValue());

        nSlider.setOnMouseReleased(e -> {
            if (!manualMode.isSelected()) regenerate();
        });

        manualMode.setStyle("-fx-text-fill: #ddd;");
        showComCenteredCircles.setStyle("-fx-text-fill: #ddd;");
        extendRays.setStyle("-fx-text-fill: #ddd;");
        showAngles.setStyle("-fx-text-fill: #ddd;");

        showComCenteredCircles.selectedProperty().addListener((obs, ov, nv) -> solveCurrentPoints());
        extendRays.selectedProperty().addListener((obs, ov, nv) -> solveCurrentPoints());
        showAngles.selectedProperty().addListener((obs, ov, nv) -> solveCurrentPoints());

        manualMode.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                regenBtn.setDisable(true);
                nSlider.setDisable(true);
            } else {
                regenBtn.setDisable(false);
                nSlider.setDisable(false);
                regenerate();
            }
        });

        regenBtn.setOnAction(e -> regenerate());
        clearBtn.setOnAction(e -> clearManual());
        solveBtn.setOnAction(e -> solveCurrentPoints());

        HBox top = new HBox(
                12,
                nLabel, nSlider,
                regenBtn,
                manualMode,
                showComCenteredCircles,
                extendRays,
                showAngles,
                solveBtn,
                clearBtn
        );
        top.setPadding(new Insets(10));
        top.setStyle("-fx-background-color: #0f0f0f; -fx-border-color: #2b2b2b; -fx-border-width: 0 0 1 0;");
        nLabel.setStyle("-fx-text-fill: #ddd;");

        bruteLabel.setStyle("-fx-text-fill: #ddd; -fx-font-size: 18;");
        heurLabel.setStyle("-fx-text-fill: #ddd; -fx-font-size: 18;");
        bruteMeta.setStyle("-fx-text-fill: #bbb; -fx-font-size: 14;");
        heurMeta.setStyle("-fx-text-fill: #bbb; -fx-font-size: 14;");

        setupDetailsArea(bruteDetails);
        setupDetailsArea(heurDetails);

        VBox.setVgrow(bruteDetails, Priority.ALWAYS);
        VBox.setVgrow(heurDetails, Priority.ALWAYS);

        VBox leftBox = new VBox(6,
                styledTitle("Brute Force (optimal for small N)"),
                left,
                bruteLabel,
                bruteMeta,
                styledSubTitle("Transitions (aligned to heuristic order)"),
                bruteDetails
        );

        VBox rightBox = new VBox(6,
                styledTitle("Heuristic (angle around COM)"),
                right,
                heurLabel,
                heurMeta,
                styledSubTitle("Transitions (heuristic path order)"),
                heurDetails
        );

        leftBox.setPadding(new Insets(10));
        rightBox.setPadding(new Insets(10));
        leftBox.setStyle("-fx-background-color: #0b0b0b;");
        rightBox.setStyle("-fx-background-color: #0b0b0b;");

        SplitPane split = new SplitPane(leftBox, rightBox);
        split.setDividerPositions(0.5);

        root.setTop(top);
        root.setCenter(split);
        root.setStyle("-fx-background-color: #0b0b0b;");
    }

    private void setupDetailsArea(TextArea ta) {
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.setPrefRowCount(14);
        ta.setStyle("""
                -fx-control-inner-background: #0f0f0f;
                -fx-text-fill: #dcdcdc;
                -fx-font-family: Consolas;
                -fx-font-size: 14px;
                """);
    }

    private Label styledTitle(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-text-fill: #ddd; -fx-font-size: 14;");
        return l;
    }

    private Label styledSubTitle(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-text-fill: #bbb; -fx-font-size: 12;");
        return l;
    }

    private void wireManualPlacement() {
        left.setOnLeftClickAdd(p -> {
            if (!manualMode.isSelected()) return;
            addManualPoint(p);
        });
        right.setOnLeftClickAdd(p -> {
            if (!manualMode.isSelected()) return;
            addManualPoint(p);
        });

        left.setOnRightClick(p -> {
            if (!manualMode.isSelected()) return;
            eraseNearestPoint(p);
        });
        right.setOnRightClick(p -> {
            if (!manualMode.isSelected()) return;
            eraseNearestPoint(p);
        });
    }

    private void addManualPoint(Point2D p) {
        double pad = 10;
        double x = Math.max(pad, Math.min(510 - pad, p.getX()));
        double y = Math.max(pad, Math.min(510 - pad, p.getY()));

        PointNode node = new PointNode(x, y);

        left.addPoint(node);
        right.addPoint(node);

        points = right.getPoints();

        bruteDetails.setText("");
        heurDetails.setText("");

        solveCurrentPoints();
    }

    private void eraseNearestPoint(Point2D click) {
        if (points == null || points.isEmpty()) return;

        int bestIndex = -1;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            PointNode pt = points.get(i);
            double dx = pt.x() - click.getX();
            double dy = pt.y() - click.getY();
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < bestDist) {
                bestDist = d;
                bestIndex = i;
            }
        }

        double threshold = 18;
        if (bestIndex != -1 && bestDist <= threshold) {
            points.remove(bestIndex);

            left.clearAll();
            right.clearAll();
            left.setPoints(points);
            right.setPoints(points);

            solveCurrentPoints();
        }
    }

    private void clearManual() {
        points.clear();

        left.clearPointsOnly();
        right.clearPointsOnly();

        bruteLabel.setText("Brute: -");
        heurLabel.setText("Heuristic: -");
        bruteMeta.setText("");
        heurMeta.setText("");
        bruteDetails.setText("");
        heurDetails.setText("");
    }

    private void regenerate() {
        if (manualMode.isSelected()) return;

        int n = (int) nSlider.getValue();
        points = randomPoints(n, 500, 500, 20);

        left.clearAll();
        right.clearAll();

        left.setPoints(points);
        right.setPoints(points);

        solveCurrentPoints();
    }

    private void solveCurrentPoints() {
        if (points == null || points.size() < 3) {
            heurLabel.setText("Heuristic length: - (need 3+ points)");
            bruteLabel.setText("Brute length: - (need 3+ points)");
            bruteDetails.setText("");
            heurDetails.setText("");

            left.clearComCenteredCircles();
            right.clearComCenteredCircles();
            left.clearAngleLabels();
            right.clearAngleLabels();
            return;
        }

        Point2D com = centreOfMass(points);
        double r = enclosingRadius(points, com);
        boolean extend = extendRays.isSelected();

        left.clearAll();
        right.clearAll();
        left.setPoints(points);
        right.setPoints(points);

        left.setOuterCircle(com, r);
        left.setComDot(com);
        left.setRaysFromCom(com, r, extend);

        right.setOuterCircle(com, r);
        right.setComDot(com);
        right.setRaysFromCom(com, r, extend);

        if (showComCenteredCircles.isSelected()) {
            left.showComCenteredCircles(com);
            right.showComCenteredCircles(com);
        } else {
            left.clearComCenteredCircles();
            right.clearComCenteredCircles();
        }

        // heuristic
        TSPHeuristic.Result heurRes = TSPHeuristic.solveByAngle(points, com);
        double heurLen = closedTourLength(points, heurRes.order);

        int n = points.size();
        if (n <= 12) {
            TSPBruteForce.Result bruteRes = TSPBruteForce.solve(points);
            double bestLen = bruteRes.length;

            left.setTourOrderColor(bruteRes.order, true, Color.LIMEGREEN);

            Set<Long> bruteEdges = buildUndirectedEdgeSet(bruteRes.order, true);
            right.setTourOrderCompare(
                    heurRes.order,
                    true,
                    (u, v) -> bruteEdges.contains(edgeKey(u, v)),
                    Color.LIMEGREEN,
                    Color.RED
            );

            double off = heurLen - bestLen;
            double pct = (bestLen == 0) ? 0 : (off / bestLen) * 100.0;

            heurLabel.setText(String.format("Heuristic length: %.2f   (off: %.2f, %.2f%%)", heurLen, off, pct));
            bruteLabel.setText(String.format("Brute length: %.2f", bestLen));

            heurMeta.setText(metaText(heurRes.bigO, heurRes.elapsedNanos, heurRes.operations));
            bruteMeta.setText(metaText(bruteRes.bigO, bruteRes.elapsedNanos, bruteRes.operations));

            int[] bruteNext = buildNextMap(n, bruteRes.order, true);
            int[] brutePrev = buildPrevFromNext(bruteNext);

            heurDetails.setText(buildTransitionsWithLabels(points, heurRes.order, com, true, null, null));
            bruteDetails.setText(buildTransitionsWithLabels(points, heurRes.order, com, true, bruteNext, brutePrev));

            // NEW: draw angles on canvases
            if (showAngles.isSelected()) {
                left.showAngles(com, bruteRes.order);
                right.showAngles(com, heurRes.order);
            } else {
                left.clearAngleLabels();
                right.clearAngleLabels();
            }

        } else {
            right.setTourOrderColor(heurRes.order, true, Color.RED);
            left.setTourOrder(List.of(), true);

            bruteLabel.setText("Brute length: (disabled for N > 12)");
            bruteMeta.setText("Big-O: O(n!)   Time: -   Ops: -");

            heurLabel.setText(String.format("Heuristic length: %.2f   (off: N/A)", heurLen));
            heurMeta.setText(metaText(heurRes.bigO, heurRes.elapsedNanos, heurRes.operations));

            heurDetails.setText(buildTransitionsWithLabels(points, heurRes.order, com, true, null, null));
            bruteDetails.setText("-");

            // NEW: only heuristic angles available
            if (showAngles.isSelected()) {
                left.clearAngleLabels();
                right.showAngles(com, heurRes.order);
            } else {
                left.clearAngleLabels();
                right.clearAngleLabels();
            }
        }
    }

    private String metaText(String bigO, long nanos, long ops) {
        double ms = nanos / 1_000_000.0;
        return String.format("Big-O: %s   Time: %.3f ms   Ops: %,d", bigO, ms, ops);
    }

    // ---- your existing transitions text (kept as-is) ----
    private String buildTransitionsWithLabels(List<PointNode> pts,
                                              List<Integer> order,
                                              Point2D com,
                                              boolean closedLoop,
                                              int[] overrideNext,
                                              int[] overridePrev) {
        if (order == null || order.size() < 2) return "-";

        StringBuilder sb = new StringBuilder();
        int steps = order.size() - 1 + (closedLoop ? 1 : 0);
        int size = order.size();

        for (int k = 0; k < steps; k++) {
            int u = order.get(k % size);

            int v = (overrideNext == null)
                    ? order.get((k + 1) % size)
                    : overrideNext[u];

            if (u < 0 || u >= pts.size() || v < 0 || v >= pts.size()) {
                sb.append(String.format("Node %02d: (invalid)\n", u + 1));
                continue;
            }

            PointNode U = pts.get(u);
            PointNode V = pts.get(v);

            double dUV = dist(U, V);
            double du = distToCom(U, com);
            double dv = distToCom(V, com);

            double rForward = (du == 0) ? Double.POSITIVE_INFINITY : dUV / du;
            double rReverse = (dv == 0) ? Double.POSITIVE_INFINITY : dUV / dv;

            int prevU, nextU;
            if (overrideNext == null || overridePrev == null) {
                prevU = order.get((k - 1 + size) % size);
                nextU = order.get((k + 1) % size);
            } else {
                prevU = overridePrev[u];
                nextU = overrideNext[u];
            }

            double nodeRatio;
            if (prevU < 0 || nextU < 0) {
                nodeRatio = Double.NaN;
            } else {
                double sumTwo = dist(pts.get(prevU), pts.get(u)) + dist(pts.get(u), pts.get(nextU));
                nodeRatio = (du == 0) ? Double.POSITIVE_INFINITY : (sumTwo / du);
            }

            String nodeRStr;
            if (Double.isNaN(nodeRatio)) nodeRStr = "NA";
            else if (Double.isInfinite(nodeRatio)) nodeRStr = "INF";
            else nodeRStr = String.format("%.4f", nodeRatio);

            sb.append(String.format(
                    "Node %02d: prev=%02d next=%02d | %02d->%02d r=%.4f | %02d->%02d r=%.4f | nodeR=%s\n",
                    u + 1,
                    (prevU < 0 ? 0 : prevU + 1),
                    (nextU < 0 ? 0 : nextU + 1),
                    u + 1, v + 1, rForward,
                    v + 1, u + 1, rReverse,
                    nodeRStr
            ));
        }

        return sb.toString();
    }

    // ---- brute helpers ----
    private int[] buildNextMap(int n, List<Integer> order, boolean closedLoop) {
        int[] next = new int[n];
        Arrays.fill(next, -1);

        if (order == null || order.size() < 2) return next;

        for (int i = 0; i < order.size() - 1; i++) {
            int u = order.get(i);
            int v = order.get(i + 1);
            if (u >= 0 && u < n) next[u] = v;
        }

        if (closedLoop && order.size() > 2) {
            int last = order.get(order.size() - 1);
            int first = order.get(0);
            if (last >= 0 && last < n) next[last] = first;
        }

        return next;
    }

    private int[] buildPrevFromNext(int[] next) {
        int[] prev = new int[next.length];
        Arrays.fill(prev, -1);
        for (int u = 0; u < next.length; u++) {
            int v = next[u];
            if (v >= 0 && v < next.length) prev[v] = u;
        }
        return prev;
    }

    private static long edgeKey(int a, int b) {
        int u = Math.min(a, b);
        int v = Math.max(a, b);
        return (((long) u) << 32) ^ (v & 0xffffffffL);
    }

    private static Set<Long> buildUndirectedEdgeSet(List<Integer> order, boolean closedLoop) {
        Set<Long> set = new HashSet<>();
        if (order == null || order.size() < 2) return set;

        for (int i = 0; i < order.size() - 1; i++) {
            set.add(edgeKey(order.get(i), order.get(i + 1)));
        }
        if (closedLoop && order.size() > 2) {
            set.add(edgeKey(order.get(order.size() - 1), order.get(0)));
        }
        return set;
    }

    // ---- geometry ----
    private List<PointNode> randomPoints(int n, double w, double h, double pad) {
        List<PointNode> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double x = pad + rng.nextDouble() * (w - 2 * pad);
            double y = pad + rng.nextDouble() * (h - 2 * pad);
            pts.add(new PointNode(x, y));
        }
        return pts;
    }

    private Point2D centreOfMass(List<PointNode> pts) {
        double sx = 0, sy = 0;
        for (PointNode p : pts) {
            sx += p.x();
            sy += p.y();
        }
        return new Point2D(sx / pts.size(), sy / pts.size());
    }

    private double enclosingRadius(List<PointNode> pts, Point2D com) {
        double max = 0;
        for (PointNode p : pts) {
            double dx = p.x() - com.getX();
            double dy = p.y() - com.getY();
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d > max) max = d;
        }
        return max + 12;
    }

    private double closedTourLength(List<PointNode> pts, List<Integer> order) {
        if (order.size() < 2) return 0;
        double total = 0;
        for (int i = 0; i < order.size() - 1; i++) {
            total += dist(pts.get(order.get(i)), pts.get(order.get(i + 1)));
        }
        total += dist(pts.get(order.get(order.size() - 1)), pts.get(order.get(0)));
        return total;
    }

    private double dist(PointNode a, PointNode b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double distToCom(PointNode a, Point2D com) {
        double dx = a.x() - com.getX();
        double dy = a.y() - com.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
}