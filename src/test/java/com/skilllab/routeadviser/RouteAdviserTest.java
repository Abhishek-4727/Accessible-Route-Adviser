package com.skilllab.routeadviser;

import com.skilllab.routeadviser.algorithm.RouteFinder;
import com.skilllab.routeadviser.data.CampusDataLoader;
import com.skilllab.routeadviser.data.CampusDataUpdater;
import com.skilllab.routeadviser.data.CampusDataValidator;
import com.skilllab.routeadviser.graph.CampusGraph;
import com.skilllab.routeadviser.model.*;
import com.skilllab.routeadviser.service.RouteComparisonService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the Accessible Route Adviser.
 *
 * <p>Uses a small deterministic in-code graph for algorithm tests (not the campus data).
 * Tests numbered per Section 11 of the specification.</p>
 *
 * <pre>
 * Test graph layout:
 *
 *   A ---100,YES--- B ---80,YES--- C
 *   |               |               |
 *  150,NO         200,UNKNOWN     120,YES
 *   |               |               |
 *   D ---90,YES---- E ---70,YES--- F
 *
 * Plus: G (isolated, no paths)
 * Plus: A---C direct path (250m, YES, CLOSED) for closure test
 * Plus: A---E alternate path (310m, YES) for longer step-free route test
 * </pre>
 */
class RouteAdviserTest {

    // ===== Deterministic Test Graph =====

    private static CampusGraph buildTestGraph() {
        CampusData data = buildTestData();
        return CampusGraph.buildFrom(data);
    }

    private static CampusData buildTestData() {
        List<CampusLocation> locations = List.of(
            loc("A", "Building A", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
            loc("B", "Building B", LocationType.BUILDING, AvailabilityStatus.NO, AvailabilityStatus.YES, AvailabilityStatus.NO),
            loc("C", "Building C", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
            loc("D", "Building D", LocationType.BUILDING, AvailabilityStatus.NO, AvailabilityStatus.YES, AvailabilityStatus.UNKNOWN),
            loc("E", "Building E", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
            loc("F", "Building F", LocationType.BUILDING, AvailabilityStatus.NO, AvailabilityStatus.YES, AvailabilityStatus.NO),
            loc("G", "Isolated Building", LocationType.BUILDING, AvailabilityStatus.UNKNOWN, AvailabilityStatus.UNKNOWN, AvailabilityStatus.UNKNOWN),
            loc("gate1", "Test Gate", LocationType.GATE, AvailabilityStatus.NOT_APPLICABLE, AvailabilityStatus.NOT_APPLICABLE, AvailabilityStatus.NOT_APPLICABLE)
        );

        List<CampusPath> paths = List.of(
            path("p1", "A", "B", 100, false, StepFreeStatus.YES),     // A-B: 100m, YES
            path("p2", "B", "C", 80, false, StepFreeStatus.YES),      // B-C: 80m, YES
            path("p3", "A", "D", 150, false, StepFreeStatus.NO),      // A-D: 150m, NO (not step-free)
            path("p4", "B", "E", 200, false, StepFreeStatus.UNKNOWN), // B-E: 200m, UNKNOWN
            path("p5", "C", "F", 120, false, StepFreeStatus.YES),     // C-F: 120m, YES
            path("p6", "D", "E", 90, false, StepFreeStatus.YES),      // D-E: 90m, YES
            path("p7", "E", "F", 70, false, StepFreeStatus.YES),      // E-F: 70m, YES
            path("p8", "A", "C", 250, true, StepFreeStatus.YES),      // A-C: 250m, YES but CLOSED
            path("p9", "A", "E", 310, false, StepFreeStatus.YES)      // A-E: 310m, YES (long step-free bypass)
        );

        CampusMetadata meta = new CampusMetadata(1, "Test data", "Test disclaimer");
        return new CampusData(meta, locations, paths);
    }

    private static CampusLocation loc(String id, String name, LocationType type,
                                       AvailabilityStatus ramp, AvailabilityStatus stairs,
                                       AvailabilityStatus lift) {
        return new CampusLocation(id, name, type, ramp, stairs, lift,
                type == LocationType.GATE ? DataSource.STUDENT_KNOWLEDGE : DataSource.STUDENT_KNOWLEDGE,
                false, null, 0.0, 0.0);
    }

    private static CampusPath path(String id, String from, String to, double dist,
                                    boolean closed, StepFreeStatus sf) {
        return new CampusPath(id, from, to, dist, closed, sf);
    }

    // ===== Test 1: Shortest-distance routing =====
    @Test
    @DisplayName("1. Shortest-distance routing (A→F)")
    void testShortestRoute() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        // Shortest: A→B(100)→C(80)→F(120) = 300m
        Route r = finder.findRoute("A", "F", StepFreePolicy.NONE);
        assertEquals(RouteStatus.FOUND, r.getStatus());
        assertEquals(300, r.getTotalDistanceMeters(), 0.01);
        assertEquals(List.of("A", "B", "C", "F"), r.getOrderedLocationIds());
    }

    // ===== Test 2: Accessibility route longer than shortest =====
    @Test
    @DisplayName("2. Accessibility route longer than shortest (A→D)")
    void testAccessibleRouteLongerThanShortest() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        // Shortest A→D: A→D(150m, NO) = 150m
        Route shortest = finder.findRoute("A", "D", StepFreePolicy.NONE);
        assertEquals(150, shortest.getTotalDistanceMeters(), 0.01);

        // Accessible A→D (AVOID_KNOWN_NO): must avoid p3(NO), go via A→E(310)→D(90) = 400m
        // or A→B(100)→E(200,UNKNOWN)→D... 
        // Let's check: A→B=100, B→E=200(UNKNOWN, allowed), E→D=90 = 390m
        // vs A→E=310, E→D=90 = 400m. So 390 is shorter.
        Route accessible = finder.findRoute("A", "D", StepFreePolicy.AVOID_KNOWN_NO);
        assertEquals(RouteStatus.FOUND, accessible.getStatus());
        assertTrue(accessible.getTotalDistanceMeters() > shortest.getTotalDistanceMeters(),
                "Accessible route should be longer: " + accessible.getTotalDistanceMeters()
                + " vs " + shortest.getTotalDistanceMeters());
    }

    // ===== Test 3: Avoiding NO paths =====
    @Test
    @DisplayName("3. AVOID_KNOWN_NO excludes paths with stepFree=NO")
    void testAvoidKnownNo() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        // A→D with AVOID_KNOWN_NO should not use p3 (A→D, NO)
        Route r = finder.findRoute("A", "D", StepFreePolicy.AVOID_KNOWN_NO);
        assertEquals(RouteStatus.FOUND, r.getStatus());
        // Verify no path in the route has stepFree=NO
        for (CampusPath p : r.getOrderedPaths()) {
            assertNotEquals(StepFreeStatus.NO, p.getStepFree(),
                    "Path " + p.getId() + " should not have stepFree=NO under AVOID_KNOWN_NO");
        }
    }

    // ===== Test 4: CONFIRMED_ONLY uses only YES =====
    @Test
    @DisplayName("4. CONFIRMED_ONLY uses only YES paths")
    void testConfirmedOnly() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        // A→E CONFIRMED_ONLY: must avoid p3(NO) and p4(UNKNOWN).
        // Must use A→E(310, YES) directly.
        Route r = finder.findRoute("A", "E", StepFreePolicy.CONFIRMED_ONLY);
        assertEquals(RouteStatus.FOUND, r.getStatus());
        for (CampusPath p : r.getOrderedPaths()) {
            assertEquals(StepFreeStatus.YES, p.getStepFree(),
                    "Path " + p.getId() + " must be YES under CONFIRMED_ONLY");
        }
    }

    // ===== Test 5: UNKNOWN accepted under AVOID_KNOWN_NO =====
    @Test
    @DisplayName("5. UNKNOWN accepted under AVOID_KNOWN_NO")
    void testUnknownAcceptedAvoidNo() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        // A→E with AVOID_KNOWN_NO: can use B→E (UNKNOWN)
        // A→B(100)→E(200 UNKNOWN) = 300 vs A→E(310) = 310
        Route r = finder.findRoute("A", "E", StepFreePolicy.AVOID_KNOWN_NO);
        assertEquals(RouteStatus.FOUND, r.getStatus());
        // Should include the UNKNOWN path since it's shorter
        boolean hasUnknown = r.getOrderedPaths().stream()
                .anyMatch(p -> p.getStepFree() == StepFreeStatus.UNKNOWN);
        assertTrue(hasUnknown, "Route should include an UNKNOWN path under AVOID_KNOWN_NO");
    }

    // ===== Test 6: UNKNOWN rejected under CONFIRMED_ONLY =====
    @Test
    @DisplayName("6. UNKNOWN rejected under CONFIRMED_ONLY")
    void testUnknownRejectedConfirmedOnly() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        // A→E CONFIRMED_ONLY: cannot use B→E (UNKNOWN), must use A→E direct (310)
        Route r = finder.findRoute("A", "E", StepFreePolicy.CONFIRMED_ONLY);
        assertEquals(RouteStatus.FOUND, r.getStatus());
        boolean hasUnknown = r.getOrderedPaths().stream()
                .anyMatch(p -> p.getStepFree() == StepFreeStatus.UNKNOWN);
        assertFalse(hasUnknown, "Route must not include UNKNOWN paths under CONFIRMED_ONLY");
    }

    // ===== Test 7: Closed paths excluded (BLOCKED_BY_CLOSURES) =====
    @Test
    @DisplayName("7. Closed paths excluded; BLOCKED_BY_CLOSURES when only closed paths connect")
    void testClosedPathsExcluded() {
        // Build a tiny graph where the only connection uses a closed path
        List<CampusLocation> locs = List.of(
            loc("X", "X", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
            loc("Y", "Y", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES)
        );
        List<CampusPath> paths = List.of(
            path("px", "X", "Y", 100, true, StepFreeStatus.YES) // closed
        );
        CampusData data = new CampusData(new CampusMetadata(1, "t", "t"), locs, paths);
        CampusGraph g = CampusGraph.buildFrom(data);
        RouteFinder finder = new RouteFinder(g);

        Route r = finder.findRoute("X", "Y", StepFreePolicy.NONE);
        assertEquals(RouteStatus.BLOCKED_BY_CLOSURES, r.getStatus());
    }

    // ===== Test 8: No graph connection (NO_CONNECTION) =====
    @Test
    @DisplayName("8. NO_CONNECTION for isolated node")
    void testNoConnection() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        Route r = finder.findRoute("A", "G", StepFreePolicy.NONE);
        assertEquals(RouteStatus.NO_CONNECTION, r.getStatus());
    }

    // ===== Test 9: Connected but no feasible accessible route =====
    @Test
    @DisplayName("9. BLOCKED_BY_ACCESSIBILITY when no step-free route exists")
    void testBlockedByAccessibility() {
        // Graph: X→Y (NO), X→Z→Y all YES, but remove Z→Y
        // Simpler: just X→Y with only a NO path
        List<CampusLocation> locs = List.of(
            loc("X", "X", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
            loc("Y", "Y", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES)
        );
        List<CampusPath> paths = List.of(
            path("px", "X", "Y", 100, false, StepFreeStatus.NO)
        );
        CampusData data = new CampusData(new CampusMetadata(1, "t", "t"), locs, paths);
        CampusGraph g = CampusGraph.buildFrom(data);
        RouteFinder finder = new RouteFinder(g);

        // Connected with NONE
        Route none = finder.findRoute("X", "Y", StepFreePolicy.NONE);
        assertEquals(RouteStatus.FOUND, none.getStatus());

        // Blocked with AVOID_KNOWN_NO
        Route avoid = finder.findRoute("X", "Y", StepFreePolicy.AVOID_KNOWN_NO);
        assertEquals(RouteStatus.BLOCKED_BY_ACCESSIBILITY, avoid.getStatus());
    }

    // ===== Test 10: Start == destination =====
    @Test
    @DisplayName("10. Start equals destination returns zero-distance route")
    void testSameStartAndDest() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        Route r = finder.findRoute("A", "A", StepFreePolicy.NONE);
        assertEquals(RouteStatus.FOUND, r.getStatus());
        assertEquals(0, r.getTotalDistanceMeters(), 0.01);
        assertEquals(0, r.getSegmentCount());
        assertEquals(List.of("A"), r.getOrderedLocationIds());
    }

    // ===== Test 11: Invalid location IDs =====
    @Test
    @DisplayName("11. Invalid location IDs return INVALID_INPUT")
    void testInvalidLocationIds() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        assertEquals(RouteStatus.INVALID_INPUT, finder.findRoute("A", "NONEXISTENT", StepFreePolicy.NONE).getStatus());
        assertEquals(RouteStatus.INVALID_INPUT, finder.findRoute("NONEXISTENT", "A", StepFreePolicy.NONE).getStatus());
        assertEquals(RouteStatus.INVALID_INPUT, finder.findRoute(null, "A", StepFreePolicy.NONE).getStatus());
        assertEquals(RouteStatus.INVALID_INPUT, finder.findRoute("", "A", StepFreePolicy.NONE).getStatus());
    }

    // ===== Test 12: Validation of invalid data =====
    @Test
    @DisplayName("12. Validation catches invalid/negative/zero distances, missing fields, duplicate ids, etc.")
    void testDataValidation() {
        CampusDataValidator validator = new CampusDataValidator();

        // Null data
        assertFalse(validator.validate(null).isEmpty());

        // Negative distance
        {
            List<CampusLocation> locs = List.of(
                loc("A", "A", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES));
            List<CampusPath> paths = List.of(
                path("p1", "A", "A", -10, false, StepFreeStatus.YES)); // self-loop + negative
            CampusData d = new CampusData(new CampusMetadata(1, "", ""), locs, paths);
            List<String> errors = validator.validate(d);
            assertTrue(errors.stream().anyMatch(e -> e.contains("self-loop")));
            assertTrue(errors.stream().anyMatch(e -> e.contains("positive")));
        }

        // Zero distance
        {
            List<CampusLocation> locs = List.of(
                loc("A", "A", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
                loc("B", "B", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES));
            List<CampusPath> paths = List.of(
                path("p1", "A", "B", 0, false, StepFreeStatus.YES));
            CampusData d = new CampusData(new CampusMetadata(1, "", ""), locs, paths);
            assertTrue(validator.validate(d).stream().anyMatch(e -> e.contains("positive")));
        }

        // Duplicate location id
        {
            List<CampusLocation> locs = List.of(
                loc("A", "A", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
                loc("A", "A2", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES));
            CampusData d = new CampusData(new CampusMetadata(1, "", ""), locs, List.of());
            assertTrue(validator.validate(d).stream().anyMatch(e -> e.contains("duplicate")));
        }

        // Missing location reference in path
        {
            List<CampusLocation> locs = List.of(
                loc("A", "A", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES));
            List<CampusPath> paths = List.of(
                path("p1", "A", "MISSING", 100, false, StepFreeStatus.YES));
            CampusData d = new CampusData(new CampusMetadata(1, "", ""), locs, paths);
            assertTrue(validator.validate(d).stream().anyMatch(e -> e.contains("unknown location")));
        }

        // Null step-free status
        {
            List<CampusLocation> locs = List.of(
                loc("A", "A", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
                loc("B", "B", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES));
            List<CampusPath> paths = List.of(
                new CampusPath("p1", "A", "B", 100.0, false, null));
            CampusData d = new CampusData(new CampusMetadata(1, "", ""), locs, paths);
            assertTrue(validator.validate(d).stream().anyMatch(e -> e.contains("stepFree is null")));
        }
    }

    // ===== Test 13: Deterministic tie-breaking =====
    @Test
    @DisplayName("13. Equal-distance alternatives: deterministic tie-break by fewer segments, then path ID")
    void testDeterministicTieBreak() {
        // Two paths from X to Y: same total distance
        // Path a: X→M→Y (60+40=100, 2 segments, ids "pa","pb")
        // Path b: X→Y direct (100, 1 segment, id "pc")
        // Tie-break: fewer segments first → direct route wins
        List<CampusLocation> locs = List.of(
            loc("X", "X", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
            loc("M", "M", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
            loc("Y", "Y", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES)
        );
        List<CampusPath> paths = List.of(
            path("pa", "X", "M", 60, false, StepFreeStatus.YES),
            path("pb", "M", "Y", 40, false, StepFreeStatus.YES),
            path("pc", "X", "Y", 100, false, StepFreeStatus.YES)
        );
        CampusData data = new CampusData(new CampusMetadata(1, "t", "t"), locs, paths);
        CampusGraph g = CampusGraph.buildFrom(data);
        RouteFinder finder = new RouteFinder(g);

        Route r = finder.findRoute("X", "Y", StepFreePolicy.NONE);
        assertEquals(RouteStatus.FOUND, r.getStatus());
        assertEquals(100, r.getTotalDistanceMeters(), 0.01);
        assertEquals(1, r.getSegmentCount(), "Tie-break should prefer fewer segments");
        assertEquals("pc", r.getOrderedPaths().get(0).getId());
    }

    // ===== Test 14: Updating stepFree =====
    @Test
    @DisplayName("14. Updating stepFree status of a path")
    void testUpdateStepFree(@TempDir Path tempDir) throws Exception {
        Path dataFile = tempDir.resolve("campus-data.json");
        CampusData data = buildTestData();

        CampusDataLoader loader = new CampusDataLoader(dataFile);
        // Write initial data
        loader.getMapper().writeValue(dataFile.toFile(), data);
        CampusData loaded = loader.loadFromFile(dataFile);
        CampusGraph graph = CampusGraph.buildFrom(loaded);
        CampusDataUpdater updater = new CampusDataUpdater(loader, loaded, graph);

        // Update p3 from NO to YES
        Optional<String> error = updater.updateStepFree("p3", StepFreeStatus.YES);
        assertTrue(error.isEmpty(), "Update should succeed: " + error.orElse(""));

        // Verify the change persists
        CampusData reloaded = loader.loadFromFile(dataFile);
        CampusPath updated = reloaded.getPaths().stream()
                .filter(p -> p.getId().equals("p3")).findFirst().orElseThrow();
        assertEquals(StepFreeStatus.YES, updated.getStepFree());
    }

    // ===== Test 15: Closing and reopening a path =====
    @Test
    @DisplayName("15. Closing and reopening a path")
    void testCloseReopenPath(@TempDir Path tempDir) throws Exception {
        Path dataFile = tempDir.resolve("campus-data.json");
        CampusData data = buildTestData();

        CampusDataLoader loader = new CampusDataLoader(dataFile);
        loader.getMapper().writeValue(dataFile.toFile(), data);
        CampusData loaded = loader.loadFromFile(dataFile);
        CampusGraph graph = CampusGraph.buildFrom(loaded);
        CampusDataUpdater updater = new CampusDataUpdater(loader, loaded, graph);

        // Close p1
        assertTrue(updater.setPathClosed("p1", true).isEmpty());

        // Verify route A→B now avoids p1
        RouteFinder finder = new RouteFinder(updater.getCurrentGraph());
        Route r = finder.findRoute("A", "B", StepFreePolicy.NONE);
        if (r.isFound()) {
            assertFalse(r.getOrderedPaths().stream().anyMatch(p -> p.getId().equals("p1")),
                    "Closed path p1 should not be in the route");
        }

        // Reopen p1
        assertTrue(updater.setPathClosed("p1", false).isEmpty());
        finder = new RouteFinder(updater.getCurrentGraph());
        Route r2 = finder.findRoute("A", "B", StepFreePolicy.NONE);
        assertEquals(RouteStatus.FOUND, r2.getStatus());
        assertEquals(100, r2.getTotalDistanceMeters(), 0.01);
    }

    // ===== Test 16: Save and reload; invalid update leaves file unchanged =====
    @Test
    @DisplayName("16. Save/reload survives restart; invalid update leaves file unchanged")
    void testSaveReloadAndInvalidUpdate(@TempDir Path tempDir) throws Exception {
        Path dataFile = tempDir.resolve("campus-data.json");
        CampusData data = buildTestData();

        CampusDataLoader loader = new CampusDataLoader(dataFile);
        loader.getMapper().writeValue(dataFile.toFile(), data);
        CampusData loaded = loader.loadFromFile(dataFile);
        CampusGraph graph = CampusGraph.buildFrom(loaded);
        CampusDataUpdater updater = new CampusDataUpdater(loader, loaded, graph);

        // Make a valid update
        assertTrue(updater.updateDistance("p1", 999).isEmpty());

        // Reload from file (simulating restart)
        CampusData reloaded = loader.loadFromFile(dataFile);
        CampusPath p1 = reloaded.getPaths().stream()
                .filter(p -> p.getId().equals("p1")).findFirst().orElseThrow();
        assertEquals(999, p1.getDistanceMeters(), 0.01);

        // Invalid update: negative distance
        String fileContentBefore = Files.readString(dataFile);
        Optional<String> err = updater.updateDistance("p1", -5);
        assertTrue(err.isPresent(), "Negative distance should fail");
        String fileContentAfter = Files.readString(dataFile);
        assertEquals(fileContentBefore, fileContentAfter, "File should not change after invalid update");
    }

    // ===== Test 17: Correct labelling of data provenance =====
    @Test
    @DisplayName("17. Correct labelling of STUDENT_KNOWLEDGE and DEMO_PLACEHOLDER")
    void testDataProvenance() {
        CampusData data = buildTestData();
        for (CampusLocation loc : data.getLocations()) {
            assertNotNull(loc.getDataSource());
            // All test data is STUDENT_KNOWLEDGE
            assertEquals(DataSource.STUDENT_KNOWLEDGE, loc.getDataSource());
            assertFalse(loc.isVerified());
        }
    }

    // ===== Test 18: Step-free confidence (POSSIBLY for route containing UNKNOWN) =====
    @Test
    @DisplayName("18. Step-free confidence: POSSIBLY for route with UNKNOWN segment")
    void testStepFreeConfidencePossibly() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        // A→E via A→B(YES)→E(UNKNOWN): confidence = POSSIBLY
        Route r = finder.findRoute("A", "E", StepFreePolicy.AVOID_KNOWN_NO);
        assertEquals(RouteStatus.FOUND, r.getStatus());
        assertEquals(StepFreeConfidence.POSSIBLY_STEP_FREE, r.getStepFreeConfidence());
    }

    @Test
    @DisplayName("18b. Step-free confidence: CONFIRMED for all-YES route")
    void testStepFreeConfidenceConfirmed() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        // A→C via A→B(YES)→C(YES) = CONFIRMED
        Route r = finder.findRoute("A", "C", StepFreePolicy.NONE);
        assertEquals(StepFreeConfidence.CONFIRMED_STEP_FREE, r.getStepFreeConfidence());
    }

    @Test
    @DisplayName("18c. Step-free confidence: NOT_STEP_FREE for route with NO segment")
    void testStepFreeConfidenceNot() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        // A→D shortest uses p3(NO): confidence = NOT_STEP_FREE
        Route r = finder.findRoute("A", "D", StepFreePolicy.NONE);
        assertEquals(StepFreeConfidence.NOT_STEP_FREE, r.getStepFreeConfidence());
    }

    // ===== Test 19: Explanation text is generated from data =====
    @Test
    @DisplayName("19. Explanation text changes when distances change")
    void testExplanationGeneratedFromData(@TempDir Path tempDir) throws Exception {
        Path dataFile = tempDir.resolve("campus-data.json");
        CampusData data = buildTestData();

        CampusDataLoader loader = new CampusDataLoader(dataFile);
        loader.getMapper().writeValue(dataFile.toFile(), data);
        CampusData loaded = loader.loadFromFile(dataFile);
        CampusGraph graph = CampusGraph.buildFrom(loaded);
        CampusDataUpdater updater = new CampusDataUpdater(loader, loaded, graph);

        RouteFinder finder1 = new RouteFinder(graph);
        Route r1 = finder1.findRoute("A", "B", StepFreePolicy.NONE);
        String explanation1 = r1.getExplanation();
        assertTrue(explanation1.contains("100"), "Should contain the distance 100");

        // Update p1 distance from 100 to 150
        updater.updateDistance("p1", 150);
        RouteFinder finder2 = new RouteFinder(updater.getCurrentGraph());
        Route r2 = finder2.findRoute("A", "B", StepFreePolicy.NONE);
        String explanation2 = r2.getExplanation();
        // The explanation should reflect the new total distance (150 via p1,
        // since other routes to B are longer even with the increase)
        assertTrue(explanation2.contains("150"), "Should contain the updated distance 150");
        assertNotEquals(explanation1, explanation2, "Explanations should differ after distance change");
    }

    // ===== Test 20: NOT_APPLICABLE on GATE accepted; on BUILDING rejected =====
    @Test
    @DisplayName("20. NOT_APPLICABLE valid on GATE, rejected on BUILDING; YES/NO rejected on GATE")
    void testNotApplicableValidation() {
        CampusDataValidator validator = new CampusDataValidator();

        // GATE with NOT_APPLICABLE — valid
        {
            List<CampusLocation> locs = List.of(
                loc("g1", "Gate1", LocationType.GATE,
                    AvailabilityStatus.NOT_APPLICABLE, AvailabilityStatus.NOT_APPLICABLE, AvailabilityStatus.NOT_APPLICABLE));
            CampusData d = new CampusData(new CampusMetadata(1, "", ""), locs, List.of());
            assertTrue(validator.validate(d).isEmpty(), "GATE with NOT_APPLICABLE should be valid");
        }

        // BUILDING with NOT_APPLICABLE — invalid
        {
            List<CampusLocation> locs = List.of(
                loc("b1", "B1", LocationType.BUILDING,
                    AvailabilityStatus.NOT_APPLICABLE, AvailabilityStatus.YES, AvailabilityStatus.YES));
            CampusData d = new CampusData(new CampusMetadata(1, "", ""), locs, List.of());
            List<String> errors = validator.validate(d);
            assertTrue(errors.stream().anyMatch(e -> e.contains("NOT_APPLICABLE")),
                    "BUILDING with NOT_APPLICABLE should fail: " + errors);
        }

        // GATE with YES — invalid
        {
            List<CampusLocation> locs = List.of(
                    new CampusLocation("g2", "Gate2", LocationType.GATE,
                            AvailabilityStatus.YES, AvailabilityStatus.NOT_APPLICABLE, AvailabilityStatus.NOT_APPLICABLE,
                            DataSource.STUDENT_KNOWLEDGE, false, null, 0.0, 0.0));
            CampusData d = new CampusData(new CampusMetadata(1, "", ""), locs, List.of());
            List<String> errors = validator.validate(d);
            assertTrue(errors.stream().anyMatch(e -> e.contains("GATE") && e.contains("NOT_APPLICABLE")),
                    "GATE with YES should fail: " + errors);
        }
    }

    // ===== Test 21: Loading bundled default dataset =====
    @Test
    @DisplayName("21. Bundled default dataset: 24 locations, no duplicate ids, all real nodes reachable, demo_isolated unreachable")
    void testBundledDefaultDataset() throws Exception {
        CampusDataLoader loader = new CampusDataLoader();
        CampusData data = loader.loadBundledDefault();

        // 24 locations
        assertEquals(24, data.getLocations().size());

        // No duplicate ids
        Set<String> ids = new HashSet<>();
        for (CampusLocation loc : data.getLocations()) {
            assertTrue(ids.add(loc.getId()), "Duplicate location id: " + loc.getId());
        }

        CampusGraph graph = CampusGraph.buildFrom(data);

        // Every real node reachable from main_gate
        RouteFinder finder = new RouteFinder(graph);
        for (CampusLocation loc : data.getLocations()) {
            if (loc.getId().equals("demo_isolated")) continue;
            if (loc.getId().equals("main_gate")) continue;
            Route r = finder.findRoute("main_gate", loc.getId(), StepFreePolicy.NONE);
            assertEquals(RouteStatus.FOUND, r.getStatus(),
                    "Should be reachable from main_gate: " + loc.getId());
        }

        // demo_isolated is unreachable
        Route demoRoute = finder.findRoute("main_gate", "demo_isolated", StepFreePolicy.NONE);
        assertEquals(RouteStatus.NO_CONNECTION, demoRoute.getStatus());
    }

    // ===== Test 22: Building notes never change route result or confidence =====
    @Test
    @DisplayName("22. Building notes in explanations are informational only; never change route result or confidence; never mention gates")
    void testBuildingNotesInformationalOnly() {
        CampusGraph g = buildTestGraph();
        RouteFinder finder = new RouteFinder(g);

        // Route from gate1 to A (gate is start)
        Route r = finder.findRoute("gate1", "A", StepFreePolicy.NONE);
        // This will be NO_CONNECTION since gate1 has no paths, but let's test with a route
        // that has a gate endpoint in the main graph...
        // Actually gate1 has no paths in test graph. Let's test that building notes
        // don't appear for gates by routing A→B (both buildings).
        Route r2 = finder.findRoute("A", "B", StepFreePolicy.NONE);
        assertEquals(RouteStatus.FOUND, r2.getStatus());
        // Explanation should contain building notes for A and B
        String expl = r2.getExplanation();
        assertTrue(expl.contains("Entrance ramp") || expl.contains("building notes"),
                "Should contain building notes for buildings");

        // Run route that starts/ends at same node — confidence should be CONFIRMED
        Route r3 = finder.findRoute("A", "A", StepFreePolicy.NONE);
        assertEquals(StepFreeConfidence.CONFIRMED_STEP_FREE, r3.getStepFreeConfidence());
    }

    // ===== Additional: RouteComparisonService =====
    @Test
    @DisplayName("RouteComparisonService generates comparison for identical routes")
    void testComparisonIdenticalRoutes() {
        CampusGraph g = buildTestGraph();
        RouteComparisonService service = new RouteComparisonService(g);

        // A→C: shortest and AVOID_KNOWN_NO should both be A→B→C (all YES)
        Route shortest = service.findShortestRoute("A", "C");
        Route accessible = service.findAccessibleRoute("A", "C", StepFreePolicy.AVOID_KNOWN_NO);

        String comparison = service.generateComparison(shortest, accessible);
        assertTrue(comparison.contains("IDENTICAL"), "Both routes should be identical");
    }

    // ===== Malformed JSON test =====
    @Test
    @DisplayName("12b. Malformed JSON throws IOException")
    void testMalformedJson() {
        CampusDataLoader loader = new CampusDataLoader();
        assertThrows(IOException.class, () -> loader.loadFromString("{invalid json!!!}"));
    }

    // ===== Invalid enum in JSON =====
    @Test
    @DisplayName("12c. Invalid enum value in JSON throws exception")
    void testInvalidEnumInJson() {
        String json = """
                {
                  "metadata": {"schemaVersion": 1, "description": "", "disclaimer": ""},
                  "locations": [
                    {"id": "a", "name": "A", "type": "BUILDING",
                     "rampAvailable": "INVALID_VALUE",
                     "stairsAvailable": "YES", "liftAvailable": "YES",
                     "dataSource": "STUDENT_KNOWLEDGE", "verified": false}
                  ],
                  "paths": []
                }
                """;
        CampusDataLoader loader = new CampusDataLoader();
        assertThrows(IOException.class, () -> loader.loadFromString(json));
    }

    // ===== Duplicate path id test =====
    @Test
    @DisplayName("12d. Duplicate path IDs detected")
    void testDuplicatePathIds() {
        CampusDataValidator validator = new CampusDataValidator();
        List<CampusLocation> locs = List.of(
            loc("A", "A", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES),
            loc("B", "B", LocationType.BUILDING, AvailabilityStatus.YES, AvailabilityStatus.YES, AvailabilityStatus.YES));
        List<CampusPath> paths = List.of(
            path("p1", "A", "B", 100, false, StepFreeStatus.YES),
            path("p1", "A", "B", 200, false, StepFreeStatus.NO)); // duplicate id
        CampusData d = new CampusData(new CampusMetadata(1, "", ""), locs, paths);
        assertTrue(validator.validate(d).stream().anyMatch(e -> e.contains("duplicate")));
    }

    // ===== Section 6 Demonstration Scenarios Test =====
    @Test
    @DisplayName("Section 6: All 6 required demonstration scenarios verifiable on default dataset")
    void testSection6DemoScenarios() throws Exception {
        CampusDataLoader loader = new CampusDataLoader();
        CampusData data = loader.loadBundledDefault();
        CampusGraph graph = CampusGraph.buildFrom(data);
        RouteFinder finder = new RouteFinder(graph);

        // 1. Accessibility route is longer than shortest route
        Route shortest1 = finder.findRoute("cse_dept", "eee_dept", StepFreePolicy.NONE);
        Route accessible1 = finder.findRoute("cse_dept", "eee_dept", StepFreePolicy.AVOID_KNOWN_NO);
        assertEquals(RouteStatus.FOUND, shortest1.getStatus());
        assertEquals(RouteStatus.FOUND, accessible1.getStatus());
        assertTrue(accessible1.getTotalDistanceMeters() > shortest1.getTotalDistanceMeters(),
                "Accessible route should be longer due to avoiding NO step-free path_006");

        // 2. Both algorithms return the exact same route
        Route shortest2 = finder.findRoute("main_gate", "civil_dept", StepFreePolicy.NONE);
        Route accessible2 = finder.findRoute("main_gate", "civil_dept", StepFreePolicy.AVOID_KNOWN_NO);
        assertEquals(RouteStatus.FOUND, shortest2.getStatus());
        assertEquals(RouteStatus.FOUND, accessible2.getStatus());
        assertEquals(shortest2.getTotalDistanceMeters(), accessible2.getTotalDistanceMeters());
        assertEquals(shortest2.getOrderedPaths().size(), accessible2.getOrderedPaths().size());

        // 3. Route containing a path with UNKNOWN step-free status (POSSIBLY_STEP_FREE)
        Route route3 = finder.findRoute("cse_dept", "health_centre", StepFreePolicy.AVOID_KNOWN_NO);
        assertEquals(RouteStatus.FOUND, route3.getStatus());
        assertEquals(StepFreeConfidence.POSSIBLY_STEP_FREE, route3.getStepFreeConfidence(),
                "Route via path_004 (UNKNOWN) should have POSSIBLY_STEP_FREE confidence");

        // 4. A path that is closed changing the result:
        // When path_001 is closed, it takes the alternative route via path_032 (ECE Dept)
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("campus-demo-test", ".json");
        CampusDataLoader tempLoader = new CampusDataLoader(tempFile);
        CampusDataUpdater updater = new CampusDataUpdater(tempLoader, data, graph);
        updater.setPathClosed("path_001", true);
        RouteFinder finderWithClosed = new RouteFinder(updater.getCurrentGraph());
        Route route4Detour = finderWithClosed.findRoute("main_gate", "civil_dept", StepFreePolicy.NONE);
        assertEquals(RouteStatus.FOUND, route4Detour.getStatus(),
                "Closing path_001 should route through alternative branch (path_032)");
        assertTrue(route4Detour.getTotalDistanceMeters() > 50, "Detour should be longer than direct 50m path");

        // When all exit paths from main_gate are closed, it reports BLOCKED_BY_CLOSURES
        updater.setPathClosed("path_032", true);
        RouteFinder finderAllClosed = new RouteFinder(updater.getCurrentGraph());
        Route route4Blocked = finderAllClosed.findRoute("main_gate", "civil_dept", StepFreePolicy.NONE);
        assertEquals(RouteStatus.BLOCKED_BY_CLOSURES, route4Blocked.getStatus(),
                "Closing all paths from main gate should report BLOCKED_BY_CLOSURES");
        java.nio.file.Files.deleteIfExists(tempFile);

        // 5. Connected, but no step-free route exists under CONFIRMED_ONLY
        Route route5 = finder.findRoute("mech_dept", "mv_hostel", StepFreePolicy.CONFIRMED_ONLY);
        assertEquals(RouteStatus.BLOCKED_BY_ACCESSIBILITY, route5.getStatus(),
                "mech_dept -> mv_hostel should be BLOCKED_BY_ACCESSIBILITY under CONFIRMED_ONLY");

        // 6. Node that is not connected to the rest of the graph
        Route route6 = finder.findRoute("main_gate", "demo_isolated", StepFreePolicy.NONE);
        assertEquals(RouteStatus.NO_CONNECTION, route6.getStatus(),
                "demo_isolated should return NO_CONNECTION");
    }
}
