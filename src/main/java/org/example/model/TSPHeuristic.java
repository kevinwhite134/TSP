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

    // Your "clock hand around COM" idea = sort points by angle about COM
    public static Result solveByAngle(List<PointNode> points, Point2D com) {
        long start = System.nanoTime();

        AtomicLong ops = new AtomicLong(0);

        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            idx.add(i);
            ops.incrementAndGet(); // counting "add index"
        }

        // count comparator calls (this is a decent proxy for work in sorting)
        Comparator<Integer> cmp = (a, b) -> {
            ops.incrementAndGet(); // comparator call
            double aa = angle(points.get(a), com); ops.incrementAndGet();
            double bb = angle(points.get(b), com); ops.incrementAndGet();
            return Double.compare(aa, bb);
        };

        idx.sort(cmp);

        long elapsed = System.nanoTime() - start;

        // Big-O for sort-based heuristic
        String bigO = "O(n log n)";

        return new Result(idx, ops.get(), elapsed, bigO);
    }

    private static double angle(PointNode p, Point2D com) {
        double dx = p.x() - com.getX();
        double dy = p.y() - com.getY();
        return Math.atan2(dy, dx);
    }
}