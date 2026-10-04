package com.ptc.halo.controller;

// Messages are fixed server-defined validation text, never request content.
public class ProfessorInputException extends RuntimeException {
    final String field;
    public ProfessorInputException(String field, String message) { super(message); this.field = field; }
}
