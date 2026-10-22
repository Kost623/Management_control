package com.managementcontrol.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String entity, Object id) {
        super(entity + " з ідентифікатором " + id + " не знайдено");
    }
}
