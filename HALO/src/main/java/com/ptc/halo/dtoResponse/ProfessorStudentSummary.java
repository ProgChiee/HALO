package com.ptc.halo.dtoResponse;

public record ProfessorStudentSummary(Long userId, String name, String section, com.ptc.halo.enums.Status status,
                                      long completedModules, long passedAssessments, long totalBadges) {
 public ProfessorStudentSummary(Long id,String name,String section,long completed,long passed,long badges) {
  this(id,name,section,com.ptc.halo.enums.Status.ACTIVE,completed,passed,badges);
 }
}
