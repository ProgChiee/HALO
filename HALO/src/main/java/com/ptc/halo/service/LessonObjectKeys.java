package com.ptc.halo.service;

import java.util.List;

/** Deterministic legacy mapping, shared contract with the offline migration tool. */
public final class LessonObjectKeys {
    private final List<String> roots;
    public LessonObjectKeys(String legacyRoots) {
        roots = java.util.Arrays.stream(legacyRoots.split(";"))
            .map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.replace('\\','/').replaceAll("/+$", "") + "/").toList();
    }
    public String key(String reference) {
        if (reference == null || reference.isBlank()) throw new IllegalArgumentException("Invalid material reference");
        String value = reference.replace('\\','/');
        if (value.startsWith("uploads/lessons/")) value = value.substring(16);
        else for (String root : roots) if (value.startsWith(root)) { value = value.substring(root.length()); break; }
        if (value.length() > 512 || value.startsWith("/") || value.contains(":") || value.contains("%")
            || java.util.Arrays.stream(value.split("/", -1)).anyMatch(s -> s.isBlank() || s.equals(".") || s.equals(".."))
            || value.chars().anyMatch(c -> c < 32 || c == 127)) throw new IllegalArgumentException("Invalid material reference");
        return value;
    }
}
