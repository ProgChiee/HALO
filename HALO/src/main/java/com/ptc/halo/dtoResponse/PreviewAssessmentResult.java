package com.ptc.halo.dtoResponse;

public class PreviewAssessmentResult extends AssessmentResultResponse {
    private int correctCount;
    private int totalQuestions;
    public boolean isPreview() { return true; }
    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int value) { correctCount = value; }
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int value) { totalQuestions = value; }
}
