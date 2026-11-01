package com.ptc.halo.service;

import java.io.IOException;

/** Private originals only. Keys are server-owned references, never browser URLs. */
public interface LessonMaterialStorage {
    int MAX_BYTES = 3 * 1024 * 1024;
    String key(String reference);
    byte[] read(String reference) throws IOException;
    void writeNew(String key, byte[] bytes, String mediaType) throws IOException;
    void delete(String reference) throws IOException;
}
