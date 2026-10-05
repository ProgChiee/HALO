package com.ptc.halo.dtoRequest;

public class MentorMessageRequest {

    private String message;
    private String requestId;
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public MentorMessageRequest() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
