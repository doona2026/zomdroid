package com.zomdroid.ui.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GamepadMappingCodecTest {
    @Test fun mappingRoundTripsUsingLegacyCsvFormat() {
        val mapping = intArrayOf(96, 97, 99, 100, 102, 103, 108, 109, 110, 106, 107, -1)
        assertThat(GamepadMappingCodec.decode(GamepadMappingCodec.encode(mapping))?.toList()).isEqualTo(mapping.toList())
    }

    @Test fun malformedMappingIsRejected() {
        assertThat(GamepadMappingCodec.decode("1,broken,3")).isNull()
        assertThat(GamepadMappingCodec.decode("")).isNull()
    }
}
