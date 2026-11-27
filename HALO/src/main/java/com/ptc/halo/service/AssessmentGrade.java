package com.ptc.halo.service;

/** Preserve integer percentage storage while deciding passage from exact points. */
record AssessmentGrade(int percent, boolean passed) {
    static AssessmentGrade calculate(int correct, int total, int threshold) {
        if (total <= 0 || correct < 0 || correct > total) throw new IllegalArgumentException("Invalid quiz points");
        long points = 100L * correct;
        boolean passed = points >= (long) threshold * total;
        int rounded = (int) ((points + total / 2) / total);
        return new AssessmentGrade(passed ? rounded : Math.min(rounded, threshold - 1), passed);
    }
}
