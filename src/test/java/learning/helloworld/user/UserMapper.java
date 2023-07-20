package learning.helloworld.user;

import org.apache.ibatis.annotations.Param;

public interface UserMapper {

  /**
   * 根据ID获取用户信息
   *
   * @param id 用户ID
   * @return 用户信息
   */
  User getById(Integer id);

  /**
   * 插入一个新的用户数据
   *
   * @param id 用户ID
   * @param name 用户名称
   * @return 本次操作影响的行数
   */
  Integer insert(@Param("id") Integer id, @Param("name") String name);
}
