package com.skilllab.routeadviser.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Top-level wrapper for the campus data JSON file.
 * Contains metadata, locations, and paths.
 */
public class CampusData {

    private final CampusMetadata metadata;
    private final List<CampusLocation> locations;
    private List<CampusPath> paths;

    @JsonCreator
    public CampusData(
            @JsonProperty("metadata") CampusMetadata metadata,
            @JsonProperty("locations") List<CampusLocation> locations,
            @JsonProperty("paths") List<CampusPath> paths) {
        this.metadata = metadata;
        this.locations = locations;
        this.paths = paths;
    }

    public CampusMetadata getMetadata() { return metadata; }
    public List<CampusLocation> getLocations() { return locations; }
    public List<CampusPath> getPaths() { return paths; }
    public void setPaths(List<CampusPath> paths) { this.paths = paths; }
}
