package git.soulbgm.annotation;

import java.lang.annotation.*;

/**
 * @author 贺瑞杰
 * @version V1.0
 * @date 2018-07-31 15:27:56
 * @description 被注解参数会作为分布式锁的key的一部分 支持多个参数
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LockKey {

    /**
     * user id = 1  key:xf.dispatcher.simulation.RedisLock:updateUser:1:zhangsan 设置过期时间
     * user id = 2  key:xf.dispatcher.simulation.RedisLock:updateUser:2
     *
     * 用在model参数前时，需指定用作key的字段名
     * e.g：@LockKey(keyField = {"id","name"})
     *
     * @return
     */
    String[] keyField() default {};
}
