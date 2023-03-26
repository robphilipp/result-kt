package com.digitalcipher.result

import java.util.Optional

/**
 * Success biased.
 */
abstract class Result<S, F> {

    fun projection() = BaseFailureProjection(this)

    fun <C> fold(successFn: (success: S) -> C, failureFn: (failure: F) -> C): C =
        if (this is BaseSuccess) successFn(success()) else failureFn((this as BaseFailure).failure())

    fun swap(): Result<F, S> =
        if (this is BaseSuccess) BaseFailure(success()) else BaseSuccess((this as BaseFailure).failure())

    fun <U> foreach(effectFn: (success: S) -> U) {
        if (this is BaseSuccess) effectFn(success())
    }

    fun getOrElse(supplier: () -> S): S =
        if (this is BaseSuccess) success() else supplier()

    fun orElse(supplier: () -> Result<S, F>): Result<S, F> =
        if (this is BaseSuccess) this else supplier()

    fun contains(elem: S): Boolean =
        if (this is BaseSuccess) success() == elem else false

    fun forall(predicate: (S) -> Boolean): Boolean =
        if (this is BaseSuccess) predicate(success()) else true

    fun exists(predicate: (S) -> Boolean): Boolean =
        if (this is BaseSuccess) predicate(success()) else false

    /**
     *
     * Note: suppress unchecked cast because the result must be either a success
     *       or a failure, and the compiler doesn't know that and thinks the
     *       else is possible.
     */
    @Suppress("UNCHECKED_CAST")
    fun <S1> flatMap(fn: (S) -> Result<S1, F>): Result<S1, F> = when (this) {
        is BaseSuccess -> fn(success())
        is BaseFailure -> BaseFailure(failure())
        else -> this as Result<S1, F>
    }

    @Suppress("UNCHECKED_CAST")
    fun <S1> flatten(): Result<S1, F> =
        if (this is BaseSuccess && success() is Result<*, *>) success() as Result<S1, F> else this as BaseFailure<S1, F>

    /**
     *
     * Note: suppress unchecked cast because the result must be either a success
     *       or a failure, and the compiler doesn't know that and thinks the
     *       else is possible.
     */
    @Suppress("UNCHECKED_CAST")
    fun <S1> map(fn: (S) -> S1): Result<S1, F> = when (this) {
        is BaseSuccess -> BaseSuccess(fn(success()))
        is BaseFailure -> BaseFailure(failure())
        else -> this as Result<S1, F>
    }

    fun toOption(): Optional<S & Any> = when (this) {
        is BaseSuccess -> Optional.ofNullable(success())
        else -> Optional.empty()
    }

    fun toResult(): kotlin.Result<S> = when (this) {
        is BaseSuccess -> kotlin.Result.success(success())
        is BaseFailure -> kotlin.Result.failure(Throwable(failure().toString()))
        else -> kotlin.Result.failure(Throwable())
    }

    abstract fun isSuccess(): Boolean
    abstract fun isFailure(): Boolean

    // todo need to create defaultSuccess and defaultFailure, then partial specializations can
    //      implement these without user intervention
//    abstract fun successSupplier(): () -> BaseSuccess<S, F>
//    abstract fun failureSupplier(): () -> BaseFailure<S, F>
//    abstract fun supplier(): () -> Result<S, F>

}

//data class BaseSuccess<S, F>(val value: S, val failureSupplier: () -> BaseFailure<S, F>) : Result<S, F>() {
data class BaseSuccess<S, F>(val value: S) : Result<S, F>() {
    override fun isSuccess() = true
    override fun isFailure() = false

//    override fun successSupplier(): () -> BaseSuccess<S, F> = { BaseSuccess(value) }
//    override fun failureSupplier(): () -> BaseFailure<S, F> = failureSupplier()

    fun success(): S = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BaseSuccess<*, *>

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "BaseSuccess(value=$value)"
    }
}

open class BaseFailure<S, F>(val error: F) : Result<S, F>() {
    override fun isSuccess() = false
    override fun isFailure() = true

//    override fun failureSupplier(): () -> BaseFailure<S, F> = { BaseFailure(error) }

    fun failure(): F = error

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BaseFailure<*, *>

        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int = error?.hashCode() ?: 0

    override fun toString(): String {
        return "Failure(error=$error)"
    }
}

typealias Success<S> = BaseSuccess<S, List<Pair<String, String>>>

typealias Failure<S> = BaseFailure<S, List<Pair<String, String>>>
typealias FailureProjection<S> = BaseFailureProjection<S, List<Pair<String, String>>>
@Suppress("FunctionName")
fun <S> Failure(value: String) = Failure<S>(listOf(Pair("error", value)))
fun <S> Failure<S>.add(name: String, value: String) = Failure<S>(error + Pair(name, value))
fun <S> FailureProjection<S>.containsDeep(elem: Pair<String, String>): Boolean =
    if (result is BaseFailure) result.failure().contains(elem) else false


class BaseFailureProjection<S, F>(val result: Result<S, F>) {
    fun <U> foreach(effectFn: (failure: F) -> U) {
        if (result is BaseFailure) effectFn(result.failure())
    }

    fun getOrElse(supplier: () -> F): F =
        if (result is BaseFailure) result.failure() else supplier()

    fun orElse(supplier: () -> Result<S, F>): Result<S, F> =
        if (result is BaseFailure) result else supplier()

    fun contains(elem: F): Boolean =
        if (result is BaseFailure) result.failure() == elem else false

    fun forall(predicate: (F) -> Boolean): Boolean =
        if (result is BaseFailure) predicate(result.failure()) else true

    fun exists(predicate: (F) -> Boolean): Boolean =
        if (result is BaseFailure) predicate(result.failure()) else false

    /**
     *
     * Note: suppress unchecked cast because the result must be either a success
     *       or a failure, and the compiler doesn't know that and thinks the
     *       else is possible.
     */
    @Suppress("UNCHECKED_CAST")
    fun <F1> flatMap(fn: (F) -> Result<S, F1>): Result<S, F1> = when (result) {
        is BaseSuccess -> BaseSuccess(result.success())
        is BaseFailure -> fn(result.failure())
        else -> result as Result<S, F1>
    }

    @Suppress("UNCHECKED_CAST")
    fun <F1> map(fn: (F) -> F1): Result<S, F1> =
        if (result is BaseFailure && result.failure() is Result<*, *>)
            result.failure() as Result<S, F1>
        else
            result as BaseSuccess<S, F1>

    @Suppress("NULLABLE_TYPE_PARAMETER_AGAINST_NOT_NULL_TYPE_PARAMETER")
    fun toOption(): Optional<F & Any> =
        if (result is BaseFailure) Optional.ofNullable(result.failure()) else Optional.empty()

    fun toResult(): kotlin.Result<F> = when (result) {
        is BaseFailure -> kotlin.Result.success(result.failure())
        is BaseSuccess -> kotlin.Result.failure(Throwable(result.success().toString()))
        else -> kotlin.Result.failure(Throwable())
    }
}

//fun <S, F> callResultSupplier(supplier: () -> Result<S, F>): Result<S, F> =
//    try {
//        supplier()
//    } catch (e: Throwable) {
//        failure()
//    }

//class ComposableFailure<S> private constructor(
//    errors: List<Pair<String, String>> = emptyList()
//) : Failure<S, List<Pair<String, String>>>(errors, { e1, e2 -> e1 + e2}) {
//    constructor(message: String, key: String = "error") : this(listOf(key to message))
//
//    fun add(key: String, message: String): ComposableFailure<S> =
//        ComposableFailure((super.add(listOf(key to message)) as Failure<S, List<Pair<String, String>>>).failure())
//
//    override fun <S1> map(fn: (S) -> S1): Result<S1, List<Pair<String, String>>> =
//        ComposableFailure((super.map(fn) as Failure).failure())
//}
