package git.soulbgm.annotation;

import git.soulbgm.lock.LockManager;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author 贺瑞杰
 * @version V1.0
 * @date 2018-07-31 15:17:40
 * @description 开启分布式锁注解 #{@link Lock}
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(LockManager.class)
public @interface EnableLock {
}
