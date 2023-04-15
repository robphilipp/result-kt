package com.digitalcipher.result

fun <S, S1> BaseResult<S, List<Pair<String, String>>>.transaction(
    transactional: (value: S) -> Boolean,
    boundedFunction: () -> BaseResult<S1, List<Pair<String, String>>>,
    commitFunction: (value: S) -> BaseResult<Boolean, List<Pair<String, String>>>,
    rollbackFunction: (value: S) -> BaseResult<Boolean, List<Pair<String, String>>>
): BaseResult<S1, List<Pair<String, String>>> = when (this) {
    is BaseFailure -> BaseFailure(error)
    is BaseSuccess -> {
        val result = boundedFunction()
        try {
            if (transactional(value)) {
                if (result.isSuccess())
                    commitFunction(value).flatMap({ _ -> result })
                else
                    rollbackFunction(value).flatMap({ _ -> result })
            } else {
                result
            }
        } catch (e: Throwable) {
            // at this point the function has to be transactional (i.e. the owner of the transaction) because
            // otherwise the result would have been returned without attempting to commit or roll back.
            // attempt to roll back
            val message = "Exception thrown when attempting to ${if (result.isSuccess()) "commit" else "rollback"} " +
                    "the transaction"
            try {
                rollbackFunction(value)
                    .flatMap({
                        val messages = listOf("error" to (e.message ?: "[no message]")) +
                                (if (result is BaseFailure) result.error else emptyList())
                        BaseFailure(messages)
                    })
            } catch (e: Throwable) {
                BaseFailure(listOf("error" to "$message, and then again on the final rollback" + e.message))
            }
        }
    }
}


