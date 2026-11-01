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

 @Test void deactivationMigrationKeepsExistingEnrollmentsActiveAndPreservesKeys() throws Exception {
  try(var connection=java.sql.DriverManager.getConnection("jdbc:h2:mem:enrollment-deactivation;MODE=MariaDB","sa","");var sql=connection.createStatement()){
   sql.execute("create table user_entity(id bigint primary key)");sql.execute("create table subjects(id bigint primary key)");
   sql.execute(java.nio.file.Files.readString(java.nio.file.Path.of("database/student-subject-enrollment.sql")).split("-- Verification")[0]);
   sql.execute("insert into user_entity values(1)");sql.execute("insert into subjects values(15)");
   sql.execute("insert into student_subject_enrollments(id,student_id,subject_id,recorded_at_epoch_ms,source) values(8,1,15,123456,'SELF_ENROLLED')");
   var migration=java.nio.file.Files.readString(java.nio.file.Path.of("database/V20261029_01__student_enrollment_deactivation.sql"));
   for(String statement:migration.split(";"))if(!statement.isBlank())sql.execute(statement);
   try(var row=sql.executeQuery("select * from student_subject_enrollments")){
    assertTrue(row.next());assertEquals(8,row.getLong("id"));assertEquals(1,row.getLong("student_id"));assertEquals(15,row.getLong("subject_id"));assertEquals(123456,row.getLong("recorded_at_epoch_ms"));assertEquals("SELF_ENROLLED",row.getString("source"));assertTrue(row.getBoolean("active"));assertNull(row.getObject("unenrolled_at_epoch_ms"));assertFalse(row.next());
   }
   assertEquals("23505",assertThrows(java.sql.SQLException.class,()->sql.execute("insert into student_subject_enrollments(student_id,subject_id,recorded_at_epoch_ms,source) values(1,15,999,'SELF_ENROLLED')")).getSQLState());
   assertThrows(java.sql.SQLException.class,()->sql.execute("insert into student_subject_enrollments(student_id,subject_id,recorded_at_epoch_ms,source) values(99,15,999,'SELF_ENROLLED')"));
  }
 }
}
