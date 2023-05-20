package com.digitalcipher.result

import java.util.Optional

/**
 * A *success-biased* result class that can be returned from functions, whose operations
 * can fail, to signify that failures must be handled by the calling function.
 *
 * Generally, a result provides a useful abstraction to denote operations that can fail.
 * When functions representing such operations return a [BaseResult], the caller of that
 * function must explicitly deal with the possibility that the function failed.
 *
 * The [BaseResult] provides a rich set of methods and extensions, providing a declarative
 * approach for representing a series of operations that could fail, without requiring
 * complex conditional structures or appropriately place exception handling.
 *
 * The [BaseResult] is *success-biased*, meaning that its operations generally apply to
 * [BaseSuccess], and pass through [BaseFailure] unchanged. In this way, when an operation
 * fails, that is part of a series of operations, the downstream operations are not
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
 *
 * Functions that wrap exceptions and return a [BaseResult] when an exception is thrown
 * in a caller-specified lambda are considered *safe*. Functions that do NOT wrap exceptions,
 * but rather pass them through to the calling function are considered *unsafe*.
 *
 * A *safety chain* refers to a chain of **safe** function invocations. For example, when
 * the [BaseResult] is provided with a [failureProducer], then it is a *safe* result, and
 * all of its function will be *safe* functions. However, there are some functions that
 * break the *safety chain*. The [swap] function is an example of this. A [failureProducer]
 * that was supplied to the [BaseResult] is no longer (necessarily) valid for the
 * [BaseResult] because the success type has been switched with the failure type. To
 * prevent the [swap] function from breaking the *safety chain* you must provide a new
 * [failureProducer] that contains the correct types. When no [failureProducer] is supplied
 * to the [swap] function, it is said to *break* the safety chain, because subsequent, chained
 * functions are no longer guaranteed to be safe.
 *
 * @param failureProducer An optional function that produces a failure from a given [Throwable].
 * When specified, this [BaseResult] is *safe*.
 */
sealed class BaseResult<S, F>(private val failureProducer: ((e: Throwable?) -> F)? = null) {

    /**
     * [copyWith] is mainly used to create an enriched version of the [BaseResult]. When
     * specifying a [producer] the [BaseResult] will use the *safe* methods.
     * @param producer An optional failure producer that signifies the result to
     * use the *safe* operations.
     * @return A copy of the [BaseResult], possibly enriched with a [failureProducer].
     */
    private fun copyWith(producer: ((e: Throwable?) -> F)? = null): BaseResult<S, F> =
        when (this) {
            is BaseSuccess -> BaseSuccess(value, producer)
            is BaseFailure -> BaseFailure(error)
        }

    /**
     * @return `true` if this [BaseResult] is using *safe* operations; `false` otherwise.
     */
    fun isSafe(): Boolean = failureProducer != null

    /**
     * Returns a failure-biased version of the [BaseResult]. This allows performing
     * operations on the failure.
     * @return [BaseFailureProjection], which is a failure-biased version of the [BaseResult]
     */
    fun projection(): BaseFailureProjection<S, F> = BaseFailureProjection(this)

    /**
     * **Unsafe**
     *
     * Folds the [BaseSuccess] or the [BaseFailure] into a raw type using the provided
     * [successFn] and [failureFn]. The function does not wrap exceptions in the
     * [successFn] or [failureFn], but rather passes through any exceptions.
     * @param successFn A function that maps a success' value of type [S] to type [C]
     * @param failureFn A function that maps a failure's value of type [F] to type [C]
     * @return A value of type [C]
     * @see fold
     */
    fun <C> unsafeFold(successFn: (success: S) -> C, failureFn: (failure: F) -> C): C =
        when (this) {
            is BaseSuccess -> successFn(value)
            is BaseFailure -> failureFn(error)
        }

    /**
     * **Safe**
     *
     * Folds the [BaseSuccess] or the [BaseFailure] into a [BaseResult] using the provided
     * [successFn] and [failureFn]. When the [producer] is specified, then the [fold] function
     * is **safe**, wrapping exceptions that may have been thrown in the [successFn] or
     * [failureFn] and always returning a [BaseResult]. When the [producer] function is not
     * specified (or is null), then the [fold] function does not wrap exceptions thrown by the
     * [successFn] or [failureFn], but rather passes through any exceptions.
     * @param successFn A function that maps a success' value of type [S] to type [C]
     * @param failureFn A function that maps a failure's value of type [F] to type [C]
     * @param producer An optional function that produces a failure from a given [Throwable]. When
     * specified, the [fold] function is **safe**, wrapping exceptions that may have been thrown
     * in the [successFn] or [failureFn] and always returning a [BaseResult].
     * @return A value of type [C].
     * @see unsafeFold
     */
    fun <C> fold(
        successFn: (success: S) -> C,
        failureFn: (failure: F) -> C,
        producer: ((e: Throwable?) -> F)? = null
    ): BaseResult<C, F> =
        // not a safe fold, so just do the unsafe version
        if (failureProducer == null && producer == null) {
            BaseSuccess(unsafeFold(successFn, failureFn))
        } else {
            // the failure producer has been set at the object-level or as a function
            //  argument, so cast it to a non-null
            val prod = (producer ?: failureProducer) as (e: Throwable?) -> F
            safeResultFn(
                { BaseSuccess(unsafeFold(successFn, failureFn), prod) },
                { e -> prod(e) }
            )
        }

    /**
     * **Safe**
     *
     * When the result is a [BaseSuccess] returns a [BaseFailure] of the same type. When
     * the result is a [BaseFailure] returns a [BaseSuccess] of the same type.
     *
     * Use this function when expecting a failure, to treat that outcome as a success. Likewise,
     * use this function when a success is the unexpected outcome and should be treated as
     * a failure.
     *
     * **Warning**: this function breaks the safety chain unless a new failure producer
     * is specified.
     * @return A [BaseResult] with success type [F] and failure type [S]
     */
    fun swap(producer: ((e: Throwable?) -> S)? = null): BaseResult<F, S> =
        when (this) {
            is BaseSuccess -> BaseFailure(value)
            is BaseFailure -> BaseSuccess(error, producer)
        }

    /**
     * *Unsafe*
     *
     * The [unsafeForeach] method applies the specified [effectFn] to the [BaseResult] value
     * if the [BaseResult] is a [BaseSuccess]. Otherwise,
     * when the [BaseResult] is a not a [BaseSuccess], does nothing.
     * @param effectFn The side-effecting function to apply to the success value
     */
    fun <U> unsafeForeach(effectFn: (success: S) -> U) {
        if (this is BaseSuccess) BaseSuccess<U, F>(effectFn(value))
    }

    /**
     * *Safe*
     *
     * The [foreach] method applies the specified [effectFn] to the [BaseResult] value if it is
     * a success. When the [BaseResult] value is a failure, then does nothing.
     *
     * When the [BaseResult] has a [failureProducer] or a [producer] was specified in the [foreach]
     * method, this is a *safe* operation. The [producer] specified in the [foreach] function takes
     * precedence over the [failureProducer].
     *
     * @param effectFn The function to apply to the success value
     * @param producer An optional function that produces a failure from a given [Throwable]. When
     * specified, the [foreach] function is **safe**, wrapping exceptions that may have been thrown
     * in the [effectFn] and always returning a [BaseResult].
     * @return A [BaseResult] where the success type is [Unit].
     * @see unsafeForeach
     */
    fun <U> foreach(effectFn: (success: S) -> U, producer: ((e: Throwable?) -> F)? = null): BaseResult<Unit, F> =
        if (failureProducer == null && producer == null) {
            BaseSuccess(unsafeForeach(effectFn))
        } else {
            val prod = (producer ?: failureProducer) as (e: Throwable?) -> F
            safeResultFn(
                { BaseSuccess(unsafeForeach(effectFn), prod) },
                { e -> prod(e) }
            )
        }

    /**
     * *Safe*
     *
     * Unwraps the [BaseResult], returning a value of type [S].
     *
     * Use this function when to guarantee a returned value, regardless of
     * whether this is a success or failure.
     *
     * @param supplier Function that returns a value of type [S], and is
     * called when this is a failure,
     * @return  When this is a success, returns the value. Otherwise, on failure
     * calls the specified supplier and returns the value returned by
     * the supplier.
     * @see toOption
     * @see orElse
     */
    fun unwrap(supplier: () -> S): S = when (this) {
        is BaseSuccess -> value
        else -> supplier()
    }

    /**
     * *Safe*
     *
     * Passes through this [BaseResult] when it is a [BaseSuccess]. Otherwise, calls
     * the specified [supplier] function and passes on the result of that call.
     *
     * Use this function to process failures into possible successes.
     *
     * @param supplier Function that supplies a [BaseResult] when this result is not a
     * [BaseSuccess].
     * @return This [BaseResult] when this result is a [BaseSuccess]. Otherwise, the
     * [BaseResult] returned by the specified [supplier].
     * @see toOption
     * @see unwrap
     */
    fun orElse(supplier: () -> BaseResult<S, F>): BaseResult<S, F> = when (this) {
        is BaseSuccess -> this
        else -> supplier()
    }

    /**
     * *Safe*
     *
     * Determines whether the result contains the specified success value [elem].
     *
     * @param elem The value to check for containment
     * @return `true` when this result is a success and the result's value equals
     * the specified [elem]; `false` otherwise.
     */
    fun contains(elem: S): Boolean = (this is BaseSuccess) && (value == elem)

    /**
     * *Safe*
     *
     *
     */
    fun forall(predicate: (S) -> Boolean): Boolean = when (this) {
        is BaseSuccess -> predicate(value)
        else -> true
    }

    fun exists(predicate: (S) -> Boolean): Boolean = this is BaseSuccess && predicate(value)

    fun <S1> unsafeFlatMap(fn: (S) -> BaseResult<S1, F>): BaseResult<S1, F> = when (this) {
        is BaseSuccess -> fn(value)
        is BaseFailure -> BaseFailure(error)
    }

    fun <S1> flatMap(fn: (S) -> BaseResult<S1, F>, producer: ((e: Throwable?) -> F)? = null): BaseResult<S1, F> =
        if (failureProducer == null && producer == null)
            unsafeFlatMap(fn)
        else {
            val prod = (producer ?: failureProducer) as (e: Throwable?) -> F
            safeResultFn(
                { unsafeFlatMap(fn).copyWith(prod) },
                { e -> prod(e) }
            )
        }

    @Suppress("UNCHECKED_CAST")
    fun <S1> flatten(): BaseResult<S1, F> =
        if (this is BaseSuccess && value is BaseResult<*, *>) value as BaseResult<S1, F> else this as BaseFailure<S1, F>

    fun <S1> map(fn: (S) -> S1): BaseResult<S1, F> = when (this) {
        is BaseSuccess -> BaseSuccess(fn(value))
        is BaseFailure -> BaseFailure(error)
    }

    fun toOption(): Optional<out S> = when (this) {
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
        { Success(unsafeFold(successFn, failureFn)) },
        { e -> errorMessagesWith(e.message ?: "") }
    )

fun <S, U> Success<S>.safeForeach(effectFn: (success: S) -> U): Result<Unit> =
    safeResultFn(
        { Success(unsafeForeach(effectFn)) },
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