package com.ptc.halo;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.dtoRequest.StudentAnswerRequest;
import com.ptc.halo.component.ModuleMaterialIndex;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@DataJpaTest(properties="spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",showSql=false)
@Import({QuizRevisionService.class,AssessmentService.class,ProfessorModuleService.class,ActivityLogService.class})
class QuizRevisionTest {
 @Autowired QuizRevisionService service;@Autowired AssessmentService assessmentService;
 @Autowired UserRepository users;@Autowired ProfessorRepository professors;@Autowired SubjectRepository subjects;@Autowired WeekRepository weeks;@Autowired AiLearningModuleRepository modules;
 @Autowired AssessmentRepository quizzes;@Autowired AssessmentAttemptRepository attempts;@Autowired StudentAnswerRepository answers;@Autowired StudentModuleProgressRepository progress;@Autowired ActivityLogRepository logs;
 @Autowired jakarta.persistence.EntityManager em;
 @MockBean AiGenerationService generation;@MockBean AssessmentAiService ai;@MockBean ModuleMaterialIndex index;@MockBean BadgeService badges;@MockBean StudentLearningProgressionService progression;@MockBean FileUploadService uploads;
 UserEntity professor,student;AiLearningModuleEntity module;AssessmentEntity original;
 @BeforeEach void setup(){
  professor=user("Professor",Role.PROFESSOR);student=user("Student",Role.STUDENT);
  var p=new ProfessorEntity();p.setUser(professor);p.setProfessorId("Q-P");professors.saveAndFlush(p);
  var s=new SubjectEntity();s.setProfessor(p);s.setSubjectCode("Q-S");s.setSubjectName("Hospitality");s.setYearLevel(YearLevel.FIRST_YEAR);subjects.saveAndFlush(s);
  var w=new WeekEntity();w.setSubject(s);w.setWeekNumber(1);w.setTitle("Guest service");weeks.saveAndFlush(w);
  module=new AiLearningModuleEntity();module.setWeek(w);module.setStatus(LessonStatus.APPROVED);module.setAiGenerationStatus(AiGenerationStatus.COMPLETED);module.setLessonText("Latest published guest care");module.setGeneratedKnowledge("Latest knowledge");module.setAssessmentNeedsReview(true);modules.saveAndFlush(module);
  original=new AssessmentEntity();original.setModule(module);original.setTitle("Original");original.setStatus(AssessmentStatus.AVAILABLE);original.setCreatedAt(LocalDateTime.now());original.setPassingScore(70);original.setSourceModuleVersion(module.getVersion());
  var q=new AssessmentQuestionEntity();q.setQuestionNumber(1);q.setQuestionText("Original question");q.setOptionA("A");q.setOptionB("B");q.setOptionC("C");q.setOptionD("D");q.setCorrectAnswer("A");original.addQuestion(q);quizzes.saveAndFlush(original);
  when(ai.generateAssessment(any(),any(),any(),any(),any())).thenReturn(generated());
 }
 UserEntity user(String name,Role role){var u=new UserEntity();u.setName(name);u.setEmail(name+"@quiz.test");u.setRole(role);u.setStatus(Status.ACTIVE);return users.saveAndFlush(u);}
 GeneratedAssessment generated(){var g=new GeneratedAssessment();var list=new ArrayList<GeneratedAssessmentQuestion>();for(int n=1;n<=10;n++){var q=new GeneratedAssessmentQuestion();q.setQuestionNumber(n);q.setQuestion("Latest question "+n);q.setOptionA("A");q.setOptionB("B");q.setOptionC("C");q.setOptionD("D");q.setCorrectAnswer("B");list.add(q);}g.setQuestions(list);return g;}
 QuizRevisionService.View draft(AdminActingSessionEntity a){var p=service.prepare(module.getId(),professor);return service.save(module.getId(),p,service.generate(p),professor,a);}
 @Test void generatedDraftUsesPublishedLessonAndOnlyApprovalSwitchesCurrent(){
  var view=draft(null);verify(ai).generateAssessment(eq("Latest published guest care"),isNull(),eq("Latest knowledge"),isNull(),isNull());verify(index).getOrBuild(module);
  assertEquals(original.getId(),quizzes.findByModuleId(module.getId()).orElseThrow().getId());assertEquals(2,view.draft().version());assertNull(view.draft().approvedAt());
  assertEquals(original.getId(),assessmentService.getAssessment(module.getId(),student).getId());
  var approved=service.approve(module.getId(),view.draft().id(),professor,null);
  assertNull(approved.draft());assertFalse(approved.needsReview());assertNotNull(approved.current().approvedAt());assertEquals(AssessmentStatus.RETIRED,original.getStatus());
  assertEquals(approved.current().id(),assessmentService.startAttempt(module.getId(),student).getAssessment().getId());
 }
 @Test void oldScoresAnswersAndCompletionSurviveWhileUnfinishedAttemptKeepsOriginalQuestions(){
  var completed=new AssessmentAttemptEntity();completed.setAssessment(original);completed.setStudent(student);completed.setStartedAt(LocalDateTime.now().minusDays(2));completed.setSubmittedAt(LocalDateTime.now().minusDays(2));completed.setScore(100);completed.setPassed(true);attempts.saveAndFlush(completed);
  var answer=new StudentAnswerEntity();answer.setAttempt(completed);answer.setQuestion(original.getQuestions().get(0));answer.setAnswer("A");answers.saveAndFlush(answer);
  var history=new StudentModuleProgressEntity();history.setStudent(student);history.setModule(module);history.setCompleted(true);history.setCompletedAt(LocalDateTime.now().minusDays(2));progress.saveAndFlush(history);var completedAt=history.getCompletedAt();
  var other=user("Other",Role.STUDENT);var unfinished=assessmentService.startAttempt(module.getId(),other);
  var draft=draft(null);service.approve(module.getId(),draft.draft().id(),professor,null);
  assertEquals(100,assessmentService.getAttemptResult(completed.getId(),student).getScore());assertEquals("Original question",assessmentService.getAttemptResult(completed.getId(),student).getFeedback().get(0).getQuestionText());
  assertEquals(1,answers.count());assertEquals(completedAt,history.getCompletedAt());assertTrue(history.getCompleted());assertEquals(original.getId(),completed.getAssessment().getId());
  assertEquals(draft.draft().id(),assessmentService.getCurrentAssessment(module.getId(),other).getId());assertEquals(original.getId(),assessmentService.getAssessment(module.getId(),other).getId());assertEquals(unfinished.getId(),assessmentService.startAttempt(module.getId(),other).getId());
  var request=new StudentAnswerRequest();var item=new StudentAnswerRequest.AnswerItem();item.setQuestionId(original.getQuestions().get(0).getId());item.setAnswer("A");request.setAnswers(List.of(item));
  assertEquals(100,assessmentService.submitAttempt(unfinished.getId(),other,request).getScore());assertEquals(original.getId(),unfinished.getAssessment().getId());
  assertEquals(draft.draft().id(),assessmentService.startAttempt(module.getId(),student).getAssessment().getId());
  assertEquals(2,assessmentService.getAttemptHistory(module.getId(),student).size());
  var newAttempt=assessmentService.startAttempt(module.getId(),student);var newAnswers=new StudentAnswerRequest();newAnswers.setAnswers(newAttempt.getAssessment().getQuestions().stream().map(q->{var i=new StudentAnswerRequest.AnswerItem();i.setQuestionId(q.getId());i.setAnswer("B");return i;}).toList());
  assessmentService.submitAttempt(newAttempt.getId(),student,newAnswers);assertEquals(completedAt,history.getCompletedAt());assertEquals(100,completed.getScore());
 }
 @Test void staleGenerationOrApprovalCannotReplaceAQuiz(){
  var captured=service.prepare(module.getId(),professor);module.setLessonText("Changed again");modules.saveAndFlush(module);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.save(module.getId(),captured,generated(),professor,null));
  var draft=draft(null);module.setLessonText("New publication");modules.saveAndFlush(module);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.approve(module.getId(),draft.draft().id(),professor,null));assertEquals(original.getId(),quizzes.findByModuleId(module.getId()).orElseThrow().getId());
 }
 @Test void invalidGenerationAndUnrelatedProfessorCannotWrite(){
  var captured=service.prepare(module.getId(),professor);var invalid=generated();invalid.getQuestions().get(0).setCorrectAnswer("Z");
  assertThrows(IllegalArgumentException.class,()->service.save(module.getId(),captured,invalid,professor,null));assertEquals(1,quizzes.count());
  assertThrows(RuntimeException.class,()->service.prepare(module.getId(),user("Unrelated",Role.PROFESSOR)));
 }
 @Test void realAdminIsActorForGenerationAndApproval(){
  var admin=user("Admin",Role.ADMIN);var session=new AdminActingSessionEntity(UUID.randomUUID().toString(),admin,professor,Instant.now(),Instant.now().plusSeconds(300));em.persist(session);em.flush();
  var draft=draft(session);service.approve(module.getId(),draft.draft().id(),professor,session);
  assertEquals(2,logs.count());for(var log:logs.findAll()){assertEquals(admin.getId(),log.getUser().getId());assertEquals(professor.getId(),log.getActingTarget().getId());assertEquals(session.getId(),log.getActingSession().getId());}
 }
}
