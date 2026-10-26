package com.skilllab.routeadviser.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A path (edge) between two campus locations.
 *
 * <p>Paths are undirected. {@code stepFree} describes whether this walking segment
 * has steps/stairs on the path itself — it is independent of the buildings'
 * ramp/stairs/lift attributes.</p>
 */
public class CampusPath {

    private final String id;
    private final String from;
    private final String to;
    private double distanceMeters;
    private boolean isClosed;
    private StepFreeStatus stepFree;

    @JsonCreator
    public CampusPath(
            @JsonProperty("id") String id,
            @JsonProperty("from") String from,
            @JsonProperty("to") String to,
            @JsonProperty("distanceMeters") Double distanceMeters,
            @JsonProperty("isClosed") @com.fasterxml.jackson.annotation.JsonAlias("closed") Boolean isClosed,
            @JsonProperty("stepFree") StepFreeStatus stepFree) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.distanceMeters = distanceMeters != null ? distanceMeters : -1;
        this.isClosed = isClosed != null ? isClosed : false;
        this.stepFree = stepFree;
    }

    public String getId() { return id; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public double getDistanceMeters() { return distanceMeters; }
    @JsonProperty("isClosed")
    public boolean isClosed() { return isClosed; }
    public StepFreeStatus getStepFree() { return stepFree; }

    public void setDistanceMeters(double distanceMeters) { this.distanceMeters = distanceMeters; }
    @JsonProperty("isClosed")
    public void setClosed(boolean closed) { this.isClosed = closed; }
    public void setStepFree(StepFreeStatus stepFree) { this.stepFree = stepFree; }

    /**
     * Returns the other endpoint of this path given one endpoint id.
     * @param nodeId one endpoint
     * @return the other endpoint, or null if nodeId is not an endpoint of this path
     */
    public String otherEnd(String nodeId) {
        if (from.equals(nodeId)) return to;
        if (to.equals(nodeId)) return from;
        return null;
    }

    @Override
    public String toString() {
        return "CampusPath{id='" + id + "', " + from + " <-> " + to +
               ", " + distanceMeters + "m, stepFree=" + stepFree +
               (isClosed ? ", CLOSED" : "") + "}";
    }
}
