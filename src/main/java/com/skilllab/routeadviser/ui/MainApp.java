package com.skilllab.routeadviser.ui;

import com.skilllab.routeadviser.data.CampusDataLoader;
import com.skilllab.routeadviser.data.CampusDataUpdater;
import com.skilllab.routeadviser.graph.CampusGraph;
import com.skilllab.routeadviser.model.*;
import com.skilllab.routeadviser.service.RouteComparisonService;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Accessible Route Adviser for RVCE Campus — Modern JavaFX Desktop Application.
 */
public class MainApp extends Application {

    private CampusDataLoader loader;
    private CampusDataUpdater updater;
    private RouteComparisonService comparisonService;

    // Tab Pane
    private TabPane tabPane;

    // UI controls - Route Finder
    private ComboBox<String> startCombo;
    private ComboBox<String> destCombo;
    private ComboBox<String> policyCombo;
    private TextArea shortestResultArea;
    private TextArea accessibleResultArea;
    private TextArea comparisonArea;
    private Label statusLabel;
    private Label shortestBadge;
    private Label accessibleBadge;

    // Interactive Map
    private Canvas mapCanvas;
    private double mapZoom = 1.0;
    private CampusLocation hoveredLocation = null;
    private Label mapSelectionLabel;

    // Current route results for highlighting on map
    private Route currentShortest;
    private Route currentAccessible;

    @Override
    public void start(Stage primaryStage) {
        try {
            initData();
        } catch (Exception e) {
            showErrorAndExit("Failed to load campus data: " + e.getMessage());
            return;
        }

        primaryStage.setTitle("Accessible Route Adviser — RVCE, Bengaluru");

        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab routeTab = new Tab("🗺️ Route Finder", buildRouteFinderPane());
        Tab buildingTab = new Tab("🏛️ Building Info", buildBuildingInfoPane());
        Tab pathTab = new Tab("🛤️ Path Management", buildPathManagementPane());
        Tab mapTab = new Tab("📍 Interactive Map", buildSchematicMapPane());
        Tab aboutTab = new Tab("ℹ️ About / Disclaimer", buildAboutPane());

        tabPane.getTabs().addAll(routeTab, buildingTab, pathTab, mapTab, aboutTab);

        Scene scene = new Scene(tabPane, 1150, 780);
        if (getClass().getResource("/style.css") != null) {
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initData() throws Exception {
        loader = new CampusDataLoader();
        CampusData data = loader.load();
        CampusGraph graph = CampusGraph.buildFrom(data);
        comparisonService = new RouteComparisonService(graph);
        updater = new CampusDataUpdater(loader, data, graph);
    }

    // ==================== ROUTE FINDER TAB ====================

    private VBox buildRouteFinderPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(16));

        // Header Section
        VBox header = new VBox(2);
        Label title = new Label("Accessible Route Adviser");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: #0F172A;");
        Label subtitle = new Label("RV College of Engineering (RVCE), Bengaluru — Find shortest and step-free routes");
        subtitle.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px;");
        header.getChildren().addAll(title, subtitle);

        // Quick Preset Scenarios Bar
        HBox presetBar = new HBox(8);
        presetBar.setAlignment(Pos.CENTER_LEFT);
        Label presetTitle = new Label("⚡ Quick Scenarios:");
        presetTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 12px;");

        Button s1Btn = createPresetPill("Detour (CSE ➔ EEE)", "CSE Department", "EEE Department",
                "Avoid known non-step-free (allow YES + UNKNOWN)");
        Button s2Btn = createPresetPill("Identical (Main Gate ➔ Civil)", "Main Gate", "Civil Department",
                "Avoid known non-step-free (allow YES + UNKNOWN)");
        Button s3Btn = createPresetPill("Unknown (CSE ➔ Health Ctr)", "CSE Department", "Health Centre",
                "Avoid known non-step-free (allow YES + UNKNOWN)");
        Button s4Btn = createPresetPill("Closed (Civil ➔ ECE)", "Civil Department", "ECE Department",
                "Shortest distance, no step-free filtering");
        Button s5Btn = createPresetPill("No Step-Free (Mech ➔ MV Hostel)", "Mechanical Department", "MV Hostel",
                "Confirmed step-free only (YES only)");
        Button s6Btn = createPresetPill("Isolated (Main Gate ➔ Demo)", "Main Gate", "Demo Isolated Building (demo)",
                "Shortest distance, no step-free filtering");

        presetBar.getChildren().addAll(presetTitle, s1Btn, s2Btn, s3Btn, s4Btn, s5Btn, s6Btn);

        // Selector Controls Card
        VBox controlsCard = new VBox(10);
        controlsCard.getStyleClass().add("card-pane");

        CampusGraph graph = comparisonService.getGraph();
        List<String> locationNames = graph.getAllLocations().stream()
                .sorted(Comparator.comparing(CampusLocation::getName))
                .map(l -> l.getName() + (l.getDataSource() == DataSource.DEMO_PLACEHOLDER ? " (demo)" : ""))
                .collect(Collectors.toList());

        startCombo = new ComboBox<>(FXCollections.observableArrayList(locationNames));
        startCombo.setPromptText("Select start location...");
        startCombo.setId("startCombo");
        startCombo.setAccessibleText("Select start location");

        destCombo = new ComboBox<>(FXCollections.observableArrayList(locationNames));
        destCombo.setPromptText("Select destination...");
        destCombo.setId("destCombo");
        destCombo.setAccessibleText("Select destination location");

        // Swap button
        Button swapBtn = new Button("⇄");
        swapBtn.getStyleClass().add("btn-swap");
        swapBtn.setTooltip(new Tooltip("Swap Start and Destination"));
        swapBtn.setOnAction(e -> {
            String temp = startCombo.getValue();
            startCombo.setValue(destCombo.getValue());
            destCombo.setValue(temp);
            if (startCombo.getValue() != null && destCombo.getValue() != null) {
                onFindRoute();
            }
        });

        policyCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Avoid known non-step-free (allow YES + UNKNOWN)",
                "Confirmed step-free only (YES only)"
        ));
        policyCombo.setValue("Avoid known non-step-free (allow YES + UNKNOWN)");
        policyCombo.setId("policyCombo");
        policyCombo.setAccessibleText("Select step-free policy for accessible route");

        Button findBtn = new Button("🔍 Find Route");
        findBtn.getStyleClass().add("btn-primary");
        findBtn.setMnemonicParsing(true);
        findBtn.setDefaultButton(true);
        findBtn.setId("findRouteBtn");
        findBtn.setOnAction(e -> onFindRoute());

        Button viewOnMapBtn = new Button("🗺️ View on Map");
        viewOnMapBtn.getStyleClass().add("btn-secondary");
        viewOnMapBtn.setOnAction(e -> {
            if (currentShortest == null && startCombo.getValue() != null && destCombo.getValue() != null) {
                onFindRoute();
            }
            tabPane.getSelectionModel().select(3); // switch to Interactive Map tab
        });

        HBox controlsRow = new HBox(12);
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        controlsRow.getChildren().addAll(
                new Label("📍 Start:"), startCombo,
                swapBtn,
                new Label("🏁 Destination:"), destCombo,
                new Label("🛡️ Policy:"), policyCombo,
                findBtn,
                viewOnMapBtn
        );
        controlsCard.getChildren().add(controlsRow);

        // Results Section (Dual Cards)
        HBox resultPanes = new HBox(14);

        // Shortest Route Card
        VBox shortestCard = new VBox(8);
        shortestCard.getStyleClass().addAll("card-pane", "result-shortest");
        HBox shortestHeader = new HBox(8);
        shortestHeader.setAlignment(Pos.CENTER_LEFT);
        Label shortestTitle = new Label("⚡ Shortest Route (Direct Path)");
        shortestTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        shortestBadge = new Label("Shortest");
        shortestBadge.getStyleClass().add("badge-possibly");
        shortestHeader.getChildren().addAll(shortestTitle, shortestBadge);

        shortestResultArea = createResultArea("shortestResult");
        shortestCard.getChildren().addAll(shortestHeader, shortestResultArea);
        VBox.setVgrow(shortestResultArea, Priority.ALWAYS);

        // Accessible Route Card
        VBox accessibleCard = new VBox(8);
        accessibleCard.getStyleClass().addAll("card-pane", "result-accessible");
        HBox accessibleHeader = new HBox(8);
        accessibleHeader.setAlignment(Pos.CENTER_LEFT);
        Label accessibleTitle = new Label("♿ Accessibility-Prioritized Route");
        accessibleTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        accessibleBadge = new Label("Accessible");
        accessibleBadge.getStyleClass().add("badge-confirmed");
        accessibleHeader.getChildren().addAll(accessibleTitle, accessibleBadge);

        accessibleResultArea = createResultArea("accessibleResult");
        accessibleCard.getChildren().addAll(accessibleHeader, accessibleResultArea);
        VBox.setVgrow(accessibleResultArea, Priority.ALWAYS);

        HBox.setHgrow(shortestCard, Priority.ALWAYS);
        HBox.setHgrow(accessibleCard, Priority.ALWAYS);
        resultPanes.getChildren().addAll(shortestCard, accessibleCard);

        // Comparison & Advisory Card
        VBox comparisonCard = new VBox(8);
        comparisonCard.getStyleClass().addAll("card-pane", "result-comparison");
        Label compTitle = new Label("📋 Route Comparison & Decision Advisory");
        compTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        comparisonArea = createResultArea("comparisonResult");
        comparisonArea.setPrefHeight(90);
        comparisonCard.getChildren().addAll(compTitle, comparisonArea);

        statusLabel = new Label("Ready. Select start and destination, then click Find Route or pick a quick scenario above.");
        statusLabel.setId("statusLabel");
        statusLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px;");

        pane.getChildren().addAll(header, presetBar, controlsCard, resultPanes, comparisonCard, statusLabel);
        VBox.setVgrow(resultPanes, Priority.ALWAYS);

        return pane;
    }

    private Button createPresetPill(String text, String start, String dest, String policy) {
        Button btn = new Button(text);
        btn.getStyleClass().add("scenario-pill");
        btn.setOnAction(e -> {
            startCombo.setValue(start);
            destCombo.setValue(dest);
            if (policy.startsWith("Confirmed")) {
                policyCombo.setValue("Confirmed step-free only (YES only)");
            } else {
                policyCombo.setValue("Avoid known non-step-free (allow YES + UNKNOWN)");
            }
            onFindRoute();
        });
        return btn;
    }

    private TextArea createResultArea(String id) {
        TextArea ta = new TextArea();
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setId(id);
        ta.setPrefHeight(220);
        return ta;
    }

    private void onFindRoute() {
        String startDisplay = startCombo.getValue();
        String destDisplay = destCombo.getValue();

        if (startDisplay == null || destDisplay == null) {
            statusLabel.setText("⚠️ Please select both a start and destination location.");
            return;
        }

        String startId = displayNameToId(startDisplay);
        String destId = displayNameToId(destDisplay);

        if (startId == null || destId == null) {
            statusLabel.setText("⚠️ Could not resolve selected locations.");
            return;
        }

        StepFreePolicy policy = policyCombo.getValue().startsWith("Confirmed")
                ? StepFreePolicy.CONFIRMED_ONLY
                : StepFreePolicy.AVOID_KNOWN_NO;

        // Run both routes
        currentShortest = comparisonService.findShortestRoute(startId, destId);
        currentAccessible = comparisonService.findAccessibleRoute(startId, destId, policy);

        shortestResultArea.setText(formatRoute(currentShortest));
        accessibleResultArea.setText(formatRoute(currentAccessible));
        comparisonArea.setText(comparisonService.generateComparison(currentShortest, currentAccessible));

        // Update badges
        if (currentShortest.isFound()) {
            shortestBadge.setText(String.format("%.0f m · %s",
                    currentShortest.getTotalDistanceMeters(), currentShortest.getStepFreeConfidence()));
        } else {
            shortestBadge.setText(currentShortest.getStatus().name());
        }

        if (currentAccessible.isFound()) {
            accessibleBadge.setText(String.format("%.0f m · %s",
                    currentAccessible.getTotalDistanceMeters(), currentAccessible.getStepFreeConfidence()));
        } else {
            accessibleBadge.setText(currentAccessible.getStatus().name());
        }

        statusLabel.setText("✓ Routes computed successfully. See details and comparison above.");

        // Refresh map if visible
        if (mapCanvas != null) drawSchematicMap();
    }

    private String displayNameToId(String displayName) {
        if (displayName == null) return null;
        String clean = displayName.replace(" (demo)", "").trim();
        for (CampusLocation loc : comparisonService.getGraph().getAllLocations()) {
            if (loc.getName().equals(clean)) return loc.getId();
        }
        return null;
    }

    private String formatRoute(Route route) {
        if (!route.isFound()) {
            return "Status: " + route.getStatus() + "\n\n" + route.getExplanation();
        }
        return route.getExplanation();
    }

    // ==================== BUILDING INFO TAB ====================

    @SuppressWarnings("unchecked")
    private VBox buildBuildingInfoPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(16));

        VBox header = new VBox(4);
        Label title = new Label("Campus Building Accessibility Directory");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #0F172A;");

        Label note = new Label(
            "📌 Note: Building flags describe entrance and interior features AT the building itself (ramp for entry, stairs/lift to floors). " +
            "They do NOT dictate whether external walking paths between buildings are step-free.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px; -fx-background-color: #F1F5F9; -fx-padding: 8 12; -fx-background-radius: 8; -fx-border-color: #CBD5E1; -fx-border-radius: 8;");
        header.getChildren().addAll(title, note);

        // Search & Filter Toolbar
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search building or department by name...");
        searchField.setPrefWidth(320);

        ObservableList<BuildingRow> allRows = FXCollections.observableArrayList();
        for (CampusLocation loc : updater.getCurrentData().getLocations()) {
            allRows.add(new BuildingRow(loc));
        }
        FilteredList<BuildingRow> filteredData = new FilteredList<>(allRows, p -> true);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(row -> {
                if (newVal == null || newVal.isBlank()) return true;
                String lower = newVal.toLowerCase();
                return row.getName().toLowerCase().contains(lower) || row.getType().toLowerCase().contains(lower);
            });
        });

        Button showAllBtn = new Button("All (24)");
        showAllBtn.getStyleClass().add("btn-secondary");
        showAllBtn.setOnAction(e -> { searchField.clear(); filteredData.setPredicate(r -> true); });

        Button hasRampBtn = new Button("Has Entrance Ramp");
        hasRampBtn.getStyleClass().add("btn-secondary");
        hasRampBtn.setOnAction(e -> { searchField.clear(); filteredData.setPredicate(r -> "Yes".equals(r.getRamp())); });

        Button hasLiftBtn = new Button("Has Elevator / Lift");
        hasLiftBtn.getStyleClass().add("btn-secondary");
        hasLiftBtn.setOnAction(e -> { searchField.clear(); filteredData.setPredicate(r -> "Yes".equals(r.getLift())); });

        filterBar.getChildren().addAll(searchField, showAllBtn, hasRampBtn, hasLiftBtn);

        // Building Table
        TableView<BuildingRow> table = new TableView<>();
        table.setId("buildingTable");

        TableColumn<BuildingRow, String> nameCol = new TableColumn<>("Building Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(240);

        TableColumn<BuildingRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(90);

        TableColumn<BuildingRow, String> rampCol = new TableColumn<>("Entrance Ramp");
        rampCol.setCellValueFactory(new PropertyValueFactory<>("ramp"));
        rampCol.setPrefWidth(120);

        TableColumn<BuildingRow, String> stairsCol = new TableColumn<>("Stairs to Floors");
        stairsCol.setCellValueFactory(new PropertyValueFactory<>("stairs"));
        stairsCol.setPrefWidth(120);

        TableColumn<BuildingRow, String> liftCol = new TableColumn<>("Lift to Floors");
        liftCol.setCellValueFactory(new PropertyValueFactory<>("lift"));
        liftCol.setPrefWidth(120);

        TableColumn<BuildingRow, String> sourceCol = new TableColumn<>("Data Source");
        sourceCol.setCellValueFactory(new PropertyValueFactory<>("source"));
        sourceCol.setPrefWidth(160);

        TableColumn<BuildingRow, String> verifiedCol = new TableColumn<>("Verified?");
        verifiedCol.setCellValueFactory(new PropertyValueFactory<>("verified"));
        verifiedCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, typeCol, rampCol, stairsCol, liftCol, sourceCol, verifiedCol);
        table.setItems(filteredData);

        pane.getChildren().addAll(header, filterBar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return pane;
    }

    // ==================== PATH MANAGEMENT TAB ====================

    private VBox buildPathManagementPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(16));

        Label title = new Label("Campus Path Network Management");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #0F172A;");

        // Path table
        TableView<PathRow> pathTable = new TableView<>();
        pathTable.setId("pathTable");
        ObservableList<PathRow> pathRows = FXCollections.observableArrayList();

        TableColumn<PathRow, String> idCol = new TableColumn<>("Path ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(90);

        TableColumn<PathRow, String> fromCol = new TableColumn<>("From (Start)");
        fromCol.setCellValueFactory(new PropertyValueFactory<>("from"));
        fromCol.setPrefWidth(170);

        TableColumn<PathRow, String> toCol = new TableColumn<>("To (End)");
        toCol.setCellValueFactory(new PropertyValueFactory<>("to"));
        toCol.setPrefWidth(170);

        TableColumn<PathRow, String> distCol = new TableColumn<>("Distance (m)");
        distCol.setCellValueFactory(new PropertyValueFactory<>("distance"));
        distCol.setPrefWidth(100);

        TableColumn<PathRow, String> sfCol = new TableColumn<>("Step-Free");
        sfCol.setCellValueFactory(new PropertyValueFactory<>("stepFree"));
        sfCol.setPrefWidth(100);

        TableColumn<PathRow, String> closedCol = new TableColumn<>("Closed?");
        closedCol.setCellValueFactory(new PropertyValueFactory<>("closed"));
        closedCol.setPrefWidth(80);

        pathTable.getColumns().addAll(idCol, fromCol, toCol, distCol, sfCol, closedCol);

        Runnable refreshPaths = () -> {
            pathRows.clear();
            CampusGraph g = updater.getCurrentGraph();
            for (CampusPath p : updater.getCurrentData().getPaths()) {
                String fromName = g.getLocation(p.getFrom()) != null ? g.getLocation(p.getFrom()).getName() : p.getFrom();
                String toName = g.getLocation(p.getTo()) != null ? g.getLocation(p.getTo()).getName() : p.getTo();
                pathRows.add(new PathRow(p.getId(), fromName, toName,
                        String.valueOf((int) p.getDistanceMeters()),
                        p.getStepFree().name(), p.isClosed() ? "YES" : "NO"));
            }
            pathTable.setItems(pathRows);
        };
        refreshPaths.run();

        // Edit Controls Card
        VBox editCard = new VBox(8);
        editCard.getStyleClass().add("card-pane");

        Label editLabel = new Label("✏️ Edit Selected Path in Table:");
        editLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        TextField distField = new TextField();
        distField.setPromptText("New distance (m)");
        distField.setPrefWidth(120);
        distField.setId("editDistance");

        ComboBox<String> sfCombo = new ComboBox<>(FXCollections.observableArrayList("YES", "NO", "UNKNOWN"));
        sfCombo.setPromptText("Step-free");
        sfCombo.setPrefWidth(120);
        sfCombo.setId("editStepFree");

        Button updateDistBtn = new Button("Update Dist");
        updateDistBtn.getStyleClass().add("btn-secondary");
        updateDistBtn.setId("updateDistBtn");

        Button updateSfBtn = new Button("Update Step-Free");
        updateSfBtn.getStyleClass().add("btn-secondary");
        updateSfBtn.setId("updateSfBtn");

        Button toggleClosedBtn = new Button("Toggle Closed / Open");
        toggleClosedBtn.getStyleClass().add("btn-secondary");
        toggleClosedBtn.setId("toggleClosedBtn");

        Label editStatus = new Label();
        editStatus.setId("editStatusLabel");
        editStatus.setStyle("-fx-text-fill: #2563EB; -fx-font-weight: bold;");

        // Pre-fill fields on row selection
        pathTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                distField.setText(newSel.getDistance());
                sfCombo.setValue(newSel.getStepFree());
                editStatus.setText("Selected " + newSel.getId() + " (" + newSel.getFrom() + " ↔ " + newSel.getTo() + ")");
            }
        });

        updateDistBtn.setOnAction(e -> {
            PathRow sel = pathTable.getSelectionModel().getSelectedItem();
            if (sel == null) { editStatus.setText("⚠️ Select a path in the table first."); return; }
            try {
                double d = Double.parseDouble(distField.getText());
                Optional<String> err = updater.updateDistance(sel.getId(), d);
                if (err.isPresent()) { editStatus.setText("Error: " + err.get()); }
                else {
                    editStatus.setText("✓ Distance for " + sel.getId() + " updated to " + (int) d + " m.");
                    afterUpdate(refreshPaths);
                }
            } catch (NumberFormatException ex) { editStatus.setText("⚠️ Invalid number for distance."); }
        });

        updateSfBtn.setOnAction(e -> {
            PathRow sel = pathTable.getSelectionModel().getSelectedItem();
            if (sel == null) { editStatus.setText("⚠️ Select a path in the table first."); return; }
            String sfVal = sfCombo.getValue();
            if (sfVal == null) { editStatus.setText("⚠️ Select a step-free status."); return; }
            Optional<String> err = updater.updateStepFree(sel.getId(), StepFreeStatus.valueOf(sfVal));
            if (err.isPresent()) { editStatus.setText("Error: " + err.get()); }
            else {
                editStatus.setText("✓ Step-free status for " + sel.getId() + " updated to " + sfVal + ".");
                afterUpdate(refreshPaths);
            }
        });

        toggleClosedBtn.setOnAction(e -> {
            PathRow sel = pathTable.getSelectionModel().getSelectedItem();
            if (sel == null) { editStatus.setText("⚠️ Select a path in the table first."); return; }
            boolean currentlyClosed = "YES".equals(sel.getClosed());
            Optional<String> err = updater.setPathClosed(sel.getId(), !currentlyClosed);
            if (err.isPresent()) { editStatus.setText("Error: " + err.get()); }
            else {
                editStatus.setText("✓ Path " + sel.getId() + " " + (currentlyClosed ? "reopened" : "closed") + ".");
                afterUpdate(refreshPaths);
            }
        });

        HBox editRow = new HBox(10, editLabel, distField, updateDistBtn, sfCombo, updateSfBtn, toggleClosedBtn);
        editRow.setAlignment(Pos.CENTER_LEFT);
        editCard.getChildren().addAll(editRow);

        // Add New Path Card
        VBox addCard = new VBox(8);
        addCard.getStyleClass().add("card-pane");

        Label addLabel = new Label("➕ Add New Path Edge:");
        addLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        TextField newIdField = new TextField(); newIdField.setPromptText("Path ID (e.g. path_032)"); newIdField.setPrefWidth(120);
        newIdField.setId("newPathId");
        ComboBox<String> newFromCombo = buildLocationIdCombo("newFrom");
        ComboBox<String> newToCombo = buildLocationIdCombo("newTo");
        TextField newDistField = new TextField(); newDistField.setPromptText("Distance (m)"); newDistField.setPrefWidth(100);
        newDistField.setId("newPathDist");
        ComboBox<String> newSfCombo = new ComboBox<>(FXCollections.observableArrayList("YES", "NO", "UNKNOWN"));
        newSfCombo.setPromptText("Step-free"); newSfCombo.setPrefWidth(100);
        newSfCombo.setId("newPathStepFree");

        Button addBtn = new Button("Add Path");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setId("addPathBtn");
        addBtn.setOnAction(e -> {
            try {
                double dist = Double.parseDouble(newDistField.getText());
                String sf = newSfCombo.getValue();
                if (sf == null) { editStatus.setText("⚠️ Select step-free status."); return; }
                Optional<String> err = updater.addPath(
                        newIdField.getText(), newFromCombo.getValue(), newToCombo.getValue(),
                        dist, false, StepFreeStatus.valueOf(sf));
                if (err.isPresent()) { editStatus.setText("Error: " + err.get()); }
                else {
                    editStatus.setText("✓ Path " + newIdField.getText() + " added successfully.");
                    newIdField.clear(); newDistField.clear();
                    afterUpdate(refreshPaths);
                }
            } catch (NumberFormatException ex) { editStatus.setText("⚠️ Invalid distance format."); }
        });

        HBox addRow = new HBox(8, addLabel, newIdField, newFromCombo, newToCombo, newDistField, newSfCombo, addBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);
        addCard.getChildren().add(addRow);

        // Reset button row
        HBox bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        Button resetBtn = new Button("↺ Reset to Default Campus Data");
        resetBtn.getStyleClass().add("btn-danger");
        resetBtn.setId("resetBtn");
        resetBtn.setOnAction(e -> {
            Optional<String> err = updater.resetToDefault();
            if (err.isPresent()) { editStatus.setText("Reset error: " + err.get()); }
            else {
                editStatus.setText("✓ Campus data restored to bundled default values.");
                afterUpdate(refreshPaths);
            }
        });
        bottomRow.getChildren().addAll(resetBtn, editStatus);

        pane.getChildren().addAll(title, pathTable, editCard, addCard, bottomRow);
        VBox.setVgrow(pathTable, Priority.ALWAYS);
        return pane;
    }

    private ComboBox<String> buildLocationIdCombo(String id) {
        List<String> ids = updater.getCurrentData().getLocations().stream()
                .map(CampusLocation::getId)
                .sorted()
                .collect(Collectors.toList());
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(ids));
        combo.setPromptText("Location ID");
        combo.setPrefWidth(140);
        combo.setId(id);
        return combo;
    }

    private void afterUpdate(Runnable refreshPaths) {
        comparisonService.updateGraph(updater.getCurrentGraph());
        refreshPaths.run();
        // Re-run current search if one was active
        if (currentShortest != null) {
            onFindRoute();
        }
        if (mapCanvas != null) drawSchematicMap();
    }

    // ==================== SCHEMATIC MAP TAB ====================

    private VBox buildSchematicMapPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(16));

        VBox header = new VBox(4);
        Label title = new Label("Interactive Campus Schematic Map");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #0F172A;");

        Label tip = new Label("💡 Interactive Controls: Click any building circle to set as START (Left-Click) or DESTINATION (Right-Click). Hover over buildings to view entrance & elevator features.");
        tip.setStyle("-fx-text-fill: #1E40AF; -fx-font-size: 12px; -fx-background-color: #DBEAFE; -fx-padding: 6 12; -fx-background-radius: 6;");
        header.getChildren().addAll(title, tip);

        // Map toolbar
        HBox mapTools = new HBox(12);
        mapTools.setAlignment(Pos.CENTER_LEFT);

        Button zoomInBtn = new Button("🔍 Zoom In (+)");
        zoomInBtn.getStyleClass().add("btn-secondary");
        zoomInBtn.setOnAction(e -> { mapZoom = Math.min(2.0, mapZoom + 0.15); drawSchematicMap(); });

        Button zoomOutBtn = new Button("🔍 Zoom Out (-)");
        zoomOutBtn.getStyleClass().add("btn-secondary");
        zoomOutBtn.setOnAction(e -> { mapZoom = Math.max(0.6, mapZoom - 0.15); drawSchematicMap(); });

        Button resetZoomBtn = new Button("↺ Reset View");
        resetZoomBtn.getStyleClass().add("btn-secondary");
        resetZoomBtn.setOnAction(e -> { mapZoom = 1.0; drawSchematicMap(); });

        Button routeNowBtn = new Button("▶ Find Route for Selection");
        routeNowBtn.getStyleClass().add("btn-primary");
        routeNowBtn.setOnAction(e -> {
            onFindRoute();
            tabPane.getSelectionModel().select(0); // switch to Route Finder tab
        });

        mapSelectionLabel = new Label("Start: (none)  ➔  Destination: (none)");
        mapSelectionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0F172A;");

        mapTools.getChildren().addAll(zoomInBtn, zoomOutBtn, resetZoomBtn, new Separator(), mapSelectionLabel, routeNowBtn);

        // Canvas
        mapCanvas = new Canvas(1050, 620);
        setupMapInteractivity();
        drawSchematicMap();

        ScrollPane scrollPane = new ScrollPane(mapCanvas);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("card-pane");

        // Legend bar
        HBox legend = new HBox(16);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setStyle("-fx-font-size: 12px; -fx-padding: 4 8;");
        legend.getChildren().addAll(
                new Label("🔵 Solid Blue = Shortest Route"),
                new Label("🟢 Dashed Green = Accessible Route"),
                new Label("❌ Red = Closed Path"),
                new Label("⚪ Gray = Campus Walkway"),
                new Label("🟠 Orange Node = Gate")
        );

        pane.getChildren().addAll(header, mapTools, scrollPane, legend);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return pane;
    }

    private void setupMapInteractivity() {
        mapCanvas.setOnMouseMoved(e -> {
            double mx = e.getX();
            double my = e.getY();
            CampusGraph graph = comparisonService.getGraph();
            CampusLocation match = null;

            for (CampusLocation loc : graph.getAllLocations()) {
                double x = loc.getLayoutX() * mapZoom;
                double y = loc.getLayoutY() * mapZoom;
                double dist = Math.hypot(mx - x, my - y);
                if (dist <= 18 * mapZoom) {
                    match = loc;
                    break;
                }
            }

            if (match != hoveredLocation) {
                hoveredLocation = match;
                mapCanvas.setCursor(hoveredLocation != null ? Cursor.HAND : Cursor.DEFAULT);
                drawSchematicMap();
            }
        });

        mapCanvas.setOnMouseClicked(e -> {
            if (hoveredLocation != null) {
                String displayName = hoveredLocation.getName() +
                        (hoveredLocation.getDataSource() == DataSource.DEMO_PLACEHOLDER ? " (demo)" : "");

                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && !e.isShiftDown()) {
                    startCombo.setValue(displayName);
                } else {
                    destCombo.setValue(displayName);
                }
                updateMapSelectionLabel();
                drawSchematicMap();
            }
        });
    }

    private void updateMapSelectionLabel() {
        String s = startCombo.getValue() != null ? startCombo.getValue() : "(none)";
        String d = destCombo.getValue() != null ? destCombo.getValue() : "(none)";
        if (currentShortest != null && currentShortest.isFound()) {
            String accText = (currentAccessible != null && currentAccessible.isFound())
                    ? (int) currentAccessible.getTotalDistanceMeters() + "m" : "None";
            mapSelectionLabel.setText("📍 " + s + "  ➔  🏁 " + d +
                    "   |   ⚡ Shortest: " + (int) currentShortest.getTotalDistanceMeters() + "m" +
                    "   |   ♿ Accessible: " + accText);
        } else {
            mapSelectionLabel.setText("📍 Start: " + s + "   ➔   🏁 Dest: " + d);
        }
    }

    private void drawSchematicMap() {
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, mapCanvas.getWidth(), mapCanvas.getHeight());

        // Background subtle grid
        gc.setFill(Color.web("#FAFAFC"));
        gc.fillRect(0, 0, mapCanvas.getWidth(), mapCanvas.getHeight());

        CampusGraph graph = comparisonService.getGraph();

        // Collect route path ids and node ids for highlighting
        Set<String> shortestPathIds = new HashSet<>();
        Set<String> accessiblePathIds = new HashSet<>();
        Set<String> routeNodeIds = new HashSet<>();

        if (currentShortest != null && currentShortest.isFound()) {
            currentShortest.getOrderedPaths().forEach(p -> shortestPathIds.add(p.getId()));
            routeNodeIds.addAll(currentShortest.getOrderedLocationIds());
        }
        if (currentAccessible != null && currentAccessible.isFound()) {
            currentAccessible.getOrderedPaths().forEach(p -> accessiblePathIds.add(p.getId()));
            routeNodeIds.addAll(currentAccessible.getOrderedLocationIds());
        }

        // Pass 1: Draw regular inactive paths and closed paths
        for (CampusPath path : graph.getAllPaths()) {
            CampusLocation from = graph.getLocation(path.getFrom());
            CampusLocation to = graph.getLocation(path.getTo());
            if (from == null || to == null) continue;

            boolean inShortest = shortestPathIds.contains(path.getId());
            boolean inAccessible = accessiblePathIds.contains(path.getId());
            if (inShortest || inAccessible) continue; // draw active paths in Pass 2 on top

            double x1 = from.getLayoutX() * mapZoom, y1 = from.getLayoutY() * mapZoom;
            double x2 = to.getLayoutX() * mapZoom, y2 = to.getLayoutY() * mapZoom;

            if (path.isClosed()) {
                gc.setStroke(Color.web("#EF4444"));
                gc.setLineWidth(1.8 * mapZoom);
                gc.setLineDashes(5 * mapZoom, 5 * mapZoom);
                gc.strokeLine(x1, y1, x2, y2);
                // Draw X in the middle
                double mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
                gc.setFill(Color.web("#DC2626"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12 * mapZoom));
                gc.fillText("✗", mx - 4, my + 4);
                gc.setLineDashes(null);
            } else {
                gc.setStroke(Color.web("#CBD5E1"));
                gc.setLineWidth(1.3 * mapZoom);
                gc.setLineDashes(null);
                gc.strokeLine(x1, y1, x2, y2);
            }
        }

        // Pass 2: Draw ACTIVE highlighted route paths (glowing halos + thick foreground)
        for (CampusPath path : graph.getAllPaths()) {
            CampusLocation from = graph.getLocation(path.getFrom());
            CampusLocation to = graph.getLocation(path.getTo());
            if (from == null || to == null) continue;

            boolean inShortest = shortestPathIds.contains(path.getId());
            boolean inAccessible = accessiblePathIds.contains(path.getId());
            if (!inShortest && !inAccessible) continue;

            double x1 = from.getLayoutX() * mapZoom, y1 = from.getLayoutY() * mapZoom;
            double x2 = to.getLayoutX() * mapZoom, y2 = to.getLayoutY() * mapZoom;
            double mx = (x1 + x2) / 2, my = (y1 + y2) / 2;

            if (inShortest && inAccessible) {
                // Shared route: Glowing Blue halo + Solid Royal Blue line
                gc.setStroke(Color.rgb(37, 99, 235, 0.35));
                gc.setLineWidth(12.0 * mapZoom);
                gc.strokeLine(x1, y1, x2, y2);

                gc.setStroke(Color.web("#2563EB"));
                gc.setLineWidth(5.5 * mapZoom);
                gc.strokeLine(x1, y1, x2, y2);

                drawPillBadge(gc, mx, my, (int) path.getDistanceMeters() + "m", Color.web("#1E40AF"), Color.WHITE);
            } else if (inShortest) {
                // Shortest only: Glowing Light Blue halo + Solid Blue line
                gc.setStroke(Color.rgb(59, 130, 246, 0.35));
                gc.setLineWidth(11.0 * mapZoom);
                gc.strokeLine(x1, y1, x2, y2);

                gc.setStroke(Color.web("#2563EB"));
                gc.setLineWidth(5.0 * mapZoom);
                gc.strokeLine(x1, y1, x2, y2);

                drawPillBadge(gc, mx, my, (int) path.getDistanceMeters() + "m", Color.web("#2563EB"), Color.WHITE);
            } else {
                // Accessible only: Glowing Emerald halo + Dashed Emerald line
                gc.setStroke(Color.rgb(16, 185, 129, 0.35));
                gc.setLineWidth(11.0 * mapZoom);
                gc.strokeLine(x1, y1, x2, y2);

                gc.setStroke(Color.web("#059669"));
                gc.setLineWidth(5.0 * mapZoom);
                gc.setLineDashes(9 * mapZoom, 5 * mapZoom);
                gc.strokeLine(x1, y1, x2, y2);
                gc.setLineDashes(null);

                drawPillBadge(gc, mx, my, (int) path.getDistanceMeters() + "m", Color.web("#059669"), Color.WHITE);
            }
        }

        String selStartId = displayNameToId(startCombo.getValue());
        String selDestId = displayNameToId(destCombo.getValue());

        // Pass 3: Draw Nodes
        for (CampusLocation loc : graph.getAllLocations()) {
            double x = loc.getLayoutX() * mapZoom, y = loc.getLayoutY() * mapZoom;
            double r = (loc.getType() == LocationType.GATE ? 10 : 8) * mapZoom;

            boolean isStart = loc.getId().equals(selStartId);
            boolean isDest = loc.getId().equals(selDestId);
            boolean isHovered = hoveredLocation != null && loc.getId().equals(hoveredLocation.getId());
            boolean isWaypoint = routeNodeIds.contains(loc.getId()) && !isStart && !isDest;

            // Highlight Halos
            if (isStart) {
                gc.setStroke(Color.rgb(16, 185, 129, 0.5));
                gc.setLineWidth(6.0 * mapZoom);
                gc.strokeOval(x - r - 6, y - r - 6, (r + 6) * 2, (r + 6) * 2);
            } else if (isDest) {
                gc.setStroke(Color.rgb(37, 99, 235, 0.5));
                gc.setLineWidth(6.0 * mapZoom);
                gc.strokeOval(x - r - 6, y - r - 6, (r + 6) * 2, (r + 6) * 2);
            } else if (isWaypoint) {
                gc.setStroke(Color.web("#38BDF8"));
                gc.setLineWidth(3.0 * mapZoom);
                gc.strokeOval(x - r - 3, y - r - 3, (r + 3) * 2, (r + 3) * 2);
            } else if (isHovered) {
                gc.setStroke(Color.web("#F59E0B"));
                gc.setLineWidth(3.0 * mapZoom);
                gc.strokeOval(x - r - 3, y - r - 3, (r + 3) * 2, (r + 3) * 2);
            }

            // Node Body Fill
            gc.setFill(loc.getType() == LocationType.GATE ? Color.web("#EA580C") :
                       loc.getDataSource() == DataSource.DEMO_PLACEHOLDER ? Color.web("#94A3B8") :
                       isStart ? Color.web("#059669") :
                       isDest ? Color.web("#1D4ED8") :
                       isWaypoint ? Color.web("#0284C7") :
                       Color.web("#3B82F6"));
            gc.fillOval(x - r, y - r, r * 2, r * 2);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.8 * mapZoom);
            gc.strokeOval(x - r, y - r, r * 2, r * 2);

            // Smart text positioning to prevent label overlaps
            String label = loc.getName();
            if (isStart) label += " 📍[START]";
            if (isDest) label += " 🏁[DEST]";
            if (loc.getDataSource() == DataSource.DEMO_PLACEHOLDER) label += " (demo)";

            double textX = x + r + 5;
            double textY = y + 4;
            if (loc.getId().equals("quadrangle")) {
                textX = x - 65 * mapZoom;
                textY = y - r - 6 * mapZoom;
            } else if (loc.getId().equals("maths_dept")) {
                textX = x - 35 * mapZoom;
                textY = y - r - 6 * mapZoom;
            } else if (loc.getId().equals("back_gate")) {
                textX = x - r - 75 * mapZoom;
                textY = y + 4;
            } else if (textX + 110 * mapZoom > mapCanvas.getWidth()) {
                textX = x - r - 95 * mapZoom;
            }

            // Translucent pill background for perfect readability over map edges
            double approxWidth = label.length() * 5.9 * mapZoom;
            gc.setFill(Color.rgb(255, 255, 255, 0.88));
            gc.fillRoundRect(textX - 3, textY - 10 * mapZoom, approxWidth + 6, 13 * mapZoom, 4, 4);

            // Node Text Labels
            gc.setFill(Color.web("#0F172A"));
            gc.setFont(Font.font("Segoe UI", (isStart || isDest ? FontWeight.BOLD : FontWeight.NORMAL), 10 * mapZoom));
            gc.fillText(label, textX, textY);
        }

        // Draw Hover Information Card on map if a node is hovered
        if (hoveredLocation != null) {
            double hx = hoveredLocation.getLayoutX() * mapZoom + 15;
            double hy = hoveredLocation.getLayoutY() * mapZoom - 40;
            if (hx + 220 > mapCanvas.getWidth()) hx -= 240;
            if (hy < 10) hy += 60;

            gc.setFill(Color.web("#0F172AE6"));
            gc.fillRoundRect(hx, hy, 220, 68, 10, 10);
            gc.setStroke(Color.web("#38BDF8"));
            gc.setLineWidth(1);
            gc.strokeRoundRect(hx, hy, 220, 68, 10, 10);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            gc.fillText(hoveredLocation.getName(), hx + 10, hy + 18);

            gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));
            gc.setFill(Color.web("#E2E8F0"));
            gc.fillText("Ramp: " + hoveredLocation.getRampAvailable() + " | Lift: " + hoveredLocation.getLiftAvailable(), hx + 10, hy + 35);
            gc.fillText("Stairs: " + hoveredLocation.getStairsAvailable() + " | Type: " + hoveredLocation.getType(), hx + 10, hy + 50);
        }
    }

    private void drawPillBadge(GraphicsContext gc, double x, double y, String text, Color bg, Color textColor) {
        double w = text.length() * 6.5 + 12;
        double h = 16;
        gc.setFill(bg);
        gc.fillRoundRect(x - w / 2, y - h / 2, w, h, 8, 8);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeRoundRect(x - w / 2, y - h / 2, w, h, 8, 8);
        gc.setFill(textColor);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        gc.fillText(text, x - w / 2 + 5, y + 3.5);
    }

    // ==================== ABOUT TAB ====================

    private VBox buildAboutPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(20));

        Label title = new Label("Accessible Route Adviser — RVCE Bengaluru");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setStyle("-fx-text-fill: #0F172A;");

        TextArea disclaimer = new TextArea();
        disclaimer.setEditable(false);
        disclaimer.setWrapText(true);
        disclaimer.setPrefHeight(450);

        CampusMetadata meta = updater.getCurrentData().getMetadata();
        String text = "Accessible Route Adviser — RVCE, Bengaluru\n" +
                "=============================================================\n\n" +
                "Scope & Boundary:\n" +
                "• Sourced specifically for RV College of Engineering (RVCE), Bengaluru.\n" +
                "• RV University buildings are out of scope, except the shared Library building.\n" +
                "• Simplified graph model using 21 main buildings and 2 campus gates.\n\n" +
                "=== Formal Accessibility Disclaimer ===\n" +
                (meta != null ? meta.getDisclaimer() : "(No disclaimer metadata found.)") +
                "\n\n=== Data Provenance ===\n" +
                "• Building accessibility flags (entrance ramp, stairs to floors, lift to floors)\n" +
                "  are sourced from unverified student observation (STUDENT_KNOWLEDGE).\n" +
                "• All 23 real campus locations are marked 'Unverified'.\n" +
                "• Pedestrian path distances and step-free values are demonstration data.\n" +
                "• The Demo Isolated Building uses DEMO_PLACEHOLDER data.\n\n" +
                "=== Technical Stack & Algorithms ===\n" +
                "• Java 21 LTS with JavaFX desktop interface.\n" +
                "• Dijkstra's shortest-path algorithm with edge-eligibility pre-filtering.\n" +
                "• Tie-breaking: fewer segments first, followed by lexicographical path ID order.\n" +
                "• Safe JSON persistence with automatic .bak backup and atomic save.\n\n" +
                "Writable Data Location: " + loader.getWritableDataFile().toString() + "\n";

        disclaimer.setText(text);
        pane.getChildren().addAll(title, disclaimer);
        VBox.setVgrow(disclaimer, Priority.ALWAYS);
        return pane;
    }

    private void showErrorAndExit(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Startup Error");
        alert.showAndWait();
    }

    // ==================== TABLE ROW CLASSES ====================

    /**
     * Row for the building info table.
     */
    public static class BuildingRow {
        private final String name, type, ramp, stairs, lift, source, verified;

        public BuildingRow(CampusLocation loc) {
            this.name = loc.getName();
            this.type = loc.getType().name();
            this.ramp = formatFlag(loc.getRampAvailable());
            this.stairs = formatFlag(loc.getStairsAvailable());
            this.lift = formatFlag(loc.getLiftAvailable());
            this.source = loc.getDataSource().name();
            this.verified = loc.isVerified() ? "Verified" : "Unverified";
        }

        private static String formatFlag(AvailabilityStatus s) {
            return switch (s) {
                case NOT_APPLICABLE -> "N/A";
                case YES -> "Yes";
                case NO -> "No";
                case UNKNOWN -> "Unknown";
            };
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getRamp() { return ramp; }
        public String getStairs() { return stairs; }
        public String getLift() { return lift; }
        public String getSource() { return source; }
        public String getVerified() { return verified; }
    }

    /**
     * Row for the path management table.
     */
    public static class PathRow {
        private final String id, from, to, distance, stepFree, closed;

        public PathRow(String id, String from, String to, String distance, String stepFree, String closed) {
            this.id = id; this.from = from; this.to = to;
            this.distance = distance; this.stepFree = stepFree; this.closed = closed;
        }

        public String getId() { return id; }
        public String getFrom() { return from; }
        public String getTo() { return to; }
        public String getDistance() { return distance; }
        public String getStepFree() { return stepFree; }
        public String getClosed() { return closed; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
