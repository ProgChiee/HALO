package com.ptc.halo.service;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivitySummaryTest {
 UserEntity user(long id,String name,Role role){var u=new UserEntity();u.setId(id);u.setName(name);u.setRole(role);return u;}
 ActivityLogEntity mode(Role actorRole,Role role,boolean preview,boolean start){
  var l=new ActivityLogEntity();l.setUser(user(4,"Alex",actorRole));l.setActingTarget(user(21,"Jordan",role));l.setActingTargetRole(role);l.setActivityType(ActivityType.ACCOUNT);
  if(!preview)l.setActingSession(new AdminActingSessionEntity("test-session",l.getUser(),l.getActingTarget(),java.time.Instant.EPOCH,java.time.Instant.EPOCH.plusSeconds(3600)));
  l.setAction((preview?(start?"Started ":"Ended ")+ActivityDisplay.role(role.name())+" preview":start?"Started acting session":"Revoked acting session")+" by "+(actorRole==Role.SUPER_ADMIN?"Super Admin":"Admin")+" 4 "+(preview?"viewing ":"acting as ")+(preview?ActivityDisplay.role(role.name()):role.name())+" 21");return l;
 }
 @Test void allModeTransitionsPreserveOriginalAuditAndIdentifyActor(){
  for(var actor:List.of(Role.ADMIN,Role.SUPER_ADMIN))for(var role:List.of(Role.ADMIN,Role.PROFESSOR,Role.STUDENT))for(boolean start:new boolean[]{true,false}){
   var l=mode(actor,role,false,start);String raw=l.getAction();var result=ActivitySummary.format(l,Map.of());
   assertEquals((start?"Entered ":"Exited ")+ActivityDisplay.role(role.name())+" Mode",result.title());assertTrue(result.description().contains("Alex"));assertTrue(result.description().contains("Jordan"));assertFalse(result.description().contains("You"));assertEquals(raw,l.getAction());assertNotNull(l.getActingSession());
  }
 }
 @Test void previewsAreNotActingSessions(){for(var role:List.of(Role.PROFESSOR,Role.STUDENT))for(boolean start:new boolean[]{true,false}){var r=ActivitySummary.format(mode(Role.SUPER_ADMIN,role,true,start),Map.of());assertTrue(r.title().endsWith(" Preview"));assertFalse(r.description().contains("acting as"));}}
 @Test void missingTargetUsesRoleFallback(){var l=mode(Role.SUPER_ADMIN,Role.PROFESSOR,false,false);l.setActingTarget(null);assertTrue(ActivitySummary.format(l,Map.of()).description().endsWith("Professor account"));}
 @Test void unknownAndMismatchedEventsDoNotInventSuccess(){var l=mode(Role.SUPER_ADMIN,Role.STUDENT,false,true);l.setAction("Failed to start acting session by Super Admin 4 acting as STUDENT 21");assertEquals("Recorded activity",ActivitySummary.format(l,Map.of()).title());l=mode(Role.SUPER_ADMIN,Role.PROFESSOR,false,true);l.setActingTarget(user(99,"Other",Role.PROFESSOR));assertEquals("Recorded activity",ActivitySummary.format(l,Map.of()).title());}
 @Test void authUsesStructuredEventAndDoesNotLeakIdentifier(){var l=new ActivityLogEntity();l.setActivityType(ActivityType.AUTH);l.setUser(user(4,"Alex",Role.SUPER_ADMIN));l.setAction("private@example.test");for(var entry:Map.of(AuthEvent.LOGIN_SUCCESS,"Signed in",AuthEvent.LOGOUT,"Signed out",AuthEvent.LOGIN_FAILED,"Failed sign-in attempt",AuthEvent.ACCOUNT_INACTIVE_LOGIN_ATTEMPT,"Sign-in blocked").entrySet()){l.setAuthEvent(entry.getKey());var r=ActivitySummary.format(l,Map.of());assertEquals(entry.getValue(),r.title());assertFalse(r.description().contains("@"));}}
 @Test void accountNamesAreBatchResolvedAndMissingNamesNeverUseRawSnapshots(){var users=mock(UserRepository.class);var logs=new ArrayList<ActivityLogEntity>();for(int i=0;i<3;i++){var l=new ActivityLogEntity();l.setActivityType(ActivityType.ACCOUNT);l.setUser(user(4,"Alex",Role.SUPER_ADMIN));l.setAction("Created PROFESSOR account ID 21: private@example.test");logs.add(l);}when(users.findAllById(Set.of(21L))).thenReturn(List.of(user(21,"Jordan",Role.PROFESSOR)));var formatter=ActivitySummary.prepare(logs,users);for(var l:logs){assertEquals("Created Professor Account",formatter.apply(l).title());assertTrue(formatter.apply(l).description().contains("Jordan"));assertFalse(formatter.apply(l).description().contains("@"));}verify(users,times(1)).findAllById(Set.of(21L));assertTrue(ActivitySummary.format(logs.get(0),Map.of()).description().contains("Professor account"));}
 @Test void apiAddsSummariesWithoutChangingPageOrRawAudit(){var repo=mock(ActivityLogRepository.class);var users=mock(UserRepository.class);var l=mode(Role.SUPER_ADMIN,Role.ADMIN,false,false);l.setId(8L);l.setCreatedAt(java.time.LocalDateTime.of(2026,11,2,9,30));String raw=l.getAction();var paging=PageRequest.of(2,10,Sort.by(Sort.Direction.DESC,"createdAt","id"));when(repo.findSuperAdminLogs(null,"",paging)).thenReturn(new PageImpl<>(List.of(l),paging,21));var result=new ActivityLogService(repo,users).getSuperAdminLogs(2,10,"",null);assertEquals(21,result.getTotalElements());assertEquals(2,result.getNumber());var dto=result.getContent().get(0);assertEquals("Exited Admin Mode",dto.getActivityTitle());assertEquals(raw,dto.getAction());assertEquals(l.getCreatedAt(),dto.getCreatedAt());assertEquals(8L,dto.getId());verify(repo).findSuperAdminLogs(null,"",paging);verifyNoMoreInteractions(repo);}
}
