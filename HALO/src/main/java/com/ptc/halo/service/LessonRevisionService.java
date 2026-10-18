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
 private final AiLearningModuleRepository modules;
 private final ProfessorModuleService professorModules;
 private final FileUploadService uploads;
 private final AiGenerationService generation;
 private final ModuleMaterialIndex index;
 private final ActivityLogService audit;
 private final ObjectMapper json;
 private final AssessmentRepository quizzes;private final AssessmentContentChange changes;
 public LessonRevisionService(LessonRevisionRepository revisions,AiLearningModuleRepository modules,ProfessorModuleService professorModules,
  FileUploadService uploads,AiGenerationService generation,ModuleMaterialIndex index,ActivityLogService audit,ObjectMapper json,AssessmentRepository quizzes,AssessmentContentChange changes) {
  this.revisions=revisions;this.modules=modules;this.professorModules=professorModules;this.uploads=uploads;this.generation=generation;this.index=index;this.audit=audit;this.json=json;this.quizzes=quizzes;this.changes=changes;
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
 private View view(LessonRevisionEntity r){var p=payload(r);return new View(r.version,r.revisionNumber,r.state,r.baseVersion,r.revisedBy,r.revisedAt,r.publishedAt,p.lessonText(),p.youtubeLink(),p.aiNotes(),p.files().stream().map(f->new FileView(f.key(),f.originalFileName())).toList(),p.objectives(),p.knowledge(),p.examples(),p.summary());}
 private LessonRevisionEntity draft(AiLearningModuleEntity m,Long version) {
  var r=revisions.findById(m.getId()).orElseThrow(()->conflict("REVISION_NOT_FOUND"));
  if(!Set.of("DRAFT","REVIEWED").contains(r.state))throw conflict("REVISION_NOT_FOUND");
  if(!Objects.equals(r.version,version)||!Objects.equals(r.baseVersion,m.getVersion()))throw conflict("STALE_MODULE_OPERATION");
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
  if(Set.of("DRAFT","REVIEWED").contains(r.state==null?"":r.state))return view(r);
  r.moduleId=id;r.baseVersion=m.getVersion();r.revisionNumber++;r.state="DRAFT";r.revisedBy=actor(p,a);r.revisedAt=LocalDateTime.now();
  payload(r,new Payload(m.getLessonText(),m.getYoutubeLink(),m.getAiNotes(),m.getFiles().stream().map(f->new Attachment(UUID.randomUUID().toString(),f.getId(),f.getOriginalFileName(),f.getStoredFileName(),f.getFilePath(),f.getFileType(),true)).toList(),m.getGeneratedObjectives(),m.getGeneratedKnowledge(),m.getGeneratedExamples(),m.getGeneratedSummary(),null));
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
  var m=live(id,p,true);var r=draft(m,version);
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
  if(assessmentChanged&&quizzes.findByModuleId(id).isPresent())m.setAssessmentNeedsReview(true);
  m.setLessonText(content.lessonText());m.setYoutubeLink(content.youtubeLink());m.setAiNotes(content.aiNotes());
  m.setGeneratedObjectives(content.objectives());m.setGeneratedKnowledge(content.knowledge());m.setGeneratedExamples(content.examples());m.setGeneratedSummary(content.summary());
  m.setAiGenerationStatus(AiGenerationStatus.COMPLETED);m.setGenerationToken(UUID.randomUUID().toString());
  // Preserve unchanged file IDs; only remove associations absent from the approved revision.
  var keep=content.files().stream().filter(Attachment::publishedSource).map(Attachment::fileId).toList();
  for(var f:new ArrayList<>(m.getFiles()))if(!keep.contains(f.getId()))m.removeFile(f);
  for(var f:content.files())if(!f.publishedSource()){var file=new AiLearningFileEntity();file.setOriginalFileName(f.originalFileName());file.setStoredFileName(f.storedFileName());file.setFilePath(f.filePath());file.setFileType(f.fileType());m.addFile(file);}
  modules.saveAndFlush(m);r.state="PUBLISHED";r.publishedBy=actor(p,a);r.publishedAt=LocalDateTime.now();revisions.saveAndFlush(r);
  log(p,a,"Published revised lesson",m);
  // Snapshot initialized data before the transaction closes. Fingerprints also prevent stale reuse on races.
  var snapshot=new Payload(m.getLessonText(),m.getYoutubeLink(),m.getAiNotes(),m.getFiles().stream().map(f->new Attachment("",f.getId(),f.getOriginalFileName(),f.getStoredFileName(),f.getFilePath(),f.getFileType(),true)).toList(),null,null,null,null,null);
  var published=candidate(m,snapshot);
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){
   try{index.invalidate(id);index.getOrBuild(published);}catch(Exception error){org.slf4j.LoggerFactory.getLogger(LessonRevisionService.class).error("revision_index_rebuild_failed moduleId={} exceptionType={}",id,error.getClass().getSimpleName());}
  }});
 }
}
