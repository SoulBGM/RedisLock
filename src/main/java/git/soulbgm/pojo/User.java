package git.soulbgm.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 贺瑞杰
 * @version V1.0
 * @date 2018-08-14 17:45
 * @description
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    private int id;
    private String username;
    private int age;
    private String mail;
    private String address;

}
