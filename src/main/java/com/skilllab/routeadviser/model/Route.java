package com.skilllab.routeadviser.model;

import java.util.List;

/**
 * Result of a route-finding operation.
 *
 * <p>Contains the ordered list of locations and paths forming the route,
 * total distance, a status code, step-free confidence, and an explanation.</p>
 */
public class Route {

    private final RouteStatus status;
    private final StepFreePolicy policyUsed;
    private final List<String> orderedLocationIds;
    private final List<CampusPath> orderedPaths;
    private final double totalDistanceMeters;
    private final StepFreeConfidence stepFreeConfidence;
    private final String explanation;

    /**
     * Creates a found route.
     */
    public Route(RouteStatus status, StepFreePolicy policyUsed,
                 List<String> orderedLocationIds, List<CampusPath> orderedPaths,
                 double totalDistanceMeters, StepFreeConfidence stepFreeConfidence,
                 String explanation) {
        this.status = status;
        this.policyUsed = policyUsed;
        this.orderedLocationIds = orderedLocationIds;
        this.orderedPaths = orderedPaths;
        this.totalDistanceMeters = totalDistanceMeters;
        this.stepFreeConfidence = stepFreeConfidence;
        this.explanation = explanation;
    }

    /**
     * Creates a non-found route (error/no-connection/blocked).
     */
    public static Route noRoute(RouteStatus status, StepFreePolicy policyUsed, String explanation) {
        return new Route(status, policyUsed, List.of(), List.of(), 0, null, explanation);
    }

    /**
     * Creates a zero-distance route (start == destination).
     */
    public static Route sameLocation(String locationId, StepFreePolicy policyUsed) {
        return new Route(RouteStatus.FOUND, policyUsed,
                List.of(locationId), List.of(), 0,
                StepFreeConfidence.CONFIRMED_STEP_FREE,
                "Start and destination are the same location. No travel needed.");
    }

    public RouteStatus getStatus() { return status; }
    public StepFreePolicy getPolicyUsed() { return policyUsed; }
    public List<String> getOrderedLocationIds() { return orderedLocationIds; }
    public List<CampusPath> getOrderedPaths() { return orderedPaths; }
    public double getTotalDistanceMeters() { return totalDistanceMeters; }
    public int getSegmentCount() { return orderedPaths.size(); }
    public StepFreeConfidence getStepFreeConfidence() { return stepFreeConfidence; }
    public String getExplanation() { return explanation; }

    public boolean isFound() { return status == RouteStatus.FOUND; }

    @Override
    public String toString() {
        if (!isFound()) {
            return "Route{status=" + status + ", explanation='" + explanation + "'}";
        }
        return "Route{status=FOUND, distance=" + totalDistanceMeters +
               "m, segments=" + getSegmentCount() +
               ", confidence=" + stepFreeConfidence + "}";
    }
}
