package com.digitalcipher.result

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class ResultTest {

    @Test
    fun `should be able fold success and failures`() {
        assertEquals(
            Success("yay!").fold({ value -> value.uppercase() }, { mess -> mess[0].second.lowercase() }),
            "YAY!"
        )
        assertEquals(
            Failure<String>("BOO").fold({ value -> value.uppercase() }, { mess -> mess }),
            listOf(Pair("error", "BOO"))
        )
        assertEquals(
            Failure<String>("BOO").fold({ value -> value.uppercase() }, { mess -> mess[0].second.lowercase() }),
            "boo"
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
        success.foreach { value -> holder = value * 2 }
        // the result should remain unchanged
        assertEquals(success, Success(314))
        // the holder is what has changed
        assertEquals(holder, 628)
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
        assertTrue(Success(Person("george")).forall { person -> person.name == "george" })
        assertFalse(Success(Person("george")).forall { person -> person.name == "jenny" })
        assertTrue(BaseSuccess<Person, String>(Person("george")).forall { person -> person.name == "george" })
        assertFalse(BaseSuccess<Person, String>(Person("george")).forall { person -> person.name == "jenny" })
    }

    @Test
    fun `on failure forall should return true`() {
        assertTrue(Failure<Int>("nope").forall { value -> value % 2 == 0 })
        assertTrue(BaseFailure<Int, String>("nope").forall { value -> value % 2 == 0 })
    }

    @Test
    fun `on success exists should apply the supplied predicate`() {
        data class Person(val name: String)
        assertTrue(Success(Person("george")).exists { person -> person.name == "george" })
        assertFalse(Success(Person("george")).exists { person -> person.name == "jenny" })
        assertTrue(BaseSuccess<Person, String>(Person("george")).exists { person -> person.name == "george" })
        assertFalse(BaseSuccess<Person, String>(Person("george")).exists { person -> person.name == "jenny" })
    }

    @Test
    fun `should be able to flat map a success`() {
        data class Person(val name: String, val age: Int)

        val result = Success(Person("baby", 2))
            .flatMap { person ->
                if (person.age < 3) Success(Person("real baby", 2))
                else Failure("not a real baby")
            }
        assertEquals(BaseSuccess<Person, String>(Person("real baby", 2)), result)

        val resultBase = BaseSuccess<Person, String>(Person("baby", 2))
            .flatMap { person ->
                if (person.age < 3) BaseSuccess(Person("real baby", 2))
                else BaseFailure("not a real baby")
            }
        assertEquals(BaseSuccess<Person, String>(Person("real baby", 2)), resultBase)
    }

    @Test
    fun `flat map should pass through failure on failure`() {
        data class Person(val name: String, val age: Int)

        val result = Failure<Person>("not a baby")
            .flatMap { person ->
                if (person.age < 3) Success(Person("real baby", 2))
                else Failure("not a REAL baby")
            }
        assertEquals(Failure<Person>("not a baby"), result)

        val resultBase = BaseFailure<Person, String>("not a baby")
            .flatMap { person ->
                if (person.age < 3) BaseSuccess(Person("real baby", 2))
                else BaseFailure("not a REAL baby")
            }
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
            .add("warning", "really an error")
            .add("info", "really? should be an error")
        assertEquals(
            failure.failure(),
            listOf("error" to "first error", "warning" to "really an error", "info" to "really? should be an error")
        )
        assertEquals(
            failure.map { value -> value * 10 },
            failure
        )
        assertEquals(
            failure.projection().getOrElse { emptyList() },
            listOf("error" to "first error", "warning" to "really an error", "info" to "really? should be an error")
        )
    }
}