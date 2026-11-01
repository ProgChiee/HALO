package com.ptc.halo.config;

@org.springframework.context.annotation.Configuration
public class LessonStorageConfiguration {
    public LessonStorageConfiguration(@org.springframework.beans.factory.annotation.Value("${halo.lesson-storage.provider:local}") String provider) {
        if (!java.util.Set.of("local","supabase").contains(provider)) throw new IllegalArgumentException("Unknown lesson storage provider");
    }
}
