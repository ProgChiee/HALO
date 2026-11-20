package com.ptc.halo.service;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Standalone child JVM: no Spring initialization, database, OCR or AI clients. */
public final class ObtlWorker {
    public static void main(String[] args) {
        // Reserve the original stream for the protocol before PDFBox/logging initializes.
        // Cold font-cache diagnostics otherwise precede JSON on stdout. Never forward
        // incidental library output to logs: it may contain private document details.
        var protocol=System.out;
        System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        try {
            byte[] bytes=System.in.readNBytes(3*1024*1024+1);
            var result=ObtlPdfText.extract(bytes, "");
            new ObjectMapper().writeValue(protocol,result);
        } catch(Throwable failure) {
            // No document contents, paths or exception details cross the process boundary.
            System.err.println("SOURCE_EXTRACTION_FAILED");
            System.exit(2);
        }
    }
}
