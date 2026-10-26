package com.skilllab.routeadviser.data;

import com.skilllab.routeadviser.graph.CampusGraph;
import com.skilllab.routeadviser.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles data updates (add/modify paths) with validation, persistence,
 * and graph rebuilding.
 *
 * <p>After every successful update: validate → save → rebuild graph → notify caller.</p>
 * <p>An invalid update never corrupts the existing file or in-memory graph.</p>
 */
public class CampusDataUpdater {

    private final CampusDataLoader loader;
    private CampusData currentData;
    private CampusGraph currentGraph;

    public CampusDataUpdater(CampusDataLoader loader, CampusData data, CampusGraph graph) {
        this.loader = loader;
        this.currentData = data;
        this.currentGraph = graph;
    }

    public CampusData getCurrentData() { return currentData; }
    public CampusGraph getCurrentGraph() { return currentGraph; }

    /**
     * Adds a new path. Validates, saves, and rebuilds the graph.
     *
     * @return empty if successful, or an error message
     */
    public Optional<String> addPath(String id, String from, String to,
                                    double distanceMeters, boolean isClosed,
                                    StepFreeStatus stepFree) {
        // Pre-validate inputs
        if (id == null || id.isBlank()) return Optional.of("Path ID must not be blank.");
        if (from == null || from.isBlank()) return Optional.of("'From' location must not be blank.");
        if (to == null || to.isBlank()) return Optional.of("'To' location must not be blank.");
        if (from.equals(to)) return Optional.of("Self-loop: 'from' and 'to' are the same ('" + from + "').");
        if (distanceMeters <= 0 || Double.isNaN(distanceMeters) || Double.isInfinite(distanceMeters)) {
            return Optional.of("Distance must be a positive number.");
        }
        if (stepFree == null) return Optional.of("Step-free status must not be null.");

        // Check for duplicate path id
        for (CampusPath p : currentData.getPaths()) {
            if (p.getId().equals(id)) {
                return Optional.of("A path with ID '" + id + "' already exists.");
            }
        }

        // Check that both locations exist
        boolean fromExists = currentData.getLocations().stream().anyMatch(l -> l.getId().equals(from));
        boolean toExists = currentData.getLocations().stream().anyMatch(l -> l.getId().equals(to));
        if (!fromExists) return Optional.of("Location '" + from + "' does not exist.");
        if (!toExists) return Optional.of("Location '" + to + "' does not exist.");

        // Create new path and add to data
        CampusPath newPath = new CampusPath(id, from, to, distanceMeters, isClosed, stepFree);
        List<CampusPath> updatedPaths = new ArrayList<>(currentData.getPaths());
        updatedPaths.add(newPath);

        CampusData updatedData = new CampusData(currentData.getMetadata(),
                currentData.getLocations(), updatedPaths);

        return trySaveAndRebuild(updatedData);
    }

    /**
     * Updates the distance of an existing path.
     */
    public Optional<String> updateDistance(String pathId, double newDistance) {
        if (newDistance <= 0 || Double.isNaN(newDistance) || Double.isInfinite(newDistance)) {
            return Optional.of("Distance must be a positive number.");
        }
        CampusPath path = findPath(pathId);
        if (path == null) return Optional.of("Path '" + pathId + "' not found.");

        double oldDistance = path.getDistanceMeters();
        path.setDistanceMeters(newDistance);
        Optional<String> result = trySaveAndRebuild(currentData);
        if (result.isPresent()) {
            path.setDistanceMeters(oldDistance); // rollback
        }
        return result;
    }

    /**
     * Updates the step-free status of an existing path.
     */
    public Optional<String> updateStepFree(String pathId, StepFreeStatus newStatus) {
        if (newStatus == null) return Optional.of("Step-free status must not be null.");
        CampusPath path = findPath(pathId);
        if (path == null) return Optional.of("Path '" + pathId + "' not found.");

        StepFreeStatus oldStatus = path.getStepFree();
        path.setStepFree(newStatus);
        Optional<String> result = trySaveAndRebuild(currentData);
        if (result.isPresent()) {
            path.setStepFree(oldStatus); // rollback
        }
        return result;
    }

    /**
     * Closes or reopens an existing path.
     */
    public Optional<String> setPathClosed(String pathId, boolean closed) {
        CampusPath path = findPath(pathId);
        if (path == null) return Optional.of("Path '" + pathId + "' not found.");

        boolean oldClosed = path.isClosed();
        path.setClosed(closed);
        Optional<String> result = trySaveAndRebuild(currentData);
        if (result.isPresent()) {
            path.setClosed(oldClosed); // rollback
        }
        return result;
    }

    /**
     * Resets to default data and rebuilds the graph.
     */
    public Optional<String> resetToDefault() {
        try {
            loader.resetToDefault();
            CampusData freshData = loader.load();
            this.currentData = freshData;
            this.currentGraph = CampusGraph.buildFrom(freshData);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of("Reset failed: " + e.getMessage());
        }
    }

    private CampusPath findPath(String pathId) {
        for (CampusPath p : currentData.getPaths()) {
            if (p.getId().equals(pathId)) return p;
        }
        return null;
    }

    /**
     * Validates, saves, and rebuilds the graph. Returns empty on success.
     */
    private Optional<String> trySaveAndRebuild(CampusData data) {
        // Validate
        List<String> errors = loader.getValidator().validate(data);
        if (!errors.isEmpty()) {
            return Optional.of("Validation failed: " + String.join("; ", errors));
        }

        // Save
        try {
            loader.save(data);
        } catch (IOException e) {
            return Optional.of("Save failed: " + e.getMessage());
        } catch (IllegalStateException e) {
            return Optional.of("Save validation failed: " + e.getMessage());
        }

        // Rebuild graph
        this.currentData = data;
        this.currentGraph = CampusGraph.buildFrom(data);
        return Optional.empty();
    }
}
