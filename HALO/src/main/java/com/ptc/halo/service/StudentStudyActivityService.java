package com.ptc.halo.service;

import com.ptc.halo.dtoResponse.StudentStudyActivityResponse;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class StudentStudyActivityService {
 private final NamedParameterJdbcTemplate jdbc;
 public StudentStudyActivityService(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
 public StudentStudyActivityResponse get(UserEntity student){return get(student,LocalDate.now());}
 public StudentStudyActivityResponse get(UserEntity student,LocalDate today){
  if(student==null || student.getId()==null || student.getRole()!=Role.STUDENT)throw new AccessDeniedException("Student required");
  var counts=mentorCounts(student.getId(),today);
  var week=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  // Same durable activity sources as Professor engagement. Preview never writes these tables.
  // SQL deduplicates days and measures the most recent contiguous island; only one row leaves DB.
  String sql="""
   """ + activityDays(false) + """
   , ranked as (
    select activity_day,row_number() over(order by activity_day desc) as row_index,max(activity_day) over() as latest from activity_days
   )
   select coalesce(sum(case when activity_day>=:week then 1 else 0 end),0) as weekly,
    coalesce(sum(case when latest>=:yesterday and timestampdiff(day,activity_day,latest)=row_index-1 then 1 else 0 end),0) as streak
   from ranked
   """;
  return jdbc.queryForObject(sql,Map.of("student",student.getId(),"until",today.plusDays(1).atStartOfDay(),"week",week,"yesterday",today.minusDays(1)),
   (rs,n)->new StudentStudyActivityResponse(rs.getLong("weekly"),rs.getLong("streak"),ZoneId.systemDefault().getId(),"MONDAY",counts[0],counts[1]));
 }

 private static String activityDays(boolean bounded){
  String sources="""
    select study_date as activity_day from lesson_study_days where student_id=:student and study_date<:until
    union select cast(started_at as date) from assessment_attempts where student_id=:student and started_at<:until
    union select cast(submitted_at as date) from assessment_attempts where student_id=:student and submitted_at<:until
    union select cast(completed_at as date) from student_module_progress where student_id=:student and completed=true and completed_at<:until
    union select cast(m.created_at as date) from mentor_messages m join mentor_sessions s on s.id=m.session_id
     where s.student_id=:student and m.sender='STUDENT' and length(trim(m.message))>0 and m.created_at<:until
   """;
  if(bounded)for(String column:java.util.List.of("study_date","started_at","submitted_at","completed_at","m.created_at"))sources=sources.replace(column+"<:until",column+">=:from and "+column+"<:until");
  return "with activity_days as ("+sources+")";
 }
 private long[] mentorCounts(Long student,LocalDate today){
  var week=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));var month=today.withDayOfMonth(1);
  return jdbc.queryForObject("""
   select count(distinct case when m.created_at>=:week then s.id end) as weekly,
    count(distinct case when m.created_at>=:month then s.id end) as monthly
   from mentor_messages m join mentor_sessions s on s.id=m.session_id
   where s.student_id=:student and m.sender='STUDENT' and length(trim(m.message))>0
    and m.created_at>=:from and m.created_at<:until
   """,Map.of("student",student,"week",week.atStartOfDay(),"month",month.atStartOfDay(),"from",(week.isBefore(month)?week:month).atStartOfDay(),"until",today.plusDays(1).atStartOfDay()),(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)});
 }
 public record CalendarMonth(String month,LocalDate today,String timezone,java.util.List<LocalDate> activeDates){}
 public CalendarMonth calendar(UserEntity student,String month){return calendar(student,month,LocalDate.now());}
 public CalendarMonth calendar(UserEntity student,String month,LocalDate today){
  if(student==null || student.getId()==null || student.getRole()!=Role.STUDENT)throw new AccessDeniedException("Student required");
  YearMonth selected;
  try{selected=month==null?YearMonth.from(today):YearMonth.parse(month);if(selected.getYear()<1000 || selected.getYear()>9998)throw new IllegalArgumentException();}
  catch(Exception ignored){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_REQUEST");}
  var from=selected.atDay(1);var until=selected.plusMonths(1).atDay(1);if(until.isAfter(today.plusDays(1)))until=today.plusDays(1);
  java.util.List<LocalDate> dates=from.isAfter(today)?java.util.List.of():jdbc.query(activityDays(true)+" select activity_day from activity_days order by activity_day",Map.of("student",student.getId(),"from",from.atStartOfDay(),"until",until.atStartOfDay()),(rs,n)->rs.getDate(1).toLocalDate());
  return new CalendarMonth(selected.toString(),today,ZoneId.systemDefault().getId(),dates);
 }
}
