package com.ptc.halo.entity;

import com.ptc.halo.enums.YearLevel;
import jakarta.persistence.*;

@Entity
@Table(name = "subjects")
public class SubjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable only for legacy subjects awaiting explicit ownership assignment.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id", foreignKey = @ForeignKey(name = "fk_subject_professor"))
    private ProfessorEntity professor;

    public ProfessorEntity getProfessor() { return professor; }
    public void setProfessor(ProfessorEntity professor) { this.professor = professor; }

    @Column(nullable = false, unique = true)
    private String subjectCode;

    @Column(nullable = false)
    private String subjectName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private YearLevel yearLevel;

    public SubjectEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public YearLevel getYearLevel() {
        return yearLevel;
    }

    public void setYearLevel(YearLevel yearLevel) {
        this.yearLevel = yearLevel;
    }
}
