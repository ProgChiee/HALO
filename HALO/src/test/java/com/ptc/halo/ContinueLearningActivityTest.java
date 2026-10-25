package com.ptc.halo;

import com.ptc.halo.repository.WeekRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.data.jpa.repository.Query;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ContinueLearningActivityTest {
    NamedParameterJdbcTemplate jdbc;
    String query;
    @BeforeEach void setup() throws Exception {
        jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MariaDB", "sa", ""));
        // Keep this isolated database alive between connections.
        jdbc.getJdbcTemplate().execute("SET DB_CLOSE_DELAY -1");
        for (String ddl : new String[]{
                "create table lesson_study_days(student_id bigint,module_id bigint,study_date date,last_opened_at timestamp)",
                "create table assessments(id bigint,module_id bigint)",
                "create table assessment_attempts(student_id bigint,assessment_id bigint,started_at timestamp,submitted_at timestamp)",
                "create table mentor_sessions(id bigint,student_id bigint,ai_learning_module_id bigint)",
                "create table mentor_messages(session_id bigint,sender varchar(20),message varchar(255),created_at timestamp)",
                "create table weeks(id bigint,subject_id bigint)",
                "create table ai_learning_modules(id bigint,week_id bigint,status varchar(20),ai_generation_status varchar(20))"})
            jdbc.getJdbcTemplate().execute(ddl);
        query = WeekRepository.class.getMethod("findModuleActivity", Long.class, Long.class).getAnnotation(Query.class).value();
        sql("insert into weeks values(1,15),(2,16)");
        sql("insert into ai_learning_modules values(42,1,'APPROVED','COMPLETED'),(43,1,'DRAFT','COMPLETED'),(44,2,'APPROVED','COMPLETED')");
    }
    void sql(String sql) { jdbc.getJdbcTemplate().execute(sql); }
    java.util.List<Map<String,Object>> rows() { return jdbc.queryForList(query, Map.of("subjectId",15L,"studentId",30L)); }
    @Test void lessonQuizAndRealMentorActivityUseLatestTimestamp() {
        sql("insert into lesson_study_days values(30,42,'2026-10-25','2026-10-25 10:00:00')");
        sql("insert into assessments values(1,42)");
        sql("insert into assessment_attempts values(30,1,'2026-10-25 11:00:00','2026-10-25 12:00:00')");
        assertEquals("2026-10-25 12:00:00.0", rows().get(0).get("LASTACTIVITYAT").toString());
        sql("insert into mentor_sessions values(1,30,42)");
        sql("insert into mentor_messages values(1,'STUDENT','Help','2026-10-25 13:00:00'),(1,'HALO','Hi','2026-10-25 14:00:00')");
        assertEquals("2026-10-25 13:00:00.0", rows().get(0).get("LASTACTIVITYAT").toString());
    }
    @Test void excludesOtherStudentsSubjectsUnpublishedAndDeletedModules() {
        sql("insert into lesson_study_days values(31,42,'2026-10-25',null),(30,43,'2026-10-25',null),(30,44,'2026-10-25',null),(30,99,'2026-10-25',null)");
        assertTrue(rows().isEmpty());
    }
    @Test void noHistoryAndLegacyDateFallback() {
        assertTrue(rows().isEmpty());
        sql("insert into lesson_study_days values(30,42,'2026-10-25',null)");
        assertEquals("2026-10-25 00:00:00.0", rows().get(0).get("LASTACTIVITYAT").toString());
    }
}
