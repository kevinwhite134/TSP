package org.example.model;

import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class TSPHeuristic {

    public static class Result {
        public final List<Integer> order;
        public final long operations;
        public final long elapsedNanos;
        public final String bigO;

        public Result(List<Integer> order, long operations, long elapsedNanos, String bigO) {
            this.order = order;
            this.operations = operations;
            this.elapsedNanos = elapsedNanos;
            this.bigO = bigO;
        }
    }

    // Backward-compatible entrypoint: legacy behavior only.
    public static Result solveByAngle(List<PointNode> points, Point2D com) {
        return solveByAngle(points, com, false);
    }

    // New entrypoint with toggleable spike reorder pass.
    public static Result solveByAngle(List<PointNode> points, Point2D com, boolean enableSpikeReorder) {
        long start = System.nanoTime();
        AtomicLong ops = new AtomicLong(0);

        List<Integer> order = initialAngleOrder(points, com, ops);

        if (enableSpikeReorder && points.size() >= 5) {
            runSpikeReorder(points, com, order, ops);
        }

        long elapsed = System.nanoTime() - start;
        String bigO = enableSpikeReorder ? "O(n log n + p*n)" : "O(n log n)";

        return new Result(order, ops.get(), elapsed, bigO);
    }

    private static List<Integer> initialAngleOrder(List<PointNode> points, Point2D com, AtomicLong ops) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            idx.add(i);
            ops.incrementAndGet();
        }

        Comparator<Integer> cmp = (a, b) -> {
            ops.incrementAndGet();
            double aa = angle(points.get(a), com);
            double bb = angle(points.get(b), com);
            ops.addAndGet(2);
            return Double.compare(aa, bb);
        };

        idx.sort(cmp);
        return idx;
    }

    private static void runSpikeReorder(List<PointNode> points, Point2D com, List<Integer> order, AtomicLong ops) {
        int n = order.size();
        int maxPasses = Math.min(8, Math.max(1, n / 3));
        int window = Math.min(4, Math.max(2, n / 5));

        for (int pass = 0; pass < maxPasses; pass++) {
            Metrics m = computeMetrics(points, com, order, ops);

            double thetaQ1 = quartile(m.thetaDeg, 0.25);
            double thetaQ3 = quartile(m.thetaDeg, 0.75);
            double thetaIqr = thetaQ3 - thetaQ1;
            double sharpThetaCutoff = Math.max(35.0, thetaQ1 - 1.5 * thetaIqr);

            double rQ1 = quartile(m.detourRatio, 0.25);
            double rQ3 = quartile(m.detourRatio, 0.75);
            double rIqr = rQ3 - rQ1;
            double detourCutoff = rQ3 + 1.5 * rIqr;

            double jQ1 = quartile(m.radialJump, 0.25);
            double jQ3 = quartile(m.radialJump, 0.75);
            double jIqr = jQ3 - jQ1;
            double jumpCutoff = jQ3 + 1.5 * jIqr;

            List<Integer> flaggedPos = new ArrayList<>();
            for (int pos = 0; pos < n; pos++) {
                boolean sharp = m.thetaDeg[pos] < sharpThetaCutoff;
                boolean detour = m.detourRatio[pos] > detourCutoff;
                boolean jump = m.radialJump[pos] > jumpCutoff;
                if ((sharp && detour) || (sharp && jump)) {
                    flaggedPos.add(pos);
                }
                ops.incrementAndGet();
            }

            if (flaggedPos.isEmpty()) {
                break;
            }

            boolean anyImproved = false;
            double currentLen = tourLength(points, order, ops);

            for (int pos : flaggedPos) {
                if (pos >= order.size()) continue;

                int u = order.get(pos);
                int actualPos = indexOf(order, u);
                if (actualPos < 0) continue;

                LocalShape before = shapeForNode(points, com, order, u, ops);
                Candidate best = null;

                for (int delta = -window; delta <= window; delta++) {
                    if (delta == 0) continue;
                    int q = wrap(actualPos + delta, n);
                    if (q == actualPos) continue;

                    // Candidate 1: swap.
                    List<Integer> swapOrder = new ArrayList<>(order);
                    swap(swapOrder, actualPos, q);
                    Candidate cSwap = assessCandidate(points, com, swapOrder, u, currentLen, ops);
                    if (isBetterCandidate(cSwap, before, best)) best = cSwap;

                    // Candidate 2: relocate.
                    List<Integer> relocateOrder = relocate(order, actualPos, q);
                    Candidate cRel = assessCandidate(points, com, relocateOrder, u, currentLen, ops);
                    if (isBetterCandidate(cRel, before, best)) best = cRel;
                }

                if (best != null) {
                    order.clear();
                    order.addAll(best.order);
                    currentLen = best.length;
                    anyImproved = true;
                    ops.incrementAndGet();
                }
            }

            if (!anyImproved) {
                break;
            }
        }

        removeCrossingsSingleSweep(points, order, ops);
    }

    private static Metrics computeMetrics(List<PointNode> points, Point2D com, List<Integer> order, AtomicLong ops) {
        int n = order.size();
        double[] thetaDeg = new double[n];
        double[] detourRatio = new double[n];
        double[] radialJump = new double[n];

        for (int pos = 0; pos < n; pos++) {
            int prev = order.get(wrap(pos - 1, n));
            int u = order.get(pos);
            int next = order.get(wrap(pos + 1, n));

            PointNode pPrev = points.get(prev);
            PointNode pU = points.get(u);
            PointNode pNext = points.get(next);

            double theta = angleAtVertexDeg(pPrev, pU, pNext);

            double dPrevU = dist(pPrev, pU);
            double dUNext = dist(pU, pNext);
            double dPrevNext = dist(pPrev, pNext);
            double r = (dPrevNext == 0) ? Double.POSITIVE_INFINITY : (dPrevU + dUNext) / dPrevNext;

            double du = distToCom(pU, com);
            double dnext = distToCom(pNext, com);
            double j = Math.abs(du - dnext);

            thetaDeg[pos] = theta;
            detourRatio[pos] = r;
            radialJump[pos] = j;

            ops.addAndGet(4);
        }

        return new Metrics(thetaDeg, detourRatio, radialJump);
    }

    private static LocalShape shapeForNode(List<PointNode> points, Point2D com, List<Integer> order, int node, AtomicLong ops) {
        int n = order.size();
        int pos = indexOf(order, node);
        if (pos < 0 || n < 3) return new LocalShape(Double.NaN, Double.NaN);

        int prev = order.get(wrap(pos - 1, n));
        int u = order.get(pos);
        int next = order.get(wrap(pos + 1, n));

        PointNode pPrev = points.get(prev);
        PointNode pU = points.get(u);
        PointNode pNext = points.get(next);

        double theta = angleAtVertexDeg(pPrev, pU, pNext);
        double dPrevU = dist(pPrev, pU);
        double dUNext = dist(pU, pNext);
        double dPrevNext = dist(pPrev, pNext);
        double r = (dPrevNext == 0) ? Double.POSITIVE_INFINITY : (dPrevU + dUNext) / dPrevNext;

        ops.addAndGet(3);
        return new LocalShape(theta, r);
    }

    private static Candidate assessCandidate(List<PointNode> points,
                                             Point2D com,
                                             List<Integer> candidateOrder,
                                             int node,
                                             double currentLen,
                                             AtomicLong ops) {
        double len = tourLength(points, candidateOrder, ops);
        if (len > currentLen + 1e-9) {
            return null;
        }

        LocalShape shape = shapeForNode(points, com, candidateOrder, node, ops);
        return new Candidate(candidateOrder, len, shape.thetaDeg, shape.detourRatio);
    }

    private static boolean isBetterCandidate(Candidate candidate, LocalShape before, Candidate incumbent) {
        if (candidate == null) return false;

        boolean improvesShape = candidate.thetaDeg > before.thetaDeg + 1e-6
                && candidate.detourRatio < before.detourRatio - 1e-6;
        if (!improvesShape) return false;

        if (incumbent == null) return true;

        if (candidate.length < incumbent.length - 1e-9) return true;
        if (Math.abs(candidate.length - incumbent.length) <= 1e-9) {
            double candidateGain = (candidate.thetaDeg - before.thetaDeg) + (before.detourRatio - candidate.detourRatio);
            double incumbentGain = (incumbent.thetaDeg - before.thetaDeg) + (before.detourRatio - incumbent.detourRatio);
            return candidateGain > incumbentGain;
        }

        return false;
    }

    // One sweep that removes obvious intersections with a 2-opt style reversal.
    private static void removeCrossingsSingleSweep(List<PointNode> points, List<Integer> order, AtomicLong ops) {
        int n = order.size();
        if (n < 4) return;

        for (int i = 0; i < n; i++) {
            int i2 = wrap(i + 1, n);
            PointNode a = points.get(order.get(i));
            PointNode b = points.get(order.get(i2));

            for (int j = i + 2; j < n; j++) {
                int j2 = wrap(j + 1, n);

                if (i == j2) continue; // adjacent in closed loop

                PointNode c = points.get(order.get(j));
                PointNode d = points.get(order.get(j2));

                ops.incrementAndGet();
                if (segmentsIntersect(a, b, c, d)) {
                    reverseBetween(order, i2, j);
                    return;
                }
            }
        }
    }

    private static void reverseBetween(List<Integer> order, int start, int end) {
        while (start < end) {
            swap(order, start, end);
            start++;
            end--;
        }
    }

    private static boolean segmentsIntersect(PointNode a, PointNode b, PointNode c, PointNode d) {
        double o1 = orient(a, b, c);
        double o2 = orient(a, b, d);
        double o3 = orient(c, d, a);
        double o4 = orient(c, d, b);
        return (o1 * o2 < 0) && (o3 * o4 < 0);
    }

    private static double orient(PointNode a, PointNode b, PointNode c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    private static List<Integer> relocate(List<Integer> order, int from, int to) {
        List<Integer> out = new ArrayList<>(order);
        Integer value = out.remove(from);
        if (to > from) to -= 1;
        out.add(to, value);
        return out;
    }

    private static void swap(List<Integer> a, int i, int j) {
        Integer tmp = a.get(i);
        a.set(i, a.get(j));
        a.set(j, tmp);
    }

    private static int indexOf(List<Integer> order, int node) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i) == node) return i;
        }
        return -1;
    }

    private static int wrap(int idx, int n) {
        int out = idx % n;
        return out < 0 ? out + n : out;
    }

    private static double quartile(double[] vals, double q) {
        if (vals.length == 0) return 0;
        double[] copy = vals.clone();
        java.util.Arrays.sort(copy);

        double pos = q * (copy.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return copy[lo];

        double t = pos - lo;
        return copy[lo] * (1.0 - t) + copy[hi] * t;
    }

    private static double tourLength(List<PointNode> points, List<Integer> order, AtomicLong ops) {
        if (order.size() < 2) return 0;
        double total = 0;
        for (int i = 0; i < order.size(); i++) {
            int j = wrap(i + 1, order.size());
            total += dist(points.get(order.get(i)), points.get(order.get(j)));
            ops.incrementAndGet();
        }
        return total;
    }

    private static double dist(PointNode a, PointNode b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double distToCom(PointNode p, Point2D com) {
        double dx = p.x() - com.getX();
        double dy = p.y() - com.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double angle(PointNode p, Point2D com) {
        double dx = p.x() - com.getX();
        double dy = p.y() - com.getY();
        return Math.atan2(dy, dx);
    }

    private static double angleAtVertexDeg(PointNode a, PointNode b, PointNode c) {
        double bax = a.x() - b.x();
        double bay = a.y() - b.y();
        double bcx = c.x() - b.x();
        double bcy = c.y() - b.y();

        double magBA = Math.sqrt(bax * bax + bay * bay);
        double magBC = Math.sqrt(bcx * bcx + bcy * bcy);
        if (magBA == 0 || magBC == 0) return 180.0;

        double dot = bax * bcx + bay * bcy;
        double cos = dot / (magBA * magBC);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    private record Metrics(double[] thetaDeg, double[] detourRatio, double[] radialJump) {}

    private record LocalShape(double thetaDeg, double detourRatio) {}

    private record Candidate(List<Integer> order, double length, double thetaDeg, double detourRatio) {}
}
