package com.ptc.halo.dtoResponse;

public record ProfessorStudentSummary(Long userId, String name, String section,
                                      long completedModules, long passedAssessments, long totalBadges) {}
