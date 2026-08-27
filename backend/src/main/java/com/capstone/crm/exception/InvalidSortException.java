package com.capstone.crm.exception;

import java.util.Collection;
import java.util.TreeSet;

/**
 * A sort property the API does not allow.
 *
 * <p>Carries the allowed set so the handler can say what would have worked. The
 * field names are already visible in every customer response, so listing them
 * tells a caller nothing they could not read off a payload, and it turns a
 * guessing game into one round trip.
 */
public class InvalidSortException extends RuntimeException {

    private final String property;
    private final Collection<String> allowed;

    public InvalidSortException(String property, Collection<String> allowed) {
        super("Cannot sort by '" + property + "'");
        this.property = property;
        // Sorted, so the message is the same every time it is rendered rather
        // than following a hash set's iteration order.
        this.allowed = new TreeSet<>(allowed);
    }

    public String getProperty() {
        return property;
    }

    public Collection<String> getAllowed() {
        return allowed;
    }
}
