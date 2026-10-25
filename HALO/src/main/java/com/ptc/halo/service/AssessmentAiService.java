package com.ptc.halo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.ptc.halo.dtoResponse.GeneratedAssessment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AssessmentAiService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AssessmentAiService(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper) {

        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public GeneratedAssessment generateAssessment(
            String lessonText,
            String objectives,
            String knowledge,
            String examples,
            String summary) {

        String prompt = """
                You are HALO, an AI learning assessment generator
                for Hospitality and Tourism students.

                Create a multiple-choice assessment based ONLY on
                the approved lesson content provided below.

                LESSON:
                %s

                LEARNING OBJECTIVES:
                %s

                KEY KNOWLEDGE:
                %s

                EXAMPLES:
                %s

                SUMMARY:
                %s

                ASSESSMENT RULES:
                - Generate exactly 10 questions.
                - Each question must have exactly 4 choices.
                - Choices must be labeled A, B, C, and D.
                - Only one answer may be correct.
                - Questions must test understanding of the lesson.
                - Include a mixture of basic knowledge, understanding,
                  application, and simple scenario-based questions.
                - Do not ask about information that is not contained
                  in the provided lesson.
                - Avoid trick questions.
                - Avoid duplicate questions.
                - Keep questions clear and appropriate for students.
                - Do not make the correct answer obviously longer
                  than the other choices.
                - The correct answer must be exactly A, B, C, or D.

                IMPORTANT OUTPUT RULE:
                Return ONLY valid JSON.
                Do NOT use Markdown.
                Do NOT use ```json.
                Do NOT include explanations before or after the JSON.

                The JSON must follow exactly this structure:

                {
                  "questions": [
                    {
                      "questionNumber": 1,
                      "question": "Question text",
                      "optionA": "Choice A",
                      "optionB": "Choice B",
                      "optionC": "Choice C",
                      "optionD": "Choice D",
                      "correctAnswer": "A"
                    }
                  ]
                }
                """.formatted(
                lessonText,
                objectives,
                knowledge,
                examples,
                summary
        );

        String response;
        try {
            response = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
        } catch (RuntimeException error) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "QUIZ_GENERATION_FAILED");
        }

        if (response == null || response.isBlank()) {
            throw GeneratedAssessmentValidator.invalid();
        }

        try {

            // Check JSON types before DTO binding can coerce numbers/booleans to strings
            // or truncate a fractional question number. Do not change the shared mapper.
            JsonNode root = objectMapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readValue(response);
            if (root == null || !root.isObject() || root.size() != 1
                    || !root.path("questions").isArray() || root.path("questions").size() != 10)
                throw GeneratedAssessmentValidator.invalid();
            for (JsonNode question : root.path("questions")) {
                if (!question.isObject() || question.size() != 7
                        || !question.path("questionNumber").isIntegralNumber()
                        || !question.path("questionNumber").canConvertToInt())
                    throw GeneratedAssessmentValidator.invalid();
                for (String field : java.util.List.of("question", "optionA", "optionB", "optionC", "optionD", "correctAnswer"))
                    if (!question.path(field).isTextual()) throw GeneratedAssessmentValidator.invalid();
            }
            var generated = objectMapper.treeToValue(root, GeneratedAssessment.class);
            GeneratedAssessmentValidator.validate(generated);
            return generated;

        } catch (JsonProcessingException e) {

            throw GeneratedAssessmentValidator.invalid();
        }
    }
}
