package com.ptc.halo.controller;
import com.ptc.halo.service.*;
import com.ptc.halo.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/admin/student-registry")
@PreAuthorize("hasRole('ADMIN')")
public class StudentRegistryController {
    private final StudentRegistryService registry;private final StudentRegistryWorkbook workbook;private final UserRepository users;
    public StudentRegistryController(StudentRegistryService registry,StudentRegistryWorkbook workbook,UserRepository users){this.registry=registry;this.workbook=workbook;this.users=users;}
    private void guard(String mode){if(mode!=null)throw new org.springframework.security.access.AccessDeniedException("Exit acting mode first");}
    @GetMapping public StudentRegistryService.Page list(@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="ALL")String eligibility,@RequestParam(defaultValue="ALL")String registration,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestHeader(value="X-Acting-Session",required=false)String mode){guard(mode);return registry.list(search,eligibility,registration,page,size);}
    @GetMapping("/history") public Object history(@RequestHeader(value="X-Acting-Session",required=false)String mode){guard(mode);return registry.history();}
    @GetMapping("/template") public ResponseEntity<byte[]> template(@RequestHeader(value="X-Acting-Session",required=false)String mode){guard(mode);return ResponseEntity.ok().header("Content-Disposition","attachment; filename=HALO-student-registry.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(workbook.template());}
    @PostMapping("/preview") public Object preview(@RequestParam MultipartFile file,@RequestHeader(value="X-Acting-Session",required=false)String mode){guard(mode);return registry.preview(file);}
    @PostMapping("/confirm") public Object confirm(@RequestParam MultipartFile file,@RequestParam boolean confirmed,Authentication auth,@RequestHeader(value="X-Acting-Session",required=false)String mode){guard(mode);if(!confirmed)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"CONFIRMATION_REQUIRED");return registry.confirm(file,com.ptc.halo.security.AuthenticatedAccount.find(users,auth).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Admin required")));}
    // Respond directly: sendError would redispatch to the secured /error endpoint and mask failures as 401.
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<?> rejected(org.springframework.web.server.ResponseStatusException error) {
        String code = "CONFIRMATION_REQUIRED".equals(error.getReason()) ? "CONFIRMATION_REQUIRED" : "INVALID_REGISTRY_WORKBOOK";
        return ResponseEntity.status(error.getStatusCode()).body(java.util.Map.of("status",error.getStatusCode().value(),"code",code,
            "message",code.equals("CONFIRMATION_REQUIRED") ? "Confirm the import before saving." : "Use a supported plain Excel workbook and check the required headers and limits."));
    }
    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> missingInput() {
        return ResponseEntity.badRequest().body(java.util.Map.of("status",400,"code","INVALID_REGISTRY_REQUEST","message","Select an Excel file and submit the required fields."));
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<?> databaseUnavailable() {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("student_registry_failure reason=DATABASE_UNAVAILABLE");
        return ResponseEntity.status(503).body(java.util.Map.of("status",503,"code","REGISTRY_UNAVAILABLE","message","Student Registry is temporarily unavailable. Check the local registry setup or contact the administrator."));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) public ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(java.util.Map.of("message","Registry changed during import. Preview again; no rows were saved."));}
}
