package com.ptc.halo.service;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ActivityDisplayTest {
 UserEntity user(long id,String name,Role role){var u=new UserEntity();u.setId(id);u.setName(name);u.setRole(role);u.setEmail("person@example.test");return u;}
 ActivityLogEntity log(String action,Role role){var l=new ActivityLogEntity();l.setUser(user(20,"Talidong",Role.ADMIN));l.setActingTarget(user(21,"Juan Dela Cruz",role));l.setActingTargetRole(role);l.setAction(action);return l;}
 @Test void previewAndSupportNamesDoNotRewriteAuditText(){
  for(var role:List.of(Role.PROFESSOR,Role.STUDENT))for(String phase:List.of("Started","Ended")){
   String label=ActivityDisplay.role(role.name());var l=log(phase+" "+label+" preview by Admin 20 viewing "+label+" 21",role);String raw=l.getAction();
   assertEquals("Talidong "+phase.toLowerCase()+" "+label+" Preview for Juan Dela Cruz",ActivityDisplay.format(l,Map.of(),Map.of()));assertEquals(raw,l.getAction());
  }
  for(var role:List.of(Role.PROFESSOR,Role.STUDENT))for(String phase:List.of("Started","Revoked")){
   var l=log(phase+" acting session by Admin 20 acting as "+role+" 21",role);
   assertEquals("Talidong "+(phase.equals("Started")?"entered ":"exited ")+(role==Role.PROFESSOR?"Professor Mode":"Student Support Mode")+" for Juan Dela Cruz",ActivityDisplay.format(l,Map.of(),Map.of()));
  }
 }
 @Test void snapshotsWinAndMissingAccountsHaveSafeFallbacks(){
  var l=log("Deleted Admin account ID 21: Historical Name",Role.ADMIN);
  assertEquals("Talidong deleted Admin account for Historical Name",ActivityDisplay.format(l,Map.of(21L,user(21,"New Name",Role.ADMIN)),Map.of()));
  l.setAction("Archived STUDENT account ID 21: ACTIVE -> ARCHIVED");assertEquals("Talidong archived Student account for Deleted Student",ActivityDisplay.format(l,Map.of(),Map.of()));
  l.setAction("Ended Professor preview by Admin 20 viewing Professor 21");l.setActingTarget(null);l.setActingTargetRole(Role.PROFESSOR);l.setUser(null);
  assertEquals("Deleted Admin ended Professor Preview for Deleted Professor",ActivityDisplay.format(l,Map.of(),Map.of()));
 }
 @Test void lifecycleAndUserIdsAreResolvedInBoundedBatches(){
  var repo=mock(UserRepository.class);var target=user(21,"Juan Dela Cruz",Role.STUDENT);
  when(repo.findAllById(any())).thenReturn(List.of(target));when(repo.findByEmailIn(any())).thenReturn(List.of(target));
  var logs=List.of(log("Archived STUDENT account ID 21: ACTIVE -> ARCHIVED",Role.STUDENT),log("Restored STUDENT account ID 21: ARCHIVED -> ACTIVE",Role.STUDENT),log("Changed Student status to INACTIVE: person@example.test",Role.STUDENT),log("Set Student status to ACTIVE: person@example.test",Role.STUDENT));
  var format=ActivityDisplay.prepare(logs,repo);
  for(int i=0;i<logs.size();i++)assertEquals("Talidong "+List.of("archived","restored","deactivated","activated").get(i)+" Student account for Juan Dela Cruz",format.apply(logs.get(i)));
  verify(repo,times(1)).findAllById(any());verify(repo,times(1)).findByEmailIn(any());verifyNoMoreInteractions(repo);
 }
 @Test void nullAndBlankNamesAndUnrecognizedEventsStayReadable(){
  var l=log("Opened lesson",Role.PROFESSOR);l.getUser().setName(" ");assertEquals("Admin opened lesson",ActivityDisplay.format(l,Map.of(),Map.of()));
  l.setAction("Viewed Student ID 21 progress");assertEquals("Admin viewed Juan progress",ActivityDisplay.format(l,Map.of(21L,user(21,"Juan",Role.STUDENT)),Map.of()));
 }
}
