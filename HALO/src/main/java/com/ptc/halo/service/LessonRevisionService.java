package com.ptc.halo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptc.halo.component.ModuleMaterialIndex;
import com.ptc.halo.dtoRequest.AiLearningModuleUpdateRequest;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.util.*;

/** Called inside the normal Professor/acting authorization transaction, except AI preparation. */
@Service
public class LessonRevisionService {
 private final LessonRevisionRepository revisions;
 private final LessonRevisionHistoryRepository history;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
 private final AiLearningModuleRepository modules;
 private final ProfessorModuleService professorModules;
 private final FileUploadService uploads;
 private final AiGenerationService generation;
 private final ModuleMaterialIndex index;
 private final ActivityLogService audit;
 private final ObjectMapper json;
 private final AssessmentRepository quizzes;private final AssessmentContentChange changes;
 public LessonRevisionService(LessonRevisionRepository revisions,AiLearningModuleRepository modules,ProfessorModuleService professorModules,
  FileUploadService uploads,AiGenerationService generation,ModuleMaterialIndex index,ActivityLogService audit,ObjectMapper json,AssessmentRepository quizzes,AssessmentContentChange changes,LessonRevisionHistoryRepository history) {
  this.revisions=revisions;this.modules=modules;this.professorModules=professorModules;this.uploads=uploads;this.generation=generation;this.index=index;this.audit=audit;this.json=json;this.quizzes=quizzes;this.changes=changes;
  this.history=history;
 }
 public record Attachment(String key,Long fileId,String originalFileName,String storedFileName,String filePath,String fileType,boolean publishedSource) {}
 public record Payload(String lessonText,String youtubeLink,String aiNotes,List<Attachment> files,String objectives,String knowledge,String examples,String summary,String fingerprint) {}
 public record FileView(String key,String originalFileName) {}
 public record View(Long version,int revisionNumber,String state,Long baseVersion,Long revisedBy,LocalDateTime revisedAt,LocalDateTime publishedAt,
  String lessonText,String youtubeLink,String aiNotes,List<FileView> files,String generatedObjectives,String generatedKnowledge,String generatedExamples,String generatedSummary) {}
 public record Prepared(Long revisionVersion,Long moduleVersion,AiLearningModuleEntity candidate,String previousContent,boolean filesChanged) {
  public Prepared(Long revisionVersion,Long moduleVersion,AiLearningModuleEntity candidate){this(revisionVersion,moduleVersion,candidate,"",true);}
 }
 private String assessed(AiLearningModuleEntity m){return String.join("\n",Objects.toString(m.getLessonText(),""),Objects.toString(m.getGeneratedObjectives(),""),Objects.toString(m.getGeneratedKnowledge(),""),Objects.toString(m.getGeneratedExamples(),""),Objects.toString(m.getGeneratedSummary(),""));}
 public boolean assessmentChanged(Prepared prepared){return prepared.filesChanged()||changes.changed(prepared.previousContent(),assessed(prepared.candidate()));}
 private ResponseStatusException conflict(String code){return new ResponseStatusException(HttpStatus.CONFLICT,code);}
 private AiLearningModuleEntity live(Long id,UserEntity professor,boolean lock) {
  professorModules.findModule(id,professor); // ownership before locking
  var m=lock?modules.findForMentorOpenById(id).orElseThrow():professorModules.findModule(id,professor);
  if(m.getStatus()!=LessonStatus.APPROVED)throw conflict("REVISION_REQUIRES_PUBLISHED");
  m.getFiles().size();m.getWeek().getSubject().getSubjectName();
  return m;
 }
 private Payload payload(LessonRevisionEntity r){try{return json.readValue(r.payload,Payload.class);}catch(Exception e){throw new IllegalStateException("Revision unavailable",e);}}
 private void payload(LessonRevisionEntity r,Payload p){try{r.payload=json.writeValueAsString(p);}catch(Exception e){throw new IllegalStateException("Revision unavailable",e);}}
 private Payload publishedPayload(AiLearningModuleEntity m){
  return new Payload(m.getLessonText(),m.getYoutubeLink(),m.getAiNotes(),m.getFiles().stream().map(f->new Attachment(UUID.randomUUID().toString(),f.getId(),f.getOriginalFileName(),f.getStoredFileName(),f.getFilePath(),f.getFileType(),true)).toList(),m.getGeneratedObjectives(),m.getGeneratedKnowledge(),m.getGeneratedExamples(),m.getGeneratedSummary(),null);
 }
 private void archivePublished(LessonRevisionEntity r,Long publishedModuleVersion){
  if(!"PUBLISHED".equals(r.state)||history.existsByModuleIdAndRevisionNumber(r.moduleId,r.revisionNumber))return;
  entityManager.persist(new LessonRevisionHistoryEntity(r.moduleId,r.revisionNumber,"REVISION",r.version,r.baseVersion,publishedModuleVersion,r.payload,r.revisedBy,r.revisedAt,r.publishedBy,r.publishedAt));
 }
 private void archiveBaseline(AiLearningModuleEntity m,String kind){
  if(history.existsByModuleId(m.getId()))return;
  var baseline=new LessonRevisionEntity();payload(baseline,publishedPayload(m));
  // Zero is reserved for a captured live baseline; it never claims to recover a lost revision number/date.
  entityManager.persist(new LessonRevisionHistoryEntity(m.getId(),0,kind,null,null,m.getVersion(),baseline.payload,null,null,null,null));
 }
 public record HistorySummary(int revisionNumber,String snapshotKind,Long revisionVersion,Long baseVersion,Long publishedModuleVersion,
  Long revisedBy,LocalDateTime revisedAt,Long publishedBy,LocalDateTime publishedAt,LocalDateTime recordedAt){}
 public record HistoryContent(HistorySummary metadata,String lessonText,String youtubeLink,String aiNotes,List<FileView> files,
  String generatedObjectives,String generatedKnowledge,String generatedExamples,String generatedSummary){}
 private HistorySummary historySummary(LessonRevisionHistoryEntity h){return new HistorySummary(h.getRevisionNumber(),h.getSnapshotKind(),h.getRevisionVersion(),h.getBaseVersion(),h.getPublishedModuleVersion(),h.getRevisedBy(),h.getRevisedAt(),h.getPublishedBy(),h.getPublishedAt(),h.getRecordedAt());}
 public org.springframework.data.domain.Page<HistorySummary> history(Long id,UserEntity p,int page,int size){
  professorModules.findModule(id,p);
  if(page<0||size<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION");
  return history.findByModuleIdOrderByRevisionNumberDesc(id,org.springframework.data.domain.PageRequest.of(page,Math.min(size,100))).map(this::historySummary);
 }
 public HistoryContent historyVersion(Long id,int revisionNumber,UserEntity p){
  professorModules.findModule(id,p);
  var h=history.findByModuleIdAndRevisionNumber(id,revisionNumber).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"));
  try{var content=json.readValue(h.getPayload(),Payload.class);return new HistoryContent(historySummary(h),content.lessonText(),content.youtubeLink(),content.aiNotes(),content.files().stream().map(f->new FileView(f.key(),f.originalFileName())).toList(),content.objectives(),content.knowledge(),content.examples(),content.summary());}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Revision unavailable",e);}
 }
 private View view(LessonRevisionEntity r){var p=payload(r);return new View(r.version,r.revisionNumber,r.state,r.baseVersion,r.revisedBy,r.revisedAt,r.publishedAt,p.lessonText(),p.youtubeLink(),p.aiNotes(),p.files().stream().map(f->new FileView(f.key(),f.originalFileName())).toList(),p.objectives(),p.knowledge(),p.examples(),p.summary());}
 private LessonRevisionEntity draft(AiLearningModuleEntity m,Long version) {
  var r=editableDraft(m,version);
  if(!Objects.equals(r.baseVersion,m.getVersion()))throw conflict("STALE_MODULE_OPERATION");
  return r;
 }
 private LessonRevisionEntity editableDraft(AiLearningModuleEntity m,Long version) {
  var r=revisions.findById(m.getId()).orElseThrow(()->conflict("REVISION_NOT_FOUND"));
  if(!Set.of("DRAFT","REVIEWED").contains(r.state))throw conflict("REVISION_NOT_FOUND");
  if(!Objects.equals(r.version,version))throw conflict("STALE_MODULE_OPERATION");
  return r;
 }
 private Long actor(UserEntity p,AdminActingSessionEntity a){return a==null?p.getId():a.getAdmin().getId();}
 private void log(UserEntity p,AdminActingSessionEntity a,String action,AiLearningModuleEntity m){
  String text=action+" for Week "+m.getWeek().getWeekNumber()+" (module "+m.getId()+")";
  if(a==null)audit.createLog(p,ActivityType.MODULE,text);else audit.createProfessorLog(p,a,ActivityType.MODULE,text);
 }
 public View get(Long id,UserEntity p){live(id,p,false);return revisions.findById(id).filter(r->Set.of("DRAFT","REVIEWED").contains(r.state)).map(this::view).orElse(null);}
 public View start(Long id,UserEntity p,AdminActingSessionEntity a){
  var m=live(id,p,true);var r=revisions.findById(id).orElseGet(LessonRevisionEntity::new);
  // Preserve an intact legacy published row before reusing the working draft slot.
  archivePublished(r,null);
  archiveBaseline(m,r.moduleId==null?"INITIAL_PUBLISHED":"LEGACY_LIVE_BASELINE");
  if(Set.of("DRAFT","REVIEWED").contains(r.state==null?"":r.state))return view(r);
  r.moduleId=id;r.baseVersion=m.getVersion();r.revisionNumber++;r.state="DRAFT";r.revisedBy=actor(p,a);r.revisedAt=LocalDateTime.now();
  r.publishedBy=null;r.publishedAt=null;
  payload(r,publishedPayload(m));
  r=revisions.saveAndFlush(r);log(p,a,"Started lesson revision",m);return view(r);
 }
 public View save(Long id,Long version,AiLearningModuleUpdateRequest values,List<String> keep,List<MultipartFile> files,UserEntity p,AdminActingSessionEntity a) throws Exception {
  var m=live(id,p,true);var r=draft(m,version);var previous=payload(r);
  if(keep==null||new HashSet<>(keep).size()!=keep.size()||!previous.files().stream().map(Attachment::key).toList().containsAll(keep))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST");
  String youtube=YoutubeVideoUrl.normalize(values.getYoutubeLink());var validated=uploads.validateFiles(files);
  var selected=new ArrayList<>(previous.files().stream().filter(f->keep.contains(f.key())).toList());uploads.checkFileCount(selected.size(),validated.size());
  for(var file:validated){var f=uploads.uploadValidated(file,null);selected.add(new Attachment(UUID.randomUUID().toString(),-Math.abs(UUID.randomUUID().getMostSignificantBits()),f.getOriginalFileName(),f.getStoredFileName(),f.getFilePath(),f.getFileType(),false));}
  if(selected.isEmpty())throw conflict("MODULE_MATERIALS_REQUIRED");
  for(var f:previous.files())if(!keep.contains(f.key())&&!f.publishedSource())uploads.deleteFile(f.filePath());
  boolean sameSources=selected.equals(previous.files())&&AssessmentContentChange.normalize(values.getLessonText()).equals(AssessmentContentChange.normalize(previous.lessonText()))&&Objects.equals(values.getAiNotes(),previous.aiNotes());
  payload(r,new Payload(values.getLessonText(),youtube,values.getAiNotes(),selected,sameSources?previous.objectives():null,sameSources?previous.knowledge():null,sameSources?previous.examples():null,sameSources?previous.summary():null,null));
  r.state="DRAFT";r.revisedBy=actor(p,a);r.revisedAt=LocalDateTime.now();revisions.saveAndFlush(r);log(p,a,"Saved lesson revision",m);return view(r);
 }
 public void discard(Long id,Long version,UserEntity p,AdminActingSessionEntity a) throws Exception {
  // Discard never writes live materials. Keep ownership, module lock and draft edit
  // token checks, but allow removing a draft whose published baseline is now stale.
  var m=live(id,p,true);var r=editableDraft(m,version);
  for(var f:payload(r).files())if(!f.publishedSource())uploads.deleteFile(f.filePath());
  r.state="DISCARDED";r.payload="{}";revisions.saveAndFlush(r);log(p,a,"Discarded lesson revision",m);
 }
 private AiLearningModuleEntity candidate(AiLearningModuleEntity m,Payload p){
  var c=new AiLearningModuleEntity();c.setId(m.getId());c.setWeek(m.getWeek());c.setStatus(LessonStatus.PENDING);
  c.setLessonText(p.lessonText());c.setYoutubeLink(p.youtubeLink());c.setAiNotes(p.aiNotes());
  c.setGeneratedObjectives(p.objectives());c.setGeneratedKnowledge(p.knowledge());c.setGeneratedExamples(p.examples());c.setGeneratedSummary(p.summary());
  for(var f:p.files()){var file=new AiLearningFileEntity();file.setId(f.fileId());file.setOriginalFileName(f.originalFileName());file.setStoredFileName(f.storedFileName());file.setFilePath(f.filePath());file.setFileType(f.fileType());c.addFile(file);}return c;
 }
 public Prepared prepare(Long id,Long version,UserEntity p,boolean publishing){
  var m=live(id,p,false);var r=draft(m,version);if(publishing&&!r.state.equals("REVIEWED"))throw conflict("REVISION_REVIEW_REQUIRED");
  var content=payload(r);var candidate=candidate(m,content);
  boolean filesChanged=content.files().stream().anyMatch(f->!f.publishedSource())||!new HashSet<>(content.files().stream().map(Attachment::fileId).toList()).equals(new HashSet<>(m.getFiles().stream().map(AiLearningFileEntity::getId).toList()));
  return new Prepared(r.version,m.getVersion(),candidate,assessed(m),filesChanged);
 }
 // No database transaction or acting-session lock is held during extraction/provider calls.
 public Prepared generate(Prepared prepared){
  // A supporting URL/formatting-only edit keeps the already approved generated content.
  if(!prepared.filesChanged()&&AssessmentContentChange.normalize(prepared.previousContent()).equals(AssessmentContentChange.normalize(assessed(prepared.candidate()))))return prepared;
  var c=generation.generateRevision(prepared.candidate());
  if(c.getAiGenerationStatus()!=AiGenerationStatus.COMPLETED)throw generation.generationFailed();
  return new Prepared(prepared.revisionVersion(),prepared.moduleVersion(),c,prepared.previousContent(),prepared.filesChanged());
 }
 public View reviewed(Long id,Prepared prepared,UserEntity p,AdminActingSessionEntity a,String fingerprint){
  var m=live(id,p,true);var r=draft(m,prepared.revisionVersion());var old=payload(r);var c=prepared.candidate();
  payload(r,new Payload(old.lessonText(),old.youtubeLink(),old.aiNotes(),old.files(),c.getGeneratedObjectives(),c.getGeneratedKnowledge(),c.getGeneratedExamples(),c.getGeneratedSummary(),fingerprint));
  r.state="REVIEWED";r.revisedBy=actor(p,a);r.revisedAt=LocalDateTime.now();revisions.saveAndFlush(r);log(p,a,"Reviewed lesson revision",m);return view(r);
 }
 public String fingerprint(Prepared prepared){return index.buildRevision(prepared.candidate()).fingerprint();}
 public void publish(Long id,Prepared prepared,String fingerprint,UserEntity p,AdminActingSessionEntity a){publish(id,prepared,fingerprint,p,a,true);}
 public void publish(Long id,Prepared prepared,String fingerprint,UserEntity p,AdminActingSessionEntity a,boolean assessmentChanged){
  var m=live(id,p,true);var r=draft(m,prepared.revisionVersion());var content=payload(r);
  if(!r.state.equals("REVIEWED")||!Objects.equals(content.fingerprint(),fingerprint))throw conflict("REVISION_REVIEW_REQUIRED");
  // Preserve still-live legacy content before activation without inventing lost revision metadata.
  archiveBaseline(m,"LEGACY_LIVE_BASELINE");
  if(assessmentChanged&&quizzes.findByModuleId(id).isPresent())m.setAssessmentNeedsReview(true);
  m.setLessonText(content.lessonText());m.setYoutubeLink(content.youtubeLink());m.setAiNotes(content.aiNotes());
  m.setGeneratedObjectives(content.objectives());m.setGeneratedKnowledge(content.knowledge());m.setGeneratedExamples(content.examples());m.setGeneratedSummary(content.summary());
  m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);m.setGenerationToken(UUID.randomUUID().toString());
  // Preserve unchanged file IDs; only remove associations absent from the approved revision.
  var keep=content.files().stream().filter(Attachment::publishedSource).map(Attachment::fileId).toList();
  for(var f:new ArrayList<>(m.getFiles()))if(!keep.contains(f.getId()))m.removeFile(f);
  for(var f:content.files())if(!f.publishedSource()){var file=new AiLearningFileEntity();file.setOriginalFileName(f.originalFileName());file.setStoredFileName(f.storedFileName());file.setFilePath(f.filePath());file.setFileType(f.fileType());m.addFile(file);}
  modules.saveAndFlush(m);r.state="PUBLISHED";r.publishedBy=actor(p,a);r.publishedAt=LocalDateTime.now();revisions.saveAndFlush(r);
  // Same transaction as activation: history and live content commit or roll back together.
  archivePublished(r,m.getVersion());entityManager.flush();
  log(p,a,"Published revised lesson",m);
  // Snapshot initialized data before the transaction closes. Fingerprints also prevent stale reuse on races.
  var snapshot=new Payload(m.getLessonText(),m.getYoutubeLink(),m.getAiNotes(),m.getFiles().stream().map(f->new Attachment("",f.getId(),f.getOriginalFileName(),f.getStoredFileName(),f.getFilePath(),f.getFileType(),true)).toList(),null,null,null,null,null);
  var published=candidate(m,snapshot);
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){
   try{index.invalidate(id);index.getOrBuild(published);}catch(Exception error){org.slf4j.LoggerFactory.getLogger(LessonRevisionService.class).error("revision_index_rebuild_failed moduleId={} exceptionType={}",id,error.getClass().getSimpleName());}
  }});
 }
}
