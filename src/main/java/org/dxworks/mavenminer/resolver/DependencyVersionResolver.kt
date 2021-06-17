package org.dxworks.mavenminer.resolver

import org.dxworks.mavenminer.MavenModule
import org.dxworks.mavenminer.MavenModuleId

fun searchVersions(values: Map<MavenModuleId, MavenModule>): Map<MavenModuleId, MavenModule> {
    val v = values

    v.map { it.value }.forEach { entry ->
        if (entry.id.version?.contains("\${") == true) {
            if (entry.properties.isNotEmpty()) {
                entry.properties.forEach {
                    if (entry.id.version == "\${${it.key}}") {
                        entry.id.version = it.value as String?
                    } else {
                        searchIdVersion(entry, v)
                    }
                }
            } else {
                searchIdVersion(entry, v)
            }
        }
    }

    v.map { it.value }.forEach { entry ->
        if (entry.parent?.version?.contains("\${") == true) {
            searchParentVersion(entry, v)
        }
    }

    v.map { it.value }.forEach { entry ->
        entry.dependencies.forEach { dep ->
            if (dep.version == "\${project.version}") {
                dep.version = entry.id.version
            }
            if (dep.version == "\${project.parent.version}") {
                dep.version = entry.parent?.version
            }
        }
        entry.dependencyManagementDependencies.forEach { dep ->
            if (dep.version == "\${project.version}") {
                dep.version = entry.id.version.toString()
            }
            if (dep.version == "\${project.parent.version}") {
                dep.version = entry.parent?.version.toString()
            }
        }
    }

    v.map { it.value }.forEach { entry ->
        entry.dependencies.forEach { dep ->
            if (dep.version?.contains("\${") == true) {
                if (entry.properties.isNotEmpty()) {
                    entry.properties.forEach {
                        if (dep.version == "\${${it.key}}") {
                            dep.version = it.value as String?
                        } else {
                            searchDependencyVersion(entry, v)
                        }
                    }
                } else {
                    searchDependencyVersion(entry, v)
                }
            }
        }
        entry.dependencyManagementDependencies.forEach { dep ->
            if (dep.version.contains("\${") == true) {
                if (entry.properties.isNotEmpty()) {
                    entry.properties.forEach {
                        if (dep.version == "\${${it.key}}") {
                            dep.version = it.value as String
                        } else {
                            searchDependencyManagementVersion(entry, v)
                        }
                    }
                } else {
                    searchDependencyManagementVersion(entry, v)
                }
            }
        }
    }

    v.map { it.value }.forEach { entry ->
        entry.dependencies.forEach { dep ->
            if (dep.version.isNullOrEmpty()) {
                if (entry.dependencyManagementDependencies.isNotEmpty()) {
                    entry.dependencyManagementDependencies.forEach {
                        if ((it.groupID == dep.groupID) && (it.artifactID == dep.artifactID)) {
                            dep.version = it.version
                        } else {
                            searchDependencyManagement(entry, v)
                        }
                    }
                } else {
                    searchDependencyManagement(entry, v)
                }
            }
        }
    }

    return v
}

fun searchDependencyManagement(entry: MavenModule, v: Map<MavenModuleId, MavenModule>) {
    var currentPath = entry.path.toString().substringBeforeLast("\\")
    var parentPath = currentPath.substringBeforeLast("\\")

    while (parentPath != currentPath) {
        v.map { it.value }.forEach { e ->
            if (e.path.toString() == "$parentPath\\pom.xml") {
                if (e.dependencyManagementDependencies.isNotEmpty()) {
                    e.dependencyManagementDependencies.forEach { dep ->
                        entry.dependencies.forEach {
                            if ((it.groupID == dep.groupID) && (it.artifactID == dep.artifactID)) {
                                it.version = dep.version
                            }
                        }

                    }
                }
            }
            currentPath = parentPath
            parentPath = parentPath.substringBeforeLast("\\")
        }
    }
}

fun searchParentVersion(entry: MavenModule, v: Map<MavenModuleId, MavenModule>) {
    v.map { it.value }.forEach { e ->
        if (e.id.groupID == entry.parent?.groupID && e.id.artifactID == entry.parent?.artifactID) {
            entry.parent.version = e.id.version
        }
    }
}

fun searchIdVersion(entry: MavenModule, v: Map<MavenModuleId, MavenModule>) {
    var currentPath = entry.path.substringBeforeLast("\\")
    var parentPath = currentPath.substringBeforeLast("\\")

    while (parentPath != currentPath) {
        v.map { it.value }.forEach { e ->
            if (e.path == "$parentPath\\pom.xml") {
                if (e.properties.isNotEmpty()) {
                    e.properties.forEach {
                        if (entry.id.version == "\${${it.key}}") {
                            entry.id.version = it.value as String?
                            return
                        }
                    }
                }
            }
            currentPath = parentPath
            parentPath = parentPath.substringBeforeLast("\\")
        }
    }
}

fun searchDependencyVersion(entry: MavenModule, v: Map<MavenModuleId, MavenModule>) {
    var currentPath = entry.path.toString().substringBeforeLast("\\")
    var parentPath = currentPath.substringBeforeLast("\\")

    while (parentPath != currentPath) {
        v.map { it.value }.forEach { e ->
            if (e.path == "$parentPath\\pom.xml") {
                if (e.properties.isNotEmpty()) {
                    e.properties.forEach { prop ->
                        entry.dependencies.forEach {
                            if (it.version == "\${${prop.key}}") {
                                it.version = prop.value as String?
                                return
                            }
                        }

                    }
                }
            }
            currentPath = parentPath
            parentPath = parentPath.substringBeforeLast("\\")
        }
    }
}

fun searchDependencyManagementVersion(entry: MavenModule, v: Map<MavenModuleId, MavenModule>) {
    var currentPath = entry.path.toString().substringBeforeLast("\\")
    var parentPath = currentPath.substringBeforeLast("\\")

    while (parentPath != currentPath) {
        v.map { it.value }.forEach { e ->
            if (e.path == "$parentPath\\pom.xml") {
                if (e.properties.isNotEmpty()) {
                    e.properties.forEach { prop ->
                        entry.dependencyManagementDependencies.forEach {
                            if (it.version == "\${${prop.key}}") {
                                it.version = prop.value as String
                                return
                            }
                        }

                    }
                }
            }
            currentPath = parentPath
            parentPath = parentPath.substringBeforeLast("\\")
        }
    }
}

