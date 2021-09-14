package org.dxworks.mavenminer.dto

data class InspectorLibDependency(
    val name: String,
    val version: String?,
    val provider: String = "maven"
)
