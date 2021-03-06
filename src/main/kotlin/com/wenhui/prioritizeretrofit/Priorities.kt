package com.wenhui.prioritizeretrofit


/**
 * Mark a request's priority
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Priority(val value: Priorities)

/**
 * Priority's value for [Priority]
 */
enum class Priorities(internal val value: Int) {
    HIGHEST(4),
    HIGH(2),
    NORMAL(0),
    LOW(-2),
    LOWEST(-4)
}

