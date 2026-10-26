package com.skilllab.routeadviser.model;

/**
 * Data provenance for a campus location entry.
 * <ul>
 *   <li>{@code STUDENT_KNOWLEDGE} — data provided by a student from their own knowledge.</li>
 *   <li>{@code DEMO_PLACEHOLDER} — synthetic/demo data used only for testing.</li>
 * </ul>
 */
public enum DataSource {
    STUDENT_KNOWLEDGE,
    DEMO_PLACEHOLDER
}
