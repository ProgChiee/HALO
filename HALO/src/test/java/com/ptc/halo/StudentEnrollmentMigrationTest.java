package com.ptc.halo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StudentEnrollmentMigrationTest {
 @Test void additiveMariaDbMigrationCreatesUniqueForeignKeyProtectedPairsOnIsolatedDatabase() throws Exception {
  try(var connection=java.sql.DriverManager.getConnection("jdbc:h2:mem:enrollment-migration;MODE=MariaDB","sa","");var sql=connection.createStatement()){
   sql.execute("create table user_entity(id bigint primary key)");sql.execute("create table subjects(id bigint primary key)");
   String migration=java.nio.file.Files.readString(java.nio.file.Path.of("database/student-subject-enrollment.sql")).split("-- Verification")[0];
   sql.execute(migration);
   sql.execute("insert into user_entity values(1),(2)");sql.execute("insert into subjects values(15)");
   sql.execute("insert into student_subject_enrollments(student_id,subject_id,recorded_at_epoch_ms,source) values(1,15,1,'SELF_ENROLLED')");
   assertEquals("23505",assertThrows(java.sql.SQLException.class,()->sql.execute("insert into student_subject_enrollments(student_id,subject_id,recorded_at_epoch_ms,source) values(1,15,2,'SELF_ENROLLED')")).getSQLState());
   assertThrows(java.sql.SQLException.class,()->sql.execute("insert into student_subject_enrollments(student_id,subject_id,recorded_at_epoch_ms,source) values(999,15,2,'SELF_ENROLLED')"));
   try(var rows=sql.executeQuery("select count(*) from student_subject_enrollments")){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
  }
 }
}
