package com.ptc.halo.service;

import com.ptc.halo.dtoRequest.StudentAnswerRequest;
import com.ptc.halo.entity.AssessmentQuestionEntity;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared pure validation: no repositories, entities are never mutated. */
final class AssessmentAnswerSet {
    private AssessmentAnswerSet() {}
    static Map<Long, String> validate(List<AssessmentQuestionEntity> questions, StudentAnswerRequest request) {
        var ids = questions.stream().map(AssessmentQuestionEntity::getId).collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty() || request == null || request.getAnswers() == null || request.getAnswers().size() != ids.size()) throw invalid();
        var submitted = new HashMap<Long, String>();
        for (var item : request.getAnswers()) {
            if (item == null || !ids.contains(item.getQuestionId()) || item.getAnswer() == null) throw invalid();
            var answer = item.getAnswer().trim().toUpperCase(Locale.ROOT);
            if (!answer.matches("[ABCD]") || submitted.putIfAbsent(item.getQuestionId(), answer) != null) throw invalid();
        }
        if (!submitted.keySet().equals(ids)) throw invalid();
        return submitted;
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER_SET");
    }
}
