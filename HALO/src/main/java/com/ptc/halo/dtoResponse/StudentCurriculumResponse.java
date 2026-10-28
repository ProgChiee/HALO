package com.ptc.halo.dtoResponse;
import com.ptc.halo.enums.YearLevel;
import java.util.List;

/** Deliberately excludes module entities, content, materials, and academic history. */
public record StudentCurriculumResponse(Long subjectId, String subjectCode, String subjectName,
        YearLevel yearLevel, int publishedWeekCount, List<PublishedWeek> weeks) {
    public record PublishedWeek(Integer weekNumber, String title) {}
}
