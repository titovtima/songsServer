package ru.titovtima.songsserver

import java.math.BigInteger

class RSAEncoder (val n: BigInteger, val e: BigInteger) {
    fun powMod(a: BigInteger, b: BigInteger, mod: BigInteger = n): BigInteger {
        if (b == BigInteger.ZERO)
            return BigInteger.ONE
        if (b == BigInteger.ONE)
            return a.mod(mod)
        val t = powMod(a, b.div(BigInteger("2")), mod)
        if (b.mod(BigInteger("2")) == BigInteger.ZERO)
            return t.multiply(t).mod(mod)
        else
            return a.multiply(t.multiply(t).mod(mod)).mod(mod)
    }

    fun stringToBigInteger(string: String, mod: BigInteger = n): BigInteger {
        var result = BigInteger.ZERO
        for (c in string) {
            result = result.multiply(BigInteger("256"))
            result = result.plus(BigInteger(c.code.toString()))
            result = result.mod(mod)
        }
        return result
    }

    fun encodeString(string: String) = powMod(stringToBigInteger(string), e)
}

val Encoder = RSAEncoder(
    BigInteger("646869329144590738088155598176410614943272927716877166869834779689202720241"),
    BigInteger("4538180641368933719349421029108585167")
)

val OldEncoder = RSAEncoder(
    BigInteger("2472942968189431706898462913067925658209124041544162680908145890301107704237"),
    BigInteger("5281668766765633818307894358032591567")
)
