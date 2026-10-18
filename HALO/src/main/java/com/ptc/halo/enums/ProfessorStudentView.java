package com.ptc.halo.enums;

import java.util.List;
/** Read-only monitoring populations; lifecycle transitions remain unchanged. */
public enum ProfessorStudentView {
    CURRENT, FORMER;
    public List<Status> statuses() { return this == CURRENT ? List.of(Status.ACTIVE) : List.of(Status.INACTIVE, Status.ARCHIVED); }
    public List<String> statusNames() { return statuses().stream().map(Enum::name).toList(); }
}
