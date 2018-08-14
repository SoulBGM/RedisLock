package git.soulbgm.annotation;

import java.lang.annotation.*;

/**
 * @author 贺瑞杰
 * @version V1.0
 * @date 2018-07-31 15:19:33
 * @description 方法分布式锁
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {

    /**
     * 锁持续时间 单位毫秒
     * 默认100毫秒
     *
     * @return
     */
    int lockTime() default 100;

    /**
     * redis锁的key前缀
     * 如果为空，则默认为类名+方法名
     *
     * @return
     */
    String keyPrefix() default "";

    /**
     * 超时时间 单位毫秒
     * 默认100毫秒
     *
     * @return
     */
    long timeOut() default 100L;

}
