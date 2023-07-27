package learning.helloworld;

import learning.helloworld.user.User;
import learning.helloworld.user.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class HelloWorldTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldTest.class);

  /**
   * Mybatis全局配置文件
   */
  private static final String GLOBAL_CONFIG_FILE = "learning/helloworld/hello-world.xml";

  @Test
  public void helloworld() {
    // 获取Mybatis会话工厂的建造器
    SqlSessionFactoryBuilder sqlSessionFactoryBuilder = new SqlSessionFactoryBuilder();
    // 创建Mybatis的会话工厂
    SqlSessionFactory sqlSessionFactory = sqlSessionFactoryBuilder.build(loadStream(GLOBAL_CONFIG_FILE));

    try (
      // 通过SqlSessionFactory获取一个SqlSession
      SqlSession sqlSession = sqlSessionFactory.openSession()
    ) {
      // 从mybatis中获取UserMapper代理对象
      UserMapper userMapper = sqlSession.getMapper(UserMapper.class);

      final int id = 1;
      final String name = "china";

      LOGGER.debug("在数据库新增了用户数据：id={},name={}", id, name);
      Integer updateCount = userMapper.insert(id, name);
      assert updateCount == 1;

      // 查询该用户
      User user = userMapper.getById(id);
      LOGGER.debug("获取数据用户id为{}的用户数据为：{}", id, user);

      // 回滚
      sqlSession.rollback();
    }
  }

  private InputStream loadStream(String path) {
    return HelloWorldTest.class.getClassLoader().getResourceAsStream(path);
  }

}
