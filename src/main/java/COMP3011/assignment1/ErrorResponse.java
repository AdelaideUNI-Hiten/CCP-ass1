package COMP3011.assignment1;

public record ErrorResponse (
	String timestamp,
    int status,
    String error,
    String message,
    String path
) {}
