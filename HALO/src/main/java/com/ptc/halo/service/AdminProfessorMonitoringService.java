package com.ptc.halo.service;
import com.ptc.halo.repository.AdminMonitoringRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.util.List;
@Service
public class AdminProfessorMonitoringService {
 private final AdminMonitoringRepository repository;
 public AdminProfessorMonitoringService(AdminMonitoringRepository repository) { this.repository=repository; }
 public record Result(List<AdminMonitoringRepository.ProfessorRow> content, int number, int size, long totalElements, int totalPages,
        AdminMonitoringRepository.ProfessorTotals summary, List<AdminMonitoringRepository.ProfessorRow> highlights) {}
 @Transactional(readOnly=true)
 public Result getProfessorMonitoring(int page, int size) {
  var result=repository.professors(PageRequest.of(page, Math.min(size,100)));
  return new Result(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
      repository.professorTotals(), repository.recentProfessors(PageRequest.of(0,8)));
 }
}
