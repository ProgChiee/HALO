package com.ptc.halo.service;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.AiLearningFileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LessonAttachmentService {
    public record Content(String name,String type,byte[] bytes) {}
    private final AiLearningFileRepository files;
    private final LessonMaterialStorage storage;
    private final StudentLearningProgressionService progression;
    public LessonAttachmentService(AiLearningFileRepository files,LessonMaterialStorage storage,StudentLearningProgressionService progression) {
        this.files=files;this.storage=storage;this.progression=progression;
    }
    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public Content student(Long id,UserEntity student) {
        var file=files.findById(id).orElseThrow(this::missing);
        progression.validateModuleAccess(file.getModule().getId(),student);
        var content=read(file);
        // Remote fetches can be slow; recheck current access before returning bytes.
        progression.validateModuleAccess(file.getModule().getId(),student);
        return content;
    }
    @Transactional(readOnly=true)
    public Content professor(Long id,UserEntity professor) {
        if(professor==null || professor.getRole()!=Role.PROFESSOR || professor.getStatus()!=Status.ACTIVE)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"INVALID_ROLE_OR_ACCESS");
        return read(files.findByIdAndModule_Week_Subject_Professor_User_Id(id,professor.getId()).orElseThrow(this::missing));
    }
    private Content read(AiLearningFileEntity file) {
        if(file.getFileType()==null || !java.util.Set.of("application/pdf","image/png","image/jpeg").contains(file.getFileType()))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"MATERIAL_TYPE_UNSUPPORTED");
        try {return new Content(safeName(file.getOriginalFileName()),file.getFileType(),storage.read(file.getFilePath()));}
        catch(java.io.FileNotFoundException error){throw missing();}
        catch(java.io.IOException | IllegalArgumentException error){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"MATERIAL_UNAVAILABLE");}
    }
    public static String safeName(String name) {
        String value=java.util.Objects.toString(name,"learning-material").replace('\\','/');
        value=value.substring(value.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}\\\";]","_").strip();
        return value.isBlank()?"learning-material":value.substring(0,Math.min(value.length(),160));
    }
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"MATERIAL_NOT_FOUND");}
}
