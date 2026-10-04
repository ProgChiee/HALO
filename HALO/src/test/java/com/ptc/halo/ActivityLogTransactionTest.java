package com.ptc.halo;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.Role;
import com.ptc.halo.repository.ActivityLogRepository;
import com.ptc.halo.service.ActivityLogService;
import org.junit.jupiter.api.Test;
import org.hibernate.LazyInitializationException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivityLogTransactionTest {
 @Test void lazyUserMappingOccursInsideReadOnlyTransaction() {
  var repository = mock(ActivityLogRepository.class); var log = mock(ActivityLogEntity.class);
  var user = new UserEntity(); user.setName("Admin"); user.setEmail("test@example.test"); user.setRole(Role.ADMIN);
  when(repository.findByUser_RoleOrderByCreatedAtDesc(Role.ADMIN)).thenReturn(java.util.List.of(log));
  when(log.getUser()).thenAnswer(inv -> {
   if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new LazyInitializationException("no persistence context");
   return user;
  });
  var service = new ActivityLogService(repository);
  assertThrows(LazyInitializationException.class, () -> service.getLogs(Role.ADMIN, null));
  var factory = new ProxyFactory(service); factory.setProxyTargetClass(true);
  factory.addAdvice(new TransactionInterceptor(new StudentLessonTransactionTest.TestTransactions(), new AnnotationTransactionAttributeSource()));
  var results = ((ActivityLogService) factory.getProxy()).getLogs(Role.ADMIN, null);
  assertEquals("Admin", results.get(0).getUserName()); assertEquals("test@example.test", results.get(0).getUserEmail());
  assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
 }
}
