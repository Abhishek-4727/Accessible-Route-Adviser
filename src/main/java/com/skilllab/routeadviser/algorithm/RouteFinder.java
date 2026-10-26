package com.skilllab.routeadviser.algorithm;

import com.skilllab.routeadviser.graph.CampusGraph;
import com.skilllab.routeadviser.model.*;

import java.util.*;

/**
 * Finds routes using Dijkstra's algorithm with edge-eligibility filtering.
 *
 * <p><b>Tie-breaking (deterministic):</b> For equal total distance, prefer
 * fewer segments, then the lexicographically smaller sequence of path ids.</p>
 */
public class RouteFinder {

    private final CampusGraph graph;

    public RouteFinder(CampusGraph graph) {
        this.graph = graph;
    }

    /**
     * Finds a route from {@code startId} to {@code destId} under the given policy.
     *
     * @param startId the start location id
     * @param destId  the destination location id
     * @param policy  the step-free filtering policy
     * @return a {@link Route} with status, path, confidence, and explanation
     */
    public Route findRoute(String startId, String destId, StepFreePolicy policy) {
        // Validate inputs
        if (startId == null || destId == null || startId.isBlank() || destId.isBlank()) {
            return Route.noRoute(RouteStatus.INVALID_INPUT, policy,
                    "Start and destination location IDs must be provided.");
        }
        if (!graph.hasLocation(startId)) {
            return Route.noRoute(RouteStatus.INVALID_INPUT, policy,
                    "Unknown start location: '" + startId + "'.");
        }
        if (!graph.hasLocation(destId)) {
            return Route.noRoute(RouteStatus.INVALID_INPUT, policy,
                    "Unknown destination location: '" + destId + "'.");
        }

        // Same start and destination
        if (startId.equals(destId)) {
            return Route.sameLocation(startId, policy);
        }

        // Try finding route with the given policy
        DijkstraResult result = dijkstra(startId, destId, policy);

        if (result != null) {
            return buildFoundRoute(startId, destId, result, policy);
        }

        // Route not found under this policy — determine why
        return diagnoseFailure(startId, destId, policy);
    }

    /**
     * Diagnoses why no route was found and returns the appropriate status.
     */
    private Route diagnoseFailure(String startId, String destId, StepFreePolicy policy) {
        // Check if reachable with NO filtering at all (NONE policy)
        DijkstraResult withNone = dijkstra(startId, destId, StepFreePolicy.NONE);

        if (withNone == null) {
            // Not even reachable ignoring closures?
            DijkstraResult ignoringClosures = dijkstraIgnoringClosures(startId, destId);
            if (ignoringClosures != null) {
                return Route.noRoute(RouteStatus.BLOCKED_BY_CLOSURES, policy,
                        "No route from '" + graph.getLocation(startId).getName() +
                        "' to '" + graph.getLocation(destId).getName() +
                        "' is available. All routes are blocked by path closures.");
            }
            return Route.noRoute(RouteStatus.NO_CONNECTION, policy,
                    "'" + graph.getLocation(startId).getName() +
                    "' and '" + graph.getLocation(destId).getName() +
                    "' are not connected in the campus graph.");
        }

        // Reachable with NONE, but not with the requested policy
        return Route.noRoute(RouteStatus.BLOCKED_BY_ACCESSIBILITY, policy,
                "A route exists from '" + graph.getLocation(startId).getName() +
                "' to '" + graph.getLocation(destId).getName() +
                "', but no route satisfies the '" + policyLabel(policy) +
                "' step-free requirement.");
    }

    /**
     * Dijkstra with edge-eligibility filtering based on the step-free policy.
     * Returns null if no path found.
     */
    private DijkstraResult dijkstra(String startId, String destId, StepFreePolicy policy) {
        // dist[node] = best distance found so far
        Map<String, Double> dist = new HashMap<>();
        // prev[node] = (previousNode, pathUsed)
        Map<String, PrevEntry> prev = new HashMap<>();
        // segCount[node] = segment count on best path
        Map<String, Integer> segCount = new HashMap<>();
        // pathIdSeq[node] = sorted path id sequence for tie-breaking
        Map<String, List<String>> pathIdSeq = new HashMap<>();

        // Priority queue: (distance, -segCount for fewer-is-better, nodeId)
        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> {
            int cmp = Double.compare(a[0], b[0]);
            if (cmp != 0) return cmp;
            return Double.compare(a[1], b[1]); // fewer segments first
        });

        Set<String> visited = new HashSet<>();
        dist.put(startId, 0.0);
        segCount.put(startId, 0);
        pathIdSeq.put(startId, List.of());
        pq.offer(new double[]{0.0, 0, startId.hashCode()});
        // Store nodeId separately since double[] can't hold strings
        Map<Double, String> hashToNode = new HashMap<>();

        // Simpler approach: use a proper node holder
        record QueueEntry(double distance, int segments, List<String> pathIds, String nodeId)
                implements Comparable<QueueEntry> {
            @Override
            public int compareTo(QueueEntry other) {
                int cmp = Double.compare(this.distance, other.distance);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(this.segments, other.segments);
                if (cmp != 0) return cmp;
                return comparePathIdLists(this.pathIds, other.pathIds);
            }
        }

        PriorityQueue<QueueEntry> queue = new PriorityQueue<>();
        queue.offer(new QueueEntry(0.0, 0, List.of(), startId));

        while (!queue.isEmpty()) {
            QueueEntry current = queue.poll();
            String nodeId = current.nodeId();

            if (visited.contains(nodeId)) continue;
            visited.add(nodeId);

            if (nodeId.equals(destId)) {
                // Reconstruct path
                return reconstructPath(startId, destId, prev);
            }

            for (CampusPath edge : graph.getEdges(nodeId)) {
                // Filter closed paths
                if (edge.isClosed()) continue;

                // Filter by step-free policy
                if (!isEdgeEligible(edge, policy)) continue;

                String neighbor = edge.otherEnd(nodeId);
                if (neighbor == null || visited.contains(neighbor)) continue;

                double newDist = current.distance() + edge.getDistanceMeters();
                int newSegs = current.segments() + 1;
                List<String> newPathIds = new ArrayList<>(current.pathIds());
                newPathIds.add(edge.getId());

                boolean isBetter = false;
                if (!dist.containsKey(neighbor)) {
                    isBetter = true;
                } else {
                    double existDist = dist.get(neighbor);
                    int existSegs = segCount.get(neighbor);
                    List<String> existPathIds = pathIdSeq.get(neighbor);

                    int cmp = Double.compare(newDist, existDist);
                    if (cmp < 0) {
                        isBetter = true;
                    } else if (cmp == 0) {
                        cmp = Integer.compare(newSegs, existSegs);
                        if (cmp < 0) {
                            isBetter = true;
                        } else if (cmp == 0) {
                            if (comparePathIdLists(newPathIds, existPathIds) < 0) {
                                isBetter = true;
                            }
                        }
                    }
                }

                if (isBetter) {
                    dist.put(neighbor, newDist);
                    segCount.put(neighbor, newSegs);
                    pathIdSeq.put(neighbor, newPathIds);
                    prev.put(neighbor, new PrevEntry(nodeId, edge));
                    queue.offer(new QueueEntry(newDist, newSegs, newPathIds, neighbor));
                }
            }
        }

        return null; // no path found
    }

    /**
     * Dijkstra ignoring closures (to distinguish NO_CONNECTION from BLOCKED_BY_CLOSURES).
     */
    private DijkstraResult dijkstraIgnoringClosures(String startId, String destId) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, PrevEntry> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();

        record QE(double distance, String nodeId) implements Comparable<QE> {
            @Override public int compareTo(QE o) { return Double.compare(distance, o.distance); }
        }

        PriorityQueue<QE> queue = new PriorityQueue<>();
        dist.put(startId, 0.0);
        queue.offer(new QE(0.0, startId));

        while (!queue.isEmpty()) {
            QE current = queue.poll();
            if (visited.contains(current.nodeId)) continue;
            visited.add(current.nodeId);

            if (current.nodeId.equals(destId)) {
                return reconstructPath(startId, destId, prev);
            }

            for (CampusPath edge : graph.getEdges(current.nodeId)) {
                // Do NOT filter closed paths here
                String neighbor = edge.otherEnd(current.nodeId);
                if (neighbor == null || visited.contains(neighbor)) continue;

                double newDist = current.distance + edge.getDistanceMeters();
                if (!dist.containsKey(neighbor) || newDist < dist.get(neighbor)) {
                    dist.put(neighbor, newDist);
                    prev.put(neighbor, new PrevEntry(current.nodeId, edge));
                    queue.offer(new QE(newDist, neighbor));
                }
            }
        }
        return null;
    }

    private boolean isEdgeEligible(CampusPath edge, StepFreePolicy policy) {
        return switch (policy) {
            case NONE -> true;
            case AVOID_KNOWN_NO -> edge.getStepFree() != StepFreeStatus.NO;
            case CONFIRMED_ONLY -> edge.getStepFree() == StepFreeStatus.YES;
        };
    }

    private DijkstraResult reconstructPath(String startId, String destId,
                                           Map<String, PrevEntry> prev) {
        List<String> locationIds = new ArrayList<>();
        List<CampusPath> paths = new ArrayList<>();
        String current = destId;

        while (!current.equals(startId)) {
            locationIds.add(current);
            PrevEntry entry = prev.get(current);
            if (entry == null) return null;
            paths.add(entry.path);
            current = entry.prevNode;
        }
        locationIds.add(startId);

        Collections.reverse(locationIds);
        Collections.reverse(paths);

        double totalDist = paths.stream().mapToDouble(CampusPath::getDistanceMeters).sum();
        return new DijkstraResult(locationIds, paths, totalDist);
    }

    private Route buildFoundRoute(String startId, String destId,
                                  DijkstraResult result, StepFreePolicy policy) {
        StepFreeConfidence confidence = computeConfidence(result.paths);
        String explanation = generateExplanation(result, policy, startId, destId, confidence);

        return new Route(RouteStatus.FOUND, policy,
                result.locationIds, result.paths,
                result.totalDistance, confidence, explanation);
    }

    /**
     * Computes step-free confidence from the path segments.
     */
    static StepFreeConfidence computeConfidence(List<CampusPath> paths) {
        if (paths.isEmpty()) return StepFreeConfidence.CONFIRMED_STEP_FREE;

        boolean hasNo = false;
        boolean hasUnknown = false;
        for (CampusPath p : paths) {
            if (p.getStepFree() == StepFreeStatus.NO) hasNo = true;
            if (p.getStepFree() == StepFreeStatus.UNKNOWN) hasUnknown = true;
        }
        if (hasNo) return StepFreeConfidence.NOT_STEP_FREE;
        if (hasUnknown) return StepFreeConfidence.POSSIBLY_STEP_FREE;
        return StepFreeConfidence.CONFIRMED_STEP_FREE;
    }

    private String generateExplanation(DijkstraResult result, StepFreePolicy policy,
                                       String startId, String destId,
                                       StepFreeConfidence confidence) {
        StringBuilder sb = new StringBuilder();
        String startName = graph.getLocation(startId).getName();
        String destName = graph.getLocation(destId).getName();

        int estMinutes = Math.max(1, (int) Math.round(result.totalDistance / 70.0));

        sb.append("📍 ROUTE: ").append(startName).append(" ➔ ").append(destName).append("\n");
        sb.append("🎯 Policy: ").append(policyLabel(policy)).append("\n\n");

        sb.append(String.format("📊 Summary: %.0f m total distance · ~%d min walk · %d path segment(s)\n",
                result.totalDistance, estMinutes, result.paths.size()));
        sb.append("♿ Step-Free Status: ").append(confidenceLabel(confidence)).append("\n\n");

        // Per-segment details
        sb.append("🗺️ Step-by-Step Directions:\n");
        for (int i = 0; i < result.paths.size(); i++) {
            CampusPath p = result.paths.get(i);
            String fromName = graph.getLocation(result.locationIds.get(i)).getName();
            String toName = graph.getLocation(result.locationIds.get(i + 1)).getName();
            String sfBadge = switch (p.getStepFree()) {
                case YES -> "✓ Step-free paved path";
                case NO -> "✗ Steps / stairs present (NOT step-free)";
                case UNKNOWN -> "⚠ Unverified step-free status";
            };
            sb.append(String.format("  Leg %d: Walk %.0f m from %s to %s\n        [%s · Path ID: %s]\n",
                    i + 1, p.getDistanceMeters(), fromName, toName, sfBadge, p.getId()));
        }

        // Warnings / Confidence Note
        if (confidence == StepFreeConfidence.POSSIBLY_STEP_FREE) {
            long unknownCount = result.paths.stream()
                    .filter(p -> p.getStepFree() == StepFreeStatus.UNKNOWN).count();
            sb.append(String.format("\n⚠️ Note: %d segment(s) on this route have UNKNOWN accessibility. " +
                    "Surface has not been formally surveyed; exercise caution.\n", unknownCount));
        } else if (confidence == StepFreeConfidence.NOT_STEP_FREE) {
            long noCount = result.paths.stream()
                    .filter(p -> p.getStepFree() == StepFreeStatus.NO).count();
            sb.append(String.format("\n⛔ Warning: %d segment(s) contain confirmed steps/curbs. " +
                    "Not suitable for unassisted wheelchair users.\n", noCount));
        }

        // Building notes for start and destination (informational only; never changes route/confidence)
        appendBuildingNote(sb, startId, "Start");
        appendBuildingNote(sb, destId, "Destination");

        return sb.toString();
    }

    /**
     * Appends an informational building note. Gates are skipped (NOT_APPLICABLE flags
     * are not meaningful to report in route explanations).
     */
    private void appendBuildingNote(StringBuilder sb, String locationId, String label) {
        CampusLocation loc = graph.getLocation(locationId);
        if (loc == null || loc.getType() == LocationType.GATE) return;

        sb.append("\n🏛️ ").append(label).append(" building notes (").append(loc.getName()).append("):\n");
        sb.append("  • Entrance ramp: ").append(flagLabel(loc.getRampAvailable()))
          .append(loc.getRampAvailable() == AvailabilityStatus.YES ? " (Ramp available for entry)" :
                  loc.getRampAvailable() == AvailabilityStatus.NO ? " (Steps required to enter)" : "").append("\n");
        sb.append("  • Stairs to floors: ").append(flagLabel(loc.getStairsAvailable())).append("\n");
        sb.append("  • Lift to floors: ").append(flagLabel(loc.getLiftAvailable()))
          .append(loc.getLiftAvailable() == AvailabilityStatus.YES ? " (Elevator available for upper floors)" :
                  loc.getLiftAvailable() == AvailabilityStatus.NO ? " (No elevator; stairs only)" : "").append("\n");
    }

    private static String flagLabel(AvailabilityStatus status) {
        return switch (status) {
            case YES -> "Yes";
            case NO -> "No";
            case UNKNOWN -> "Unknown";
            case NOT_APPLICABLE -> "N/A";
        };
    }

    static String policyLabel(StepFreePolicy policy) {
        return switch (policy) {
            case NONE -> "Shortest distance, no step-free filtering";
            case AVOID_KNOWN_NO -> "Avoid known non-step-free paths";
            case CONFIRMED_ONLY -> "Confirmed step-free paths only";
        };
    }

    private static String confidenceLabel(StepFreeConfidence c) {
        return switch (c) {
            case CONFIRMED_STEP_FREE -> "Confirmed step-free (all segments verified YES)";
            case POSSIBLY_STEP_FREE -> "Possibly step-free (no confirmed obstacles, but some segments unknown)";
            case NOT_STEP_FREE -> "Not step-free (at least one segment confirmed NO)";
        };
    }

    private static int comparePathIdLists(List<String> a, List<String> b) {
        int len = Math.min(a.size(), b.size());
        for (int i = 0; i < len; i++) {
            int cmp = a.get(i).compareTo(b.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.size(), b.size());
    }

    // Internal data holders
    private record PrevEntry(String prevNode, CampusPath path) {}

    record DijkstraResult(List<String> locationIds, List<CampusPath> paths, double totalDistance) {}
}
