package com.ptc.halo.entity;
import jakarta.persistence.*;
@Entity
@Table(name="student_subject_enrollments",uniqueConstraints=@UniqueConstraint(name="uk_student_subject_enrollment",columnNames={"student_id","subject_id"}))
public class StudentSubjectEnrollmentEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="student_id",nullable=false) private UserEntity student;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="subject_id",nullable=false) private SubjectEntity subject;
 @Column(nullable=false) private long recordedAtEpochMs;
 @Column(nullable=false,length=32) private String source;
 protected StudentSubjectEnrollmentEntity() {}
 public StudentSubjectEnrollmentEntity(UserEntity student,SubjectEntity subject){this.student=student;this.subject=subject;this.recordedAtEpochMs=java.time.Instant.now().toEpochMilli();this.source="SELF_ENROLLED";}
 public Long getId(){return id;}
}
