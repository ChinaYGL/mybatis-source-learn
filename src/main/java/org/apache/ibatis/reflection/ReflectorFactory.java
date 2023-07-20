/*
 *    Copyright 2009-2023 the original author or authors.
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
package org.apache.ibatis.reflection;

/**
 * ReflectorFactory接口主要实现对Reflector对象的创建和缓存
 */
public interface ReflectorFactory {

  /**
   * 是否开启了缓存
   * 检测该ReflectorFactory对象是否会缓存Reflector对象
   *
   * @return 真/假
   */
  boolean isClassCacheEnabled();

  /**
   * 设置是否启用缓存
   * 设置是否缓存Reflector对象
   *
   * @param classCacheEnabled 真/假
   */
  void setClassCacheEnabled(boolean classCacheEnabled);

  /**
   * 获取指定类的缓存信息
   * 创建指定Class对应的Reflector对象
   *
   * @param type 类对象
   * @return Reflector对象
   */
  Reflector findForClass(Class<?> type);
}
