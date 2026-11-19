package com.ptc.halo;

import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.*;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicitly launched Java 17 test tool. Never included in the production JAR. */
@SuppressWarnings("removal")
public final class LocalRegistrationSandbox {
 public static void main(String[] args)throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("Disposable directory required");
  Path root=Path.of(args[0]).toAbsolutePath();
  if(!Files.exists(root.resolve("HALO-DISPOSABLE-SANDBOX")))throw new IllegalStateException("Sandbox marker required");
  // No DNS, remote database, real SMTP, Gemini, or Supabase network access in this JVM.
  System.setSecurityManager(new SecurityManager(){
   @Override public void checkPermission(java.security.Permission permission){}
   @Override public void checkConnect(String host,int port){
    if(!Set.of("127.0.0.1","localhost","::1","[::1]").contains(host)
      || (port!=-1 && port!=11025 && port!=18025 && port!=8182))
     throw new SecurityException("Sandbox outbound connection blocked");
   }
   @Override public void checkListen(int port){if(port!=8182)throw new SecurityException("Sandbox listener blocked");}
   @Override public void checkConnect(String host,int port,Object context){checkConnect(host,port);}
  });
  for(String host:List.of("example.aivencloud.com","example.supabase.co","example.onrender.com","smtp.gmail.com")){
   boolean blocked=false;try{System.getSecurityManager().checkConnect(host,443);}catch(SecurityException expected){blocked=true;}
   if(!blocked)throw new IllegalStateException("Outbound guard failed");
  }
  String adminPassword=UUID.randomUUID()+"!aA1";
  Map<String,Object> settings=new HashMap<>();
  settings.put("spring.config.location","optional:file:"+root.resolve("no-application-config.properties").toUri().getPath());
  settings.put("server.address","127.0.0.1");settings.put("server.port","8182");
  settings.put("spring.datasource.url","jdbc:h2:mem:registration_sandbox;MODE=MariaDB;DB_CLOSE_DELAY=-1");
  settings.put("spring.datasource.driver-class-name","org.h2.Driver");settings.put("spring.datasource.username","sa");settings.put("spring.datasource.password","");
  settings.put("spring.jpa.hibernate.ddl-auto","create-drop");settings.put("spring.jpa.properties.hibernate.dialect","org.hibernate.dialect.H2Dialect");
  settings.put("spring.jpa.open-in-view","false");settings.put("spring.jpa.show-sql","false");
  settings.put("spring.sql.init.mode","never");
  settings.put("jwt.secret",key());settings.put("halo.password-reset.pepper",key());
  settings.put("spring.mail.host","127.0.0.1");settings.put("spring.mail.port","11025");
  settings.put("spring.mail.properties.mail.smtp.auth","false");settings.put("spring.mail.properties.mail.smtp.starttls.enable","false");
  settings.put("spring.mail.properties.mail.smtp.connectiontimeout","5000");settings.put("spring.mail.properties.mail.smtp.timeout","5000");
  settings.put("spring.ai.google.genai.api-key","sandbox-no-provider-access");
  settings.put("spring.ai.google.genai.embedding.api-key","sandbox-no-provider-access");
  settings.put("halo.lesson-storage.provider","local");settings.put("halo.lesson-storage.root",root.resolve("lessons").toString());
  settings.put("halo.profile-photo-directory",root.resolve("photos").toString());settings.put("halo.subject-cover-directory",root.resolve("covers").toString());
  settings.put("halo.cors.allowed-origins","http://127.0.0.1:5179");
  settings.put("SUPER_ADMIN_EMAIL","sandbox-superadmin@example.test");settings.put("SUPER_ADMIN_PASSWORD",key());
  settings.put("logging.level.root","WARN");settings.put("spring.main.banner-mode","off");
  StandardEnvironment environment=new StandardEnvironment();
  environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
  environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
  environment.getPropertySources().addFirst(new MapPropertySource("fixed-isolated-settings",settings));
  SpringApplication application=new SpringApplication(HaloApplication.class);application.setEnvironment(environment);
  var context=application.run();
  try {
  var jdbc=context.getBean(JdbcTemplate.class);
  for(String sql:Files.readString(root.resolve("database/V20261115_01__student_registry.sql")).split(";"))if(!sql.isBlank())jdbc.execute(sql);
  var user=new UserEntity();user.setName("Sandbox Admin");user.setEmail("sandbox-admin@example.test");user.setRole(Role.ADMIN);user.setStatus(Status.ACTIVE);
  user.setPassword(context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class).encode(adminPassword));
  user=context.getBean(UserRepository.class).saveAndFlush(user);
  jdbc.update("insert into student_registry_import(imported_by,imported_at,total_rows,imported_rows,skipped_rows) values (?,CURRENT_TIMESTAMP,2,2,0)",user.getId());
  long batch=jdbc.queryForObject("select id from student_registry_import",Long.class);
  for(String suffix:List.of("9001","9002"))jdbc.update("insert into student_registry(student_id,full_name,institutional_email,course,eligibility_status,registration_status,imported_by,import_id,imported_at,updated_at) values (?,?,?,'AHRT','ELIGIBLE','UNREGISTERED',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
   "29AHRT-"+suffix,"Sandbox Student "+(suffix.equals("9001")?"One":"Two"),"halo.sandbox."+suffix+"@paterostechnologicalcollege.edu.ph",user.getId(),batch);
  Files.writeString(root.resolve("admin-access.txt"),"Email: sandbox-admin@example.test\nPassword: "+adminPassword+"\nLOCAL DISPOSABLE ACCOUNT ONLY\n",StandardOpenOption.CREATE_NEW);
  Files.writeString(root.resolve("ready"),"Local sandbox ready; no persistent database used.");
  Runtime.getRuntime().addShutdownHook(new Thread(()->{try{Files.deleteIfExists(root.resolve("admin-access.txt"));Files.deleteIfExists(root.resolve("ready"));}catch(Exception ignored){}}));
  }catch(Exception error){context.close();throw new IllegalStateException("Sandbox fixture setup failed",error);}
 }
 private static String key(){byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
}
