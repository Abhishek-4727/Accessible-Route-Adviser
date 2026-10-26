package com.skilllab.routeadviser.data;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.skilllab.routeadviser.model.CampusData;

import java.io.*;
import java.nio.file.*;
import java.util.List;

/**
 * Loads, validates, and manages campus data from JSON files.
 *
 * <p><b>Data location strategy:</b></p>
 * <ol>
 *   <li>On first run, the bundled {@code campus-data.json} from the classpath is copied
 *       to a writable location: {@code ~/.accessible-route-adviser/campus-data.json}.</li>
 *   <li>Subsequent loads read from the writable copy.</li>
 *   <li>A "Reset to default data" action re-copies from the classpath.</li>
 * </ol>
 */
public class CampusDataLoader {

    private static final String RESOURCE_NAME = "/campus-data.json";
    private static final String APP_DIR_NAME = ".accessible-route-adviser";
    private static final String DATA_FILE_NAME = "campus-data.json";

    private final ObjectMapper mapper;
    private final CampusDataValidator validator;
    private Path writableDataFile;

    public CampusDataLoader() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, false);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.validator = new CampusDataValidator();
    }

    /**
     * Creates a loader with a custom writable data file path (for testing).
     */
    public CampusDataLoader(Path writableDataFile) {
        this();
        this.writableDataFile = writableDataFile;
    }

    /**
     * Returns the path to the writable data file.
     */
    public Path getWritableDataFile() {
        if (writableDataFile == null) {
            String home = System.getProperty("user.home");
            writableDataFile = Paths.get(home, APP_DIR_NAME, DATA_FILE_NAME);
        }
        return writableDataFile;
    }

    /**
     * Loads campus data. If the writable file doesn't exist, copies from classpath first.
     *
     * @return validated CampusData
     * @throws IOException if reading fails
     * @throws IllegalStateException if validation fails
     */
    public CampusData load() throws IOException {
        Path dataFile = getWritableDataFile();
        if (!Files.exists(dataFile)) {
            resetToDefault();
        }
        return loadFromFile(dataFile);
    }

    /**
     * Loads campus data from a specific file path.
     */
    public CampusData loadFromFile(Path file) throws IOException {
        String json = Files.readString(file);
        return parseAndValidate(json);
    }

    /**
     * Loads campus data from a JSON string.
     */
    public CampusData loadFromString(String json) throws IOException {
        return parseAndValidate(json);
    }

    /**
     * Loads the bundled default data from the classpath (for testing/reset).
     */
    public CampusData loadBundledDefault() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(RESOURCE_NAME)) {
            if (is == null) {
                throw new IOException("Bundled resource not found: " + RESOURCE_NAME);
            }
            String json = new String(is.readAllBytes());
            return parseAndValidate(json);
        }
    }

    private CampusData parseAndValidate(String json) throws IOException {
        CampusData data;
        try {
            data = mapper.readValue(json, CampusData.class);
        } catch (Exception e) {
            throw new IOException("Failed to parse campus data JSON: " + e.getMessage(), e);
        }

        List<String> errors = validator.validate(data);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Campus data validation failed with " + errors.size() + " error(s):\n"
                    + String.join("\n", errors));
        }
        return data;
    }

    /**
     * Resets the writable data file to the bundled default.
     */
    public void resetToDefault() throws IOException {
        Path dataFile = getWritableDataFile();
        Files.createDirectories(dataFile.getParent());

        try (InputStream is = getClass().getResourceAsStream(RESOURCE_NAME)) {
            if (is == null) {
                throw new IOException("Bundled resource not found: " + RESOURCE_NAME);
            }
            Files.copy(is, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Saves campus data to the writable file with safe-save (write to temp, then atomic move).
     * Creates a .bak backup of the previous version.
     *
     * @param data the data to save (validated before writing)
     * @throws IOException if saving fails
     * @throws IllegalStateException if data fails validation
     */
    public void save(CampusData data) throws IOException {
        // Validate before writing
        List<String> errors = validator.validate(data);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot save: data validation failed with " + errors.size() + " error(s):\n"
                    + String.join("\n", errors));
        }

        Path dataFile = getWritableDataFile();
        Files.createDirectories(dataFile.getParent());

        // Write to a temp file first
        Path tempFile = dataFile.resolveSibling(DATA_FILE_NAME + ".tmp");
        mapper.writeValue(tempFile.toFile(), data);

        // Backup previous version
        if (Files.exists(dataFile)) {
            Path backupFile = dataFile.resolveSibling(DATA_FILE_NAME + ".bak");
            Files.copy(dataFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Atomic move (best-effort on Windows; replace existing)
        try {
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING,
                       StandardCopyOption.ATOMIC_MOVE);
        } catch (UnsupportedOperationException | IOException e) {
            // Fallback: non-atomic move
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public ObjectMapper getMapper() { return mapper; }
    public CampusDataValidator getValidator() { return validator; }
}
