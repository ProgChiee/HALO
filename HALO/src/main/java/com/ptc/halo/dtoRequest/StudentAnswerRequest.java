package com.ptc.halo.dtoRequest;

import java.util.List;

public class StudentAnswerRequest {

    @jakarta.validation.constraints.NotEmpty
    private List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid AnswerItem> answers;

    public StudentAnswerRequest() {
    }

    public List<AnswerItem> getAnswers() {
        return answers;
    }

    public void setAnswers(List<AnswerItem> answers) {
        this.answers = answers;
    }

    public static class AnswerItem {

        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.Positive
        private Long questionId;
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Pattern(regexp = "\\s*[ABCDabcd]\\s*")
        private String answer;

        public AnswerItem() {
        }

        public Long getQuestionId() {
            return questionId;
        }

        public void setQuestionId(Long questionId) {
            this.questionId = questionId;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }
    }
}
