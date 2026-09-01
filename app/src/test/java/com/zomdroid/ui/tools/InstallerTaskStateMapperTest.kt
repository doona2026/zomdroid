package com.zomdroid.ui.tools

import com.google.common.truth.Truth.assertThat
import com.zomdroid.InstallerService
import org.junit.Test

class InstallerTaskStateMapperTest {
    @Test fun mapsProgressAndFinishedErrorState() {
        val state = InstallerService.TaskState("Installing", "Copying", 3, 10, false, false)
        val ui = InstallerTaskStateMapper.from(state)
        assertThat(ui?.title).isEqualTo("Installing")
        assertThat(ui?.progress).isEqualTo(3)
        assertThat(ui?.progressMax).isEqualTo(10)
        assertThat(ui?.failed).isFalse()
    }

    @Test fun mapsFinishedAndNullStates() {
        assertThat(InstallerTaskStateMapper.from(null)).isNull()
        val ui = InstallerTaskStateMapper.from(InstallerService.TaskState("Done", "ok", -1, 0, true, true))
        assertThat(ui?.finished).isTrue()
        assertThat(ui?.failed).isTrue()
    }
}
