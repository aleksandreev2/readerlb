package com.readerlb.app.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MangaSourceTest {
    @Test fun filenameExtractsVolumeChapterAndOptionalTitle() {
        assertEquals(ComicFileHint("1", "3", "Контратака"), parseComicFileHint("1_-_3_Контратака.cbz"))
        assertEquals(ComicFileHint("1", "4", ""), parseComicFileHint("1_-_4.pdf"))
    }

    @Test fun cbzCountsMagicDetectedPagesInNaturalOrder() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            for (name in listOf("p10.webp", "p2.webp", "notes.txt", "p1.webp")) {
                out.putNextEntry(ZipEntry(name))
                out.write(if (name.endsWith(".webp")) "RIFF0000WEBP".toByteArray() else "ignore".toByteArray())
                out.closeEntry()
            }
        }
        assertEquals(listOf("p1.webp", "p2.webp", "p10.webp"), inspectComicZip(ByteArrayInputStream(zip.toByteArray())).pageNames)
    }

    @Test fun cbzRejectsArchiveWithoutImagePages() {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            out.putNextEntry(ZipEntry("notes.txt"))
            out.write("text".toByteArray())
            out.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            inspectComicZip(ByteArrayInputStream(zip.toByteArray()))
        }
    }
}
