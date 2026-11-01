package com.ptc.halo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Deletion is conservative: unreadable references retain bytes for recovery. */
@Service
public class LessonMaterialRetention {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LessonMaterialStorage storage;
    public LessonMaterialRetention(JdbcTemplate jdbc,ObjectMapper json,LessonMaterialStorage storage) {
        this.jdbc=jdbc;this.json=json;this.storage=storage;
    }
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public boolean referenced(String key) {
        var found=new java.util.concurrent.atomic.AtomicBoolean();
        jdbc.query("select file_path from ai_learning_files",(org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            if(matches(key,rs.getString(1))) found.set(true);
        });
        for(String table:java.util.List.of("lesson_revisions","lesson_revision_history")) {
            jdbc.query("select payload from "+table,(org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                try {
                    var files=json.readTree(rs.getString(1)).path("files");
                    if(!files.isArray()) {found.set(true);return;}
                    for(var file:files) if(matches(key,file.path("filePath").asText(null))) found.set(true);
                } catch(Exception error) {found.set(true);}
            });
        }
        return found.get();
    }
    private boolean matches(String key,String path) {
        try {return storage.key(path).equals(key);} catch(RuntimeException error){return true;}
    }
}
