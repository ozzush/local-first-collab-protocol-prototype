package common

import kotlin.random.Random

class RandomStringGenerator(seed: Long) {
    private val generator = Random(seed)

    fun generate(length: Int) : String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random(generator) }
            .joinToString("")
    }
}