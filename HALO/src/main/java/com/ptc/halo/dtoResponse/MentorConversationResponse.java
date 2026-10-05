package com.ptc.halo.dtoResponse;

import java.util.List;

public class MentorConversationResponse {

    private Long sessionId;
    private Long moduleId;
    private List<MentorMessageResponse> messages;
    private boolean hasOlder;
    private Long nextBeforeId;
    public boolean isHasOlder() { return hasOlder; }
    public void setHasOlder(boolean hasOlder) { this.hasOlder = hasOlder; }
    public Long getNextBeforeId() { return nextBeforeId; }
    public void setNextBeforeId(Long nextBeforeId) { this.nextBeforeId = nextBeforeId; }

    public MentorConversationResponse() {
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public List<MentorMessageResponse> getMessages() {
        return messages;
    }

    public void setMessages(
            List<MentorMessageResponse> messages) {

        this.messages = messages;
    }
}
