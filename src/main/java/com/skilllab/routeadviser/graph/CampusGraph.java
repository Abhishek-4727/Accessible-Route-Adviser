package com.skilllab.routeadviser.graph;

import com.skilllab.routeadviser.model.CampusLocation;
import com.skilllab.routeadviser.model.CampusPath;
import com.skilllab.routeadviser.model.CampusData;

import java.util.*;

/**
 * Adjacency-list graph representation of the campus.
 *
 * <p>Each node (location id) maps to a list of {@link CampusPath} edges.
 * Paths are undirected: each path appears in both endpoint lists.</p>
 */
public class CampusGraph {

    private final Map<String, CampusLocation> locations;
    private final Map<String, List<CampusPath>> adjacencyList;
    private final Map<String, CampusPath> pathsById;

    public CampusGraph() {
        this.locations = new LinkedHashMap<>();
        this.adjacencyList = new LinkedHashMap<>();
        this.pathsById = new LinkedHashMap<>();
    }

    /**
     * Builds the graph from a loaded {@link CampusData} object.
     */
    public static CampusGraph buildFrom(CampusData data) {
        CampusGraph graph = new CampusGraph();
        for (CampusLocation loc : data.getLocations()) {
            graph.addLocation(loc);
        }
        for (CampusPath path : data.getPaths()) {
            graph.addPath(path);
        }
        return graph;
    }

    public void addLocation(CampusLocation location) {
        locations.put(location.getId(), location);
        adjacencyList.putIfAbsent(location.getId(), new ArrayList<>());
    }

    public void addPath(CampusPath path) {
        pathsById.put(path.getId(), path);
        adjacencyList.computeIfAbsent(path.getFrom(), k -> new ArrayList<>()).add(path);
        adjacencyList.computeIfAbsent(path.getTo(), k -> new ArrayList<>()).add(path);
    }

    /**
     * Returns all paths incident to the given node.
     */
    public List<CampusPath> getEdges(String locationId) {
        return adjacencyList.getOrDefault(locationId, Collections.emptyList());
    }

    public CampusLocation getLocation(String id) {
        return locations.get(id);
    }

    public boolean hasLocation(String id) {
        return locations.containsKey(id);
    }

    public CampusPath getPath(String id) {
        return pathsById.get(id);
    }

    public Collection<CampusLocation> getAllLocations() {
        return Collections.unmodifiableCollection(locations.values());
    }

    public Collection<CampusPath> getAllPaths() {
        return Collections.unmodifiableCollection(pathsById.values());
    }

    public Set<String> getAllLocationIds() {
        return Collections.unmodifiableSet(locations.keySet());
    }

    public int locationCount() { return locations.size(); }
    public int pathCount() { return pathsById.size(); }
}
