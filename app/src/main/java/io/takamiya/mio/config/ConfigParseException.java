package io.takamiya.mio.config;

public class ConfigParseException extends Exception {
    private final int lineNumber;
    private final int columnNumber;
    private final String fieldName;
    private final String fieldType;
    private final String expectedType;
    private final String message;

    public ConfigParseException(int lineNumber, int columnNumber, String fieldName,
                                String fieldType, String expectedType, String message) {
        super(formatMessage(lineNumber, columnNumber, fieldName, fieldType, expectedType, message));
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.expectedType = expectedType;
        this.message = message;
    }

    public ConfigParseException(String fieldName, String expectedType, String message) {
        this(-1, -1, fieldName, null, expectedType, message);
    }

    public int getLineNumber() { return lineNumber; }
    public int getColumnNumber() { return columnNumber; }
    public String getFieldName() { return fieldName; }
    public String getFieldType() { return fieldType; }
    public String getExpectedType() { return expectedType; }

    private static String formatMessage(int line, int col, String fieldName,
                                         String fieldType, String expectedType, String msg) {
        StringBuilder sb = new StringBuilder();
        if (line > 0) {
            sb.append("第").append(line).append("行");
            if (col > 0) sb.append("第").append(col).append("列");
            sb.append(": ");
        }
        if (fieldName != null) {
            sb.append("字段 \"").append(fieldName).append("\"");
            if (fieldType != null) sb.append("(").append(fieldType).append(")");
            sb.append(" ");
        }
        if (expectedType != null) {
            sb.append("需要").append(expectedType).append("类型");
        }
        if (msg != null) {
            sb.append(msg);
        }
        return sb.toString();
    }
}
