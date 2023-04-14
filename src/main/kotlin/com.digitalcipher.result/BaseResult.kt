package com.digitalcipher.result

import java.util.Optional

/**
 * A success-biased result class returned from functions whose operations can fail.
 *
 * Generally, a result provides a useful abstraction to denote operations that can fail.
 * When functions representing such operations return a [BaseResult], the caller of that
 * function must explicitly deal with the possibility that the function failed.
 *
 * The [BaseResult] provides a rich set of methods and extensions, providing a declarative
 * approach for representing a series of operations that could fail, without requiring
 * complex conditional structures or appropriately place exception handling.
 *
 * The [BaseResult] is "success-biased", meaning that its operations generally apply to
 * [BaseSuccess], and pass through [BaseFailure] unchanged. In this way, when an operation,
 * that is part of a series of operations, fails, the downstream operations are not
 * performed. Rather, a [BaseFailure] is passed through from each downstream operation
 * and returned.
 *
 * [BaseResult] has generic types for the success and the failure. A failure that is a
 * collection of error messages is quite common. And therefore, the [Success] and [Failure]
 * classes provide a partial specialization by using a `List<Pair<String, String>>` as the
 * type for the [Failure]'s value. The `List<Pair<String, String>>` type provides a
 * convenient way to specify key-value pairs describing various aspect of the failure.
 * For example, the key "error" could have a summary message, and additional keys could
 * represent variables and their associated values.
 */
sealed class BaseResult<S, F>(private val failureProducer: ((e: Throwable?) -> F)? = null) {

    /**
     * Returns a failure-biased version of the [BaseResult]. This allows performing
     * operations on the failure.
     * @return [BaseFailureProjection], which is a failure-biased version of the [BaseResult]
     */
    fun projection() = BaseFailureProjection(this)

    /**
     * Folds the [BaseSuccess] or the [BaseFailure] into a raw type using the provided
     * [successFn] and [failureFn].
     * @param successFn A function that maps a success' value of type [S] to type [C]
     * @param failureFn A function that maps a failure's value of type [F] to type [C]
     * @return A value of type [C]
     */
    fun <C> fold(successFn: (success: S) -> C, failureFn: (failure: F) -> C): C = when (this) {
        is BaseSuccess -> successFn(value)
        is BaseFailure -> failureFn(error)
    }

    fun <C> safeFold(
        successFn: (success: S) -> C,
        failureFn: (failure: F) -> C
    ): BaseResult<C, F> =
        if (failureProducer != null)
            safeResultFn(
                { BaseSuccess(fold(successFn, failureFn), failureProducer) },
                { e -> failureProducer.invoke(e) }
            )
        else
            BaseSuccess(fold(successFn, failureFn))


    /**
     * When the result is a [BaseSuccess] returns a [BaseFailure] of the same type. When
     * the result is a [BaseFailure] returns a [BaseSuccess] of the same type.
     * @return A [BaseResult] with success type [F] and failure type [S]
     */
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

data class BaseSuccess<S, F>(
    val value: S,
    private val failureProducer: ((e: Throwable?) -> F)? = null
) : BaseResult<S, F>(failureProducer)

data class BaseFailure<S, F>(val error: F) : BaseResult<S, F>()

typealias ErrorMessages = List<Pair<String, String>>
typealias Result<S> = BaseResult<S, ErrorMessages>
typealias Success<S> = BaseSuccess<S, ErrorMessages>

typealias Failure<S> = BaseFailure<S, ErrorMessages>
typealias FailureProjection<S> = BaseFailureProjection<S, ErrorMessages>

fun ErrorMessages.add(key: String, message: String): ErrorMessages = this + Pair(key, message)
fun emptyErrorMessages(): ErrorMessages = emptyList()
fun errorMessagesWith(message: String): ErrorMessages = listOf(Pair("error", message))
fun errorMessagesReducer(key: String, message: String, messages: ErrorMessages): ErrorMessages =
    messages.add(key, message)

@Suppress("FunctionName")
fun <S> Failure(value: String) = Failure<S>(errorMessagesWith(value))
fun <S> Failure<S>.addError(name: String, value: String) = Failure<S>(error.add(name, value))
fun <S> FailureProjection<S>.containsDeep(elem: Pair<String, String>): Boolean =
    if (result is BaseFailure) result.error.contains(elem) else false

fun <S, C> Success<S>.safeFold(
    successFn: (success: S) -> C,
    failureFn: (failure: ErrorMessages) -> C
): Result<C> =
    safeResultFn(
        { Success(fold(successFn, failureFn)) },
        { e -> errorMessagesWith(e.message ?: "") }
    )

fun <S, U> Success<S>.safeForeach(effectFn: (success: S) -> U): Result<Unit> =
    safeResultFn(
        { Success(foreach(effectFn)) },
        { e -> errorMessagesWith(e.message ?: "") }
    )

fun <S> Success<S>.safeForall(predicate: (S) -> Boolean): Result<Boolean> =
    safeResultFn(
        { Success(forall(predicate)) },
        { e -> errorMessagesWith(e.message ?: "") }
    )

fun <S> Success<S>.safeExists(predicate: (S) -> Boolean): Result<Boolean> =
    safeResultFn(
        { Success(exists(predicate)) },
        { e -> errorMessagesWith(e.message ?: "") }
    )

fun <S, S1> Success<S>.safeFlatMap(fn: (S) -> Result<S1>): Result<S1> =
    safeResultFn(
        { flatMap(fn) },
        { e -> errorMessagesWith(e.message ?: "") }
    )

fun <S, S1> Success<S>.safeMap(fn: (S) -> S1): Result<S1> =
    safeResultFn(
        { map(fn) },
        { e -> listOf(Pair("error", e.message ?: "")) }
    )

/**
 * [BaseResult] classes are "success-biased", meaning that the methods of the Result class operated
 * on [BaseSuccess] and not on [BaseFailure]. There are cases where unpacking or transforming
 * [BaseFailure] is useful. This projection provides the methods for doing that.
 * @param result The [BaseResult] to project to a "failure-biased" result.
 * @constructor
 */
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