@file:JvmName("Priorities")
package org.wenhui.prioritizeretrofit

const val PRIORITY_HIGHEST = 4
const val PRIORITY_HIGH = 2
const val PRIORITY_NORMAL = 0
const val PRIORITY_LOW = -2
const val PRIORITY_LOWEST = -4

/**
 * Use to mark a request's priority
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Priority(val value: Int)


