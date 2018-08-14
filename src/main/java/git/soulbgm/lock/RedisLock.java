package git.soulbgm.lock;

import lombok.extern.log4j.Log4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author 贺瑞杰
 * @version V1.0
 * @date 2018-08-01 16:08
 * @description
 */
@Log4j
public class RedisLock {

    private RedisTemplate redisTemplate;

    /**
     * 调用set nx ex后成功获取锁的返回值
     */
    private static final String OK = "OK";

    /**
     * 等同于SETNX
     * 若给定的 key 已经存在，则 SETNX 不做任何动作
     */
    private static final String NX = "NX";

    /**
     * 等同于SETEX
     * 如果 key 已经存在，SETEX 命令将覆写旧值
     */
    private static final String EX = "EX";

    /**
     * 锁标志对应的key
     */
    private String lockKey;

    /**
     * 锁对应的值
     */
    private String lockValue;

    /**
     * 锁的有效时间(ms:毫秒)
     * 默认500毫秒
     */
    private int expireTime = 100;

    /**
     * 获取锁的超时时间(ms:毫秒)
     * 默认100毫秒
     */
    private long timeout = 100L;

    /**
     * 锁标识
     */
    private boolean locked = false;

    private static final String UNLOCK_LUA;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("if redis.call(\"get\",KEYS[1]) == ARGV[1] ");
        sb.append("then ");
        sb.append("    return redis.call(\"del\",KEYS[1]) ");
        sb.append("else ");
        sb.append("    return 0 ");
        sb.append("end ");
        UNLOCK_LUA = sb.toString();
    }


    /**
     * 使用默认的锁过期时间和请求锁的超时时间
     *
     * @param redisTemplate
     * @param lockKey
     */
    public RedisLock(RedisTemplate redisTemplate, String lockKey) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey;
    }

    /**
     * 使用默认的请求锁的超时时间，指定锁的过期时间
     *
     * @param redisTemplate
     * @param lockKey
     * @param expireTime
     */
    public RedisLock(RedisTemplate redisTemplate, String lockKey, int expireTime) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey;
        this.expireTime = expireTime;
    }

    /**
     * 使用默认的锁过期时间，指定请求锁的超时时间
     *
     * @param redisTemplate
     * @param lockKey
     * @param timeout
     */
    public RedisLock(RedisTemplate redisTemplate, String lockKey, long timeout) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey;
        this.timeout = timeout;
    }

    /**
     * 锁的过期时间和请求锁的超时时间都用指定的值
     *
     * @param redisTemplate
     * @param lockKey
     * @param expireTime
     * @param timeout
     */
    public RedisLock(RedisTemplate redisTemplate, String lockKey, int expireTime, long timeout) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey;
        this.expireTime = expireTime;
        this.timeout = timeout;
    }

    /**
     * 尝试获取锁 立即返回
     *
     * @return
     */
    public boolean lock() {
        lockValue = UUID.randomUUID().toString();
        String result = set(lockKey, lockValue, expireTime);
        locked = OK.equalsIgnoreCase(result);
        return locked;
    }

    /**
     * 以阻塞方式获取锁
     *
     * @return
     */
    public boolean lockBlock() {
        lockValue = UUID.randomUUID().toString();
        while (true) {
            // 不存在则添加 且设置过期时间
            String result = set(lockKey, lockValue, expireTime);
            if (OK.equalsIgnoreCase(result)) {
                locked = true;
                return true;
            }
            // 每次请求等待一段时间
            sleep(10, 400);
        }
    }

    /**
     * 尝试获取锁 超时返回
     *
     * @return
     */
    public boolean tryBlock() {
        lockValue = UUID.randomUUID().toString();
        // 将毫秒转为纳秒
        long timeoutNano = timeout * 1000000;
        // 系统当前时间，纳秒
        long nowTime = System.nanoTime();
        while ((System.nanoTime() - nowTime) < timeoutNano) {
            String result = set(lockKey, lockValue, expireTime);
            if (OK.equalsIgnoreCase(result)) {
                locked = true;
                // 上锁成功结束请求
                return true;
            }
            // 每次请求等待一段时间
            sleep(10, 400);
        }
        return locked;
    }

    /**
     * 解锁
     * 可以通过以下修改，让这个锁实现更健壮：
     * 不使用固定的字符串作为键的值，而是设置一个不可猜测（non-guessable）的长随机字符串，作为口令串（token）。
     * 不使用 DEL 命令来释放锁，而是发送一个 Lua 脚本，这个脚本只在客户端传入的值和键的口令串相匹配时，才对键进行删除。
     * 这两个改动可以防止持有过期锁的客户端误删现有锁的情况出现。
     *
     * @return
     */
    public boolean unlock() {
        // 只有加锁成功并且锁还有效才去释放锁
        if (locked) {
            return (boolean) redisTemplate.execute(new RedisCallback<Boolean>() {

                @Override
                public Boolean doInRedis(RedisConnection connection) throws DataAccessException {
                    List<String> keys = new ArrayList<>();
                    keys.add(lockKey);

                    List<String> values = new ArrayList<>();
                    values.add(lockValue);

                    Object nativeConnection = connection.getNativeConnection();
                    Long result = 0L;
                    // 集群模式
                    if (nativeConnection instanceof JedisCluster) {
                        result = (Long) ((JedisCluster) nativeConnection).eval(UNLOCK_LUA, keys, values);
                    }
                    // 单机模式
                    if (nativeConnection instanceof Jedis) {
                        result = (Long) ((Jedis) nativeConnection).eval(UNLOCK_LUA, keys, values);
                    }

                    if (result == 0) {
                        log.warn("Redis分布式锁，解锁" + lockKey + "失败！解锁时间：" + System.currentTimeMillis());
                    }

                    locked = (result == 0);

                    return result == 1;
                }
            });
        }
        return true;
    }

    /**
     * Redis 中实现锁的简单方法
     * Redis实现命令：set key value NX EX 过期时间（单位秒）
     *
     * @param key
     * @param value
     * @param seconds
     * @return 如果服务器返回 OK ，那么这个客户端获得锁，如果服务器返回 NIL ，那么客户端获取锁失败，可以在稍后再重试。
     */
    public String set(String key, String value, long seconds) {
        return (String) redisTemplate.execute(new RedisCallback<String>() {
            @Override
            public String doInRedis(RedisConnection connection) throws DataAccessException {
                Object nativeConnection = connection.getNativeConnection();
                String result = null;
                // 集群模式
                if (nativeConnection instanceof JedisCluster) {
                    result = ((JedisCluster) nativeConnection).set(key, value, NX, EX, seconds);
                }
                // 单机模式
                if (nativeConnection instanceof Jedis) {
                    result = ((Jedis) nativeConnection).set(key, value, NX, EX, seconds);
                }
                return result;
            }
        });
    }

    /**
     * 重新设置过期时间
     *
     * @param key        键
     * @param expireTime 过期时间（毫秒）
     * @return 重新设置的过期时间
     */
    public long expire(String key, int expireTime) {
        return (Long) redisTemplate.execute(new RedisCallback<Long>() {
            @Override
            public Long doInRedis(RedisConnection connection) throws DataAccessException {
                Object nativeConnection = connection.getNativeConnection();
                Long result = null;
                // 集群模式
                if (nativeConnection instanceof JedisCluster) {
                    result = ((JedisCluster) nativeConnection).expire(key, expireTime);
                }
                // 单机模式
                if (nativeConnection instanceof Jedis) {
                    result = ((Jedis) nativeConnection).expire(key, expireTime);
                }
                return result;
            }
        });
    }

    /**
     * 线程等待时间
     *
     * @param millis 毫秒
     * @param nanos  纳秒
     */
    private void sleep(long millis, int nanos) {
        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            log.error("获取分布式锁休眠被中断：", e);
        }
    }

}
