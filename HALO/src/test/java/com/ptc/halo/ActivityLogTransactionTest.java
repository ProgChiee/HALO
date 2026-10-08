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
  when(repository.findAdminLogs(eq(Role.ADMIN), isNull(), eq(""), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(log)));
  when(log.getUser()).thenAnswer(inv -> {
   if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new LazyInitializationException("no persistence context");
   return user;
  });
  var service = new ActivityLogService(repository);
  assertThrows(LazyInitializationException.class, () -> service.getLogs(Role.ADMIN, null, 0, 20, ""));
  var factory = new ProxyFactory(service); factory.setProxyTargetClass(true);
  factory.addAdvice(new TransactionInterceptor(new StudentLessonTransactionTest.TestTransactions(), new AnnotationTransactionAttributeSource()));
  var results = ((ActivityLogService) factory.getProxy()).getLogs(Role.ADMIN, null, 0, 20, "");
  assertEquals("Admin", results.getContent().get(0).getUserName()); assertEquals("test@example.test", results.getContent().get(0).getUserEmail());
  assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
 }
}
