@file:JvmName("Priorities")
package org.wenhui.prioritizeretrofit

const val PRIORITY_HIGHEST = 4
const val PRIORITY_HIGH = 2
const val PRIORITY_NORMAL = 0
const val PRIORITY_LOW = -2
const val PRIORITY_LOWEST = -4

/**
 * Mark a request's priority
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented

/**
 * @param value Priority value, should be either of [PRIORITY_LOWEST], [PRIORITY_LOW], [PRIORITY_NORMAL], [PRIORITY_HIGH],
 *  [PRIORITY_HIGHEST].
 */
annotation class Priority(val value: Int)


