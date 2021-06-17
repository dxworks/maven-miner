package org.dxworks.mavenminer.deptree

import org.junit.jupiter.api.Test

internal class TransformKtTest {

    @Test
    fun transform() {
        transform(arrayOf("convert", "src/test/resources/deptree/dx-platform-deptree.txt", "src/test/resources/deptree/dx-platform-maven-model.json"))
    }
}
