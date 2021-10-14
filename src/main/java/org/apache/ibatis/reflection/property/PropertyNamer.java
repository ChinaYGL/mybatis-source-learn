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
package org.apache.ibatis.reflection.property;

import java.util.Locale;

import org.apache.ibatis.reflection.ReflectionException;

/**
 * 处理属性的工具类，主要用于获取getter/setter方法对应的属性名称，
 * 校验方法名称是否是getter/setter方法，以及判断方法可以获取属性名称。
 * @author Clinton Begin
 */
public final class PropertyNamer {

  private PropertyNamer() {
    // Prevent Instantiation of Static Class
  }

  /**
   * 方法名转换为属性名称
   * @param name
   * @return
   */
  public static String methodToProperty(String name) {
    // 只处理is/get/set开头的方法
    if (name.startsWith("is")) {
      name = name.substring(2);
    } else if (name.startsWith("get") || name.startsWith("set")) {
      name = name.substring(3);
    } else {
      throw new ReflectionException("Error parsing property name '" + name + "'.  Didn't start with 'is', 'get' or 'set'.");
    }

    // 在JavaBean规范中，通常会将获取到的属性名称的第一个字母转为小写
    // 但是为了支持偶尔出现的全大写的名称，检查前两个字符是否都是大写，如果是的话，就不进行处理。
    // 属性名称首字母小写，比如：Name -> name, MYName -> MYName。
    // 属性名称长度为1，或者属性名称长度大于1的同时属性名称的第二个字符不是大写的。
    if (name.length() == 1 || (name.length() > 1 && !Character.isUpperCase(name.charAt(1)))) {
      // 首字母小写
      name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
    }

    return name;
  }

  /**
   * 判断方法名可以获取属性名称
   * @param name
   * @return
   */
  public static boolean isProperty(String name) {
    return isGetter(name) || isSetter(name);
  }

  /**
   * 判断方法是否为getter方法
   * @param name
   * @return
   */
  public static boolean isGetter(String name) {
    return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
  }

  /**
   * 判断方法是否为setter方法
   * @param name
   * @return
   */
  public static boolean isSetter(String name) {
    return name.startsWith("set") && name.length() > 3;
  }

}
