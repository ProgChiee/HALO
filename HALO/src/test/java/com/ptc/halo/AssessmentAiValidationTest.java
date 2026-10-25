package com.ptc.halo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ptc.halo.service.AssessmentAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentAiValidationTest {
    ChatClient chat;
    AssessmentAiService service;
    @BeforeEach void setup() {
        chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        var builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chat);
        service = new AssessmentAiService(builder, new ObjectMapper());
    }
    static ObjectNode valid() {
        var root = new ObjectMapper().createObjectNode();
        var questions = root.putArray("questions");
        for (int number = 1; number <= 10; number++) questions.addObject()
                .put("questionNumber", number).put("question", "Hospitality question " + number)
                .put("optionA", "Choice A").put("optionB", "Choice B")
                .put("optionC", "Choice C").put("optionD", "Choice D").put("correctAnswer", "A");
        return root;
    }
    static Stream<String> malformed() {
        var responses = new ArrayList<String>();
        responses.add(null); responses.add(""); responses.add(" "); responses.add("not JSON");
        responses.add("null"); responses.add("[]"); responses.add("{}");
        responses.add("{\"questions\":{}}"); responses.add("{\"questions\":[null]}");
        responses.add(valid() + " {}");
        responses.add("{\"questions\":[],\"questions\":" + valid().get("questions") + "}");
        for (String field : new String[]{"questionNumber", "question", "optionA", "optionB", "optionC", "optionD", "correctAnswer"}) {
            var missing = valid(); ((ObjectNode)missing.get("questions").get(0)).remove(field); responses.add(missing.toString());
            var nullField = valid(); ((ObjectNode)nullField.get("questions").get(0)).putNull(field); responses.add(nullField.toString());
        }
        var fractional = valid(); ((ObjectNode)fractional.get("questions").get(0)).put("questionNumber", 1.5); responses.add(fractional.toString());
        var stringNumber = valid(); ((ObjectNode)stringNumber.get("questions").get(0)).put("questionNumber", "1"); responses.add(stringNumber.toString());
        var numberOption = valid(); ((ObjectNode)numberOption.get("questions").get(0)).put("optionA", 123); responses.add(numberOption.toString());
        var extraChoice = valid(); ((ObjectNode)extraChoice.get("questions").get(0)).put("optionE", "Extra"); responses.add(extraChoice.toString());
        var invalidKey = valid(); ((ObjectNode)invalidKey.get("questions").get(0)).put("correctAnswer", "Z"); responses.add(invalidKey.toString());
        return responses.stream();
    }
    @ParameterizedTest @MethodSource("malformed")
    void malformedProviderResponsesHaveSafeConsistentError(String response) {
        when(chat.prompt().user(anyString()).call().content()).thenReturn(response);
        var error = assertThrows(ResponseStatusException.class, () -> service.generateAssessment("Lesson", "Objectives", "Knowledge", "Examples", "Summary"));
        assertEquals(502, error.getStatusCode().value()); assertEquals("QUIZ_GENERATION_INVALID", error.getReason()); assertNull(error.getCause());
    }
    @Test void validResponseKeepsPromptAndAnswerContract() {
        when(chat.prompt().user(anyString()).call().content()).thenReturn(valid().toString());
        var quiz = service.generateAssessment("Published lesson", "Objectives", "Knowledge", "Examples", "Summary");
        assertEquals(10, quiz.getQuestions().size()); assertEquals("A", quiz.getQuestions().get(0).getCorrectAnswer());
        verify(chat.prompt()).user(contains("Generate exactly 10 questions."));
        verify(chat.prompt()).user(contains("Published lesson"));
    }
    @Test void providerFailureDoesNotExposeInternalDetails() {
        when(chat.prompt().user(anyString()).call().content()).thenThrow(new IllegalStateException("provider secret"));
        var error = assertThrows(ResponseStatusException.class, () -> service.generateAssessment("Lesson", null, null, null, null));
        assertEquals(502, error.getStatusCode().value()); assertEquals("QUIZ_GENERATION_FAILED", error.getReason()); assertNull(error.getCause());
    }
}
