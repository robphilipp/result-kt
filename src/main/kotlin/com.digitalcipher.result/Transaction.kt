//package com.digitalcipher.result
//
//fun <S, F, S1> Result<S, F>.transaction(
//    transactional: (value: S) -> Boolean,
//    boundedFunction: () -> Result<S1, F>,
//    commitFunction: (value: S) -> Result<Boolean, F>,
//    rollbackFunction: (value: S) -> Result<Boolean, F>
//): Result<S1, F> {
//    return if (this is BaseFailure) {
//        BaseFailure(failure())
//    } else {
//        val result = boundedFunction()
//        // because this result is a success, we are guaranteed that the value is not null
//        val value = this.orElseNull()!!
//        try {
//            if (transactional(value)) {
//                if (result.isSuccess)
//                    commitFunction(value).flatMap { result }
//                else
//                    rollbackFunction(value).flatMap { result }
//            } else {
//                result
//            }
//        } catch (e: Throwable) {
//            handleTransactionException(result, rollbackFunction, value, e.message)
//        }
//    }
//}
//
//private fun <S, F, S1> handleTransactionException(
//    result: Result<S1, F>?,
//    rollbackFunction: (value: S) -> Result<Boolean, F>,
//    transaction: S,
//    exceptionMessage: String?
//): Result<S1, F> {
//    // at this point the function has to be transactional (i.e. the owner of the transaction) because
//    // otherwise the result would have been returned without attempting to commit or roll back.
//    // attempt to roll back
//    val message = "Exception thrown when attempting to ${if (result?.isSuccess == true) "commit" else "rollback"} " +
//            "the transaction"
//    return try {
//        rollbackFunction(transaction)
//            .flatMap {
//                val messages = mapOf(EXCEPTION to (exceptionMessage ?: "[no message]")) + (result?.messages()?: emptyMap())
//                failure(result?.messages() ?: emptyMap())
//            }
//    } catch (e: Throwable) {
//        failure("$message, and then again on the final rollback", e)
//    }
//}
//
