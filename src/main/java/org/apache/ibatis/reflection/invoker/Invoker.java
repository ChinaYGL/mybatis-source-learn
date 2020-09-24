/**
 *    Copyright 2009-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;

/**
 * Mybatis中定义的用于执行方法或字段反射操作的类
 * 作用是统一基于反射处理方法/字段的调用方式，它是适配器模式的一种实现。
 * 适配器模式（包装模式）将一个类的接口适配成用户所期待的，是将原本由于接口不兼容而不能一起工作的类可以一起工作。
 *
 * @author Clinton Begin
 */
public interface Invoker {

  /**
   * 执行反射操作
   * 调用获取指定字段的值或执行指定的方法，执行反射操作，获取响应的操作结果。
   *
   * @param target 方法/字段执行的目标对象
   * @param args 方法/字段执行时依赖的参数
   * @return 执行结果
   * @throws IllegalAccessException 无访问权限
   * @throws InvocationTargetException 反射执行异常
   */
  Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;

  /**
   * 返回方法/字段对应的类型
   * @return 类型对象
   */
  Class<?> getType();
}
