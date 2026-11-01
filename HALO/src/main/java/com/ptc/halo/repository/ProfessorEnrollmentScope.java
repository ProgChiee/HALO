package com.ptc.halo.repository;

/** Membership within owned subjects matching the Student's current academic year. */
public final class ProfessorEnrollmentScope {
 private ProfessorEnrollmentScope() {}
 public static final String JPQL_MEMBERSHIP = """
  select e.id from StudentSubjectEnrollmentEntity e where e.student.id=u.id
  and e.subject.professor.user.id=:professorId
  and exists (select ep.id from StudentProfileEntity ep where ep.user.id=u.id and ep.yearLevel=e.subject.yearLevel)
  """;
 public static final String JPQL_ACTIVE = "exists ("+JPQL_MEMBERSHIP+" and e.active=true)";
 public static final String JPQL_ANY = "exists ("+JPQL_MEMBERSHIP+")";
 public static final String JPQL_STATUS = "case when "+JPQL_ACTIVE+" then 'ACTIVE' when "+JPQL_ANY+" then 'PREVIOUS' else 'NEVER' end";
 public static final String JPQL_FILTER = " and (:enrollment='ALL' or (:enrollment='ACTIVE' and "+JPQL_ACTIVE+") or (:enrollment='NEVER' and not "+JPQL_ANY+") or (:enrollment='PREVIOUS' and "+JPQL_ANY+" and not "+JPQL_ACTIVE+"))";
 public static final String SQL_MEMBERSHIP = """
  select e.id from student_subject_enrollments e join subjects es on es.id=e.subject_id
  join professor_entity ep on ep.id=es.professor_id
  where e.student_id=u.id and ep.user_id=:professor
  and exists (select 1 from student_profile_entity sp where sp.user_id=u.id and sp.year_level=es.year_level)
  """;
 public static final String SQL_ACTIVE = "exists ("+SQL_MEMBERSHIP+" and e.active=true)";
 public static final String SQL_ANY = "exists ("+SQL_MEMBERSHIP+")";
 public static final String SQL_STATUS = "case when "+SQL_ACTIVE+" then 'ACTIVE' when "+SQL_ANY+" then 'PREVIOUS' else 'NEVER' end";
 public static final String SQL_FILTER = " and (:enrollment='ALL' or (:enrollment='ACTIVE' and "+SQL_ACTIVE+") or (:enrollment='NEVER' and not "+SQL_ANY+") or (:enrollment='PREVIOUS' and "+SQL_ANY+" and not "+SQL_ACTIVE+"))";
}
