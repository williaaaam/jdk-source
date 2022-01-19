/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.lang.reflect;


/**
 * ParameterizedType represents a parameterized type such as
 * Collection&lt;String&gt;.
 *
 * <p>A parameterized type is created the first time it is needed by a
 * reflective method, as specified in this package. When a
 * parameterized type p is created, the generic type declaration that
 * p instantiates is resolved, and all type arguments of p are created
 * recursively. See {@link java.lang.reflect.TypeVariable
 * TypeVariable} for details on the creation process for type
 * variables. Repeated creation of a parameterized type has no effect.
 *
 * <p>Instances of classes that implement this interface must implement
 * an equals() method that equates any two instances that share the
 * same generic type declaration and have equal type parameters.
 *
 * @since 1.5
 */
public interface ParameterizedType extends Type {
	/**
	 * Returns an array of {@code Type} objects representing the actual type
	 * arguments to this type.
	 *
	 * <p>Note that in some cases, the returned array be empty. This can occur
	 * if this type represents a non-parameterized type nested within
	 * a parameterized type.
	 *
	 * @return an array of {@code Type} objects representing the actual type
	 *     arguments to this type
	 * @throws TypeNotPresentException if any of the
	 *     actual type arguments refers to a non-existent type declaration
	 * @throws MalformedParameterizedTypeException if any of the
	 *     actual type parameters refer to a parameterized type that cannot
	 *     be instantiated for any reason
	 * @since 1.5
	 */
	/**
	 * 返回实际的参数类型，因为一个类型可以有多个参数类型，所以是一个数组
	 * 比如：List<String>返回的是String类的Class对象
	 *
	 * @return
	 */
	Type[] getActualTypeArguments();

	/**
	 * Returns the {@code Type} object representing the class or interface
	 * that declared this type.
	 *
	 * @return the {@code Type} object representing the class or interface
	 *     that declared this type
	 * @since 1.5
	 */
	/**
	 * 返回擦除类型参数后的类型
	 * List<String>返回List类的Class
	 *
	 * @return
	 */
	Type getRawType();

	/**
	 * Returns a {@code Type} object representing the type that this type
	 * is a member of.  For example, if this type is {@code O<T>.I<S>},
	 * return a representation of {@code O<T>}.
	 *
	 * <p>If this type is a top-level type, {@code null} is returned.
	 *
	 * @return a {@code Type} object representing the type that
	 *     this type is a member of. If this type is a top-level type,
	 *     {@code null} is returned
	 * @throws TypeNotPresentException if the owner type
	 *     refers to a non-existent type declaration
	 * @throws MalformedParameterizedTypeException if the owner type
	 *     refers to a parameterized type that cannot be instantiated
	 *     for any reason
	 * @since 1.5
	 */
	/**
	 * 如果当前类型定义在另一个类型的内部，则返回外部的类型；否则，则是一个顶层类型（top-level type），返回null。
	 * 比如List<String>类型的这个方法返回的是null，
	 * 但是Map.Entry<String, String>类型的这个方法返回的就是Map类的Class对象，因为Entry类定义在Map类的内部
	 *
	 * @return
	 */
	Type getOwnerType();
}
