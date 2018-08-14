package git.soulbgm.service;

import git.soulbgm.annotation.Lock;
import git.soulbgm.annotation.LockKey;
import git.soulbgm.pojo.User;
import org.springframework.stereotype.Service;

/**
 * @author 贺瑞杰
 * @version V1.0
 * @date 2018-08-14 17:44
 * @description
 */
@Service
public class RedisLockService {

    @Lock
    public User updateUser(@LockKey(keyField = "id") User user){
        user.setAddress("北京");
        return user;
    }

}
