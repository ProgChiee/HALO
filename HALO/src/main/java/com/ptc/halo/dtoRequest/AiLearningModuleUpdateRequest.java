package com.ptc.halo.dtoRequest;

import jakarta.validation.constraints.*;

public class AiLearningModuleUpdateRequest {

    @Size(max = 100000)
    private String lessonText;
    @Size(max = 255)
    @org.hibernate.validator.constraints.URL(regexp = "https?://.+", message = "must be a valid HTTP(S) URL")
    private String youtubeLink;
    @Size(max = 10000)
    private String aiNotes;

    public String getLessonText() {
        return lessonText;
    }

    public void setLessonText(String lessonText) {
        this.lessonText = lessonText == null ? null : lessonText.strip();
    }

    public String getYoutubeLink() {
        return youtubeLink;
    }

    public void setYoutubeLink(String youtubeLink) {
        this.youtubeLink = youtubeLink == null ? null : youtubeLink.strip();
    }

    public String getAiNotes() {
        return aiNotes;
    }

    public void setAiNotes(String aiNotes) {
        this.aiNotes = aiNotes == null ? null : aiNotes.strip();
    }
}