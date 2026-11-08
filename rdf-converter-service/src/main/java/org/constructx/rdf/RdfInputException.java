package org.constructx.rdf;

public class RdfInputException extends RuntimeException {
    public RdfInputException(String message) {
        super(message);
    }

    public RdfInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
