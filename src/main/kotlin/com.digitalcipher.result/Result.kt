package com.digitalcipher.result

import java.util.Optional

/**
 * Success biased.
 */
sealed class Result<S, F> {

    fun projection() = BaseFailureProjection(this)

    fun <C> fold(successFn: (success: S) -> C, failureFn: (failure: F) -> C): C = when (this) {
        is BaseSuccess -> successFn(value)
        is BaseFailure -> failureFn(error)
    }

    fun swap(): Result<F, S> = when (this) {
        is BaseSuccess -> BaseFailure(value)
        is BaseFailure -> BaseSuccess(error)
    }

    fun <U> foreach(effectFn: (success: S) -> U) {
        if (this is BaseSuccess) effectFn(value)
    }

    fun getOrElse(supplier: () -> S): S = when (this) {
        is BaseSuccess -> value
        else -> supplier()
    }

    fun orElse(supplier: () -> Result<S, F>): Result<S, F> = when (this) {
        is BaseSuccess -> this
        else -> supplier()
    }

    fun contains(elem: S): Boolean = this is BaseSuccess && value == elem

    fun forall(predicate: (S) -> Boolean): Boolean = when (this) {
        is BaseSuccess -> predicate(value)
        else -> true
    }

    fun exists(predicate: (S) -> Boolean): Boolean = this is BaseSuccess && predicate(value)

    /**
     *
     * Note: suppress unchecked cast because the result must be either a success
     *       or a failure, and the compiler doesn't know that and thinks the
     *       else is possible.
     */
    fun <S1> flatMap(fn: (S) -> Result<S1, F>): Result<S1, F> = when (this) {
        is BaseSuccess -> fn(value)
        is BaseFailure -> BaseFailure(error)
    }

    @Suppress("UNCHECKED_CAST")
    fun <S1> flatten(): Result<S1, F> =
        if (this is BaseSuccess && value is Result<*, *>) value as Result<S1, F> else this as BaseFailure<S1, F>

    /**
     *
     * Note: suppress unchecked cast because the result must be either a success
     *       or a failure, and the compiler doesn't know that and thinks the
     *       else is possible.
     */
    @Suppress("UNCHECKED_CAST")
    fun <S1> map(fn: (S) -> S1): Result<S1, F> = when (this) {
        is BaseSuccess -> BaseSuccess(fn(value))
        is BaseFailure -> BaseFailure(error)
    }

    fun toOption(): Optional<S & Any> = when (this) {
        is BaseSuccess -> Optional.ofNullable(value)
        else -> Optional.empty()
    }

    fun toResult(): kotlin.Result<S> = when (this) {
        is BaseSuccess -> kotlin.Result.success(value)
        is BaseFailure -> kotlin.Result.failure(Throwable(error.toString()))
    }

    fun isSuccess(): Boolean = this is BaseSuccess
    fun isFailure(): Boolean = this is BaseFailure
}

data class BaseSuccess<S, F>(val value: S) : Result<S, F>()

data class BaseFailure<S, F>(val error: F) : Result<S, F>()

typealias Success<S> = BaseSuccess<S, List<Pair<String, String>>>

typealias Failure<S> = BaseFailure<S, List<Pair<String, String>>>
typealias FailureProjection<S> = BaseFailureProjection<S, List<Pair<String, String>>>
@Suppress("FunctionName")
fun <S> Failure(value: String) = Failure<S>(listOf(Pair("error", value)))
fun <S> Failure<S>.add(name: String, value: String) = Failure<S>(error + Pair(name, value))
fun <S> FailureProjection<S>.containsDeep(elem: Pair<String, String>): Boolean =
    if (result is BaseFailure) result.error.contains(elem) else false


class BaseFailureProjection<S, F>(val result: Result<S, F>) {
    fun <U> foreach(effectFn: (failure: F) -> U) {
        if (result is BaseFailure) effectFn(result.error)
    }

    fun getOrElse(supplier: () -> F): F =
        if (result is BaseFailure) result.error else supplier()

    fun orElse(supplier: () -> Result<S, F>): Result<S, F> =
        if (result is BaseFailure) result else supplier()

    fun contains(elem: F): Boolean =
        if (result is BaseFailure) result.error == elem else false

    fun forall(predicate: (F) -> Boolean): Boolean =
        if (result is BaseFailure) predicate(result.error) else true

    fun exists(predicate: (F) -> Boolean): Boolean =
        if (result is BaseFailure) predicate(result.error) else false

    /**
     *
     * Note: suppress unchecked cast because the result must be either a success
     *       or a failure, and the compiler doesn't know that and thinks the
     *       else is possible.
     */
    @Suppress("UNCHECKED_CAST")
    fun <F1> flatMap(fn: (F) -> Result<S, F1>): Result<S, F1> = when (result) {
        is BaseSuccess -> BaseSuccess(result.value)
        is BaseFailure -> fn(result.error)
        else -> result as Result<S, F1>
    }

    @Suppress("UNCHECKED_CAST")
    fun <F1> map(fn: (F) -> F1): Result<S, F1> =
        if (result is BaseFailure && result.error is Result<*, *>)
            result.error as Result<S, F1>
        else
            result as BaseSuccess<S, F1>

    @Suppress("NULLABLE_TYPE_PARAMETER_AGAINST_NOT_NULL_TYPE_PARAMETER")
    fun toOption(): Optional<F & Any> =
        if (result is BaseFailure) Optional.ofNullable(result.error) else Optional.empty()

    fun toResult(): kotlin.Result<F> = when (result) {
        is BaseFailure -> kotlin.Result.success(result.error)
        is BaseSuccess -> kotlin.Result.failure(Throwable(result.value.toString()))
    }
}

//fun <S, F> callResultSupplier(supplier: () -> Result<S, F>): Result<S, F> =
//    try {
//        supplier()
//    } catch (e: Throwable) {
//        error
//    }

//class ComposableFailure<S> private constructor(
//    errors: List<Pair<String, String>> = emptyList()
//) : Failure<S, List<Pair<String, String>>>(errors, { e1, e2 -> e1 + e2}) {
//    constructor(message: String, key: String = "error") : this(listOf(key to message))
//
//    fun add(key: String, message: String): ComposableFailure<S> =
//        ComposableFailure((super.add(listOf(key to message)) as Failure<S, List<Pair<String, String>>>).error)
//
//    override fun <S1> map(fn: (S) -> S1): Result<S1, List<Pair<String, String>>> =
//        ComposableFailure((super.map(fn) as Failure).error)
//}
