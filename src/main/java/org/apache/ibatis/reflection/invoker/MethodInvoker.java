/*
 *    Copyright 2009-2021 the original author or authors.
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
import java.lang.reflect.Method;

import org.apache.ibatis.reflection.Reflector;

/**
 * 用于执行方法的反射操作
 *
 * @author Clinton Begin
 */
public class MethodInvoker implements Invoker {

  /**
   * 如果方法是getter方法，则表示返回值类型
   * 如果方法是setter方法，则表示入参类型
   */
  private final Class<?> type;
  /**
   * 方法本身
   */
  private final Method method;

  public MethodInvoker(Method method) {
    this.method = method;

    if (method.getParameterTypes().length == 1) {
      // 有方法入参，表示是setter方法
      type = method.getParameterTypes()[0];
    } else {
      // 无入参表示是getter方法
      type = method.getReturnType();
    }
  }

  @Override
  public Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
    try {
      // 执行方法
      return method.invoke(target, args);
    } catch (IllegalAccessException e) {
      if (Reflector.canControlMemberAccessible()) {
        // 赋予访问权限之后重试
        method.setAccessible(true);
        return method.invoke(target, args);
      } else {
        throw e;
      }
    }
  }

  @Override
  public Class<?> getType() {
    return type;
  }
}
