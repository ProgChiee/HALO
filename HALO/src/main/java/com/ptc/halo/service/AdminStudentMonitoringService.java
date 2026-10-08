package com.ptc.halo.service;
import com.ptc.halo.repository.AdminMonitoringRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.util.List;
@Service
public class AdminStudentMonitoringService {
 private final AdminMonitoringRepository repository;
 public AdminStudentMonitoringService(AdminMonitoringRepository repository) { this.repository=repository; }
 public record Result(List<AdminMonitoringRepository.StudentRow> content, int number, int size, long totalElements, int totalPages,
        AdminMonitoringRepository.StudentTotals summary, List<AdminMonitoringRepository.StudentRow> highlights) {}
 @Transactional(readOnly=true)
 public Result getStudentMonitoring(int page, int size) {
  var result=repository.students(PageRequest.of(page, Math.min(size,100)));
  return new Result(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
      repository.studentTotals(), repository.lowestStudents(PageRequest.of(0,8)));
 }
}
