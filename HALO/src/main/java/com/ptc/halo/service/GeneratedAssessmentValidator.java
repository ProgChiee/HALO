package com.ptc.halo.service;

import com.ptc.halo.dtoResponse.GeneratedAssessment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared provider-output contract for initial and replacement quizzes. */
public final class GeneratedAssessmentValidator {
    private GeneratedAssessmentValidator() {}

    public static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "QUIZ_GENERATION_INVALID");
    }

    public static void validate(GeneratedAssessment generated) {
        if (generated == null || generated.getQuestions() == null || generated.getQuestions().size() != 10)
            throw invalid();
        Set<Integer> numbers = new HashSet<>();
        Set<String> texts = new HashSet<>();
        for (var question : generated.getQuestions()) {
            if (question == null || question.getQuestionNumber() == null
                    || question.getQuestionNumber() < 1 || question.getQuestionNumber() > 10
                    || !numbers.add(question.getQuestionNumber())
                    || question.getQuestion() == null || question.getQuestion().isBlank()
                    || question.getQuestion().length() > 12000
                    || !texts.add(question.getQuestion().strip().toLowerCase(Locale.ROOT))
                    || !Set.of("A", "B", "C", "D").contains(question.getCorrectAnswer() == null ? "" : question.getCorrectAnswer()))
                throw invalid();
            for (String option : Arrays.asList(question.getOptionA(), question.getOptionB(), question.getOptionC(), question.getOptionD()))
                if (option == null || option.isBlank() || option.length() > 255) throw invalid();
        }
    }
}
