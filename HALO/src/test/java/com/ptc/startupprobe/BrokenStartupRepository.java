package com.ptc.startupprobe;

/** Intentionally invalid query: startup must reject it before readiness. */
public interface BrokenStartupRepository extends org.springframework.data.jpa.repository.JpaRepository<StartupProbe, Long> {
    @org.springframework.data.jpa.repository.Query("select p.nonexistentProperty from StartupProbe p")
    java.util.List<String> invalidQuery();
}
