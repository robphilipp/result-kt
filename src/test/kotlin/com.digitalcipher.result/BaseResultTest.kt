package com.digitalcipher.result

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class BaseResultTest {

    @Test
    fun `should be able to do an unsafe fold success for base type`() {
        assertEquals(
            "YAY!",
            BaseSuccess<String, Throwable>("yay!").unsafeFold({ it.uppercase() }, { Throwable("damn") })
        )
    }

    @Test
    fun `should be able to fold success for base type`() {
        assertEquals(
            "YAY!",
            BaseSuccess<String, Throwable>("yay!")
                .fold({ it.uppercase() }, { Throwable("damn") })
                .getOrElse { "boo!" }
        )
    }

    @Test
    fun `should be able to an unsafe fold failure for base type`() {
        assertEquals(
            Throwable("BOO").message,
            BaseFailure<String, Throwable>(Throwable("BOO")).unsafeFold({ it.uppercase() }, { it.message })
        )
    }

    @Test
    fun `should be able to fold failure for base type`() {
        assertEquals(
            Throwable("BOO").message,
            BaseFailure<String, Throwable>(Throwable("BOO"))
                .fold({ it.uppercase() }, { it.message })
                .getOrElse { "damn" }
        )
    }

    @Test
    fun `should be able to convert a BaseFailure to a Failure`() {
        assertEquals(
            Failure<String>("BOO"),
            BaseFailure<String, Throwable>(Throwable("BOO"))
                .projection()
                .flatMap { Failure(it.message ?: "") }
        )

    }

    @Test
    fun `should be able to do an unsafe fold success and failures`() {
        assertEquals(
            "YAY!",
            Success("yay!").unsafeFold({ it.uppercase() }, { it[0].second.lowercase() })
        )
        assertEquals(
            listOf(Pair("error", "BOO")),
            Failure<String>("BOO").unsafeFold({ it.uppercase() }, { it })
        )
        assertEquals(
            "boo",
            Failure<String>("BOO").unsafeFold({ it.uppercase() }, { it[0].second.lowercase() })
        )
    }

    @Test
    fun `should be able fold success and failures`() {
        assertEquals(
            "YAY!",
            Success("yay!").fold({ it.uppercase() }, { it[0].second.lowercase() }).getOrElse { "boo!" }
        )
        assertEquals(
            listOf(Pair("error", "BOO")),
            Failure<String>("BOO").fold({ it.uppercase() }, { it }).getOrElse { "hmm?" }
        )
        assertEquals(
            "boo",
            Failure<String>("BOO").fold({ it.uppercase() }, { it[0].second.lowercase() }).getOrElse { "damn!" }
        )
    }

    @Test
    fun `should be able to throw an exception in a fold and get back an exception`() {
        assertThrows(Throwable::class.java) {
            Success("yay!").unsafeFold({ throw Throwable("oops") }, { it[0].second.lowercase() })
        }
    }

    @Test
    fun `should be able to throw an exception in a safe-fold and get back a result`() {
        assertEquals(
            Failure<String>(errorMessagesWith("oops!")),
            Success("yay!").fold({ throw Throwable("oops!") }, { "damn!" }, { errorMessagesWith(it?.message ?: "") })
        )
    }

    @Test
    fun `safe-fold should return a successful result`() {
        assertEquals(
            "YAY!",
            Success("yay!").fold({ it.uppercase() }, { "boo!" }).getOrElse { "BOO!" }
        )
    }

    @Test
    fun `safe-fold should return a successful base result when no failure producer is supplied`() {
        assertEquals(
            "YAY!",
            BaseSuccess<String, String>("yay!").fold({ it.uppercase() }, { "boo!" }).getOrElse { "BOO!" }
        )
    }

    @Test
    fun `should be able to throw an exception in a safe-fold and get back a base result`() {
        assertEquals(
            BaseFailure<String, String>("oops!"),
            BaseSuccess("yay!") { e -> e?.message ?: "boo!" }
                .fold({ throw Throwable("oops!") }, { "damn!" })
        )
    }

    @Test
    fun `should be able to do a safe fold for a base result`() {
        assertEquals(
            "YAY!",
            BaseSuccess("yay!") { e -> e?.message ?: "boo!" }
                .fold({ it.uppercase() }, { "BOO!" })
                .getOrElse { "damn!" }
        )
    }

    @Test
    fun `should be able to swap a success and a failure`() {
        assertEquals(
            BaseSuccess<Int, String>(314).swap(),
            BaseFailure<String, Int>(314)
        )
        assertEquals(
            BaseFailure<String, Int>(314).swap(),
            BaseSuccess<Int, String>(314)
        )
        assertEquals(
            Success(314).swap(),
            BaseFailure<List<Pair<String, String>>, Int>(314)
        )
    }

    @Test
    fun `a success should be a success`() {
        assertTrue(Success("test").isSuccess())
        assertFalse(Failure<String>("test").isSuccess())
    }

    @Test
    fun `a failure should be a failure`() {
        assertTrue(Failure<String>("test").isFailure())
        assertFalse(Success("test").isFailure())
        assertTrue(BaseFailure<String, String>("test").isFailure())
        assertFalse(BaseSuccess<String, String>("test").isFailure())
    }

    @Test
    fun `should be able to apply a side-effect function to a success, leaving the success unchanged`() {
        val success = Success(314)
        var holder = 0
        success.foreach({ holder = it * 2 })
        // the result should remain unchanged
        assertEquals(success, Success(314))
        // the holder is what has changed
        assertEquals(holder, 628)
    }

    @Test
    fun `a foreach should throw an exception of the lambda passed to it throws an exception`() {
        assertThrows(Throwable::class.java) {
            Success(314).foreach( { throw Throwable("ouch!") })
        }
    }

    @Test
    fun `a safe foreach should be the same as a foreach when no exception is thrown`() {
        var holder = 0
        Success(314).foreach( { holder = it * 2 })
        var safeHolder = 0
        Success(314).safeForeach { safeHolder = it * 2 }
        assertEquals(holder, safeHolder)
    }

    @Test
    fun `a safe foreach should return a failure when the foreach function throws an exception`() {
        assertEquals(
            errorMessagesWith("safety is our top priority"),
            Success(314)
                .safeForeach { throw Throwable("safety is our top priority") }
                .projection()
                .getOrElse { emptyErrorMessages() }
        )
    }

    @Test
    fun `should be able to get a value on success and the specified default on failure`() {
        assertEquals("yay!", Success("yay!").getOrElse { "boo" })
        assertEquals("boo", Failure<String>("yay!").getOrElse { "boo" })
        assertEquals("yay!", BaseSuccess<String, String>("yay!").getOrElse { "boo" })
        assertEquals("boo", BaseFailure<String, String>("yay!").getOrElse { "boo" })
    }

    @Test
    fun `should return this result on success, and the supplied result on failure`() {
        assertEquals(
            Success("yay!"),
            Success("yay!").orElse { Success("boo") }
        )
        assertEquals(
            Success("boo"),
            Failure<String>("314").orElse { Success("boo") }
        )
        assertEquals(
            BaseSuccess<String, Int>("yay!"),
            BaseSuccess<String, Int>("yay!").orElse { BaseSuccess("boo") }
        )
        assertEquals(
            BaseSuccess<String, Int>("boo"),
            BaseFailure<String, Int>(314).orElse { BaseSuccess("boo") }
        )
    }

    @Test
    fun `on success should contain value held in the success`() {
        data class Person(val name: String)
        assertTrue(Success(Person("henry")).contains(Person("henry")))
        assertFalse(Success(Person("henry")).contains(Person("jane")))
        assertTrue(BaseSuccess<Person, String>(Person("henry")).contains(Person("henry")))
        assertFalse(BaseSuccess<Person, String>(Person("henry")).contains(Person("jane")))
    }

    @Test
    fun `on failure should not contain any value`() {
        data class Person(val name: String)
        assertFalse(Failure<Person>("nobody").contains(Person("henry")))
        assertFalse(BaseFailure<Person, String>("nobody").contains(Person("henry")))
    }

    @Test
    fun `on success forall should apply the supplied predicate on the success value`() {
        data class Person(val name: String)
        assertTrue(Success(Person("george")).forall { it.name == "george" })
        assertFalse(Success(Person("george")).forall { it.name == "jenny" })
        assertTrue(BaseSuccess<Person, String>(Person("george")).forall { it.name == "george" })
        assertFalse(BaseSuccess<Person, String>(Person("george")).forall { it.name == "jenny" })
    }

    @Test
    fun `on success safe forall should apply the supplied predicate`() {
        data class Person(val name: String)
        assertTrue(Success(Person("george")).safeForall { it.name == "george" }.getOrElse { false })
        assertFalse(Success(Person("george")).safeForall { it.name == "jenny" }.getOrElse { true })
    }

    @Test
    fun `on success safe forall should return a failure when the supplied predicate throws an exception`() {
        data class Person(val name: String)
        assertEquals(
            errorMessagesWith("no one"),
            Success(Person("george"))
                .safeForall { throw Throwable("no one") }
                .projection()
                .getOrElse { emptyErrorMessages() }
        )
    }

    @Test
    fun `on failure forall should return true`() {
        assertTrue(Failure<Int>("nope").forall { it % 2 == 0 })
        assertTrue(BaseFailure<Int, String>("nope").forall { it % 2 == 0 })
    }

    @Test
    fun `on success exists should apply the supplied predicate`() {
        data class Person(val name: String)
        assertTrue(Success(Person("george")).exists { it.name == "george" })
        assertFalse(Success(Person("george")).exists { it.name == "jenny" })
        assertTrue(BaseSuccess<Person, String>(Person("george")).exists { it.name == "george" })
        assertFalse(BaseSuccess<Person, String>(Person("george")).exists { it.name == "jenny" })
    }

    @Test
    fun `safe exists should return a failure when the predicate throws an exception`() {
        data class Person(val name: String)
        assertTrue(Success(Person("george")).safeExists { it.name == "george" }.getOrElse { false })
        assertFalse(Success(Person("george")).safeExists { it.name == "jenny" }.getOrElse { true })
        assertEquals(
            errorMessagesWith("oops!"),
            Success(Person("george"))
                .safeExists { throw Throwable("oops!") }
                .projection()
                .getOrElse { emptyErrorMessages() })
    }

    @Test
    fun `should be able to flat map a success`() {
        data class Person(val name: String, val age: Int)

        val result = Success(Person("baby", 2))
            .flatMap({
                if (it.age < 3) Success(Person("real baby", 2))
                else Failure("not a real baby")
            })
        assertEquals(BaseSuccess<Person, String>(Person("real baby", 2)), result)

        val resultBase = BaseSuccess<Person, String>(Person("baby", 2))
            .flatMap({
                if (it.age < 3) BaseSuccess(Person("real baby", 2))
                else BaseFailure("not a real baby")
            })
        assertEquals(BaseSuccess<Person, String>(Person("real baby", 2)), resultBase)
    }

    @Test
    fun `safe flatMap should return a failure when map function throws and exception`() {
        data class Person(val name: String, val age: Int)

        assertEquals(
            errorMessagesWith("badly"),
            Success(Person("binny", 12))
                .safeFlatMap<Person, String> { throw Throwable("badly") }
                .projection()
                .getOrElse { emptyErrorMessages() }
        )
    }

    @Test
    fun `flat map should pass through failure on failure`() {
        data class Person(val name: String, val age: Int)

        val result = Failure<Person>("not a baby")
            .flatMap({
                if (it.age < 3) Success(Person("real baby", 2))
                else Failure("not a REAL baby")
            })
        assertEquals(Failure<Person>("not a baby"), result)

        val resultBase = BaseFailure<Person, String>("not a baby")
            .flatMap({
                if (it.age < 3) BaseSuccess(Person("real baby", 2))
                else BaseFailure("not a REAL baby")
            })
        assertEquals(BaseFailure<Person, String>("not a baby"), resultBase)
    }

    @Test
    fun `should be able to flatten a Success(Success(x)) to a Success(x)`() {
        assertEquals(
            Success(314),
            Success(Success(314)).flatten<Int>()
        )
        assertEquals(
            BaseSuccess<Int, String>(314),
            BaseSuccess<BaseSuccess<Int, String>, String>(BaseSuccess(314)).flatten<Int>()
        )
    }

    @Test
    fun `flatten should not flatten a Failure(Success(x)) any further`() {
        assertEquals(
            Failure<Success<Int>>("boo"),
            Failure<Success<Int>>("boo").flatten<Success<Int>>()
        )
        assertEquals(
            BaseFailure<BaseSuccess<Int, String>, String>("boo"),
            BaseFailure<BaseSuccess<Int, String>, String>("boo").flatten<BaseSuccess<Int, String>>()
        )
    }

    @Test
    fun `should be able to add a message to an extended failure`() {
//        val failure = Failure<Int>(listOf(Pair("error", "first error")))
        val failure = Failure<Int>("first error")
            .addError("warning", "really an error")
            .addError("info", "really? should be an error")
        assertEquals(
            failure.error,
            listOf("error" to "first error", "warning" to "really an error", "info" to "really? should be an error")
        )
        assertEquals(
            failure.map { it * 10 },
            failure
        )
        assertEquals(
            failure.projection().getOrElse { emptyErrorMessages() },
            listOf("error" to "first error", "warning" to "really an error", "info" to "really? should be an error")
        )
    }

    @Test
    fun `map should work on success but not failure`() {
        assertEquals(
            31400,
            Success(314).map { it * 100 }.getOrElse { 0 }
        )
        assertEquals(
            errorMessagesWith("oops!"),
            Failure<Double>("oops!").map { it * 10 }.projection().getOrElse { emptyList() }
        )
    }

    @Test
    fun `should be able to do a safe map on a success`() {
        assertEquals(
            "YAY!",
            Success("yay!").safeMap { it.uppercase() }.getOrElse { "boo" }
        )
    }

    @Test
    fun `should be able to do a safe map and a success where the function throws`() {
        assertEquals(
            errorMessagesWith("boo"),
            Success("yay!").safeMap { throw Exception("boo") }.projection().getOrElse { emptyErrorMessages() }
        )
    }

    @Test
    fun `regular map on a success whose function throws an exception should throw exception`() {
        assertThrows(java.lang.Exception::class.java) { Success("yay!").map { throw Exception("boo") } }
    }
}