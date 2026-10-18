package com.ptc.halo.service;

import com.ptc.halo.entity.*;
import com.ptc.halo.repository.UserRepository;
import java.util.*;
import java.util.function.Function;
import java.util.regex.*;

/** Presentation only. Never modifies the stored action or audit relationships. */
public final class ActivityDisplay {
 private ActivityDisplay() {}
 private static final Pattern ACCOUNT=Pattern.compile("^(Created|Updated|Deleted|Archived|Restored) (ADMIN|PROFESSOR|STUDENT) account ID (\\d+): (.*)$",Pattern.CASE_INSENSITIVE);
 private static final Pattern STATUS=Pattern.compile("^(?:Set|Changed) (Admin|Professor|Student) status to (ACTIVE|INACTIVE|BLOCKED): (.*)$");
 private static final Pattern ADMIN_SNAPSHOT=Pattern.compile("Admin ID (\\d+) \\((.*)\\)$");
 private static final Pattern PROVENANCE=Pattern.compile("^(.*) by Admin \\d+ (?:viewing|acting as) (?:PROFESSOR|STUDENT) \\d+$",Pattern.CASE_INSENSITIVE);
 private static final Pattern USER_ID=Pattern.compile("\\b(Admin|Professor|Student|User|student|professor) ID (\\d+)\\b");
 public static String role(String value){return value==null?"User":value.substring(0,1).toUpperCase(Locale.ROOT)+value.substring(1).toLowerCase(Locale.ROOT);}
 public static String name(UserEntity user,String fallback){return user==null?"Deleted "+fallback:user.getName()==null||user.getName().isBlank()?fallback:user.getName().trim();}
 private static Long id(String value){try{return Long.valueOf(value);}catch(NumberFormatException e){return null;}}
 public static Function<ActivityLogEntity,String> prepare(List<ActivityLogEntity> logs,UserRepository repository){
  Set<Long> ids=new HashSet<>();Set<String> emails=new HashSet<>();
  for(var log:logs){String action=Objects.toString(log.getAction(),"");var account=ACCOUNT.matcher(action);if(account.matches())ids.add(id(account.group(3)));var status=STATUS.matcher(action);if(status.matches()&&status.group(3).contains("@"))emails.add(status.group(3));var refs=USER_ID.matcher(action);while(refs.find())ids.add(id(refs.group(2)));}
  ids.remove(null);Map<Long,UserEntity> byId=new HashMap<>();Map<String,UserEntity> byEmail=new HashMap<>();
  if(!ids.isEmpty())for(var u:repository.findAllById(ids))byId.put(u.getId(),u);
  if(!emails.isEmpty())for(var u:repository.findByEmailIn(emails))byEmail.put(u.getEmail(),u);
  return log->format(log,byId,byEmail);
 }
 static String format(ActivityLogEntity log,Map<Long,UserEntity> ids,Map<String,UserEntity> emails){
  if(log.getActivityType()==com.ptc.halo.enums.ActivityType.AUTH && log.getAuthEvent()!=null && log.getAuthDisplay()!=null) return log.getAuthDisplay();
  String actor=name(log.getUser(),"Admin"), raw=Objects.toString(log.getAction(),""), action=raw;
  var provenance=PROVENANCE.matcher(raw);
  if(provenance.matches()){
   action=provenance.group(1);String r=role(log.getActingTargetRole()==null?null:log.getActingTargetRole().name());String target=name(log.getActingTarget(),r);
   if(action.matches("(?i)(Started|Ended) (Professor|Student) preview"))return actor+" "+(action.startsWith("Started")?"started ":"ended ")+r+" Preview for "+target;
   if(action.equals("Started acting session")||action.equals("Revoked acting session"))return actor+(action.startsWith("Started")?" entered ":" exited ")+(r.equals("Student")?"Student Support Mode":"Professor Mode")+" for "+target;
   return actor+" "+lower(replaceIds(action,ids))+" on behalf of "+target+" ("+r+")";
  }
  var account=ACCOUNT.matcher(raw);
  if(account.matches()){
   String verb=account.group(1),r=role(account.group(2)),detail=account.group(4);
   boolean lifecycle=verb.equals("Archived")||verb.equals("Restored");
   String target=lifecycle?name(ids.get(id(account.group(3))),r):detail.isBlank()?name(ids.get(id(account.group(3))),r):detail;
   return actor+" "+lower(verb)+" "+r+" account for "+target;
  }
  var status=STATUS.matcher(raw);
  if(status.matches()){
   String r=status.group(1), detail=status.group(3);var snapshot=ADMIN_SNAPSHOT.matcher(detail);
   String target=snapshot.matches()?snapshot.group(2):name(emails.get(detail),r);
   String verb=switch(status.group(2)){case "ACTIVE"->"activated";case "INACTIVE"->"deactivated";default->"blocked";};
   return actor+" "+verb+" "+r+" account for "+target;
  }
  return actor+" "+lower(replaceIds(action,ids));
 }
 private static String replaceIds(String action,Map<Long,UserEntity> ids){
  var refs=USER_ID.matcher(action);StringBuffer text=new StringBuffer();
  while(refs.find())refs.appendReplacement(text,Matcher.quoteReplacement(name(ids.get(id(refs.group(2))),role(refs.group(1)))));
  refs.appendTail(text);return text.toString();
 }
 private static String lower(String text){return text.isEmpty()?"recorded an activity":Character.toLowerCase(text.charAt(0))+text.substring(1);}
}
