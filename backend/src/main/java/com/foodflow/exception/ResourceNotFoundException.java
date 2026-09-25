package com.foodflow.exception;

import org.springframework.http.HttpStatus;

/** 404: the resource doesn't exist, or it isn't visible to the caller. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " not found with id " + id);
    }
}
