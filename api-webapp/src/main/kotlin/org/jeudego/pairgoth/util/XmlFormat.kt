package org.jeudego.pairgoth.util

import com.republicate.kson.Json
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.w3c.dom.Element
import org.w3c.dom.ElementTraversal
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

// "XMLFormat" xml parsing and formatting utility class
// It is currently being packaged as an external open source library.
// See opengotha import code as a self-documenting example for how to use this class

open class XmlFormat(val xml: Element)

// standard types delegates

fun XmlFormat.string() = StringXmlDelegate(xml.element())
fun XmlFormat.optString() = OptionalStringXmlDelegate(xml.element())
fun XmlFormat.boolean() = BooleanXmlDelegate(xml.element())
fun XmlFormat.optBoolean() = OptionalBooleanXmlDelegate(xml.element())
fun XmlFormat.int() = IntXmlDelegate(xml.element())
fun XmlFormat.optInt() = OptionalIntXmlDelegate(xml.element())
fun XmlFormat.long() = LongXmlDelegate(xml.element())
fun XmlFormat.optLong() = OptionalLongXmlDelegate(xml.element())
fun XmlFormat.double() = DoubleXmlDelegate(xml.element())
//fun XmlFormat.optDouble() = OptinalDoubleXmlDelegate(xml.element())
inline fun <reified E: Enum<*>> XmlFormat.enum() = EnumXmlDelegate<E>(xml.element(), E::class)
fun XmlFormat.date(format: String = ISO_LOCAL_DATE_FORMAT) = DateXmlDelegate(xml.element(), format)
fun XmlFormat.datetime(format: String = ISO_LOCAL_DATETIME_FORMAT) = DateTimeXmlDelegate(xml.element(), format)
inline fun <reified T: XmlFormat> XmlFormat.arrayOf() = ArrayXmlDelegate(xml, T::class)
inline fun <reified T: XmlFormat> XmlFormat.arrayOf(tagName: String) = ChildrenArrayXmlDelegate(xml, tagName, T::class)
inline fun <reified T: XmlFormat> XmlFormat.mutableArrayOf() = MutableArrayXmlDelegate(xml, T::class)
inline fun <reified T: XmlFormat> XmlFormat.objectOf() = ObjectXmlDelegate(xml, T::class)

// standard type delegates for attributes

fun XmlFormat.stringAttr() = StringXmlAttrDelegate(xml.element())
fun XmlFormat.booleanAttr() = BooleanXmlAttrDelegate(xml.element())
fun XmlFormat.intAttr() = IntXmlAttrDelegate(xml.element())
fun XmlFormat.longAttr() = LongXmlAttrDelegate(xml.element())
fun XmlFormat.doubleAttr() = DoubleXmlAttrDelegate(xml.element())
fun XmlFormat.dateAttr(format: String = ISO_LOCAL_DATE_FORMAT) = DateXmlAttrDelegate(xml.element(), format)

// xpath delegates

fun XmlFormat.string(xpath: String) = StringXmlAttrDelegate(xml.element().find(xpath)[0].element())
fun XmlFormat.boolean(xpath: String) = StringXmlAttrDelegate(xml.element().find(xpath)[0].element())
fun XmlFormat.int(xpath: String) = StringXmlAttrDelegate(xml.element().find(xpath)[0].element())
fun XmlFormat.long(xpath: String) = StringXmlAttrDelegate(xml.element().find(xpath)[0].element())

// Helper classes and functions

private fun error(propName: String): Nothing { throw Error("missing property $propName") }

open class OptionalStringXmlDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): String? = xml.childOrNull(property.name)?.value()
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) { value?.let { xml.child(property.name).textContent = value } }
}

open class StringXmlDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): String = xml.childOrNull(property.name)?.value() ?: error(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { value.let { xml.child(property.name).textContent = value } }
}

open class OptionalBooleanXmlDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean? = Json.TypeUtils.toBoolean(xml.childOrNull(property.name)?.value())
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean?) { value?.let { xml.child(property.name).textContent = value.toString() } }
}

open class BooleanXmlDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = Json.TypeUtils.toBoolean(xml.childOrNull(property.name)?.value()) ?: error(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { value.let { xml.child(property.name).textContent = value.toString() } }
}

open class OptionalIntXmlDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Int? = Json.TypeUtils.toInt(xml.childOrNull(property.name)?.value())
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int?) { value?.let { xml.child(property.name).textContent = value.toString() } }
}

open class IntXmlDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = Json.TypeUtils.toInt(xml.childOrNull(property.name)?.value()) ?: error(property.name)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { value.let { xml.child(property.name).textContent = value.toString() } }
}

open class OptionalLongXmlDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Long? = Json.TypeUtils.toLong(xml.childOrNull(property.name)?.value())
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long?) { value?.let { xml.child(property.name).textContent = value.toString() } }
}

open class LongXmlDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = Json.TypeUtils.toLong(xml.childOrNull(property.name)?.value()) ?: error(property.name)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { value.let { xml.child(property.name).textContent = value.toString() } }
}

open class OptionalDoubleXmlDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Double? = Json.TypeUtils.toDouble(xml.childOrNull(property.name)?.value())
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Double?) { value?.let { xml.child(property.name).textContent = value.toString() } }
}

open class DoubleXmlDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Double = Json.TypeUtils.toDouble(xml.childOrNull(property.name)?.value()) ?: error(property.name)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) { value.let { xml.child(property.name).textContent = value.toString() } }
}

open class OptionalEnumXmlDelegate<E: Enum<*>> (val xml: Element, private val kclass: KClass<E>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): E? {
        val enumValues = kclass.java.enumConstants as Array<E>
        val xmlValue = xml.childOrNull(property.name)?.textContent
        return enumValues.firstOrNull() { it.name == xmlValue }
    }
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: E?) { value.let { xml.child(property.name).textContent = value.toString() } }
}

open class EnumXmlDelegate<E: Enum<*>> (val xml: Element, private val kclass: KClass<E>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): E {
        val enumValues = kclass.java.enumConstants as Array<E>
        val xmlValue = xml.childOrNull(property.name)?.textContent
        return enumValues.firstOrNull() { it.name == xmlValue } ?: error(property.name)
    }
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: E) { value.let { xml.child(property.name).textContent = value.toString() } }
}

const val ISO_LOCAL_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
const val ISO_LOCAL_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
const val ISO_UTC_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"
const val ISO_ZONED_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX"
const val ISO_LOCAL_YMD = "yyyyMMdd"
const val ISO_LOCAL_YMDHM = "yyyyMMddHHmm"
const val LOCAL_FRENCHY = "dd/MM/yyyy HH:mm"

internal fun dateTimeFormat(format: String): DateTimeFormatter {
    val builder = DateTimeFormatterBuilder()
    if (format.startsWith("yyyy")) {
        // workaround Java bug
        builder.appendValue(ChronoField.YEAR_OF_ERA, 4)
            .appendPattern(format.substring(4))
    } else {
        builder.appendPattern(format)
    }
    builder.parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
    return builder.toFormatter()
}

open class OptionalDateXmlDelegate(val xml: Element, formatString: String = ISO_LOCAL_DATE_FORMAT) {
    private val format = dateTimeFormat(formatString)
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalDate? = xml.childOrNull(property.name)?.value()?.let { LocalDate.parse(it /*, format */) }
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDate?) { value?.let { xml.child(property.name).textContent = value.let { /* format.format(value)*/ value.toString() } } }
}

open class DateXmlDelegate(val xml: Element, formatString: String = ISO_LOCAL_DATE_FORMAT) {
    private val format = dateTimeFormat(formatString)
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalDate = xml.childOrNull(property.name)?.value()?.let { LocalDate.parse(it /*, format */) } ?: error(property.name)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDate) { value.let { xml.child(property.name).textContent = value.let { /* format.format(value)*/ value.toString() } } }
}

open class OptionalDateTimeXmlDelegate(val xml: Element, formatString: String = ISO_LOCAL_DATETIME_FORMAT) {
    private val format = dateTimeFormat(formatString)
    //FB TODO ** To rewrite
    //open operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalDateTime? = xml.childOrNull(property.name)?.value()?.let { LocalDateTime.parse(it, format) }
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalDateTime? {
        var inputString : String? = xml.childOrNull(property.name)?.value()?.replace("_","0")
        if (inputString?.length == 16) inputString = inputString.plus(":00")
        return inputString?.let { LocalDateTime.parse(it /*, format*/) } // CB TODO format handling
    }
    //**
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDateTime?) { value?.let { xml.child(property.name).textContent = value.let { value.toString() /* format.format(value)*/  } } }
}

open class DateTimeXmlDelegate(val xml: Element, formatString: String = ISO_LOCAL_DATETIME_FORMAT) {
    private val format = dateTimeFormat(formatString)
    //FB TODO ** To rewrite
    //open operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalDateTime? = xml.childOrNull(property.name)?.value()?.let { LocalDateTime.parse(it, format) }
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalDateTime {
        var inputString : String? = xml.childOrNull(property.name)?.value()?.replace("_","0")
        if (inputString?.length == 16) inputString = inputString.plus(":00")
        return inputString?.let { LocalDateTime.parse(it /*, format*/) } ?: error(property.name) // CB TODO format handling
    }
    //**
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDateTime) { value.let { xml.child(property.name).textContent = value.let { value.toString() /* format.format(value)*/  } } }
}

fun <F: XmlFormat, T: Any> KClass<F>.instantiate(content: T): F = constructors.first().call(content)

open class ObjectXmlDelegate <T: XmlFormat> (val xml: Node, private val klass: KClass<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val obj = xml.element().child(property.name)
        return obj.let {
            klass.instantiate(it)
        }
    }
}

// standard types attributes delegates
open class OptionalStringXmlAttrDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): String? = xml.attr(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) { value?.let { xml.setAttr(property.name, value) } }
}

open class StringXmlAttrDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): String = xml.attr(property.name) ?: error(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { value.let { xml.setAttr(property.name, value) } }
}

open class OptionalBooleanXmlAttrDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean? = xml.boolAttr(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean?) { value?.let { xml.setAttr(property.name, value) } }
}

open class BooleanXmlAttrDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = xml.boolAttr(property.name) ?: error(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { value.let { xml.setAttr(property.name, value) } }
}

open class OptionalIntXmlAttrDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Int? = xml.intAttr(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int?) { value?.let { xml.setAttr(property.name, value) } }
}

open class IntXmlAttrDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = xml.intAttr(property.name) ?: error(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { value.let { xml.setAttr(property.name, value) } }
}

open class OptionalLongXmlAttrDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Long? = xml.longAttr(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long?) { value?.let { xml.setAttr(property.name, value) } }
}

open class LongXmlAttrDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = xml.longAttr(property.name) ?: error(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) { value.let { xml.setAttr(property.name, value) } }
}

open class OptionalDoubleXmlAttrDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Double? = xml.doubleAttr(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Double?) { value?.let { xml.setAttr(property.name, value) } }
}

open class DoubleXmlAttrDelegate(val xml: Element) {
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): Double = xml.doubleAttr(property.name) ?: error(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) { value.let { xml.setAttr(property.name, value) } }
}

open class OptionalDateXmlAttrDelegate(val xml: Element, formatString: String = ISO_LOCAL_DATE_FORMAT) {
    private val format = dateTimeFormat(formatString)
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalDate? = xml.attr(property.name)?.let { LocalDate.parse(it/*, format*/) }
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDate?) { value?.let { xml.setAttr(property.name, value.toString() /* format.format(value) */) } }
}

open class DateXmlAttrDelegate(val xml: Element, formatString: String = ISO_LOCAL_DATE_FORMAT) {
    private val format = dateTimeFormat(formatString)
    open operator fun getValue(thisRef: Any?, property: KProperty<*>): LocalDate = xml.attr(property.name)?.let { LocalDate.parse(it/*, format*/) } ?: error(property.name)
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: LocalDate) { value.let { xml.setAttr(property.name, value.toString() /* format.format(value) */) } }
}

// containers delegates

open class XmlArrayAdapter<T: XmlFormat>(val parent: Node, protected val functor: (Node)->T): List<T> {
    override val size = (parent as ElementTraversal).childElementCount
    override fun contains(element: T): Boolean { throw Error("not implemented") }
    override fun containsAll(elements: Collection<T>): Boolean { throw Error("not implemented") }
    override fun get(index: Int): T {
        try {
            return functor(parent.element().children()[index])
        } catch (e: Exception) {
            throw Error("could not get child element", e)
        }
    }
    override fun indexOf(element: T): Int { throw Error("not implemented") }
    override fun isEmpty() = parent.childNodes.length == 0
    override fun iterator(): Iterator<T> = object: Iterator<T> {
        private val it = parent.element().children().iterator()
        override fun hasNext() = it.hasNext()
        override fun next() = functor(it.next())
    }
    override fun lastIndexOf(element: T): Int { throw Error("not implemented") }
    override fun listIterator() = listIterator(0)
    override fun listIterator(index: Int): ListIterator<T> { throw Error("not implemented") }
    override fun subList(fromIndex: Int, toIndex: Int): List<T> { throw Error("not implemented") }
}

class MutableXmlArrayAdapter<T: XmlFormat>(parent: Node, functor: (Node)->T): XmlArrayAdapter<T>(parent, functor), MutableList<T> {
    override fun iterator(): MutableIterator<T> = object: MutableIterator<T> {
        private val it = parent.element().children().iterator()
        override fun hasNext() = it.hasNext()
        override fun next() = functor(it.next())
        override fun remove() { throw Error("not implemented") }
    }
    override fun add(element: T): Boolean { parent.appendChild(element.xml); return true }
    override fun add(index: Int, element: T) { throw Error("not implemented") }
    override fun addAll(index: Int, elements: Collection<T>): Boolean { throw Error("not implemented") }
    override fun addAll(elements: Collection<T>): Boolean { throw Error("not implemented") }
    override fun clear() {
        while (parent.firstChild != null) {
            parent.removeChild(parent.firstChild)
        }
    }
    override fun listIterator() = listIterator(0)
    override fun listIterator(index: Int): MutableListIterator<T>  { throw Error("not implemented") }
    override fun remove(element: T): Boolean { throw Error("not implemented") }
    override fun removeAll(elements: Collection<T>): Boolean { throw Error("not implemented") }
    override fun removeAt(index: Int): T { throw Error("not implemented") }
    override fun retainAll(elements: Collection<T>): Boolean { throw Error("not implemented") }
    override fun set(index: Int, element: T): T { throw Error("not implemented") }
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        throw NotImplementedError("Not implemented")
    }
}

open class XmlArrayInlineAdapter<T: XmlFormat>(val list: NodeList, protected val functor: (Node)->T): List<T> {
    override val size = list.length
    override fun contains(element: T): Boolean { throw Error("not implemented") }
    override fun containsAll(elements: Collection<T>): Boolean { throw Error("not implemented") }
    override fun get(index: Int): T {
        try {
            return functor(list[index])
        } catch (e: Exception) {
            throw Error("could not get child element", e)
        }
    }
    override fun indexOf(element: T): Int { throw Error("not implemented") }
    override fun isEmpty() = list.length == 0
    override fun iterator(): Iterator<T> = object: Iterator<T> {
        private val it = list.iterator()
        override fun hasNext() = it.hasNext()
        override fun next() = functor(it.next())
    }
    override fun lastIndexOf(element: T): Int { throw Error("not implemented") }
    override fun listIterator() = listIterator(0)
    override fun listIterator(index: Int): ListIterator<T> { throw Error("not implemented") }
    override fun subList(fromIndex: Int, toIndex: Int): List<T> { throw Error("not implemented") }
}

inline fun <reified T: XmlFormat> MutableXmlArrayAdapter<T>.newChild(): T {
    val node = parent.document().createElement(T::class.simpleName!!.lowercase())
    // should be done explicitely in client code
    // parent.element().appendChild(node)
    return T::class.instantiate(node)
}

open class ArrayXmlDelegate <T: XmlFormat> (val xml: Node, private val klass: KClass<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): List<T> =
        XmlArrayAdapter<T>(xml.element().child(property.name), klass::instantiate)
}

open class MutableArrayXmlDelegate <T: XmlFormat> (val xml: Node, private val klass: KClass<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): MutableXmlArrayAdapter<T> =
        MutableXmlArrayAdapter<T>(xml.element().child(property.name), klass::instantiate)
}

open class ChildrenArrayXmlDelegate <T: XmlFormat> (val xml: Node, private val tagName: String, private val klass: KClass<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): List<T> = XmlArrayInlineAdapter(xml.element().getElementsByTagName(tagName), klass::instantiate)
}
