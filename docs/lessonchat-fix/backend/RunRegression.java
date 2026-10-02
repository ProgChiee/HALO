import java.lang.reflect.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.TestContextManager;
public class RunRegression {
 public static void main(String[] args) throws Exception {
  int passed=0, failed=0;
  for(String name: args){
   Class<?> c=Class.forName(name);
   boolean spring=name.endsWith("HttpTest");
   TestContextManager context=spring?new TestContextManager(c):null;
   if(spring)context.beforeTestClass();
   for(Method test:c.getDeclaredMethods())if(test.isAnnotationPresent(Test.class)){
    Constructor<?> ctor=c.getDeclaredConstructor();ctor.setAccessible(true);Object instance=ctor.newInstance();
    Throwable problem=null;
    try {
     if(spring){context.prepareTestInstance(instance);context.beforeTestMethod(instance,test);context.beforeTestExecution(instance,test);}
     for(Method setup:c.getDeclaredMethods())if(setup.isAnnotationPresent(BeforeEach.class)){setup.setAccessible(true);setup.invoke(instance);}
     test.setAccessible(true);test.invoke(instance);passed++;System.out.println("PASS "+test.getName());
    }catch(Throwable e){problem=e instanceof InvocationTargetException?e.getCause():e;failed++;problem.printStackTrace();}
    finally{if(spring){context.afterTestExecution(instance,test,problem);context.afterTestMethod(instance,test,problem);}}
   }
   if(spring)context.afterTestClass();
  }
  System.out.println("RESULT passed="+passed+" failed="+failed);if(failed>0)System.exit(1);
 }
}