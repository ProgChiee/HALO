package com.ptc.halo;
import com.ptc.halo.service.AssessmentContentChange;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AssessmentContentChangeTest {
 @Test void formattingOnlyChangesDoNotCallProvider(){var builder=mock(ChatClient.Builder.class);var client=mock(ChatClient.class);when(builder.build()).thenReturn(client);var check=new AssessmentContentChange(builder,new ObjectMapper());assertFalse(check.changed("# Guest care\n\nBe **kind**.","Guest care Be kind."));verifyNoInteractions(client);}
 @Test void semanticCorrectionsAreAllowedButMaterialChangesAndUncertainResponsesNeedReview(){
  var builder=mock(ChatClient.Builder.class);var client=mock(ChatClient.class,RETURNS_DEEP_STUBS);when(builder.build()).thenReturn(client);var check=new AssessmentContentChange(builder,new ObjectMapper());
  when(client.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("{\"assessmentChanged\":false}");assertFalse(check.changed("Greet the gest.","Greet the guest."));
  when(client.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("{\"assessmentChanged\":true}");assertTrue(check.changed("Guest care", "Food safety"));
  when(client.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("invalid");assertTrue(check.changed("Guest care", "Food safety"));
 }
}
