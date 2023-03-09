package org.dxworks.mavenminer.dto

data class DepinderProject (
    val name: String,
    val version: String,
    var path: String = "",
    var dependencies: Map<String, DepinderDependency> = HashMap() // id of the dependency as Key
)