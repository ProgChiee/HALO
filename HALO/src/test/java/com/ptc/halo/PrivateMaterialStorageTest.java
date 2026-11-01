package com.ptc.halo;
import com.ptc.halo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PrivateMaterialStorageTest {
 @TempDir Path root;
 HttpServer server; SupabaseLessonMaterialStorage storage; Map<String,byte[]> objects=new HashMap<>();List<String> calls=new ArrayList<>();boolean publicBucket=false,redirect=false;
 @BeforeEach void setup() throws Exception {
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/storage/v1/",exchange->{
   assertEquals("sb_secret_synthetic",exchange.getRequestHeaders().getFirst("apikey"));assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
   String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();calls.add(method+" "+path);int status=200;byte[] result=new byte[0];
   if(path.contains("/bucket/")) result=("{\"public\":"+publicBucket+"}").getBytes();
   else if(redirect){status=302;exchange.getResponseHeaders().add("Location","http://127.0.0.1:1/never");}
   else if(method.equals("GET")){String key=path.substring(path.indexOf("halo-learning-materials/")+24);result=objects.get(key);if(result==null){status=404;result=new byte[0];}}
   else if(method.equals("POST")){assertEquals("false",exchange.getRequestHeaders().getFirst("x-upsert"));String key=path.substring(path.indexOf("halo-learning-materials/")+24);if(objects.containsKey(key))status=409;else objects.put(key,exchange.getRequestBody().readAllBytes());}
   else if(method.equals("DELETE")){var json=new ObjectMapper().readTree(exchange.getRequestBody());for(var key:json.get("prefixes"))objects.remove(key.asText());}
   exchange.sendResponseHeaders(status,result.length==0?-1:result.length);try(var output=exchange.getResponseBody()){output.write(result);}exchange.close();
  });server.start();storage=new SupabaseLessonMaterialStorage("http://127.0.0.1:"+server.getAddress().getPort(),"sb_secret_synthetic","C:/backup/lessons");
 }
 @AfterEach void stop(){server.stop(0);}
 @Test void privateRoundTripPreservesKeysAndNeverOverwrites() throws Exception {
  storage.writeNew("abc",new byte[]{1,2},"application/pdf");assertArrayEquals(new byte[]{1,2},storage.read("uploads/lessons/abc"));assertArrayEquals(new byte[]{1,2},storage.read("C:\\backup\\lessons\\abc"));
  assertThrows(java.io.IOException.class,()->storage.writeNew("abc",new byte[]{9},"image/png"));assertArrayEquals(new byte[]{1,2},storage.read("abc"));storage.delete("abc");assertThrows(java.io.FileNotFoundException.class,()->storage.read("abc"));
 }
 @Test void publicBucketFailsClosedBeforeObjectAccess() {publicBucket=true;assertThrows(java.io.IOException.class,()->storage.read("abc"));assertEquals(1,calls.size());assertTrue(calls.get(0).contains("/bucket/"));}
 @Test void invalidPathsAndRedirectsNeverEscapeStorage() {for(String ref:List.of("../secret","uploads/lessons/../../secret","https://evil/file","C:/elsewhere/file","a%2fb","a//b"))assertThrows(IllegalArgumentException.class,()->storage.read(ref));assertTrue(calls.isEmpty());redirect=true;assertThrows(java.io.IOException.class,()->storage.read("abc"));assertEquals(2,calls.size());}
 @Test void boundedReadsAndLocalCompatibility() throws Exception {
  objects.put("big",new byte[LessonMaterialStorage.MAX_BYTES+1]);assertThrows(java.io.IOException.class,()->storage.read("big"));
  var local=new LessonStoragePaths(root.toString());local.writeNew("uuid",new byte[]{1},"image/png");assertArrayEquals(new byte[]{1},local.read("uploads/lessons/uuid"));assertThrows(java.nio.file.FileAlreadyExistsException.class,()->local.writeNew("uuid",new byte[]{2},"image/png"));assertEquals("uuid",local.key(root.resolve("uuid").toString()));
 }
 @Test void remoteUploadsCleanUpOnDatabaseRollback() throws Exception {
  org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
  try {var uploads=new FileUploadService(storage);var file=uploads.uploadValidated(new FileUploadService.ValidatedUpload("name.pdf","application/pdf",new byte[]{1,2}),null);assertArrayEquals(new byte[]{1,2},storage.read(file.getFilePath()));for(var callback:org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations())callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);assertTrue(objects.isEmpty());}
  finally{org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
 }
 @Test void referenceRetentionIncludesImmutableSnapshotsAndFailsClosed() throws Exception {
  var db=new org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder().generateUniqueName(true).setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2).build();
  try {var jdbc=new org.springframework.jdbc.core.JdbcTemplate(db);jdbc.execute("create table ai_learning_files(file_path varchar(255))");jdbc.execute("create table lesson_revisions(payload clob)");jdbc.execute("create table lesson_revision_history(payload clob)");var guard=new LessonMaterialRetention(jdbc,new ObjectMapper(),storage);
   assertFalse(guard.referenced("abc"));jdbc.update("insert into lesson_revision_history values(?)","{\"files\":[{\"filePath\":\"uploads/lessons/abc\"}]}");assertTrue(guard.referenced("abc"));assertFalse(guard.referenced("other"));jdbc.update("insert into lesson_revisions values(?)","unreadable");assertTrue(guard.referenced("other"));
  }finally{db.shutdown();}
 }
 @Test void committedRemovalRetainsHistoricalBytes() throws Exception {
  storage.writeNew("historical",new byte[]{1},"application/pdf");
  var uploads=new FileUploadService(storage);var retention=org.mockito.Mockito.mock(LessonMaterialRetention.class);
  org.springframework.test.util.ReflectionTestUtils.setField(uploads,"retention",retention);
  org.mockito.Mockito.when(retention.referenced("historical")).thenReturn(true);
  org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
  try {uploads.deleteFile("historical");assertTrue(objects.containsKey("historical"));for(var callback:org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations())callback.afterCommit();assertTrue(objects.containsKey("historical"));org.mockito.Mockito.verify(retention).referenced("historical");}
  finally{org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
 }
 @Test void providerSelectionKeepsLocalCacheAndSelectsPrivateOriginalStorage() {
  new org.springframework.boot.test.context.runner.ApplicationContextRunner()
   .withUserConfiguration(com.ptc.halo.config.LessonStorageConfiguration.class,LessonStoragePaths.class,SupabaseLessonMaterialStorage.class)
   .withPropertyValues("halo.lesson-storage.provider=supabase","halo.lesson-storage.root="+root,"SUPABASE_URL=http://127.0.0.1:"+server.getAddress().getPort(),"SUPABASE_SECRET_KEY=sb_secret_synthetic")
   .run(context->{assertNull(context.getStartupFailure());assertInstanceOf(SupabaseLessonMaterialStorage.class,context.getBean(LessonMaterialStorage.class));assertNotNull(context.getBean(LessonStoragePaths.class));assertTrue(calls.isEmpty());});
 }
}
