package com.ptc.halo.service;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.SubjectRepository;
import java.io.*;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@Transactional(readOnly=true)
public class SubjectCoverService {
    private final SubjectRepository subjects;
    private final SubjectCoverStorage storage;
    private final StudentEnrollmentService enrollment;
    private final ActivityLogService audit;
    public SubjectCoverService(SubjectRepository subjects,SubjectCoverStorage storage,StudentEnrollmentService enrollment,ActivityLogService audit){this.subjects=subjects;this.storage=storage;this.enrollment=enrollment;this.audit=audit;}
    private void professor(UserEntity actor){if(actor==null || actor.getRole()!=Role.PROFESSOR || actor.getStatus()!=Status.ACTIVE)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"PROFESSOR_REQUIRED");}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"SUBJECT_UNAVAILABLE");}
    private byte[] bytes(long id){try{return storage.read(id);}catch(IOException failure){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"COVER_UNAVAILABLE");}}
    public byte[] professorCover(UserEntity actor,long id){professor(actor);subjects.findByIdAndProfessor_User_Id(id,actor.getId()).orElseThrow(this::missing);return bytes(id);}
    public byte[] studentCover(UserEntity student,long id){
        // Exactly the existing management/Browse visibility; a cover never grants lesson access.
        boolean visible=enrollment.list(student,true).stream().anyMatch(s->s.getSubjectId()==id);
        if(!visible)visible=enrollment.list(student,false).stream().anyMatch(s->s.getSubjectId()==id);
        if(!visible)throw missing();
        return bytes(id);
    }
    @Transactional
    public void change(UserEntity actor,long id,MultipartFile upload,AdminActingSessionEntity acting){
        professor(actor);
        subjects.findCoverForUpdate(id,actor.getId()).orElseThrow(this::missing);
        final Path staged;
        try{staged=upload==null?null:storage.prepare(upload);}catch(IOException failure){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"COVER_STORAGE_UNAVAILABLE");}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){
                try{if(staged==null)storage.remove(id);else storage.install(id,staged);}
                catch(IOException failure){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"COVER_STORAGE_UNAVAILABLE");}
            }
            @Override public void afterCompletion(int status){if(staged!=null)storage.discard(staged);}
        });
        String action="Requested subject cover "+(upload==null?"removal":"replacement")+" for subject ID "+id;
        if(acting==null)audit.createLog(actor,ActivityType.MODULE,action);else audit.createActingLog(acting,ActivityType.MODULE,action);
    }
}
