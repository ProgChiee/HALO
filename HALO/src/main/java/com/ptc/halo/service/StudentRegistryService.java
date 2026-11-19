package com.ptc.halo.service;

import java.util.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.dtoRequest.StudentRequest;

@Service
public class StudentRegistryService {
    private final JdbcTemplate jdbc; private final StudentRegistryWorkbook workbook;
    private final PasswordResetProtection protection; private final PasswordResetThrottle throttle;
    private final EmailService mail; private final ActivityLogService audit; private final TransactionTemplate tx;
    public StudentRegistryService(JdbcTemplate jdbc,StudentRegistryWorkbook workbook,PasswordResetProtection protection,
        PasswordResetThrottle throttle,EmailService mail,ActivityLogService audit,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.workbook=workbook;this.protection=protection;this.throttle=throttle;this.mail=mail;this.audit=audit;tx=new TransactionTemplate(manager);
    }
    public record CheckedRow(int row,String studentId,String fullName,String email,String course,String status,List<String> errors) {}
    public record Preview(int total,int eligible,int ineligible,int invalid,int duplicates,List<CheckedRow> rows) {}
    public record Entry(long id,String studentId,String fullName,String email,String course,String eligibilityStatus,String registrationStatus) {}
    public record Page(List<Entry> content,long totalElements,int page,int size) {}
    public static String email(String value) { return value==null?"":value.strip().toLowerCase(Locale.ROOT); }
    public static boolean validEmail(String value) {
        return value.length()<=254 && value.matches("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@paterostechnologicalcollege\\.edu\\.ph");
    }
    @Transactional(readOnly=true)
    public Preview preview(MultipartFile file) {
        var rows=workbook.read(file); Map<String,Integer> ids=new HashMap<>(),emails=new HashMap<>();
        for(var r:rows){ids.merge(r.studentId(),1,Integer::sum);emails.merge(email(r.email()),1,Integer::sum);}
        var named=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        var parameters=Map.of("ids",ids.keySet(),"emails",emails.keySet());
        Set<String> existingIds=new HashSet<>(named.queryForList("select student_id from student_profile_entity where student_id in (:ids)",parameters,String.class));
        Set<String> existingEmails=new HashSet<>(named.queryForList("select lower(trim(email)) from user_entity where lower(trim(email)) in (:emails)",parameters,String.class));
        named.query("select student_id,institutional_email from student_registry where student_id in (:ids) or institutional_email in (:emails)",parameters,(org.springframework.jdbc.core.RowCallbackHandler) row->{existingIds.add(row.getString(1));existingEmails.add(row.getString(2));});
        List<CheckedRow> checked=new ArrayList<>();int eligible=0,ineligible=0,invalid=0,duplicates=0;
        for(var r:rows){
            List<String> errors=new ArrayList<>();String name=r.fullName(),address=email(r.email()),course=r.course().toUpperCase(Locale.ROOT),status="ELIGIBLE";
            try{name=FullNameValidator.normalize(name);}catch(RuntimeException e){errors.add("Enter a valid full name (maximum 100 characters).");}
            if(!validEmail(address))errors.add("Use the official @paterostechnologicalcollege.edu.ph email.");
            if(!r.studentId().matches("[0-9]{2}AHRT-[0-9]{4}"))errors.add("Student ID must match 23AHRT-0168.");
            if(course.isBlank())errors.add("Course is required.");
            boolean duplicate=ids.get(r.studentId())>1 || emails.get(address)>1;
            if(duplicate)errors.add("Repeated Student ID or email in this file.");
            if(existingIds.contains(r.studentId()) || existingEmails.contains(address)) {duplicate=true;errors.add("Student ID or email already exists. No existing record will be overwritten.");}
            if(duplicate){status="DUPLICATE";duplicates++;}
            else if(!course.isBlank()&&!course.equals("AHRT")){status="INELIGIBLE";errors.add("Only AHRT is eligible; this row will not be imported.");ineligible++;}
            else if(!errors.isEmpty()){status="INVALID";invalid++;}else eligible++;
            checked.add(new CheckedRow(r.row(),r.studentId(),name,address,course,status,List.copyOf(errors)));
        }
        return new Preview(rows.size(),eligible,ineligible,invalid,duplicates,List.copyOf(checked));
    }
    @Transactional
    public Preview confirm(MultipartFile file,UserEntity actor) {
        if(actor==null||actor.getRole()!=com.ptc.halo.enums.Role.ADMIN||actor.getStatus()!=com.ptc.halo.enums.Status.ACTIVE)throw new org.springframework.security.access.AccessDeniedException("Admin required");
        Preview preview=preview(file);
        var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement("insert into student_registry_import(imported_by,imported_at,total_rows,imported_rows,skipped_rows) values (?,CURRENT_TIMESTAMP,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);statement.setLong(1,actor.getId());statement.setInt(2,preview.total());statement.setInt(3,preview.eligible());statement.setInt(4,preview.total()-preview.eligible());return statement;},key);
        long batch=java.util.Objects.requireNonNull(key.getKey()).longValue();
        for(var r:preview.rows())if(r.status().equals("ELIGIBLE"))jdbc.update("insert into student_registry(student_id,full_name,institutional_email,course,eligibility_status,registration_status,imported_by,import_id,imported_at,updated_at) values (?,?,?,?,'ELIGIBLE','UNREGISTERED',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",r.studentId(),r.fullName(),r.email(),r.course(),actor.getId(),batch);
        audit.createLog(actor,com.ptc.halo.enums.ActivityType.ACCOUNT,"Student Registry import ID "+batch+": "+preview.eligible()+" imported; "+(preview.total()-preview.eligible())+" skipped");return preview;
    }
    @Transactional(readOnly=true)
    public Page list(String search,String eligibility,String registration,int page,int size) {
        if(page<0||size<1||size>50||search.length()>100||!Set.of("ALL","ELIGIBLE","INELIGIBLE").contains(eligibility)||!Set.of("ALL","UNREGISTERED","REGISTERED").contains(registration))throw StudentRegistryWorkbook.invalid();
        String clause=" where (lower(student_id) like ? or lower(full_name) like ? or institutional_email like ?) and (?='ALL' or eligibility_status=?) and (?='ALL' or registration_status=?)";
        String query="%"+search.strip().toLowerCase(Locale.ROOT)+"%";Object[] args={query,query,query,eligibility,eligibility,registration,registration};
        long total=jdbc.queryForObject("select count(*) from student_registry"+clause,Long.class,args);
        List<Object> parameters=new ArrayList<>(Arrays.asList(args));parameters.add(size);parameters.add((long)page*size);
        var entries=jdbc.query("select id,student_id,full_name,institutional_email,course,eligibility_status,registration_status from student_registry"+clause+" order by id desc limit ? offset ?",(r,i)->new Entry(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7)),parameters.toArray());
        return new Page(entries,total,page,size);
    }
    public List<Map<String,Object>> history() { return jdbc.queryForList("select id,imported_at,total_rows,imported_rows,skipped_rows from student_registry_import order by id desc limit 50"); }
    public void admit(String address,String ip,boolean verify) {
        try { throttle.admit("registration:"+email(address),"registration:"+ip,verify); }
        catch (PasswordResetException error) {
            if(error.getStatus()==429)throw new RegistrationRateLimitException(error.getRetryAfterSeconds());
            throw error;
        }
    }
    private static RegistrationValidationException denied(){return new RegistrationValidationException(Map.of("verificationCode","Unable to complete registration. Check your details and code, or contact your school Admin."),400);}
    private record Verification(long id,String name,String email,String hash,LocalDateTime expires) {}
    private Verification eligible(String id,String address) {
        var rows=jdbc.query("select id,full_name,institutional_email,verification_hash,verification_expires_at from student_registry where student_id=? and institutional_email=? and course='AHRT' and eligibility_status='ELIGIBLE' and registration_status='UNREGISTERED' and linked_user_id is null for update",(r,i)->new Verification(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getTimestamp(5)==null?null:r.getTimestamp(5).toLocalDateTime()),id,address);
        return rows.isEmpty()?null:rows.get(0);
    }
    // No user account, password or registry claim is created when requesting a code.
    public void requestCode(String id,String address,String ip) {
        String normalized=email(address);admit(normalized,ip,false);
        String code=protection.newCode();
        String recipient=tx.execute(status->{
            var record=eligible(id,normalized);if(record==null)return null;
            if(jdbc.queryForObject("select count(*) from user_entity where lower(trim(email))=?",Long.class,normalized)>0)return null;
            jdbc.update("update student_registry set verification_hash=?,verification_expires_at=?,updated_at=CURRENT_TIMESTAMP where id=?",protection.verifier(-record.id(),code),throttle.databaseNow().plusSeconds(protection.ttlSeconds),record.id());return record.email();
        });
        if(recipient!=null)try{mail.sendRegistrationCode(recipient,code);}catch(RuntimeException ignored){org.slf4j.LoggerFactory.getLogger(getClass()).warn("registration_mail_unavailable");}
    }
    // Caller owns the account-creation transaction; the row lock serializes competing claims.
    public long validateClaim(StudentRequest request) {
        var record=eligible(request.getStudentId(),email(request.getEmail()));
        if(record==null||record.expires()==null||!record.expires().isAfter(throttle.databaseNow())||request.getVerificationCode()==null||!request.getVerificationCode().matches("[0-9]{6}")||!protection.matches(-record.id(),request.getVerificationCode(),record.hash()))throw denied();
        // The official name is authoritative; supported punctuation/case differences do not reject applicants.
        request.setName(record.name());return record.id();
    }
    public void claim(long registryId,long userId) {
        if(jdbc.update("update student_registry set linked_user_id=?,registration_status='REGISTERED',verification_hash=null,verification_expires_at=null,updated_at=CURRENT_TIMESTAMP where id=? and linked_user_id is null and registration_status='UNREGISTERED'",userId,registryId)!=1)throw denied();
    }
}
