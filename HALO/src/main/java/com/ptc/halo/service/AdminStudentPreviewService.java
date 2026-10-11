package com.ptc.halo.service;

import com.ptc.halo.dtoRequest.StudentAnswerRequest;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Bounded, process-local preview capabilities. No Student history write dependencies. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentPreviewService {
    private final AdminActingSessionService authorization;
    private final StudentProfileRepository profiles;
    private final AssessmentService assessments;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questions;
    private final StudentLearningProgressionService progression;
    private final MentorService mentor;
    private final TransactionTemplate reads;
    private final TransactionTemplate lifecycle;
    private final Map<String, Preview> previews = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    private static final class Preview {
        final AdminActingSessionEntity context;
        final ReentrantLock lock = new ReentrantLock();
        final Map<Long, Quiz> quizzes = new LinkedHashMap<>();
        final Map<Long, Chat> chats = new LinkedHashMap<>();
        Preview(AdminActingSessionEntity context) { this.context = context; }
    }
    private static final class Quiz {
        final long id, moduleId, assessmentId;
        final List<String> signature;
        final LocalDateTime started = LocalDateTime.now();
        LocalDateTime submitted;
        PreviewAssessmentResult result;
        Quiz(long id, long moduleId, long assessmentId, List<String> signature) {
            this.id=id; this.moduleId=moduleId; this.assessmentId=assessmentId; this.signature=signature;
        }
    }
    private static final class Chat {
        final long id, moduleId;
        final List<MentorMessageResponse> messages = new ArrayList<>();
        final Map<String, Exchange> exchanges = new LinkedHashMap<>();
        Chat(long id, long moduleId) { this.id=id; this.moduleId=moduleId; }
    }
    private record Exchange(String question, MentorSessionResponse response) {}

    public AdminStudentPreviewService(AdminActingSessionService authorization, StudentProfileRepository profiles,
            AssessmentService assessments, AssessmentRepository assessmentRepository, AssessmentQuestionRepository questions,
            StudentLearningProgressionService progression, MentorService mentor, PlatformTransactionManager manager) {
        this.authorization=authorization; this.profiles=profiles; this.assessments=assessments;
        this.assessmentRepository=assessmentRepository; this.questions=questions; this.progression=progression; this.mentor=mentor;
        this.reads=new TransactionTemplate(manager); this.reads.setReadOnly(true);
        this.lifecycle=new TransactionTemplate(manager);
    }
    private ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
    private Preview preview(String id) {
        previews.entrySet().removeIf(e -> !e.getValue().context.getExpiresAt().isAfter(Instant.now()));
        var value=previews.get(id);
        if (value==null) throw new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID);
        return value;
    }
    private UserEntity student(Authentication auth, String id, Preview preview) {
        if (preview(id)!=preview) throw new AdminApiException(AdminApiException.Code.ACTING_SESSION_INVALID);
        var student=authorization.validateStudentPreview(auth, preview.context);
        profiles.findByUserId(student.getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"));
        return student;
    }
    public synchronized AdminActingSessionResponse create(Authentication auth, Long studentId) {
        previews.entrySet().removeIf(e -> !e.getValue().context.getExpiresAt().isAfter(Instant.now()));
        if (previews.size()>=100) throw error(HttpStatus.TOO_MANY_REQUESTS,"PREVIEW_LIMIT_REACHED");
        var context=lifecycle.execute(tx -> {
            var created=authorization.createStudentPreview(auth,studentId);
            profiles.findByUserId(created.getTarget().getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"));
            return created;
        });
        previews.put(context.getId(),new Preview(context));
        return validate(auth,context.getId());
    }
    public AdminActingSessionResponse validate(Authentication auth,String id) {
        var preview=preview(id);
        return read(auth,id,s -> new AdminActingSessionResponse(id,preview.context.getAdmin().getId(),
                new AdminActingTargetResponse(s.getId(),s.getName(),s.getEmail(),Role.STUDENT),
                preview.context.getCreatedAt(),preview.context.getExpiresAt()));
    }
    public void revoke(Authentication auth,String id) {
        var preview=preview(id);
        authorization.endStudentPreview(auth,preview.context);
        // Removal immediately invalidates in-flight external work at its final guard.
        previews.remove(id,preview);
    }
    public <T> T read(Authentication auth,String id,Function<UserEntity,T> work) {
        var preview=preview(id);
        return reads.execute(tx -> work.apply(student(auth,id,preview)));
    }
    private <T> T temporary(Authentication auth,String id,Function<Preview,T> work) {
        var preview=preview(id);
        read(auth,id,s -> null);
        if (!preview.lock.tryLock()) throw error(HttpStatus.CONFLICT,"PREVIEW_OPERATION_PENDING");
        try { return work.apply(preview); } finally { preview.lock.unlock(); }
    }
    public AssessmentStatusResponse status(Authentication auth,String id,Long moduleId) {
        return temporary(auth,id,p -> read(auth,id,s -> {
            var status=assessments.getAssessmentStatus(moduleId,s);
            // Practice is allowed for eligible published assessments, even if already completed in real history.
            status.setAlreadyPassed(false);
            status.setHasUnfinishedAttempt(p.quizzes.values().stream().anyMatch(q -> q.moduleId==moduleId && q.result==null));
            status.setCanTakeAssessment(Boolean.TRUE.equals(status.getAssessmentAvailable()));
            return status;
        }));
    }
    private AssessmentEntity available(Long moduleId,UserEntity student) {
        progression.validateModuleAccess(moduleId,student);
        var assessment=assessmentRepository.findByModuleId(moduleId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"ASSESSMENT_NOT_FOUND"));
        if (assessment.getStatus()!=AssessmentStatus.AVAILABLE) throw error(HttpStatus.CONFLICT,"ASSESSMENT_NOT_AVAILABLE");
        return assessment;
    }
    private List<String> signature(AssessmentEntity assessment,List<AssessmentQuestionEntity> questions) {
        var result=new ArrayList<String>(); result.add(assessment.getId()+":"+assessment.getPassingScore());
        for (var q:questions) result.add(Arrays.asList(q.getId(),q.getQuestionNumber(),q.getQuestionText(),q.getOptionA(),q.getOptionB(),q.getOptionC(),q.getOptionD(),q.getCorrectAnswer()).toString());
        return result;
    }
    public AssessmentAttemptResponse start(Authentication auth,String id,Long moduleId) {
        return temporary(auth,id,p -> read(auth,id,s -> {
            var assessment=available(moduleId,s);
            var quiz=p.quizzes.values().stream().filter(q -> q.moduleId==moduleId && q.result==null).findFirst().orElse(null);
            if (quiz==null) {
                if (p.quizzes.size()>=10) throw error(HttpStatus.CONFLICT,"PREVIEW_LIMIT_REACHED");
                quiz=new Quiz(ids.incrementAndGet(),moduleId,assessment.getId(),signature(assessment,questions.findByAssessmentIdOrderByQuestionNumberAsc(assessment.getId())));
                p.quizzes.put(quiz.id,quiz);
            }
            var result=new AssessmentAttemptResponse(); result.setAttemptId(quiz.id); result.setAssessmentId(quiz.assessmentId); result.setStartedAt(quiz.started); return result;
        }));
    }
    private Quiz quiz(Preview p,Long id) {
        var quiz=p.quizzes.get(id); if (quiz==null) throw error(HttpStatus.NOT_FOUND,"ATTEMPT_NOT_FOUND"); return quiz;
    }
    public PreviewAssessmentResult submit(Authentication auth,String id,Long attemptId,StudentAnswerRequest request) {
        return temporary(auth,id,p -> read(auth,id,s -> {
            var quiz=quiz(p,attemptId); var assessment=available(quiz.moduleId,s);
            if (quiz.result!=null) return quiz.result;
            var expected=questions.findByAssessmentIdOrderByQuestionNumberAsc(assessment.getId());
            if (!quiz.signature.equals(signature(assessment,expected))) throw error(HttpStatus.CONFLICT,"PREVIEW_STATE_CHANGED");
            var submitted=AssessmentAnswerSet.validate(expected,request);
            int correct=0; var feedback=new ArrayList<AssessmentAnswerFeedbackResponse>();
            for (var q:expected) {
                boolean matches=q.getCorrectAnswer().equalsIgnoreCase(submitted.get(q.getId()));
                if (matches) correct++;
                var item=new AssessmentAnswerFeedbackResponse(); item.setQuestionId(q.getId()); item.setQuestionNumber(q.getQuestionNumber());
                item.setQuestionText(q.getQuestionText()); item.setStudentAnswer(submitted.get(q.getId())); item.setCorrectAnswer(q.getCorrectAnswer()); item.setCorrect(matches); feedback.add(item);
            }
            var result=new PreviewAssessmentResult(); result.setAttemptId(quiz.id); result.setCorrectCount(correct); result.setTotalQuestions(expected.size());
            result.setScore((int)Math.round(100.0*correct/expected.size())); result.setPassed(result.getScore()>=assessment.getPassingScore()); result.setFeedback(feedback);
            quiz.result=result; quiz.submitted=LocalDateTime.now(); return result;
        }));
    }
    public PreviewAssessmentResult result(Authentication auth,String id,Long attemptId) {
        return temporary(auth,id,p -> read(auth,id,s -> {
            var quiz=quiz(p,attemptId); available(quiz.moduleId,s);
            if (quiz.result==null) throw error(HttpStatus.CONFLICT,"ASSESSMENT_NOT_SUBMITTED"); return quiz.result;
        }));
    }
    public List<AssessmentAttemptHistoryResponse> history(Authentication auth,String id,Long moduleId) {
        return temporary(auth,id,p -> read(auth,id,s -> {
            available(moduleId,s);
            return p.quizzes.values().stream().filter(q -> q.moduleId==moduleId).map(q -> {
                var item=new AssessmentAttemptHistoryResponse(); item.setAttemptId(q.id); item.setStartedAt(q.started); item.setSubmittedAt(q.submitted);
                item.setScore(q.result==null?0:q.result.getScore()); item.setPassed(q.result!=null && q.result.getPassed()); return item;
            }).toList();
        }));
    }
    private Chat chat(Preview p,Long id) {
        var chat=p.chats.get(id); if (chat==null) throw error(HttpStatus.NOT_FOUND,"SESSION_NOT_FOUND"); return chat;
    }
    public MentorConversationResponse open(Authentication auth,String id,Long moduleId) {
        return temporary(auth,id,p -> {
            var student=read(auth,id,s -> s);
            mentor.preparePreview(moduleId,student,() -> read(auth,id,s -> null));
            return read(auth,id,s -> {
                var chat=p.chats.values().stream().filter(c -> c.moduleId==moduleId).findFirst().orElse(null);
                if (chat==null) {
                    if (p.chats.size()>=5) throw error(HttpStatus.CONFLICT,"PREVIEW_LIMIT_REACHED");
                    chat=new Chat(ids.incrementAndGet(),moduleId); p.chats.put(chat.id,chat);
                }
                return window(chat,null);
            });
        });
    }
    public MentorSessionResponse send(Authentication auth,String id,Long chatId,String message,String requestId) {
        return temporary(auth,id,p -> {
            var chat=chat(p,chatId);
            var student=read(auth,id,s -> { progression.validateModuleAccess(chat.moduleId,s); return s; });
            if (requestId==null || !requestId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST_ID");
            var key=requestId.toLowerCase(Locale.ROOT);
            var existing=chat.exchanges.get(key);
            if (existing!=null) {
                if (!Objects.equals(existing.question(),message)) throw error(HttpStatus.CONFLICT,"REQUEST_ID_REUSED");
                return existing.response();
            }
            // Bounded history and keys are retained together; keys are never evicted while this chat is usable.
            if (chat.exchanges.size()>=20) throw error(HttpStatus.CONFLICT,"PREVIEW_LIMIT_REACHED");
            var recent=chat.messages.subList(Math.max(0,chat.messages.size()-9),chat.messages.size());
            var history=new StringBuilder(); for(var m:recent) history.append(m.getSender()).append(": ").append(m.getMessage()).append("\n");
            var reply=mentor.answerPreview(chat.moduleId,student,message,history.toString(),() -> read(auth,id,s -> null));
            return read(auth,id,s -> {
                progression.validateModuleAccess(chat.moduleId,s);
                var response=new MentorSessionResponse(); response.setSessionId(chat.id); response.setModuleId(chat.moduleId); response.setHaloMessage(reply);
                chat.messages.add(message(MessageSender.STUDENT,message)); chat.messages.add(message(MessageSender.HALO,reply));
                chat.exchanges.put(key,new Exchange(message,response)); return response;
            });
        });
    }
    private MentorMessageResponse message(MessageSender sender,String text) {
        var item=new MentorMessageResponse(); item.setId(ids.incrementAndGet()); item.setSender(sender); item.setMessage(text); item.setCreatedAt(LocalDateTime.now()); return item;
    }
    public MentorSessionResponse exchange(Authentication auth,String id,Long chatId,String key) {
        return temporary(auth,id,p -> read(auth,id,s -> {
            var chat=chat(p,chatId); progression.validateModuleAccess(chat.moduleId,s);
            var exchange=chat.exchanges.get(key.toLowerCase(Locale.ROOT));
            if(exchange==null) throw error(HttpStatus.NOT_FOUND,"EXCHANGE_NOT_FOUND"); return exchange.response();
        }));
    }
    public MentorConversationResponse conversation(Authentication auth,String id,Long chatId,Long beforeId) {
        return temporary(auth,id,p -> read(auth,id,s -> {
            var chat=chat(p,chatId); progression.validateModuleAccess(chat.moduleId,s); return window(chat,beforeId);
        }));
    }
    private MentorConversationResponse window(Chat chat,Long beforeId) {
        var eligible=chat.messages.stream().filter(m -> beforeId==null || m.getId()<beforeId).toList();
        var messages=eligible.subList(Math.max(0,eligible.size()-30),eligible.size());
        var response=new MentorConversationResponse(); response.setSessionId(chat.id); response.setModuleId(chat.moduleId); response.setMessages(List.copyOf(messages));
        response.setHasOlder(eligible.size()>30); response.setNextBeforeId(response.isHasOlder()?messages.get(0).getId():null); return response;
    }
}
