package com.digitalcipher.result

import java.util.Optional

/**
 * Success biased.
 */
sealed class BaseResult<S, F> {

    fun projection() = BaseFailureProjection(this)

    fun <C> fold(successFn: (success: S) -> C, failureFn: (failure: F) -> C): C = when (this) {
        is BaseSuccess -> successFn(value)
        is BaseFailure -> failureFn(error)
    }

    fun swap(): BaseResult<F, S> = when (this) {
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

    fun orElse(supplier: () -> BaseResult<S, F>): BaseResult<S, F> = when (this) {
        is BaseSuccess -> this
        else -> supplier()
    }

    fun contains(elem: S): Boolean = this is BaseSuccess && value == elem

    fun forall(predicate: (S) -> Boolean): Boolean = when (this) {
        is BaseSuccess -> predicate(value)
        else -> true
    }

    fun exists(predicate: (S) -> Boolean): Boolean = this is BaseSuccess && predicate(value)

    fun <S1> flatMap(fn: (S) -> BaseResult<S1, F>): BaseResult<S1, F> = when (this) {
        is BaseSuccess -> fn(value)
        is BaseFailure -> BaseFailure(error)
    }

    @Suppress("UNCHECKED_CAST")
    fun <S1> flatten(): BaseResult<S1, F> =
        if (this is BaseSuccess && value is BaseResult<*, *>) value as BaseResult<S1, F> else this as BaseFailure<S1, F>

    fun <S1> map(fn: (S) -> S1): BaseResult<S1, F> = when (this) {
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

data class BaseSuccess<S, F>(val value: S) : BaseResult<S, F>()

data class BaseFailure<S, F>(val error: F) : BaseResult<S, F>()

typealias Result<S> = BaseResult<S, List<Pair<String, String>>>
typealias Success<S> = BaseSuccess<S, List<Pair<String, String>>>

typealias Failure<S> = BaseFailure<S, List<Pair<String, String>>>
typealias FailureProjection<S> = BaseFailureProjection<S, List<Pair<String, String>>>

@Suppress("FunctionName")
fun <S> Failure(value: String) = Failure<S>(listOf(Pair("error", value)))
fun <S> Failure<S>.add(name: String, value: String) = Failure<S>(error + Pair(name, value))
fun <S> FailureProjection<S>.containsDeep(elem: Pair<String, String>): Boolean =
    if (result is BaseFailure) result.error.contains(elem) else false

fun <S, S1> Success<S>.safeMap(fn: (S) -> S1): Result<S1> =
    safeResultFn({ map(fn) }, { e -> listOf(Pair("error", e.message ?: "")) })

fun <S, C> Success<S>.safeFold(
    successFn: (success: S) -> C,
    failureFn: (failure: List<Pair<String, String>>) -> C
): Result<C> = try {
    Success(fold(successFn, failureFn))
} catch (e: Throwable) {
    BaseFailure(listOf(Pair("error", e.message ?: "")))
}

fun <S, U> Success<S>.safeForeach(effectFn: (success: S) -> U): Result<Unit> = try {
    foreach(effectFn)
    Success(Unit)
} catch (e: Throwable) {
    Failure(listOf(Pair("error", e.message ?: "")))
}

fun <S> Success<S>.safeForall(predicate: (S) -> Boolean): Result<Boolean> = try {
    val result = forall(predicate)
    Success(result)
} catch (e: Throwable) {
    Failure(listOf(Pair("error", e.message ?: "")))
}

class BaseFailureProjection<S, F>(val result: BaseResult<S, F>) {
    fun <U> foreach(effectFn: (failure: F) -> U) {
        if (result is BaseFailure) effectFn(result.error)
    }

    fun getOrElse(supplier: () -> F): F =
        if (result is BaseFailure) result.error else supplier()

    fun orElse(supplier: () -> BaseResult<S, F>): BaseResult<S, F> =
        if (result is BaseFailure) result else supplier()

    fun contains(elem: F): Boolean =
        if (result is BaseFailure) result.error == elem else false

    fun forall(predicate: (F) -> Boolean): Boolean =
        if (result is BaseFailure) predicate(result.error) else true

    fun exists(predicate: (F) -> Boolean): Boolean =
        if (result is BaseFailure) predicate(result.error) else false

    fun <F1> flatMap(fn: (F) -> BaseResult<S, F1>): BaseResult<S, F1> = when (result) {
        is BaseSuccess -> BaseSuccess(result.value)
        is BaseFailure -> fn(result.error)
    }

    fun <F1> map(fn: (F) -> F1): BaseResult<S, F1> = when (result) {
        is BaseSuccess -> BaseSuccess(result.value)
        is BaseFailure -> BaseFailure(fn(result.error))
    }

    fun toOption(): Optional<F & Any> =
        if (result is BaseFailure) Optional.ofNullable(result.error) else Optional.empty()

    fun toResult(): kotlin.Result<F> = when (result) {
        is BaseFailure -> kotlin.Result.success(result.error)
        is BaseSuccess -> kotlin.Result.failure(Throwable(result.value.toString()))
    }
}

inline fun <S, F> safeCall(fn: () -> S, errorSupplier: (e: Throwable) -> F): BaseResult<S, F> =
    try {
        BaseSuccess(fn())
    } catch (e: Throwable) {
        BaseFailure(errorSupplier(e))
    }

inline fun <S, F> safeResultFn(fn: () -> BaseResult<S, F>, errorSupplier: (e: Throwable) -> F): BaseResult<S, F> =
    try {
        fn()
    } catch (e: Throwable) {
        BaseFailure(errorSupplier(e))
    }