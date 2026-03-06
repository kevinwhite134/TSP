package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class TSPBruteForce {

    public static class Result {
        public final List<Integer> order;
        public final double length;

        public final long operations;
        public final long elapsedNanos;
        public final String bigO;

        public Result(List<Integer> order, double length, long operations, long elapsedNanos, String bigO) {
            this.order = order;
            this.length = length;
            this.operations = operations;
            this.elapsedNanos = elapsedNanos;
            this.bigO = bigO;
        }
    }

    public static Result solve(List<PointNode> points) {
        long start = System.nanoTime();
        AtomicLong ops = new AtomicLong(0);

        int n = points.size();
        if (n < 2) {
            return new Result(List.of(), 0, 0, System.nanoTime() - start, "O(n!)");
        }

        // fix starting node at 0 to reduce duplicates
        List<Integer> perm = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            perm.add(i);
            ops.incrementAndGet();
        }

        Best best = new Best();
        permute(points, perm, 0, best, ops);

        List<Integer> bestOrder = new ArrayList<>();
        bestOrder.add(0);
        bestOrder.addAll(best.bestPerm);

        long elapsed = System.nanoTime() - start;

        // Big-O for brute force TSP
        String bigO = "O(n!)";

        return new Result(bestOrder, best.bestLen, ops.get(), elapsed, bigO);
    }

    private static class Best {
        double bestLen = Double.POSITIVE_INFINITY;
        List<Integer> bestPerm = List.of();
    }

    private static void permute(List<PointNode> points, List<Integer> a, int k, Best best, AtomicLong ops) {
        int n = a.size();

        ops.incrementAndGet(); // entering permute

        if (k == n) {
            double len = tourLength(points, a, ops);
            ops.incrementAndGet(); // compare best
            if (len < best.bestLen) {
                best.bestLen = len;
                best.bestPerm = new ArrayList<>(a);
                ops.incrementAndGet();
            }
            return;
        }

        for (int i = k; i < n; i++) {
            Collections.swap(a, k, i);
            ops.incrementAndGet(); // swap

            permute(points, a, k + 1, best, ops);

            Collections.swap(a, k, i);
            ops.incrementAndGet(); // swap back
        }
    }

    private static double tourLength(List<PointNode> pts, List<Integer> permNoStart, AtomicLong ops) {
        // order: 0 -> perm... -> back to 0
        PointNode start = pts.get(0);
        PointNode prev = start;

        double total = 0;

        for (int idx : permNoStart) {
            PointNode cur = pts.get(idx);
            total += dist(prev, cur, ops);
            prev = cur;
            ops.incrementAndGet();
        }

        total += dist(prev, start, ops);
        ops.incrementAndGet();

        return total;
    }

    private static double dist(PointNode a, PointNode b, AtomicLong ops) {
        double dx = a.x() - b.x(); ops.incrementAndGet();
        double dy = a.y() - b.y(); ops.incrementAndGet();
        return Math.sqrt(dx * dx + dy * dy);
    }
}