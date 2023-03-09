package org.dxworks.mavenminer.dto

data class DepinderDependency(
    val name: String,
    val version: String,
    val id: String = "$name@$version",
    val type: String?,
    val classifier: String?,
    val omitted: Boolean,
    val description: String?,
    val requestedBy: MutableList<String> = ArrayList()
)