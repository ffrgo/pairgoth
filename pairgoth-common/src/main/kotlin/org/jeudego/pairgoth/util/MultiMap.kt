package org.jeudego.pairgoth.util

/**
 * MultiMap is an associative structure where each key can have one or several values.
 *
 * BiMultiMap is an associative structure where:
 * <ul>
 *     <li>each key can have one or several values (as in a MultiMap)</li>
 *     <li>each value has only a distinct key</li>
 * </ul>
 */

// CB TODO - ways to have Set instead of MutableSet here?
interface MultiMap<K, V>: Map<K, MutableSet<V>>

interface MutableMultiMap<K, V>: MultiMap<K, V>, MutableMap<K, MutableSet<V>> {
    fun put(key: K, value: V): Boolean
    fun putAll(vararg pairs: Pair<K, V>)
}

open class LinkedHashMultiMap<K, V>(vararg pairs: Pair<K, V>):
    MutableMultiMap<K, V>,
    LinkedHashMap<K, MutableSet<V>>(pairs.groupBy {
        it.first
    }.mapValues {
        it.value.map { it.second }.toMutableSet()
    }) {
    override fun put(key: K, value: V): Boolean {
        val set = super<LinkedHashMap>.computeIfAbsent(key) { mutableSetOf() }
        return set.add(value)
    }

    override fun putAll(vararg pairs: Pair<K, V>) {
        pairs.forEach { put(it.first, it.second) }
    }
}

interface BiMultiMap<K, V>: MultiMap<K, V> {
    val inverse: Map<V, K>
}

interface MutableBiMultiMap<K, V>: MutableMultiMap<K, V>, BiMultiMap<K, V> {
    override val inverse: MutableMap<V, K>
}

open class LinkedHashBiMultiMap<K, V>(vararg pairs: Pair<K, V>
): MutableBiMultiMap<K, V>, LinkedHashMultiMap<K, V>(*pairs) {
    override val inverse: MutableMap<V, K> = mutableMapOf(*pairs.map { Pair(it.second, it.first) }.toTypedArray())
    override fun put(key: K, value: V): Boolean {
        inverse[value] = key
        return super<LinkedHashMultiMap>.put(key, value)
    }
    override fun remove(key: K): MutableSet<V>? {
        return super<LinkedHashMultiMap>.remove(key)?.also {
            it.forEach { inverse.remove(it) }
        }
    }
}

fun <K, V> multiMapOf(vararg pairs: Pair<K, V>): MultiMap<K, V> =
    LinkedHashMultiMap<K, V>().apply {
        pairs.forEach { (k, v) ->
            put(k, v)
        }
    }

fun <K, V> mutableMultiMapOf(vararg pairs: Pair<K, V>): MutableMultiMap<K, V> =
    LinkedHashMultiMap<K, V>().apply {
        pairs.forEach { (k, v) ->
            put(k, v)
        }
    }

fun <K, V> biMultiMapOf(vararg pairs: Pair<K, V>): BiMultiMap<K, V> =
    LinkedHashBiMultiMap<K, V>().apply {
        pairs.forEach { (k, v) ->
            put(k, v)
        }
    }

fun <K, V> mutableBiMultiMapOf(vararg pairs: Pair<K, V>): MutableBiMultiMap<K, V> =
    LinkedHashBiMultiMap<K, V>().apply {
        pairs.forEach { (k, v) ->
            put(k, v)
        }
    }
