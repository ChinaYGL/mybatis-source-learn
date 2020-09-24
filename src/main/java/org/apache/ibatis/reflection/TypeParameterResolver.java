/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * Mybatis中定义的一个用于解析泛型实际类型的工具类
 *
 * @author Iwao AVE!
 */
public class TypeParameterResolver {

  /**
   * Resolve field type.
   * 解析字段类型
   * 获取指定字段的实际类型，将泛型转换为实际类型
   *
   * @param field
   *          the field 字段
   * @param srcType
   *          the src type 运行时所属对象类型
   * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   *         字段的类型，如果字段在声明的时候有泛型定义，这些泛型会被解析为运行时的实际类型。
   */
  public static Type resolveFieldType(Field field, Type srcType) {
    // 获取字段的声明类型
    Type fieldType = field.getGenericType();
    // 获取字段定义所在的类的Class对象
    Class<?> declaringClass = field.getDeclaringClass();
    // 调用resolveType()方法进行后续处理，执行解析操作
    return resolveType(fieldType, srcType, declaringClass);
  }

  /**
   * Resolve return type.
   * 解析返回类型
   * 获取指定方法返回值的实际类型，将泛型转换为实际类型
   *
   * @param method
   *          the method 方法
   * @param srcType
   *          the src type 运行时所属对象类型
   * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   *         返回值的类型，如果方法返回值在声明的时候有泛型定义，这些泛型会被解析为运行时的实际类型。
   */
  public static Type resolveReturnType(Method method, Type srcType) {
    // 获取方法的形式返回类型
    Type returnType = method.getGenericReturnType();
    // 获取方法定义所在的类的Class对象
    Class<?> declaringClass = method.getDeclaringClass();
    return resolveType(returnType, srcType, declaringClass);
  }

  /**
   * Resolve param types.
   * 解析参数类型
   * 获取指定方法参数的实际类型，将泛型转换为实际类型
   *
   * @param method
   *          the method 方法
   * @param srcType
   *          the src type 运行时所属对象类型
   * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the
   *         declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   *         方法参数的类型数组，如果方法参数在声明的时候有泛型定义，这些泛型会被解析为运行时的实际类型。
   */
  public static Type[] resolveParamTypes(Method method, Type srcType) {
    Type[] paramTypes = method.getGenericParameterTypes();
    Class<?> declaringClass = method.getDeclaringClass();
    Type[] result = new Type[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      result[i] = resolveType(paramTypes[i], srcType, declaringClass);
    }
    return result;
  }

  /**
   * 将指定类型中的泛型定义转换为运行时的实际类型定义
   * @param type 需要被处理的可能包含泛型定义的类型
   * @param srcType 运行期间type所属的实例对象的类型
   * @param declaringClass 声明type定义的类
   * @return 指定类型处理完泛型定义后的类型
   */
  private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
    if (type instanceof TypeVariable) {
      // 解析类型变量，比如: 类型变量T表示具体的某一独立的类型
      return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
    } else if (type instanceof ParameterizedType) {
      // 解析参数化类型，比如：List<T> list; List<String> list;
      return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
    } else if (type instanceof GenericArrayType) {
      // 解析泛型数组，比如: List<N>[] listArray; N[];
      return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
    } else {
      // 普通类型（Class）直接返回，比如：String[]
      return type;
    }
  }

  /**
   * 解析泛型数组的实际类型
   * @param genericArrayType 泛型数组
   * @param srcType 运行期间genericArrayType所属的实例对象的类型
   * @param declaringClass 声明declaringClass定义的类
   * @return 指定类型处理完泛型定义后的类型
   */
  private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
    // 获取数组泛型中元素的泛型类型，比如: T[] testArray中的T
    Type componentType = genericArrayType.getGenericComponentType();
    // 解析元素的泛型类型
    Type resolvedComponentType = null;
    if (componentType instanceof TypeVariable) {
      // 泛型变量
      resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
    } else if (componentType instanceof GenericArrayType) {
      // 泛型数组，递归解析
      resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
    } else if (componentType instanceof ParameterizedType) {
      // 参数化类型
      resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
    }
    if (resolvedComponentType instanceof Class) {
      // 将解析出的类型转换为数组类型
      return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
    } else {
      // 解析出来的类型还是一个泛型数组
      return new GenericArrayTypeImpl(resolvedComponentType);
    }
  }

  /**
   * 解析参数化类型的实际类型
   * @param parameterizedType 参数化类型
   * @param srcType 运行期间parameterizedType所属的实例对象的类型
   * @param declaringClass 声明parameterizedType定义的类
   * @return 指定类型处理完泛型定义后的类型
   */
  private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
    // 获取参数化类型中的原始类型
    Class<?> rawType = (Class<?>) parameterizedType.getRawType();
    // 获取参数化类型的泛型变量或是实际类型列表
    Type[] typeArgs = parameterizedType.getActualTypeArguments();
    // 声明参数集合
    Type[] args = new Type[typeArgs.length];
    // 依次处理泛型参数的实际类型,比如：Map<K,V>,依次获取K和V的实际类型
    for (int i = 0; i < typeArgs.length; i++) {
      if (typeArgs[i] instanceof TypeVariable) {
        // 解析类型变量
        args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof ParameterizedType) {
        // 解析参数化类型
        args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof WildcardType) {
        // 解析通配符表达式形式的泛型，比如：List<? extends N> list中的? extends N;
        args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
      } else {
        // 普通类型（Class）
        args[i] = typeArgs[i];
      }
    }
    // 重新包装成ParameterizedTypeImpl返回
    return new ParameterizedTypeImpl(rawType, null, args);
  }

  /**
   * 解析指定通配符表达式形式的泛型
   * @param wildcardType 通配符表达式形式的泛型
   * @param srcType 运行期间wildcardType所属的实例对象的类型
   * @param declaringClass 声明wildcardType的类
   * @return 指定类型处理完泛型定义后的类型
   */
  private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
    // 获取通配符表达式形式的泛型的类型下界解析后的类型
    Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
    // 获取通配符表达式形式的泛型的类型上界解析后的类型
    Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
    // 重新包装成WildcardTypeImpl返回
    return new WildcardTypeImpl(lowerBounds, upperBounds);
  }

  /**
   * 解析通配符表达式形式的泛型的类型边界
   * @param bounds 泛型变量的下界或者上界
   * @param srcType 运行期间泛型变量所属的实例对象的类型
   * @param declaringClass 声明泛型变量的类
   * @return 处理完泛型定义后的类型
   */
  private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
    Type[] result = new Type[bounds.length];
    // 依次处理泛型边界中的每个具体的泛型定义
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] instanceof TypeVariable) {
        // 泛型变量
        result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof ParameterizedType) {
        // 参数化泛型
        result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof WildcardType) {
        // 通配符泛型
        result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
      } else {
        // 普通类型
        result[i] = bounds[i];
      }
    }
    return result;
  }

  /**
   * 解析指定泛型变量的类型
   * @param typeVar 泛型变量
   * @param srcType 运行期间typeVar所属的实例对象的类型
   * @param declaringClass 声明typeVar的类
   * @return 指定类型处理完泛型定义后的类型
   */
  private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
    Type result;
    Class<?> clazz;
    // 步骤一: 处理srcType，移除泛型定义，获取对应的Class类型
    if (srcType instanceof Class) {
      // 普通类型
      clazz = (Class<?>) srcType;
    } else if (srcType instanceof ParameterizedType) {
      // 参数化类型
      ParameterizedType parameterizedType = (ParameterizedType) srcType;
      clazz = (Class<?>) parameterizedType.getRawType();
    } else {
      throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
    }

    // 步骤二: 获取泛型定义的实际类型
    // 分支一：当前类就是声明了泛型的类
    if (clazz == declaringClass) {
      // 当前类就是声明了泛型的类，则泛型一定未被指定具体类型，获取泛型变量类型上限
      Type[] bounds = typeVar.getBounds();
      if (bounds.length > 0) {
        return bounds[0];
      }
      return Object.class;
    }

    // 分支二: 运行期间泛型所属的对象和声明泛型定义的不是同一个
    // 获取其直接父类类型，尝试从父类中获取泛型变量的实际类型
    Type superclass = clazz.getGenericSuperclass();
    // 递归处理父类，直到找到该泛型对应的实际类型，或者null值。
    result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass);
    if (result != null) {
      return result;
    }

    // 无法通过父类获取泛型变量的实际类型,则通过接口定义获取泛型变量对应的实际类型
    // 获取类直接实现的所有接口，尝试从接口中获取泛型变量的定义
    Type[] superInterfaces = clazz.getGenericInterfaces();
    for (Type superInterface : superInterfaces) {
      result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface);
      if (result != null) {
        return result;
      }
    }
    return Object.class;
  }

  /**
   * 从指定的类及其父类/接口中找到指定泛型的实际类型定义
   * @param typeVar 泛型变量
   * @param srcType 运行期间typeVar所属的实例对象的类型
   * @param declaringClass 声明typeVar的类
   * @param clazz 泛型变量所属实例对象处理泛型后的类型
   * @param superclass clazz直接父类或者实现的接口
   * @return 处理完泛型定义后的实际类型
   */
  private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
    // 针对superclass的类型定义有三种处理方案
    // 1.参数化类型
    // 2.普通类同时是声明了泛型的类的实现
    // 3.普通类但不是声明了泛型实现的类的实现
    if (superclass instanceof ParameterizedType) {
      // 分支一：参数化类型
      ParameterizedType parentAsType = (ParameterizedType) superclass;
      // 获取参数化类型中的原始类型
      Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();
      // 获取参数化类型中泛型定义
      TypeVariable<?>[] parentTypeVars = parentAsClass.getTypeParameters();
      // 泛型变量所属对象的类也是一个参数化类型
      if (srcType instanceof ParameterizedType) {
        // 合并子类和父类的泛型变量，比如: Level2Mapper extends Level1Mapper<Date, Integer>,
        // Level1Mapper<E, F> extends Level0Mapper<E, F, String>, Level0Mapper<L, M, N>。
        parentAsType = translateParentTypeVars((ParameterizedType) srcType, clazz, parentAsType);
      }
      if (declaringClass == parentAsClass) {
        // 父类就是声明了泛型变量的类
        for (int i = 0; i < parentTypeVars.length; i++) {
          if (typeVar.equals(parentTypeVars[i])) {
            return parentAsType.getActualTypeArguments()[i];
          }
        }
      }
      // 父类是声明了泛型变量的类的子类，继续递归查找
      if (declaringClass.isAssignableFrom(parentAsClass)) {
        return resolveTypeVar(typeVar, parentAsType, declaringClass);
      }
    } else if (superclass instanceof Class && declaringClass.isAssignableFrom((Class<?>) superclass)) {
      // 分支二: 父类是一个普通的类，同时父类是声明了泛型变量的类的子实现
      return resolveTypeVar(typeVar, superclass, declaringClass);
    }
    return null;
  }

  /**
   * 合并子类和父类的泛型定义
   * @param srcType 子类定义的泛型变量
   * @param srcClass 子类
   * @param parentType 父类定义的泛型变量
   * @return 合并后的泛型定义
   */
  private static ParameterizedType translateParentTypeVars(ParameterizedType srcType, Class<?> srcClass, ParameterizedType parentType) {
    // 获取父类定义的泛型变量的实际类型数组，比如：Level0Mapper<E, F, String>中的<E, F, String>
    Type[] parentTypeArgs = parentType.getActualTypeArguments();
    // 获取子类定义的泛型变量的实际类型数组，比如：Level1Mapper<Date, Integer>中的<Date, Integer>
    Type[] srcTypeArgs = srcType.getActualTypeArguments();
    // 获取子类中的泛型变量定义，比如：Level1Mapper<E, F>中的<E, F>
    TypeVariable<?>[] srcTypeVars = srcClass.getTypeParameters();
    // 父类泛型实参数组
    Type[] newParentArgs = new Type[parentTypeArgs.length];
    boolean noChange = true;
    for (int i = 0; i < parentTypeArgs.length; i++) {
      if (parentTypeArgs[i] instanceof TypeVariable) {
        // 泛型变量
        for (int j = 0; j < srcTypeVars.length; j++) {
          if (srcTypeVars[j].equals(parentTypeArgs[i])) {
            // 子类泛型定义和父类泛型定义一致,则子类中泛型变量的实参对应着父类泛型变量的实参
            noChange = false;
            // 从子类中取出泛型变量的实际类型
            newParentArgs[i] = srcTypeArgs[j];
          }
        }
      } else {
        // 父类中指定了泛型对应的实际类型
        newParentArgs[i] = parentTypeArgs[i];
      }
    }
    // 返回合并后的泛型定义
    return noChange ? parentType : new ParameterizedTypeImpl((Class<?>)parentType.getRawType(), null, newParentArgs);
  }

  private TypeParameterResolver() {
    super();
  }

  static class ParameterizedTypeImpl implements ParameterizedType {
    private Class<?> rawType;

    private Type ownerType;

    private Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
      super();
      this.rawType = rawType;
      this.ownerType = ownerType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public String toString() {
      return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
    }
  }

  static class WildcardTypeImpl implements WildcardType {
    private Type[] lowerBounds;

    private Type[] upperBounds;

    WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      super();
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds;
    }
  }

  static class GenericArrayTypeImpl implements GenericArrayType {
    private Type genericComponentType;

    GenericArrayTypeImpl(Type genericComponentType) {
      super();
      this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }
  }
}
