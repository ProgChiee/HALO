package com.ptc.halo.service;

import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Read-only aggregates over durable learning records. Preview data never enters these tables. */
@Service
@Transactional(readOnly=true)
public class ProfessorEngagementService {
    private final NamedParameterJdbcTemplate jdbc;
    public ProfessorEngagementService(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String SCOPE = """
        with scoped_modules as (
          select m.id,s.year_level from ai_learning_modules m join weeks w on w.id=m.week_id
          join subjects s on s.id=w.subject_id join professor_entity p on p.id=s.professor_id
          where p.user_id=:professor
        ), scoped_students as (
          select distinct profile.user_id as student_id from student_profile_entity profile
          join subjects subject on subject.year_level=profile.year_level
          join professor_entity professor on professor.id=subject.professor_id
          where professor.user_id=:professor
        )
        """;
    public record Engagement(Long userId, String name, long activeDaysWeek, long activeDaysMonth, long mentorSessionsWeek, long mentorSessionsMonth) {
        public String getWeeklyConsistency(){return consistency(activeDaysWeek,3,2);}
        public String getMonthlyConsistency(){return consistency(activeDaysMonth,8,4);}
    }
    public static String consistency(long days,long consistent,long moderate){return days>=consistent?"Consistent":days>=moderate?"Moderate":"Low Activity";}
    public record ModuleEngagement(Long moduleId,String subjectName,Integer weekNumber,String title,long reviewCount,long reviewsMonth,long assessmentAttempts) {}
    public Map<String,Object> modules(UserEntity professor,Long studentId,int page,int size){
        if(professor==null || professor.getId()==null || professor.getRole()!=Role.PROFESSOR)throw new AccessDeniedException("Professor required");
        if(page<0 || size<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION");
        size=Math.min(size,100);
        var args=new HashMap<String,Object>();args.put("professor",professor.getId());args.put("student",studentId);args.put("limit",size);args.put("offset",(long)page*size);
        args.put("month",LocalDate.now().withDayOfMonth(1));args.put("until",LocalDate.now().plusDays(1));
        long eligible=jdbc.queryForObject(SCOPE+"select count(*) from scoped_students s join user_entity u on u.id=s.student_id where u.role='STUDENT' and u.id=:student",args,Long.class);
        if(eligible==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
        String from="""
          from ai_learning_modules m join weeks w on w.id=m.week_id join subjects s on s.id=w.subject_id
          join professor_entity p on p.id=s.professor_id
          where p.user_id=:professor and exists(select 1 from student_profile_entity profile where profile.user_id=:student and profile.year_level=s.year_level)
          """;
        long total=jdbc.queryForObject("select count(*) "+from,args,Long.class);
        var content=jdbc.query("""
          select m.id,s.subject_name,w.week_number,w.title,
          (select coalesce(sum(d.review_count),0) from lesson_study_days d where d.student_id=:student and d.module_id=m.id) as reviews,
          (select coalesce(sum(d.review_count),0) from lesson_study_days d where d.student_id=:student and d.module_id=m.id and d.study_date>=:month and d.study_date<:until) as month_reviews,
          (select count(*) from assessment_attempts a join assessments q on q.id=a.assessment_id where a.student_id=:student and q.module_id=m.id) as attempts
          """+from+" order by s.id,w.week_number,m.id limit :limit offset :offset",args,(rs,n)->new ModuleEngagement(rs.getLong(1),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getLong(5),rs.getLong(6),rs.getLong(7)));
        return Map.of("content",content,"number",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size,"first",page==0,"last",((long)page+1)*size>=total);
    }
    public record Page(List<Engagement> content,int number,int size,long totalElements,int totalPages,boolean first,boolean last,String timezone,String weekStartsOn) {}
    public Page get(UserEntity professor,int page,int size) { return get(professor,page,size,LocalDate.now()); }
    // Same server-local calendar used by HALO's LocalDateTime activity timestamps; ISO Monday weeks.
    public Page get(UserEntity professor,int page,int size,LocalDate today) {
        if(professor==null || professor.getId()==null || professor.getRole()!=Role.PROFESSOR) throw new AccessDeniedException("Professor required");
        if(page<0 || size<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION");
        size=Math.min(size,100);
        var week=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));var month=today.withDayOfMonth(1);
        var args=new HashMap<String,Object>();args.put("professor",professor.getId());args.put("limit",size);args.put("offset",(long)page*size);
        args.put("week",week.atStartOfDay());args.put("month",month.atStartOfDay());args.put("from",(week.isBefore(month)?week:month).atStartOfDay());args.put("until",today.plusDays(1).atStartOfDay());
        String roster=" from user_entity u join scoped_students s on s.student_id=u.id where u.role='STUDENT'";
        long total=jdbc.queryForObject(SCOPE+"select count(*)"+roster,args,Long.class);
        var students=jdbc.query(SCOPE+"select u.id,u.name"+roster+" order by u.id limit :limit offset :offset",args,(rs,n)->new Engagement(rs.getLong(1),rs.getString(2),0,0,0,0));
        var metrics=new HashMap<Long,Engagement>();
        if(!students.isEmpty()) {
            args.put("students",students.stream().map(Engagement::userId).toList());
            String events="""
                , events as (
                  select a.student_id, a.started_at as happened, null as session_id
                  from assessment_attempts a join assessments q on q.id=a.assessment_id join scoped_modules m on m.id=q.module_id
                  where exists(select 1 from student_profile_entity eligible where eligible.user_id=a.student_id and eligible.year_level=m.year_level) and a.student_id in (:students) and a.started_at>=:from and a.started_at<:until
                  union all select a.student_id,a.submitted_at,null from assessment_attempts a join assessments q on q.id=a.assessment_id join scoped_modules m on m.id=q.module_id
                  where exists(select 1 from student_profile_entity eligible where eligible.user_id=a.student_id and eligible.year_level=m.year_level) and a.student_id in (:students) and a.submitted_at>=:from and a.submitted_at<:until
                  union all select d.student_id,d.study_date,null from lesson_study_days d join scoped_modules m on m.id=d.module_id
                  where exists(select 1 from student_profile_entity eligible where eligible.user_id=d.student_id and eligible.year_level=m.year_level) and d.student_id in (:students) and d.study_date>=:from and d.study_date<:until
                  union all select p.student_id,p.completed_at,null from student_module_progress p join scoped_modules m on m.id=p.module_id
                  where exists(select 1 from student_profile_entity eligible where eligible.user_id=p.student_id and eligible.year_level=m.year_level) and p.student_id in (:students) and p.completed=true and p.completed_at>=:from and p.completed_at<:until
                  union all select s.student_id,t.created_at,s.id from mentor_messages t join mentor_sessions s on s.id=t.session_id join scoped_modules m on m.id=s.ai_learning_module_id
                  where exists(select 1 from student_profile_entity eligible where eligible.user_id=s.student_id and eligible.year_level=m.year_level) and s.student_id in (:students) and t.sender='STUDENT' and length(trim(t.message))>0 and t.created_at>=:from and t.created_at<:until
                )
                select student_id,
                  count(distinct case when happened>=:week then cast(happened as date) end) as days_week,
                  count(distinct case when happened>=:month then cast(happened as date) end) as days_month,
                  count(distinct case when happened>=:week then session_id end) as sessions_week,
                  count(distinct case when happened>=:month then session_id end) as sessions_month
                from events group by student_id
                """;
            jdbc.query(SCOPE+events,args,(org.springframework.jdbc.core.RowCallbackHandler)rs->metrics.put(rs.getLong(1),new Engagement(rs.getLong(1),null,rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5))));
        }
        var result=students.stream().map(s->{var m=metrics.getOrDefault(s.userId(),s);return new Engagement(s.userId(),s.name(),m.activeDaysWeek(),m.activeDaysMonth(),m.mentorSessionsWeek(),m.mentorSessionsMonth());}).toList();
        int pages=(int)((total+size-1)/size);
        return new Page(result,page,size,total,pages,page==0,(long)(page+1)*size>=total,ZoneId.systemDefault().getId(),"MONDAY");
    }
}
