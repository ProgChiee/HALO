package com.ptc.halo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "weeks", uniqueConstraints = @UniqueConstraint(name = "uk_week_subject_number", columnNames = {"subject_id", "week_number"}))
public class WeekEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private Integer weekNumber;


    private String title;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private SubjectEntity subject;


    public WeekEntity() {
    }


    public Long getId() {
        return id;
    }


    public Integer getWeekNumber() {
        return weekNumber;
    }


    public void setWeekNumber(Integer weekNumber) {
        this.weekNumber = weekNumber;
    }


    public String getTitle() {
        return title;
    }


    public void setTitle(String title) {
        this.title = title;
    }


    public SubjectEntity getSubject() {
        return subject;
    }


    public void setSubject(SubjectEntity subject) {
        this.subject = subject;
    }
}