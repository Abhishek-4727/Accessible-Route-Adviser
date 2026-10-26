package com.skilllab.routeadviser.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A campus location node representing a building or gate.
 *
 * <p>Building-level accessibility flags describe features <b>at the building itself</b>:</p>
 * <ul>
 *   <li>{@code rampAvailable} — whether the building has an entrance ramp (for entering).</li>
 *   <li>{@code stairsAvailable} — whether the building has stairs (for reaching its floors).</li>
 *   <li>{@code liftAvailable} — whether the building has a lift (for reaching its floors).</li>
 * </ul>
 * <p>These flags do <b>not</b> determine whether a <i>path</i> between buildings is step-free.
 * A building's entrance ramp does not make the walking path to it step-free.</p>
 */
public class CampusLocation {

    private final String id;
    private final String name;
    private final LocationType type;
    private final AvailabilityStatus rampAvailable;
    private final AvailabilityStatus stairsAvailable;
    private final AvailabilityStatus liftAvailable;
    private final DataSource dataSource;
    private final boolean verified;
    private final String description;
    private final double layoutX;
    private final double layoutY;

    @JsonCreator
    public CampusLocation(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") LocationType type,
            @JsonProperty("rampAvailable") AvailabilityStatus rampAvailable,
            @JsonProperty("stairsAvailable") AvailabilityStatus stairsAvailable,
            @JsonProperty("liftAvailable") AvailabilityStatus liftAvailable,
            @JsonProperty("dataSource") DataSource dataSource,
            @JsonProperty("verified") Boolean verified,
            @JsonProperty("description") String description,
            @JsonProperty("layoutX") Double layoutX,
            @JsonProperty("layoutY") Double layoutY) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.rampAvailable = rampAvailable;
        this.stairsAvailable = stairsAvailable;
        this.liftAvailable = liftAvailable;
        this.dataSource = dataSource;
        this.verified = verified != null ? verified : false;
        this.description = description;
        this.layoutX = layoutX != null ? layoutX : 0.0;
        this.layoutY = layoutY != null ? layoutY : 0.0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public LocationType getType() { return type; }

    /** Entrance ramp availability (building-level: for entering the building). */
    public AvailabilityStatus getRampAvailable() { return rampAvailable; }

    /** Stairs availability (building-level: for reaching floors within the building). */
    public AvailabilityStatus getStairsAvailable() { return stairsAvailable; }

    /** Lift availability (building-level: for reaching floors within the building). */
    public AvailabilityStatus getLiftAvailable() { return liftAvailable; }

    public DataSource getDataSource() { return dataSource; }
    public boolean isVerified() { return verified; }
    public String getDescription() { return description; }
    public double getLayoutX() { return layoutX; }
    public double getLayoutY() { return layoutY; }

    @Override
    public String toString() {
        return "CampusLocation{id='" + id + "', name='" + name + "'}";
    }
}
