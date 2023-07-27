/*
 *    Copyright 2009-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.session;

import java.sql.Connection;

/**
 * Creates an {@link SqlSession} out of a connection or a DataSource
 *
 * @author Clinton Begin
 */
public interface SqlSessionFactory {

  /**
   * 创建SqlSession对象
   *
   * @return 结果
   */
  SqlSession openSession();

  /**
   * 创建SqlSession对象
   *
   * @param autoCommit 是否自动提交事务
   * @return 结果
   */
  SqlSession openSession(boolean autoCommit);

  /**
   * 创建SqlSession对象
   *
   * @param connection 数据库连接
   * @return 结果
   */
  SqlSession openSession(Connection connection);

  /**
   * 创建SqlSession对象
   *
   * @param level 事务的隔离级别
   * @return 结果
   */
  SqlSession openSession(TransactionIsolationLevel level);

  /**
   * 创建SqlSession对象
   *
   * @param execType 底层使用Executor的类型
   * @return 结果
   */
  SqlSession openSession(ExecutorType execType);

  /**
   * 创建SqlSession对象
   *
   * @param execType 底层使用Executor的类型
   * @param autoCommit 是否自动提交事务
   * @return 结果
   */
  SqlSession openSession(ExecutorType execType, boolean autoCommit);

  /**
   * 创建SqlSession对象
   *
   * @param execType 底层使用Executor的类型
   * @param level 事务的隔离级别
   * @return 结果
   */
  SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level);

  /**
   * 创建SqlSession对象
   *
   * @param execType 底层使用Executor的类型
   * @param connection 数据库连接
   * @return 结果
   */
  SqlSession openSession(ExecutorType execType, Connection connection);

  /**
   * 获取配置Configuration对象
   *
   * @return 结果
   */
  Configuration getConfiguration();

}
