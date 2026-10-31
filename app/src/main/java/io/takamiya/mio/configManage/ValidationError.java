package io.takamiya.mio.configManage;

public class ValidationError {
    private final String field;
    private final String message;
    private final Object expected;
    private final Object actual;

    public ValidationError(String field, String message, Object expected, Object actual) {
        this.field = field;
        this.message = message;
        this.expected = expected;
        this.actual = actual;
    }

    public String getField() { return field; }
    public String getMessage() { return message; }
    public Object getExpected() { return expected; }
    public Object getActual() { return actual; }

    @Override
    public String toString() {
        return "Field \"" + field + "\": " + message
                + (expected != null ? " (expected: " + expected + ")" : "")
                + (actual != null ? " (got: " + actual + ")" : "");
    }
}
