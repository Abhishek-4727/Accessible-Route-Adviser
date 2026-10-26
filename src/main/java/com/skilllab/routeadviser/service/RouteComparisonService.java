package com.skilllab.routeadviser.service;

import com.skilllab.routeadviser.algorithm.RouteFinder;
import com.skilllab.routeadviser.graph.CampusGraph;
import com.skilllab.routeadviser.model.*;

/**
 * Compares shortest-distance and accessibility-prioritized routes.
 *
 * <p>This service runs two route searches (one with {@link StepFreePolicy#NONE}
 * and one with the user-selected accessibility policy) and produces a textual comparison.</p>
 */
public class RouteComparisonService {

    private CampusGraph graph;
    private RouteFinder finder;

    public RouteComparisonService(CampusGraph graph) {
        this.graph = graph;
        this.finder = new RouteFinder(graph);
    }

    /**
     * Replaces the current graph (e.g. after a data update) and rebuilds the finder.
     */
    public void updateGraph(CampusGraph newGraph) {
        this.graph = newGraph;
        this.finder = new RouteFinder(newGraph);
    }

    /**
     * Finds the shortest route (no step-free filtering).
     */
    public Route findShortestRoute(String startId, String destId) {
        return finder.findRoute(startId, destId, StepFreePolicy.NONE);
    }

    /**
     * Finds an accessibility-prioritized route under the given policy.
     */
    public Route findAccessibleRoute(String startId, String destId, StepFreePolicy policy) {
        if (policy == StepFreePolicy.NONE) {
            // NONE means no accessibility filter — same as shortest
            return finder.findRoute(startId, destId, StepFreePolicy.NONE);
        }
        return finder.findRoute(startId, destId, policy);
    }

    /**
     * Generates a textual comparison of two route results.
     */
    public String generateComparison(Route shortestRoute, Route accessibleRoute) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 ROUTE COMPARISON & ADVISORY\n");
        sb.append("─────────────────────────────────────────────────────────────\n");

        if (shortestRoute.isFound() && accessibleRoute.isFound()) {
            if (routesAreIdentical(shortestRoute, accessibleRoute)) {
                sb.append("✨ Result: Both the shortest and accessible routes are IDENTICAL.\n\n");
                sb.append(String.format("• Walking Distance: %.0f m across %d segment(s)\n",
                        shortestRoute.getTotalDistanceMeters(),
                        shortestRoute.getSegmentCount()));
                sb.append("• Step-Free Confidence: ").append(shortestRoute.getStepFreeConfidence()).append("\n\n");
                sb.append("💡 Decision: The shortest path is already confirmed step-free. You can take the direct route with complete confidence!");
            } else {
                double diff = accessibleRoute.getTotalDistanceMeters()
                             - shortestRoute.getTotalDistanceMeters();
                int extraMin = (int) Math.round(diff / 70.0);

                sb.append(String.format("Shortest route: %.0f m (%d segments)\n",
                        shortestRoute.getTotalDistanceMeters(),
                        shortestRoute.getSegmentCount()));
                sb.append(String.format("Accessible route: %.0f m (%d segments)\n",
                        accessibleRoute.getTotalDistanceMeters(),
                        accessibleRoute.getSegmentCount()));

                if (diff > 0) {
                    sb.append(String.format("The accessible route is %.0f m longer", diff));
                    if (extraMin > 0) {
                        sb.append(String.format(" (~%d min extra walking).\n\n", extraMin));
                    } else {
                        sb.append(".\n\n");
                    }
                } else if (diff < 0) {
                    sb.append(String.format("The accessible route is %.0f m shorter.\n\n", -diff));
                } else {
                    sb.append("Both routes have equal distance but use different segments.\n\n");
                }

                sb.append("• Shortest route step-free confidence: ")
                  .append(shortestRoute.getStepFreeConfidence()).append("\n");
                sb.append("• Accessible route step-free confidence: ")
                  .append(accessibleRoute.getStepFreeConfidence()).append("\n\n");

                sb.append("💡 Decision: ");
                if (shortestRoute.getStepFreeConfidence() == StepFreeConfidence.NOT_STEP_FREE) {
                    sb.append("The shortest path contains stairs or steps. Wheelchair users, individuals with strollers, or anyone avoiding stairs should follow the Accessible Route detour.");
                } else {
                    sb.append("The accessible route prioritizes verified paths. Choose the accessible route for maximum comfort and reliability.");
                }
            }
        } else {
            if (!shortestRoute.isFound()) {
                sb.append("• Shortest route: ").append(shortestRoute.getStatus())
                  .append(" — ").append(shortestRoute.getExplanation()).append("\n");
            }
            if (!accessibleRoute.isFound()) {
                sb.append("• Accessible route: ").append(accessibleRoute.getStatus())
                  .append(" — ").append(accessibleRoute.getExplanation()).append("\n");
                if (accessibleRoute.getStatus() == RouteStatus.BLOCKED_BY_ACCESSIBILITY) {
                    sb.append("\n💡 Decision: A physical route exists (shown on the left), but it contains steps or unverified paths. If assistance is available, the shortest path may be navigated with help.");
                } else if (accessibleRoute.getStatus() == RouteStatus.BLOCKED_BY_CLOSURES) {
                    sb.append("\n💡 Decision: Paths connecting these buildings are currently marked as CLOSED (e.g. maintenance). Check the Path Management tab to inspect closures.");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Checks if two found routes use the same sequence of paths.
     */
    private boolean routesAreIdentical(Route a, Route b) {
        if (!a.isFound() || !b.isFound()) return false;
        if (a.getOrderedPaths().size() != b.getOrderedPaths().size()) return false;
        for (int i = 0; i < a.getOrderedPaths().size(); i++) {
            if (!a.getOrderedPaths().get(i).getId().equals(b.getOrderedPaths().get(i).getId())) {
                return false;
            }
        }
        return true;
    }

    public CampusGraph getGraph() { return graph; }
}
