#!/usr/bin/env kscript

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

val xml = """
<lint>
    <issue id="UnusedAttribute">
        <ignore regexp="android:autoVerify"/>
    </issue>
</lint>
""".trimIndent()

val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
        xml.byteInputStream()
)

val ignoreNodes = document.documentElement.getElementsByTagName("ignore")

for (i in 0 until ignoreNodes.length){
    val node = ignoreNodes.item(i)

    val regex = (node as Element).getAttribute("regexp")

    println("regex='$regex'")

    val pattern = Pattern.compile(regex)
    val matcher = pattern.matcher("""
         /Users/m.bonnin/git/dailymotion-android/dailymotion/src/main/AndroidManifest.xml:86: Error: Attribute autoVerify is only used in API level 23 and higher (current min is 21) [UnusedAttribute]
              <intent-filter android:autoVerify="true">
    """.trimIndent())

    if (matcher.find()) {
        println("found")
    } else {
        println("not found")
    }
}