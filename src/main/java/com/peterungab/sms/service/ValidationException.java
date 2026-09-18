package com.peterungab.sms.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown when input fails validation. Carries one message per invalid field so the UI can
 * show each error next to the field it belongs to.
 */
public class ValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super(String.join(" ", errors.values()));
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public ValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    /** Field name to error message, in the order the problems were found. */
    public Map<String, String> errors() {
        return errors;
    }

    public boolean hasError(String field) {
        return errors.containsKey(field);
    }

    /** Collects field errors and throws a single {@link ValidationException} at the end. */
    static final class Errors {
        private final Map<String, String> map = new LinkedHashMap<>();

        void add(String field, String message) {
            map.putIfAbsent(field, message);
        }

        boolean has(String field) {
            return map.containsKey(field);
        }

        void throwIfAny() {
            if (!map.isEmpty()) {
                throw new ValidationException(map);
            }
        }
    }
}
