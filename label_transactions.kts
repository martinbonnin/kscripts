#!/usr/bin/env kscript

import java.io.File

val file = File(args[0])

var total = 0
var found = 0

val lines = file.readLines()
val numberOfColumns = 8

lines.drop(1).map {
    val r = it.split(";")

    // Pad all the columns to the same sizes
    val columns = (r + List(numberOfColumns - r.size) {""}).toMutableList()

    val libelle = r[2]

    var category: String? = null
    var subCategory: String? = null

    when {
        libelle.contains("FRANPRIX") -> {
            category = "Courses"
            subCategory = "Franprix"
        }
        libelle.contains("BOUCHERIE") -> {
            category = "Courses"
            subCategory = "Boucherie"
        }
        libelle.contains("NATURALIA") -> {
            category = "Courses"
            subCategory = "Naturalia"
        }
        libelle.toLowerCase().contains("spotify") -> {
            category = "Tel & Internet"
        }
        libelle.toLowerCase().contains("netflix") -> {
            category = "Tel & Internet"
        }
        libelle.contains("PRLV FREE") -> {
            category = "Tel & Internet"
        }
        libelle.contains("Free Telecom") -> {
            category = "Tel & Internet"
        }
        libelle.contains("Loyer") -> {
            category = "Loyer"
        }
        libelle.contains("PAIN") -> {
            category = "Courses"
            subCategory = "Pain"
        }
        libelle.contains("AU BOUT DU CHAMP") -> {
            category = "Courses"
            subCategory = "Au bout du champ"
        }
        libelle.contains("SCBP MOINES") -> {
            category = "Courses"
            subCategory = "Biocop"
        }
        libelle.contains("FROMAGE") -> {
            category = "Courses"
            subCategory = "Fromage"
        }
        libelle.contains("ENGIE") -> {
            category = "Energie"
        }
        libelle.contains("SNCF") -> {
            category = "WE & Vacances"
        }
        libelle.contains("CAFE MEO") -> {
            category = "Vie Courante"
        }
        libelle.contains("LEROY MERLIN") -> {
            category = "Vie Courante"
        }
        libelle.contains("CHARBONN") -> {
            category = "Courses"
            subCategory = "Pain"
        }
        libelle.contains("GOUIN") -> {
            category = "Courses"
            subCategory = "Pain"
        }
        libelle.contains("SETTE") -> {
            // Pizza rue brochant
            category = "Sorties"
            subCategory = "Resto"
        }
        libelle.contains("MABADIS") -> {
            category = "Courses"
            subCategory = "G20"
        }
        libelle.contains("DROGUERIE") -> {
            category = "Vie courante"
        }
        libelle.contains("UBER") -> {
            category = "Transport"
        }
        libelle.contains("LANNI") -> {
            category = "Courses"
            subCategory = "Cafe"
        }
        libelle.contains("VIR BONNIN") -> {
            category = "Virement"
        }
        libelle.startsWith("VIR ") -> {
            category = "Virement"
        }
        libelle.toLowerCase().contains("pharmacie") -> {
            category = "Pharmacie"
        }
        libelle.contains("KH PRIMEUR") -> {
            category = "Courses"
            subCategory = "Primeur Chinois"
        }
        libelle.contains("NICOLAS") -> {
            category = "Courses"
            subCategory = "Alcool"
        }

    }

    total += 1
    if (category != null) {
        found += 1
    }

    // csv ignores trailing null values
    category?.let {
        columns.set(5, category)
    }
    subCategory?.let {
        columns.set(6, subCategory)
    }
    columns.toList()
}.let {
    listOf(listOf("date", "date", "libelle", "debit", "credit", "category", "subcategory", "comment")) + it
}.map {
    it.joinToString(";")
}.joinToString(separator = "\n", postfix = "\n").also {
    File("output.csv").writeText(it)
}

println("Found: $found/$total (${found.toDouble()/total}%")
println("output.csv written.")