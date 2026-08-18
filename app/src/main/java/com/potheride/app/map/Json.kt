package com.potheride.app.map

/**
 * A minimal, dependency-free JSON reader for the three map services this package talks to.
 *
 * ### Why not `org.json`?
 * `org.json` ships with Android, so it is the obvious choice — but it is a *stub* on the
 * unit-test classpath. This module's `build.gradle.kts` sets
 * `testOptions.unitTests.isReturnDefaultValues = true`, which makes every stubbed
 * framework method return `null`/`0` instead of throwing. A parser built on `org.json`
 * would therefore not fail loudly in tests; it would quietly parse every response into
 * nothing, every test asserting "no route came back" would pass, and the breakage would
 * only surface on a device. Requirement 4 of this level is that everything network-facing
 * is testable without network, and that is not compatible with a parser that cannot run
 * under JUnit.
 *
 * ### Why not a serialisation library?
 * Adding kotlinx-serialization or Moshi means editing `build.gradle.kts`, which this
 * package does not own, and pulls a KSP/plugin change for three small response shapes.
 * Roughly a hundred lines of recursive descent is the cheaper trade here.
 *
 * The parser is deliberately permissive on input it does not need to police (it accepts
 * trailing content, does not validate number grammar beyond what [String.toDouble] does)
 * and strict where a silent mistake would cost us: unterminated strings, arrays and
 * objects all throw [JsonParseException], which the callers translate into a routing
 * failure and hence a fallback, rather than an empty map.
 *
 * Values map to: [Map]<String, Any?>, [List]<Any?>, [String], [Double], [Boolean], `null`.
 * Numbers are always [Double] — JSON has one number type, and pretending otherwise is how
 * you end up with a `ClassCastException` the first time a whole-number distance arrives.
 */
object Json {

    fun parse(text: String): Any? = Parser(text).readValue()

    /** Parses and casts to an object, or throws. */
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw JsonParseException("expected a JSON object")

    /** Parses and casts to an array, or throws. */
    fun parseArray(text: String): List<Any?> =
        parse(text) as? List<Any?> ?: throw JsonParseException("expected a JSON array")

    private class Parser(private val src: String) {
        private var pos = 0

        fun readValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw JsonParseException("unexpected end of input")
            return when (val c = src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber()
                else throw JsonParseException("unexpected character '$c' at $pos")
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { pos++; return out }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                out[key] = readValue()
                skipWhitespace()
                when (val c = next()) {
                    ',' -> Unit
                    '}' -> return out
                    else -> throw JsonParseException("expected ',' or '}' but found '$c' at ${pos - 1}")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') { pos++; return out }
            while (true) {
                out += readValue()
                skipWhitespace()
                when (val c = next()) {
                    ',' -> Unit
                    ']' -> return out
                    else -> throw JsonParseException("expected ',' or ']' but found '$c' at ${pos - 1}")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (pos >= src.length) throw JsonParseException("unterminated string")
                when (val c = src[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw JsonParseException("unterminated escape")
                        when (val e = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) throw JsonParseException("truncated \\u escape")
                                val hex = src.substring(pos, pos + 4)
                                pos += 4
                                sb.append(
                                    hex.toIntOrNull(16)?.toChar()
                                        ?: throw JsonParseException("bad \\u escape '$hex'")
                                )
                            }
                            else -> throw JsonParseException("unknown escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Double {
            val start = pos
            if (peek() == '-') pos++
            while (pos < src.length && (src[pos].isDigit() || src[pos] in ".eE+-")) pos++
            val text = src.substring(start, pos)
            return text.toDoubleOrNull() ?: throw JsonParseException("bad number '$text'")
        }

        private fun <T> readLiteral(literal: String, value: T): T {
            if (!src.startsWith(literal, pos)) throw JsonParseException("expected '$literal' at $pos")
            pos += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? = src.getOrNull(pos)

        private fun next(): Char =
            if (pos < src.length) src[pos++] else throw JsonParseException("unexpected end of input")

        private fun expect(c: Char) {
            if (peek() != c) throw JsonParseException("expected '$c' at $pos")
            pos++
        }
    }
}

class JsonParseException(message: String) : RuntimeException(message)

// ----------------------------------------------------------------------
// Defensive accessors
//
// Every remote body is untrusted: a service can change a field's type between
// deployments, return an error envelope with the same 200 status, or truncate a
// response mid-flight. These read through to `null` rather than throwing, so a
// surprising payload degrades to "no route" (and therefore the next provider in the
// fallback chain) instead of a crash on a background coroutine.
// ----------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
internal fun Any?.jsonObject(): Map<String, Any?>? = this as? Map<String, Any?>

internal fun Any?.jsonArray(): List<Any?>? = this as? List<*>

internal fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = this[key].jsonObject()

internal fun Map<String, Any?>.arr(key: String): List<Any?>? = this[key].jsonArray()

internal fun Map<String, Any?>.string(key: String): String? = this[key] as? String

internal fun Map<String, Any?>.double(key: String): Double? = when (val v = this[key]) {
    is Double -> v
    is Number -> v.toDouble()
    // Nominatim returns latitude and longitude as *strings*, not numbers. Reading them
    // as numbers only would silently drop every search result.
    is String -> v.toDoubleOrNull()
    else -> null
}
