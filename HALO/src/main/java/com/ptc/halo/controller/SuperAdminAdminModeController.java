package com.ptc.halo.controller;
import com.ptc.halo.service.*;
import com.ptc.halo.dtoRequest.*;
import com.ptc.halo.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/super-admin/acting/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminAdminModeController {
 private final SuperAdminAdminModeService mode;private final AdminService accounts;private final AdminDashboardService dashboard;
 private final AdminProfessorMonitoringService professors;private final AdminStudentMonitoringService students;private final ActivityLogService audit;private final ProfileService profiles;
 public SuperAdminAdminModeController(SuperAdminAdminModeService mode,AdminService accounts,AdminDashboardService dashboard,AdminProfessorMonitoringService professors,AdminStudentMonitoringService students,ActivityLogService audit,ProfileService profiles){this.mode=mode;this.accounts=accounts;this.dashboard=dashboard;this.professors=professors;this.students=students;this.audit=audit;this.profiles=profiles;}
 @GetMapping("/dashboard") public Object dashboard(Authentication a,@RequestHeader("X-Acting-Session") UUID s){return mode.execute(a,s.toString(),t->dashboard.getDashboard());}
 @GetMapping("/dashboard/recent-activity") public Object recent(Authentication a,@RequestHeader("X-Acting-Session") UUID s){return mode.execute(a,s.toString(),t->dashboard.getRecentActivity());}
 @GetMapping("/profile") public Object profile(Authentication a,@RequestHeader("X-Acting-Session") UUID s){return mode.execute(a,s.toString(),profiles::getUserProfile);}
 @GetMapping("/professors") public Object professors(Authentication a,@RequestHeader("X-Acting-Session") UUID s){return mode.execute(a,s.toString(),t->accounts.viewAllProfessors());}
 @GetMapping("/students") public Object students(Authentication a,@RequestHeader("X-Acting-Session") UUID s){return mode.execute(a,s.toString(),t->accounts.viewAllStudents());}
 @GetMapping("/professors/{id}") public Object professor(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@PathVariable @Positive Long id){return mode.execute(a,s.toString(),t->accounts.viewProfessorById(id));}
 @GetMapping("/students/{id}") public Object student(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@PathVariable @Positive Long id){return mode.execute(a,s.toString(),t->accounts.viewStudentById(id));}
 @PostMapping("/create-professor") public Object create(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@Valid @RequestBody ProfessorRequest request){return mode.execute(a,s.toString(),t->accounts.createProfessor(request,t));}
 @PutMapping("/professors/{id}") public Object updateProfessor(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@Positive @PathVariable Long id,@Valid @RequestBody ProfessorUpdateRequest request){return mode.execute(a,s.toString(),t->accounts.updateProfessor(id,request,t));}
 @PutMapping("/students/{id}") public Object updateStudent(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@Positive @PathVariable Long id,@Valid @RequestBody StudentUpdateRequest request){return mode.execute(a,s.toString(),t->accounts.updateStudent(id,request,t));}
 @PatchMapping("/professors/{id}/status") public Object professorStatus(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@Positive @PathVariable Long id,@Valid @RequestBody AdminStatusRequest request){return mode.execute(a,s.toString(),t->accounts.changeProfessorStatus(id,request.status(),t));}
 @PatchMapping("/students/{id}/status") public Object studentStatus(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@Positive @PathVariable Long id,@Valid @RequestBody AdminStatusRequest request){return mode.execute(a,s.toString(),t->accounts.changeStudentStatus(id,request.status(),t));}
 @PatchMapping("/professors/{id}/archive") public Object archiveProfessor(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@Positive @PathVariable Long id){return mode.execute(a,s.toString(),t->accounts.archiveOrRestore(id,Role.PROFESSOR,t,false));}
 @PatchMapping("/professors/{id}/restore") public Object restoreProfessor(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@Positive @PathVariable Long id){return mode.execute(a,s.toString(),t->accounts.archiveOrRestore(id,Role.PROFESSOR,t,true));}
 @PatchMapping("/students/{id}/archive") public Object archiveStudent(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@Positive @PathVariable Long id){return mode.execute(a,s.toString(),t->accounts.archiveOrRestore(id,Role.STUDENT,t,false));}
 @PatchMapping("/students/{id}/restore") public Object restoreStudent(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@Positive @PathVariable Long id){return mode.execute(a,s.toString(),t->accounts.archiveOrRestore(id,Role.STUDENT,t,true));}
 @GetMapping("/professors/monitoring") public Object professorMonitoring(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return mode.execute(a,s.toString(),t->professors.getProfessorMonitoring(page,size));}
 @GetMapping("/students/monitoring") public Object studentMonitoring(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return mode.execute(a,s.toString(),t->students.getStudentMonitoring(page,size));}
 @GetMapping("/activity-logs") public Object logs(Authentication a,@RequestHeader("X-Acting-Session") UUID s,@RequestParam(required=false) Role role,@RequestParam(name="type",required=false) ActivityType type,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(defaultValue="") @Size(max=200) String search){return mode.execute(a,s.toString(),t->audit.getLogs(role,type,page,size,search));}
}
