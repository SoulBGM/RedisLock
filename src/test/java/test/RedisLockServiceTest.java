package test;

import git.soulbgm.Application;
import git.soulbgm.pojo.User;
import git.soulbgm.service.RedisLockService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author 贺瑞杰
 * @version V1.0
 * @date 2018-08-14 17:48
 * @description
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class RedisLockServiceTest {

    @Autowired
    private RedisLockService redisLockService;

    @Test
    public void updateUserTest() {
        User user = new User(1, "zhangsan", 18, "12255@qq.com", "不知道");
        System.out.println(redisLockService.updateUser(user));
    }

}
