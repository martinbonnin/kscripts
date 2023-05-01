#!/usr/bin/env kotlin

@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:3.2.0")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.FileWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

val MainCommand = object : CliktCommand() {
    val input by option("--in").help("input svg").required()

    val output by option("--out").help("output file").required()

    val command by argument().help("flatten")

    val tolerance by option().double().default(0.00001).help(
        """
        The tolerance when approximating cubic bezier curves. See http://agg.sourceforge.net/antigrain.com/research/adaptive_bezier/index.html
        """.trimIndent()
    )

    override fun run() {
        when (command) {
            "flatten" -> {

                val document = File(input).inputStream().use {
                    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
                }

                val paths = flatten(document, tolerance)

                document.documentElement.childNodes.toList().filter { it is Element }.forEach {
                    document.documentElement.removeChild(it)
                }

                paths.forEach {
                    val newNode = document.createElement("path")
                    newNode.setAttribute("id", it.id)
                    it.stroke?.let { newNode.setAttribute("stroke", it) }
                    it.fill?.let { newNode.setAttribute("fill", it) }
                    newNode.setAttribute("d", it.points.toPathString())
                    document.documentElement.appendChild(
                        newNode
                    )
                }

                val transformer = TransformerFactory.newInstance().newTransformer()
                val writer = FileWriter(File(output))
                val result = StreamResult(writer)
                transformer.transform(DOMSource(document), result)
            }
        }
    }
}

MainCommand.main(args)

class KVec3(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 1.0) {
    operator fun times(other: KVec3) = KVec3(x * other.x, y * other.y, z * other.z)
    operator fun get(i: Int) = when (i) {
        0 -> x
        1 -> y
        2 -> z
        else -> error("cannot get index $i")
    }

    fun toPoint() = KPoint(x, y)
    fun sum() = x + y + z
}

class KMat3(val x: KVec3, val y: KVec3, val z: KVec3) {
    operator fun times(other: KMat3): KMat3 {
        return KMat3(
            x = KVec3(x = mult(other, 0, 0), y = mult(other, 0, 1), z = mult(other, 0, 2)),
            y = KVec3(x = mult(other, 1, 0), y = mult(other, 1, 1), z = mult(other, 1, 2)),
            z = KVec3(x = mult(other, 2, 0), y = mult(other, 2, 1), z = mult(other, 2, 2))
        )
    }

    private fun mult(other: KMat3, i: Int, j: Int): Double {
        return (row(i) * other.col(j)).sum()
    }

    fun col(i: Int): KVec3 {
        return KVec3(x[i], y[i], z[i])
    }

    fun row(i: Int): KVec3 {
        return when (i) {
            0 -> x
            1 -> y
            2 -> z
            else -> error("cannot get row $i")
        }
    }

    operator fun times(vec: KVec3): KVec3 {
        return KVec3(
            x = (x * vec).sum(),
            y = (y * vec).sum(),
            z = (z * vec).sum()
        )
    }

    operator fun times(point: KPoint): KPoint {
        return (this * point.toVec3()).toPoint()
    }

    companion object {
        val identity = scale(1.0, 1.0)

        fun scale(sx: Double, sy: Double) = KMat3(
            x = KVec3(x = sx),
            y = KVec3(y = sy),
            z = KVec3(z = 1.0)
        )

        fun translate(tx: Double, ty: Double) = KMat3(
            x = KVec3(x = 1.0, y = 0.0, z = tx),
            y = KVec3(x = 0.0, y = 1.0, z = ty),
            z = KVec3(x = 0.0, y = 0.0, z = 1.0)
        )

        fun rotate(theta: Double) = KMat3(
            x = KVec3(x = cos(theta), y = -sin(theta), z = 0.0),
            y = KVec3(x = sin(theta), y = cos(theta), z = 0.0),
            z = KVec3(x = 0.0, y = 0.0, z = 1.0)
        )
    }
}

data class KPoint(val x: Double, val y: Double) {
    fun toVec3() = KVec3(x, y, 1.0)
}

class KPath(
    val points: List<KPoint>,
    val stroke: String?,
    val fill: String?,
    val id: String,
    val isClosed: Boolean
)

data class WalkState(
    var transform: KMat3,
    var id: String,
    val tolerance: Double,
    val paths: MutableList<KPath>
)

fun flatten(document: Document, tolerance: Double): List<KPath> {

    val state = WalkState(
        KMat3.identity,
        "",
        tolerance,
        mutableListOf()
    )

    walk(document.documentElement, state)

    return state.paths
}

fun walk(element: Element, state: WalkState) {
    val id = "${state.id}:${element.getAttribute("id")}"
    val transform = element.getAttribute("transform").parseTransform().times(state.transform)
    when (element.nodeName) {
        "g" -> {
            element.childNodes.toList().filter { it is Element }.forEach {
                walk(element, state.copy(id = id, transform = transform))
            }
        }
        "path" -> {
            val points = element.getAttribute("d").parsePath(state.tolerance).map {
                transform.times(it)
            }

            state.paths.add(
                KPath(
                    points = points,
                    id = state.id,
                    fill = element.getAttribute("fill"),
                    stroke = element.getAttribute("stroke"),
                    isClosed = points.first() == points.last()
                )
            )
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun NodeList.toList() = buildList<Node> {
    for (i in 0.until(length)) {
        add(this[i])
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun String?.parseTransform(): KMat3 {
    var result = KMat3.identity
    var start = 0

    if (this == null) {
        return result
    }

    while (true) {
        // XXX: handle rotations
        val regex = Regex("(.*)\\(([0-9+-.eE]*), *([0-9+-.eE]*)\\)")

        val matchResult = regex.matchAt(this, start) ?: return result

        start += matchResult.groupValues[0].length

        result *= when (matchResult.groupValues[1]) {
            "translate" -> KMat3.translate(
                matchResult.groupValues[2].toDouble(),
                matchResult.groupValues[3].toDouble(),
            )
            "scale" -> KMat3.scale(matchResult.groupValues[2].toDouble(), matchResult.groupValues[3].toDouble())
            else -> error("unsupported transform ${matchResult.groupValues[1]}")
        }
    }
}

class PathParser(private val str: String) {
    var i = 0

    private fun skipWhiteSpace() {
        while (str[i] == ' ') {
            i++
        }
    }

    /**
     * Returns the next command or null if the string is over
     */
    fun nextCommand(): Char? {
        skipWhiteSpace()
        if (i >= str.length) {
            return null
        }

        return str[i].also {
            check(it in 'a'..'z' || it in 'A'..'Z') {
                "unknown command '$it'"
            }
        }
    }

    fun nextNumber(): Double {
        skipWhiteSpace()

        return buildString {
            // skip leading whitespace
            while (true) {
                val c = str[i]
                when (c) {
                    ',' -> break
                    ' ' -> break
                    else -> {
                        check(c in '0'..'9' || c == 'e' || c == '.' || c == '-' || c == '+') {
                            "cannot parse number at $i: ${str.substring(i)}"
                        }
                        append(c)
                        i++
                    }
                }
            }
        }.toDouble()
    }

    fun nextPoint(): KPoint {
        return KPoint(nextNumber(), nextNumber())
    }
}

fun String.parsePath(tolerance: Double): List<KPoint> {
    val list = mutableListOf<KPoint>()
    val parser = PathParser(this)
    while (true) {
        val command = parser.nextCommand()

        when (command) {
            'M' -> {
                check(list.isEmpty())
                list.add(parser.nextPoint())
            }
            'L' -> {
                check(list.isNotEmpty())
                list.add(parser.nextPoint())
            }
            'C' -> {
                check(list.isNotEmpty())
                val point1 = list.last()
                val point2 = parser.nextPoint() // control 1
                val point3 = parser.nextPoint() // control 2
                val point4 = parser.nextPoint()
                recursiveBeziers(
                    list,
                    tolerance,
                    point1.x, point1.y,
                    point2.x, point2.y,
                    point3.x, point3.y,
                    point4.x, point4.y,
                )
            }
            'Z' -> {
                check(list.last() == list.first())
            }
            null -> {
                check(list.isNotEmpty())
                return list
            }
        }
    }
}

// From http://agg.sourceforge.net/antigrain.com/research/adaptive_bezier/index.html
fun recursiveBeziers(
    list: MutableList<KPoint>,
    tolerance: Double,
    x1: Double, y1: Double,
    x2: Double, y2: Double,
    x3: Double, y3: Double,
    x4: Double, y4: Double
) {
    // Calculate all the mid-points of the line segments
    //----------------------
    // Calculate all the mid-points of the line segments
    //----------------------
    val x12: Double = (x1 + x2) / 2
    val y12: Double = (y1 + y2) / 2
    val x23: Double = (x2 + x3) / 2
    val y23: Double = (y2 + y3) / 2
    val x34: Double = (x3 + x4) / 2
    val y34: Double = (y3 + y4) / 2
    val x123 = (x12 + x23) / 2
    val y123 = (y12 + y23) / 2
    val x234 = (x23 + x34) / 2
    val y234 = (y23 + y34) / 2
    val x1234 = (x123 + x234) / 2
    val y1234 = (y123 + y234) / 2

    // Try to approximate the full cubic curve by a single straight line
    //------------------

    // Try to approximate the full cubic curve by a single straight line
    //------------------
    val dx: Double = x4 - x1
    val dy: Double = y4 - y1

    val d2: Double = abs((x2 - x4) * dy - (y2 - y4) * dx)
    val d3: Double = abs((x3 - x4) * dy - (y3 - y4) * dx)

    if ((d2 + d3) * (d2 + d3) < tolerance * (dx * dx + dy * dy)) {
        list.add(KPoint(x1234, y1234))
        return
    }

    // Continue subdivision
    //----------------------

    // Continue subdivision
    //----------------------
    recursiveBeziers(list, tolerance, x1, y1, x12, y12, x123, y123, x1234, y1234)
    recursiveBeziers(list, tolerance, x1234, y1234, x234, y234, x34, y34, x4, y4)

}

fun List<KPoint>.toPathString(): String {
    return buildString {
        append("M")
        append(first())


        this.drop(1).forEach {
            append(" ")

            append("L")
            append(it)
        }

        if (first() == last()) {
            append(" Z")
        }
    }
}

fun StringBuilder.append(point: KPoint) {
    append(point.x.toString())
    append(",")
    append(point.y.toString())
}