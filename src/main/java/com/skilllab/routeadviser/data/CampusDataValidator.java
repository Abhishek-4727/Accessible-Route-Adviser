package com.skilllab.routeadviser.data;

import com.skilllab.routeadviser.model.*;

import java.util.*;

/**
 * Validates loaded {@link CampusData} for structural and semantic correctness.
 *
 * <p>Validation rules:</p>
 * <ul>
 *   <li>No null/blank ids or names</li>
 *   <li>No duplicate location or path ids</li>
 *   <li>Path endpoints must reference existing locations</li>
 *   <li>No self-loops</li>
 *   <li>Distances must be positive (not zero, negative, NaN, or infinite)</li>
 *   <li>All mandatory fields must be present (non-null)</li>
 *   <li>Enum values must be valid</li>
 *   <li>{@code NOT_APPLICABLE} only valid on GATE nodes; YES/NO/UNKNOWN invalid on GATEs</li>
 * </ul>
 */
public class CampusDataValidator {

    /**
     * Validates the given campus data. Returns a list of error messages
     * (empty if valid).
     */
    public List<String> validate(CampusData data) {
        List<String> errors = new ArrayList<>();

        if (data == null) {
            errors.add("Campus data is null.");
            return errors;
        }

        if (data.getLocations() == null) {
            errors.add("Locations list is null.");
        }
        if (data.getPaths() == null) {
            errors.add("Paths list is null.");
        }
        if (!errors.isEmpty()) return errors;

        // Collect valid location ids
        Set<String> locationIds = new HashSet<>();
        validateLocations(data.getLocations(), locationIds, errors);
        validatePaths(data.getPaths(), locationIds, errors);

        return errors;
    }

    private void validateLocations(List<CampusLocation> locations,
                                   Set<String> locationIds,
                                   List<String> errors) {
        for (int i = 0; i < locations.size(); i++) {
            CampusLocation loc = locations.get(i);
            String prefix = "Location[" + i + "]: ";

            if (loc.getId() == null || loc.getId().isBlank()) {
                errors.add(prefix + "id is null or blank.");
                continue;
            }
            if (!locationIds.add(loc.getId())) {
                errors.add(prefix + "duplicate id '" + loc.getId() + "'.");
            }
            if (loc.getName() == null || loc.getName().isBlank()) {
                errors.add(prefix + "name is null or blank.");
            }
            if (loc.getType() == null) {
                errors.add(prefix + "type is null.");
                continue;
            }
            if (loc.getDataSource() == null) {
                errors.add(prefix + "dataSource is null.");
            }

            // Validate building accessibility flags
            validateBuildingFlags(loc, prefix, errors);
        }
    }

    private void validateBuildingFlags(CampusLocation loc, String prefix, List<String> errors) {
        if (loc.getRampAvailable() == null) {
            errors.add(prefix + "rampAvailable is null.");
            return;
        }
        if (loc.getStairsAvailable() == null) {
            errors.add(prefix + "stairsAvailable is null.");
            return;
        }
        if (loc.getLiftAvailable() == null) {
            errors.add(prefix + "liftAvailable is null.");
            return;
        }

        if (loc.getType() == LocationType.GATE) {
            // Gates must use NOT_APPLICABLE for all three flags
            if (loc.getRampAvailable() != AvailabilityStatus.NOT_APPLICABLE) {
                errors.add(prefix + "GATE node must use NOT_APPLICABLE for rampAvailable, got "
                        + loc.getRampAvailable() + ".");
            }
            if (loc.getStairsAvailable() != AvailabilityStatus.NOT_APPLICABLE) {
                errors.add(prefix + "GATE node must use NOT_APPLICABLE for stairsAvailable, got "
                        + loc.getStairsAvailable() + ".");
            }
            if (loc.getLiftAvailable() != AvailabilityStatus.NOT_APPLICABLE) {
                errors.add(prefix + "GATE node must use NOT_APPLICABLE for liftAvailable, got "
                        + loc.getLiftAvailable() + ".");
            }
        } else if (loc.getType() == LocationType.BUILDING) {
            // Buildings must NOT use NOT_APPLICABLE
            if (loc.getRampAvailable() == AvailabilityStatus.NOT_APPLICABLE) {
                errors.add(prefix + "BUILDING node must not use NOT_APPLICABLE for rampAvailable.");
            }
            if (loc.getStairsAvailable() == AvailabilityStatus.NOT_APPLICABLE) {
                errors.add(prefix + "BUILDING node must not use NOT_APPLICABLE for stairsAvailable.");
            }
            if (loc.getLiftAvailable() == AvailabilityStatus.NOT_APPLICABLE) {
                errors.add(prefix + "BUILDING node must not use NOT_APPLICABLE for liftAvailable.");
            }
        }
    }

    private void validatePaths(List<CampusPath> paths, Set<String> locationIds,
                               List<String> errors) {
        Set<String> pathIds = new HashSet<>();

        for (int i = 0; i < paths.size(); i++) {
            CampusPath path = paths.get(i);
            String prefix = "Path[" + i + "]: ";

            if (path.getId() == null || path.getId().isBlank()) {
                errors.add(prefix + "id is null or blank.");
                continue;
            }
            if (!pathIds.add(path.getId())) {
                errors.add(prefix + "duplicate id '" + path.getId() + "'.");
            }
            if (path.getFrom() == null || path.getFrom().isBlank()) {
                errors.add(prefix + "from is null or blank.");
            } else if (!locationIds.contains(path.getFrom())) {
                errors.add(prefix + "from references unknown location '" + path.getFrom() + "'.");
            }
            if (path.getTo() == null || path.getTo().isBlank()) {
                errors.add(prefix + "to is null or blank.");
            } else if (!locationIds.contains(path.getTo())) {
                errors.add(prefix + "to references unknown location '" + path.getTo() + "'.");
            }
            // Self-loop check
            if (path.getFrom() != null && path.getFrom().equals(path.getTo())) {
                errors.add(prefix + "self-loop (from == to = '" + path.getFrom() + "').");
            }
            // Distance validation
            if (Double.isNaN(path.getDistanceMeters()) || Double.isInfinite(path.getDistanceMeters())) {
                errors.add(prefix + "distanceMeters is NaN or infinite.");
            } else if (path.getDistanceMeters() <= 0) {
                errors.add(prefix + "distanceMeters must be positive, got " + path.getDistanceMeters() + ".");
            }
            // Step-free status
            if (path.getStepFree() == null) {
                errors.add(prefix + "stepFree is null.");
            }
        }
    }
}
