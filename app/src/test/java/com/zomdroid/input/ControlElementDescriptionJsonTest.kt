package com.zomdroid.input

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

class ControlElementDescriptionJsonTest {
    private val gson = Gson()

    @Test
    fun readsChineseEnumAliasesAndWritesCanonicalValues() {
        val description = gson.fromJson(
            """{"type":"圆形按钮","inputType":"键鼠","icon":"开始键","style":"轮廓"}""",
            ControlElementDescription::class.java,
        )

        assertThat(description.type).isEqualTo(AbstractControlElement.Type.BUTTON_CIRCLE)
        assertThat(description.inputType).isEqualTo(AbstractControlElement.InputType.MNK)
        assertThat(description.icon).isEqualTo(ControlElementDescription.Icon.GAMEPAD_START_ICON)
        assertThat(description.style).isEqualTo(ControlElementDescription.Style.OUTLINE)
        assertThat(gson.toJson(description)).contains("\"type\":\"BUTTON_CIRCLE\"")
    }

    @Test
    fun readsEverySupportedChineseControlType() {
        val aliases = mapOf(
            "摇杆" to AbstractControlElement.Type.STICK,
            "方向键" to AbstractControlElement.Type.DPAD,
            "矩形按钮" to AbstractControlElement.Type.BUTTON_RECT,
            "圆形按钮" to AbstractControlElement.Type.BUTTON_CIRCLE,
            "触摸板" to AbstractControlElement.Type.TOUCHPAD,
            "滚动条" to AbstractControlElement.Type.SCROLL_BAR,
        )

        aliases.forEach { (alias, expected) ->
            val description = gson.fromJson(
                """{"type":"$alias"}""",
                ControlElementDescription::class.java,
            )
            assertThat(description.type).isEqualTo(expected)
        }
    }

    @Test
    fun readsEverySupportedChineseInputAndIconAlias() {
        val gamepad = gson.fromJson(
            """{"type":"摇杆","inputType":"手柄","icon":"无","style":"轮廓"}""",
            ControlElementDescription::class.java,
        )
        val mouseAndKeyboard = gson.fromJson(
            """{"type":"方向键","inputType":"键鼠","icon":"返回键","style":"OUTLINE"}""",
            ControlElementDescription::class.java,
        )

        assertThat(gamepad.inputType).isEqualTo(AbstractControlElement.InputType.GAMEPAD)
        assertThat(gamepad.icon).isEqualTo(ControlElementDescription.Icon.NO_ICON)
        assertThat(mouseAndKeyboard.inputType).isEqualTo(AbstractControlElement.InputType.MNK)
        assertThat(mouseAndKeyboard.icon).isEqualTo(ControlElementDescription.Icon.GAMEPAD_BACK_ICON)
    }

    @Test
    fun removesInvisibleControlCharactersBeforeEnumResolution() {
        val normalized = InputControlsView.normalizeControlsJsonForLoad(
            """[{"type":"\u0016STICK","inputType":"\u0016GAMEPAD","icon":"\u0016NO_ICON","style":"\u0016OUTLINE"}]""",
        )

        val description = gson.fromJson(
            normalized,
            Array<ControlElementDescription>::class.java,
        ).single()

        assertThat(description.type).isEqualTo(AbstractControlElement.Type.STICK)
        assertThat(description.inputType).isEqualTo(AbstractControlElement.InputType.GAMEPAD)
        assertThat(description.icon).isEqualTo(ControlElementDescription.Icon.NO_ICON)
        assertThat(description.style).isEqualTo(ControlElementDescription.Style.OUTLINE)
    }
}
