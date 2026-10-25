package com.ptc.halo;

import java.sql.DriverManager;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class PasswordResetMigrationTest {
 @Test void additiveMigrationExpiresLegacyCodesWithoutChangingAccounts()throws Exception{
  try(var db=DriverManager.getConnection("jdbc:h2:mem:resetmigration;MODE=MariaDB","sa","");var sql=db.createStatement()){
   sql.execute("create table users(id bigint primary key,password varchar(255))");
   sql.execute("insert into users values(1,'unchanged-password-hash')");
   sql.execute("create table password_reset_otps(id bigint primary key,user_id bigint unique not null,otp varchar(255) not null,created_at datetime(6) not null,expires_at datetime(6) not null)");
   sql.execute("insert into password_reset_otps values(1,1,'123456',current_timestamp,DATEADD('MINUTE',10,CURRENT_TIMESTAMP))");
   Path migration=Path.of("database/password-reset-security.sql");if(!migration.toFile().exists())migration=Path.of("HALO/database/password-reset-security.sql");
   ScriptUtils.executeSqlScript(db,new FileSystemResource(migration));
   try(var row=sql.executeQuery("select otp,failed_attempts,expires_at<=current_timestamp from password_reset_otps where id=1")){assertTrue(row.next());assertEquals("",row.getString(1));assertEquals(0,row.getInt(2));assertTrue(row.getBoolean(3));}
   try(var row=sql.executeQuery("select password from users where id=1")){assertTrue(row.next());assertEquals("unchanged-password-hash",row.getString(1));}
   sql.execute("insert into password_reset_rate_limits values('test',current_timestamp,1,null)");
  }
 }
}
