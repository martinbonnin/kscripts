#!/usr/bin/env kscript

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import java.io.FileWriter
import javax.xml.transform.dom.DOMSource


var factory = DocumentBuilderFactory.newInstance()
val builder = factory.newDocumentBuilder()

File("dailymotion/src/main/res/").listFiles().filter { it.isDirectory }
        .map {
            File(it, "strings.xml")
        }
        .filter { it.exists() }
        .take(1)
        .forEach {
            println("processincezg ${it.absolutePath}")
            processFile(it)
        }

fun processFile(file: File) {
    val doc = builder.parse(file)
    val resources = doc.documentElement

    val strings = resources.getElementsByTagName("string")

    val translations = mutableListOf<Pair<String, String>>()
    val collectionPlural = doc.createElement("plural")
    collectionPlural.setAttribute("name", "collectionPlural")

    val removedNodes = mutableListOf<Node>()
    for (i in 0 until strings.length) {
        val string = strings.item(i) as Element
        val name = string.getAttribute("name")
        val value = string.textContent
        var removeNode = true
        when(name) {
            "noCollection" -> addPlural(collectionPlural, "zero", value)
            "oneCollection" -> addPlural(collectionPlural, "one", value)
            "xCollection" -> addPlural(collectionPlural, "many", value)
            else -> {
                removeNode = false
                translations.add(name to value)
            }
        }
        if (removeNode) {
            removedNodes.add(string)
        }
    }

    removedNodes.forEach {
        resources.removeChild(it)
    }
    resources.appendChild(collectionPlural)

    val transformer = TransformerFactory.newInstance().newTransformer()
    val writer = FileWriter(file)
    val result = StreamResult(writer)
    transformer.transform(DOMSource(doc), result)
}

fun addPlural(plural: Element, quantity: String, value: String) {
    val doc = plural.ownerDocument
    val item = doc.createElement("item")

    item.setAttribute("quantity", quantity)
    item.textContent = value

    plural.appendChild(item)
}
