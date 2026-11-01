package com.ptc.halo.service;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.UserRepository;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Read-only summaries. Patterns recognize only exact historical logger contracts, never arbitrary prose. */
public final class ActivitySummary {
 private ActivitySummary() {}
 public record Summary(String title, String description, String kind) {}
 private static final Pattern MODE=Pattern.compile("^(Started acting session|Revoked acting session|Started Professor preview|Ended Professor preview|Started Student preview|Ended Student preview) by (Admin|Super Admin) (\\d+) (acting as|viewing) (ADMIN|PROFESSOR|STUDENT|Professor|Student) (\\d+)$");
 private static final Pattern ACCOUNT=Pattern.compile("^(Created|Updated|Deleted|Archived|Restored) (ADMIN|PROFESSOR|STUDENT) account ID (\\d+): .*$",Pattern.DOTALL);
 public static Function<ActivityLogEntity,Summary> prepare(List<ActivityLogEntity> logs,UserRepository users) {
  var ids=new HashSet<Long>();
  for(var log:logs){var match=ACCOUNT.matcher(Objects.toString(log.getAction(),""));if(match.matches())try{ids.add(Long.valueOf(match.group(3)));}catch(NumberFormatException ignored){}}
  var names=new HashMap<Long,UserEntity>();if(!ids.isEmpty())for(var user:users.findAllById(ids))names.put(user.getId(),user);
  return log->format(log,names);
 }
 private static String name(UserEntity user,String fallback){return user==null||user.getName()==null||user.getName().isBlank()?fallback:user.getName().trim();}
 static Summary format(ActivityLogEntity log,Map<Long,UserEntity> names) {
  String actor=name(log.getUser(),"Administrator"),raw=Objects.toString(log.getAction(),"");
  if(log.getActivityType()==ActivityType.AUTH && log.getAuthEvent()!=null){
   String title=switch(log.getAuthEvent()){
    case LOGIN_SUCCESS->"Signed in";case LOGOUT->"Signed out";case LOGIN_FAILED->"Failed sign-in attempt";
    case PASSWORD_RESET_REQUESTED->"Requested password reset";case PASSWORD_RESET_COMPLETED->"Reset password";
    case OTP_VERIFIED->"Verified verification code";
    default->"Sign-in blocked";
   };
   String description=switch(log.getAuthEvent()){
    case LOGIN_SUCCESS->actor+" successfully signed in to HALO.";case LOGOUT->actor+" signed out of HALO.";
    case LOGIN_FAILED->"A sign-in attempt was unsuccessful.";
    case ACCOUNT_LOGIN_BLOCKED,ACCOUNT_INACTIVE_LOGIN_ATTEMPT,ACCOUNT_ARCHIVED_LOGIN_ATTEMPT,ACCOUNT_BLOCKED_LOGIN_ATTEMPT->"A sign-in attempt was blocked by account restrictions.";
    default->actor+" recorded an account security event.";
   };
   return new Summary(title,description,log.getAuthEvent()==AuthEvent.LOGIN_FAILED?"warning":"auth");
  }
  var mode=MODE.matcher(raw);
  if(log.getActivityType()==ActivityType.ACCOUNT && mode.matches() && log.getActingTargetRole()!=null && log.getUser()!=null
    && Objects.toString(log.getUser().getId(),"").equals(mode.group(3))
    && log.getActingTargetRole().name().equalsIgnoreCase(mode.group(5))
    && (log.getActingTarget()==null || Objects.toString(log.getActingTarget().getId(),"").equals(mode.group(6)))) {
   String event=mode.group(1),role=ActivityDisplay.role(log.getActingTargetRole().name());
   boolean preview=event.endsWith("preview"),start=event.startsWith("Started");
   boolean valid=preview?mode.group(4).equals("viewing")&&event.contains(role):mode.group(4).equals("acting as")&&log.getActingSession()!=null;
   if(valid){String target=name(log.getActingTarget(),role+" account");
    return new Summary((start?"Entered ":"Exited ")+role+(preview?" Preview":" Mode"),
      actor+(preview?(start?" began a read-only preview of ":" ended the preview of "):(start?" is acting as ":" ended the session as "))+target,"mode");}
  }
  var account=ACCOUNT.matcher(raw);
  if(log.getActivityType()==ActivityType.ACCOUNT && account.matches()){
   String role=ActivityDisplay.role(account.group(2)),target=role+" account";
   try{target=name(names.get(Long.valueOf(account.group(3))),target);}catch(NumberFormatException ignored){}
   return new Summary(account.group(1)+" "+role+" Account",actor+" "+account.group(1).toLowerCase(Locale.ROOT)+" the "+role+" account"+(target.equals(role+" account")?".":" for "+target+"."),"account");
  }
  // Unknown events must not guess success or display raw IDs/emails on the dashboard.
  return new Summary("Recorded activity",actor+" recorded an administrative activity. See the audit log for details.","activity");
 }
}
