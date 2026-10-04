package com.ptc.halo.dtoRequest;
import com.ptc.halo.enums.Status;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
public record AdminStatusRequest(@NotNull Status status) {
    @AssertTrue(message = "status must be ACTIVE or INACTIVE") @JsonIgnore
    public boolean isSupportedStatus() { return status == null || status == Status.ACTIVE || status == Status.INACTIVE; }
}
