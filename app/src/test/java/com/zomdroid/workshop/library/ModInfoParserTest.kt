package com.zomdroid.workshop.library

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import org.junit.Test

class ModInfoParserTest {
    @Test
    fun parsesKnownFieldsAndKeepsUnknownFields() {
        val file = Files.createTempFile("mod", ".info").toFile()
        file.writeText(
            """
            # comment
            name=Example Mod
            id=example.mod
            description=An example mod
            poster=poster.png
            icon=icon.png
            custom=value=with=equals
            """.trimIndent(),
        )

        val parsed = ModInfoParser.parse(file)

        assertThat(parsed.name).isEqualTo("Example Mod")
        assertThat(parsed.id).isEqualTo("example.mod")
        assertThat(parsed.description).isEqualTo("An example mod")
        assertThat(parsed.poster).isEqualTo("poster.png")
        assertThat(parsed.icon).isEqualTo("icon.png")
        assertThat(parsed.fields["custom"]).isEqualTo("value=with=equals")
        assertThat(parsed.readable).isTrue()
    }

    @Test
    fun ignoresBlankMalformedAndCommentLinesAndUsesFirstDuplicateValue() {
        val file = Files.createTempFile("mod", ".info").toFile()
        file.writeText(
            """

            not a key value
            name=First
            name=Second
            ; another comment
            id=
            """.trimIndent(),
        )

        val parsed = ModInfoParser.parse(file)

        assertThat(parsed.name).isEqualTo("First")
        assertThat(parsed.id).isNull()
        assertThat(parsed.fields).containsExactly("name", "First")
    }

    @Test
    fun missingFileIsReportedWithoutThrowing() {
        val parsed = ModInfoParser.parse(Files.createTempDirectory("mod").resolve("missing.info").toFile())

        assertThat(parsed.readable).isFalse()
        assertThat(parsed.fields).isEmpty()
    }
}
