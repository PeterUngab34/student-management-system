package com.peterungab.sms.model;

/**
 * Search criteria for the student list. Every criterion is optional ({@code null} = any).
 *
 * @param keyword   matched against student number, first/last/full name and e-mail
 * @param programId only students of this program
 * @param yearLevel only students in this year level
 * @param status    only students with this status
 * @param sortBy    server-side ordering (whitelisted columns only)
 * @param ascending sort direction
 */
public record StudentFilter(
        String keyword,
        Integer programId,
        Integer yearLevel,
        StudentStatus status,
        SortField sortBy,
        boolean ascending) {

    /** Columns the student list can be ordered by. The SQL fragment never comes from user input. */
    public enum SortField {
        STUDENT_NUMBER("s.student_number"),
        NAME("s.last_name %1$s, s.first_name"),
        PROGRAM("p.code %1$s, s.last_name"),
        YEAR_LEVEL("s.year_level %1$s, s.last_name");

        private final String orderByTemplate;

        SortField(String orderByTemplate) {
            this.orderByTemplate = orderByTemplate;
        }

        /** Builds the ORDER BY expression, applying the direction to every column. */
        public String orderBy(boolean ascending) {
            String dir = ascending ? "ASC" : "DESC";
            String expr = orderByTemplate.formatted(dir);
            return expr + " " + dir;
        }
    }

    public StudentFilter {
        sortBy = sortBy == null ? SortField.NAME : sortBy;
    }

    public static StudentFilter all() {
        return new StudentFilter(null, null, null, null, SortField.NAME, true);
    }

    public StudentFilter withKeyword(String value) {
        return new StudentFilter(value, programId, yearLevel, status, sortBy, ascending);
    }

    public StudentFilter withProgramId(Integer value) {
        return new StudentFilter(keyword, value, yearLevel, status, sortBy, ascending);
    }

    public StudentFilter withYearLevel(Integer value) {
        return new StudentFilter(keyword, programId, value, status, sortBy, ascending);
    }

    public StudentFilter withStatus(StudentStatus value) {
        return new StudentFilter(keyword, programId, yearLevel, value, sortBy, ascending);
    }

    public StudentFilter sortedBy(SortField field, boolean asc) {
        return new StudentFilter(keyword, programId, yearLevel, status, field, asc);
    }
}
