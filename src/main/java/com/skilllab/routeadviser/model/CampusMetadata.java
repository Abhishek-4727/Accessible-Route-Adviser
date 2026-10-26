package com.skilllab.routeadviser.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Dataset-level metadata and disclaimer, stored in the JSON file.
 */
public class CampusMetadata {

    private final int schemaVersion;
    private final String description;
    private final String disclaimer;

    @JsonCreator
    public CampusMetadata(
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("description") String description,
            @JsonProperty("disclaimer") String disclaimer) {
        this.schemaVersion = schemaVersion;
        this.description = description;
        this.disclaimer = disclaimer;
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getDescription() { return description; }
    public String getDisclaimer() { return disclaimer; }
}
