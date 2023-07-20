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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.util.MapUtil;

/**
 * This class represents a cached set of class definition information that allows for easy mapping between property
 * names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  private static final MethodHandle isRecordMethodHandle = getIsRecordMethodHandle();

  /**
   * 对应的Class类型
   */
  private final Class<?> type;

  /**
   * 可读属性的名称集合，可读属性就是存在相应getter方法的属性
   */
  private final String[] readablePropertyNames;

  /**
   * 可写属性的名称集合，可写属性就是存在setter方法的属性
   */
  private final String[] writablePropertyNames;

  /**
   * 记录了属性相应的setter方法，key是属性名称，value是Invoker对象，它是对setter方法对应Method对象的封装
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();

  /**
   * 记录了属性相应的getter方法，key是属性名称，value也是Invoker对象
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();

  /**
   * 记录了属性相应setter方法的参数值类型，key是属性名称，value是setter方法的参数类型
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();

  /**
   * 记录了属性相应getter方法的返回值类型，key是属性名称，value是getter方法的返回值类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();

  /**
   * 记录了默认构造方法
   */
  private Constructor<?> defaultConstructor;

  /**
   * 记录了所有属性名称的集合
   */
  private final Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    // 初始化type字段
    type = clazz;
    // 查找clazz的默认构造方法（无参构造方法），具体实现是通过反射遍历所有构造方法
    addDefaultConstructor(clazz);
    // 获取指定类以及其父类和接口中定义的所有方法，这里的所有方法指排除掉被子类重写的方法后的方法集合
    Method[] classMethods = getClassMethods(clazz);
    if (isRecord(type)) {
      addRecordGetMethods(classMethods);
    } else {
      // 处理clazz中的getter方法，填充getMethods集合和getTypes集合
      addGetMethods(classMethods);
      // 处理clazz中的setter方法，填充setMethods集合和setTypes集合
      addSetMethods(classMethods);
      // 处理没有getter/setter方法的字段
      addFields(clazz);
    }
    // 根据getMothods/setMothods集合，初始化可读/可写属性的名称集合
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    // 初始化caseInsensitivePropertyMap集合，其中记录了所有大写格式的属性名称
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addRecordGetMethods(Method[] methods) {
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0)
        .forEach(m -> addGetMethod(m.getName(), m, false));
  }

  private void addDefaultConstructor(Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0).findAny()
        .ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  private void addGetMethods(Method[] methods) {
    // conflictingGetters集合用于解决方法冲突，key为属性名称，value为相应的getter方法集合，
    // 因为子类可能覆盖(重写或者重载)父类的getter方法，所以同一属性名称可能存在多个getter方法
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    //  步骤1：根据JavaBean规范查找getter方法，并记录到conflictingGetters集合中
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
        .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 步骤2：对conflictingGetters集合进行处理
    resolveGetterConflicts(conflictingGetters);
  }

  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 期望能够从重载的方法中获取最终的候选方法
    // 最优getter方法计算规则如下：
    //    如果属性对应的多个方法的返回值类型一致，表示该属性同时有get和is开头的两个方法，根据JavaBean规范，
    //    除了boolean类型的属性，其他类型的属性不允许有两种getter方法，如果是boolean类型的属性，优先使用is开头的方法。
    //    如果属性对应的多个方法的返回值类型不一致，那么多个方法返回值类型之间必须存在继承关系，
    //    此时就尽可能的选择返回值类型更精准的方法作为最优方法
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      // 保存最终采用的方法
      Method winner = null;
      // 当前属性名称
      String propName = entry.getKey();
      // 是否含糊标示，默认值false
      boolean isAmbiguous = false;
      for (Method candidate : entry.getValue()) {
        // 处理每一个有效的方法
        if (winner == null) {
          // 第一个方法赋值后跳出
          winner = candidate;
          continue;
        }
        // 获取目前胜出的方法的返回值类型
        Class<?> winnerType = winner.getReturnType();
        // 获取当前侯选方法的返回值类型
        Class<?> candidateType = candidate.getReturnType();
        if (candidateType.equals(winnerType)) {
          // 两个方法的返回值类型一致，但不是boolean类型
          if (!boolean.class.equals(candidateType)) {
            // 这里单独拆出boolean类型，是因为在JavaBean规范中，它可以同时有get*()和is*()方法。
            // 非boolean类型的属性同时有两个getter方法，比如name属性同时有getName()和isName()方法，
            // 二义性，是否含糊标识为真，跳出
            isAmbiguous = true;
            break;
          }
          if (candidate.getName().startsWith("is")) {
            // 针对boolean类型的属性，如果同时有get*()和is*()方法优先选择is开头方法。
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // 当前候选方法的返回值类型是目前胜出的方法的返回值类型的超类，不需要处理，使用范围较小的子类。
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // 当前候选方法的返回值类型是目前胜出的方法的返回值类型的子类，更新胜出的方法
          winner = candidate;
        } else {
          // 两个方法的返回值类型无关，二义性，是否含糊标识为真，跳出
          isAmbiguous = true;
          break;
        }
      }
      // 根据是否含糊标识抛出异常或者完成对getMethods集合和getTypes集合的填充
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    // 根据是否含糊标识进行不同的对象封装
    MethodInvoker invoker = isAmbiguous ? new AmbiguousMethodInvoker(method, MessageFormat.format(
        "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
        name, method.getDeclaringClass().getName())) : new MethodInvoker(method);
    // 将属性名称以及对应的MethodInvoker对象添加到getMethods集合中
    getMethods.put(name, invoker);
    // 获取返回值的Type
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    // 将属性名称及其getter方法的返回值类型添加到getTypes集合中
    getTypes.put(name, typeToClass(returnType));
  }

  private void addSetMethods(Method[] methods) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
        .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    if (isValidPropertyName(name)) {
      // 如果不存在指定属性名称对应的方法列表，则新建一个方法列表
      List<Method> list = MapUtil.computeIfAbsent(conflictingMethods, name, k -> new ArrayList<>());
      // 保存方法
      list.add(method);
    }
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    // 处理每个属性对应的setter方法集合
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      // 属性名称
      String propName = entry.getKey();
      // 获取属性名称对应的setter方法集合
      List<Method> setters = entry.getValue();
      // 尝试从getTypes中获取到属性名称对应的类型
      Class<?> getterType = getTypes.get(propName);
      // 尝试从getMothds中获取属性名称对应的方法封装对象，并判断是否含糊
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      // setter方法是否含糊，初始值为假
      boolean isSetterAmbiguous = false;
      // 最终匹配的方法
      Method match = null;
      for (Method setter : setters) {
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // getter方法没有二义性并且setter方法入参类型和getter返回值类型一致
          // should be the best match
          match = setter;
          break;
        }
        if (!isSetterAmbiguous) {
          // 尝试获取两个方法中更好的setter方法
          match = pickBetterSetter(match, setter, propName);
          // 由于二义性无法获取更好的setter方法，是否含糊为真
          isSetterAmbiguous = match == null;
        }
      }
      if (match != null) {
        // 完成对setMothds集合和setTypes集合填充
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    // 分别获取两个方法的入参
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    // 范围较小的子类
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    }
    if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    //如果两个方法的入参不相关，封装信息填充到setMothds集合和setTypes集合
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.", property,
            setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    setMethods.put(property, invoker);
    // 获取方法1入参的Type数组
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    // 将属性名称及入参类型添加到setTypes集合中
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    // 将属性名称以及对应的MethodInvoker对象添加到setMethods集合中
    setMethods.put(name, invoker);
    // 获取入参的Type
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    // 将属性名称及其setter方法的入参类型添加到setTypes集合中
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  private Class<?> typeToClass(Type src) {
    // 处理逻辑如下：
    //    如果src就是一个普通的Class，直接返回该类型即可，
    //    如果src是一个ParameterizedType(参数化泛型)，获取该参数化泛型所属类型，例如：泛型K的定义是List<K>，那么就得到List。
    //    如果src是一个GenericArrayType(泛型数组)，获取该数组元素的泛型定义，如果不是泛型直接返回元素类型即可，如果是泛型递归调用typeToClass方法。
    //    如果src不满足上面任何一种条件，则返回Object.class。
    Class<?> result = null;
    if (src instanceof Class) {
      // 普通对象
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      // 处理参数化泛型，获取原始类型（泛型<>前面的值），例如：Map<K,V>得到Map
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      // 获取泛型数组的声明类型（数组[]前面的值），例如List<T>[][]得到List<T>[]
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        // 泛型数组声明类型是普通对象
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        // 泛型数组声明类型还是泛型
        // 递归处理
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      // TypeVariable类型或者WildcardType类型
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        if ((!Modifier.isFinal(modifiers) || !Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 检验是否为有效的属性名称
   * @param name 名称
   * @return 结果
   */
  private boolean isValidPropertyName(String name) {
    return (!name.startsWith("$") && !"serialVersionUID".equals(name) && !"class".equals(name));
  }

  /**
   * This method returns an array containing all methods declared in this class and any superclass. We use this method,
   * instead of the simpler <code>Class.getMethods()</code>, because we want to look for private methods as well.
   *
   * @param clazz
   *          The class
   *
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    // 用于记录指定类中定义的全部方法的唯一签名以及对应的Method对象
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    while (currentClass != null && currentClass != Object.class) {
      // 记录currentClass这个类中定义的全部方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // 记录接口中定义的方法
      // we also need to look for interface methods -
      // because the class may be abstract
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      // 获取父类，继续while循环
      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  /**
   * 该方法会为methods数组中的每一个方法生成唯一签名，并保存到uniqueMethods集合中
   * @param uniqueMethods
   * @param methods
   */
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // 跳过JDK自动生成的桥接方法
      if (!currentMethod.isBridge()) {
        // 通过getSignature()方法得到的方法签名是全局唯一的，可以作为该方法的唯一标识
        String signature = getSignature(currentMethod);
        // 检测是否在子类中已经添加过该方法，如果在子类中已经添加过，则表示子类覆盖了该方法，
        // 无须再向uniqueMethods集合中添加该方法了
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        if (!uniqueMethods.containsKey(signature)) {
          // 记录该签名和方法的对应关系
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 获得的签名格式是：返回值类型#方法名称:参数类型列表（以,分隔）
   * 例如，本方法的唯一签名是：
   * java.lang.String#getSignature:java.lang.reflect.Method
   * @param method
   * @return
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   *
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    }
    throw new ReflectionException("There is no default constructor for " + type);
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName
   *          - the name of the property
   *
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName
   *          - the name of the property
   *
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName
   *          - the name of the property to check
   *
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName
   *          - the name of the property to check
   *
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }

  /**
   * Class.isRecord() alternative for Java 15 and older.
   */
  private static boolean isRecord(Class<?> clazz) {
    try {
      return isRecordMethodHandle != null && (boolean) isRecordMethodHandle.invokeExact(clazz);
    } catch (Throwable e) {
      throw new ReflectionException("Failed to invoke 'Class.isRecord()'.", e);
    }
  }

  private static MethodHandle getIsRecordMethodHandle() {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodType mt = MethodType.methodType(boolean.class);
    try {
      return lookup.findVirtual(Class.class, "isRecord", mt);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }
}
