package com.skilllab.routeadviser.model;

/**
 * Status of a route-finding result.
 */
public enum RouteStatus {
    /** A valid route was found. */
    FOUND,
    /** Destination is unreachable even ignoring closures and accessibility. */
    NO_CONNECTION,
    /** Destination is reachable only if closed paths were open. */
    BLOCKED_BY_CLOSURES,
    /** Destination is reachable ignoring the step-free policy, but the policy removes every route. */
    BLOCKED_BY_ACCESSIBILITY,
    /** Invalid input (unknown location id, null, etc.). */
    INVALID_INPUT
}
