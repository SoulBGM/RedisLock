package git.soulbgm.lock;

import lombok.extern.log4j.Log4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import git.soulbgm.annotation.Lock;
import git.soulbgm.annotation.LockKey;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author 贺瑞杰
 * @version V1.0
 * @date 2018-07-31 15:16
 * @description 分布式锁管理
 * 大致实现思路如下：
 * 1、首先拦截下所有带有@Lock注解的方法
 * 2、获得方法的参数
 * 3、通过方法参数的值拼接Redis的key
 * 4、然后使用set key value NX EX 过期时间（单位秒）命令
 * 5、最后使用完成解锁
 */
@Aspect
@Component
@Log4j
public class LockManager {

    @Autowired
    RedisTemplate redisTemplate;

    /**
     * 延缓的时间
     */
    private final int postponeTime = 5;

    @Around(value = "@annotation(lock)", argNames = "pjp, lock")
    public Object around(ProceedingJoinPoint pjp, Lock lock) throws Exception {
        // 获得目标bean的class
        Class<?> clazz = pjp.getTarget().getClass();
        // 获得目标方法名称
        String methodName = pjp.getSignature().getName();
        // 获得参数
        Object[] params = pjp.getArgs();
        // 获得方法
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        Method method = ms.getMethod();

        // key的前缀
        String keyPrefix = lock.keyPrefix();
        if ("".equals(keyPrefix)) {
            // 如果keyPrefix为空 则默认keyPrefix=当前类名+方法名
            keyPrefix = clazz.getName() + ":" + methodName;
        }

        // 分布式事务锁的key
        StringBuilder lockKeyStr = new StringBuilder("lock:");
        lockKeyStr.append(keyPrefix);
        // 获得加注解的方法参数的值
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            Annotation[] parameterAnnotation = parameterAnnotations[i];
            for (Annotation annotation : parameterAnnotation) {
                // 如果参数上的注解类型等于LockKey类
                if (annotation.annotationType() == LockKey.class) {
                    LockKey lockKey = (LockKey) annotation;
                    // 判断方法的参数中是否有LockKey的注解
                    if (lockKey.keyField().length > 0) {
                        // 如果有则将该参数model中的字段值追加上
                        lockKeyStr.append(getModelKey(params[i], lockKey.keyField()));
                    } else {
                        // 如果没有则直接将参数的值添加进去
                        lockKeyStr.append(":");
                        lockKeyStr.append(params[i]);
                    }
                }
            }
        }

        RedisLock redisLock = new RedisLock(redisTemplate, lockKeyStr.toString(), lock.lockTime(), lock.timeOut());
        Object result = null;
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        // 获得锁
        if (redisLock.lock()) {
            service.scheduleAtFixedRate(() -> {
                redisLock.expire(lockKeyStr.toString(), lock.lockTime());
                log.info("key为{ " + lockKeyStr.toString() + " }的锁重新设置了过期时间");
            }, lock.lockTime() - postponeTime, lock.lockTime() - postponeTime, TimeUnit.MILLISECONDS);
            Thread.sleep(lock.lockTime());
            try {
                result = pjp.proceed();
            } catch (Exception e) {
                throw e;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            } finally {
                boolean unlock = redisLock.unlock();
                if (!unlock) {
                    log.warn("释放分布式锁失败, key=" + lockKeyStr.toString());
                }
                service.shutdown();
            }
        } else {
            throw new Exception("获取分布式锁失败, key=" + lockKeyStr.toString());
        }
        return result;
    }

    /**
     * 获得参数的key
     *
     * @param model
     * @param fields
     * @return
     */
    private String getModelKey(Object model, String[] fields) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String fieldStr : fields) {
            stringBuilder.append(":");
            try {
                Field field = model.getClass().getDeclaredField(fieldStr);
                boolean isBoolean = field.getType() == boolean.class;
                // 执行get方法 获得值
                Object val = model.getClass().getMethod(buildGetMethod(fieldStr, isBoolean)).invoke(model);
                stringBuilder.append(val);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
        return stringBuilder.toString();
    }

    /**
     * 通过给字段名称构建出相应的get方法名称
     *
     * @param field 字段名称
     * @return
     */
    private String buildGetMethod(String field, boolean isBoolean) {
        StringBuilder sb = new StringBuilder();
        if (isBoolean) {
            sb.append("is");
        } else {
            sb.append("get");
        }
        sb.append(Character.toUpperCase(field.charAt(0)));
        sb.append(field.substring(1));
        return sb.toString();
    }

}
