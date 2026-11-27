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
    static final int QUESTION_COUNT = 20;
    private GeneratedAssessmentValidator() {}

    public static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "QUIZ_GENERATION_INVALID");
    }

    public static void validate(GeneratedAssessment generated) {
        if (generated == null || generated.getQuestions() == null || generated.getQuestions().size() != QUESTION_COUNT)
            throw invalid();
        Set<Integer> numbers = new HashSet<>();
        var stems = new java.util.ArrayList<Set<String>>();
        Set<String> texts = new HashSet<>();
        for (var question : generated.getQuestions()) {
            if (question == null || question.getQuestionNumber() == null
                    || question.getQuestionNumber() < 1 || question.getQuestionNumber() > QUESTION_COUNT
                    || !numbers.add(question.getQuestionNumber())
                    || question.getQuestion() == null || question.getQuestion().isBlank()
                    || question.getQuestion().length() > 12000
                    || !texts.add(normalize(question.getQuestion()))
                    || !Set.of("A", "B", "C", "D").contains(question.getCorrectAnswer() == null ? "" : question.getCorrectAnswer()))
                throw invalid();
            var words = new HashSet<>(Arrays.asList(normalize(question.getQuestion()).split(" ")));
            if (words.contains("")) throw invalid();
            for (var previous : stems) {
                long shared = words.stream().filter(previous::contains).count();
                int union = words.size() + previous.size() - (int) shared;
                if (shared == union || (shared >= 6 && shared * 100 >= 85L * union)) throw invalid();
            }
            stems.add(words);
            Set<String> choices = new HashSet<>();
            for (String option : Arrays.asList(question.getOptionA(), question.getOptionB(), question.getOptionC(), question.getOptionD()))
                if (option == null || option.isBlank() || option.length() > 255 || !choices.add(option.strip().toLowerCase(Locale.ROOT))) throw invalid();
        }
    }
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
