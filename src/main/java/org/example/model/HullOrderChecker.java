package org.example.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HullOrderChecker {

    private static final double EPS = 1e-9;

    private HullOrderChecker() {}

    public static boolean followsHullOrder(List<PointNode> points, List<Integer> tourOrder) {
        return check(points, tourOrder).follows();
    }

    public static CheckResult check(List<PointNode> points, List<Integer> tourOrder) {
        if (points == null || tourOrder == null || points.size() < 3) {
            return new CheckResult(true, 0);
        }
        if (tourOrder.size() != points.size()) {
            return new CheckResult(false, 0);
        }

        List<Integer> hull = convexHullCorners(points);
        int hullCorners = hull.size();
        if (hullCorners < 3) {
            return new CheckResult(true, hullCorners);
        }

        Set<Integer> hullSet = new HashSet<>(hull);
        List<Integer> inTourOrder = new ArrayList<>();
        for (int idx : tourOrder) {
            if (hullSet.contains(idx)) {
                inTourOrder.add(idx);
            }
        }

        if (inTourOrder.size() != hullCorners) {
            return new CheckResult(false, hullCorners);
        }

        if (isCyclicShift(inTourOrder, hull)) {
            return new CheckResult(true, hullCorners);
        }

        List<Integer> reversed = new ArrayList<>(hull);
        java.util.Collections.reverse(reversed);
        return new CheckResult(isCyclicShift(inTourOrder, reversed), hullCorners);
    }

    public static List<Integer> convexHullCorners(List<PointNode> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }

        List<Integer> sorted = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            sorted.add(i);
        }

        sorted.sort(Comparator
                .comparingDouble((Integer i) -> points.get(i).x())
                .thenComparingDouble(i -> points.get(i).y())
                .thenComparingInt(i -> i));

        List<Integer> lower = new ArrayList<>();
        for (int idx : sorted) {
            while (lower.size() >= 2
                    && cross(points, lower.get(lower.size() - 2), lower.get(lower.size() - 1), idx) <= EPS) {
                lower.remove(lower.size() - 1);
            }
            lower.add(idx);
        }

        List<Integer> upper = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            int idx = sorted.get(i);
            while (upper.size() >= 2
                    && cross(points, upper.get(upper.size() - 2), upper.get(upper.size() - 1), idx) <= EPS) {
                upper.remove(upper.size() - 1);
            }
            upper.add(idx);
        }

        if (!lower.isEmpty()) lower.remove(lower.size() - 1);
        if (!upper.isEmpty()) upper.remove(upper.size() - 1);

        List<Integer> hull = new ArrayList<>(lower.size() + upper.size());
        hull.addAll(lower);
        hull.addAll(upper);
        return hull;
    }

    private static double cross(List<PointNode> points, int ia, int ib, int ic) {
        PointNode a = points.get(ia);
        PointNode b = points.get(ib);
        PointNode c = points.get(ic);
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    private static boolean isCyclicShift(List<Integer> actual, List<Integer> expected) {
        int n = expected.size();
        if (actual.size() != n || n == 0) return false;

        int first = actual.get(0);
        for (int start = 0; start < n; start++) {
            if (expected.get(start) != first) continue;

            boolean allMatch = true;
            for (int i = 0; i < n; i++) {
                if (!actual.get(i).equals(expected.get((start + i) % n))) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }
        return false;
    }

    public record CheckResult(boolean follows, int hullCorners) {}
}
