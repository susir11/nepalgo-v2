package com.nepalgo.catalog.destination;

public class DestinationNotFoundException extends RuntimeException {
    public DestinationNotFoundException(Long id) {
        super("Destination not found: " + id);
    }
}