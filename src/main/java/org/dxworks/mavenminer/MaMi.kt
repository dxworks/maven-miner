package org.dxworks.mavenminer

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.maven.model.Exclusion
import org.apache.maven.model.Model
import org.apache.maven.model.Parent
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.streams.toList

@OptIn(ExperimentalPathApi::class)
fun main(args: Array<String>) {
    if (args.size != 1) {
        throw IllegalArgumentException("Bad arguments! Please provide only one argument, namely the path to the folder containing the source code.")
    }

    val baseFolderArg = args[0]

    val baseFolder = File(baseFolderArg)

    println("Starting MaMi (MavenMiner)\n")
    println("Reading Files...")

    val baseFolderPath = baseFolder.toPath()
    val poms = Files.walk(baseFolderPath)
        .filter { it.isRegularFile() }
        .filter { it.name == "pom.xml" }
        .toList()

    val mavenReader = MavenXpp3Reader()

    val modulesMap = poms
        .mapNotNull { path ->
            val mavenModel = mavenReader.read(path.inputStream())
            val mavenModuleId = extractMavenModuleId(mavenModel)
            val deps = mavenModel.dependencies.mapNotNull {
                MavenDependency(
                    it.groupId,
                    it.artifactId,
                    it.version,
                    it.isOptional
                )
            }
            val dependencyManagementDeps = mavenModel.dependencyManagement?.dependencies?.mapNotNull {
                MavenDependencyManagementDependency(
                    it.groupId,
                    it.artifactId,
                    it.version,
                    it.type,
                    it.classifier,
                    it.scope,
                    it.exclusions
                )
            }.orEmpty()
            MavenModule(
                mavenModuleId,
                baseFolderPath.relativize(path),
                mavenModel.parent?.let { MavenParent(it) },
                deps,
                mavenModel.properties,
                dependencyManagementDeps
            )
        }
        .map { it.id to it }
        .toMap()

    println("Creating Graph...")
    val nodes = modulesMap.keys.map { GraphNode(it.artifactID) }
    val links = modulesMap.values.map { module ->
        module.dependencies
            .mapNotNull { modulesMap[it.toModuleId()] }
            .map { GraphLink(module.id.artifactID, it.id.artifactID) }
    }.flatten().toList()

    val resultsPath = Path.of("results")
    resultsPath.toFile().mkdirs()

    val modelPath = Path.of("results", "maven-model.json")
    val newmodelPath = Path.of("results", "new-maven-model.json")
    val graphPath = Path.of("results", "maven-graph.json")
    val relationsPath = Path.of("results", "maven-relations.csv")
    val inspectorLibPath = Path.of("results", "il-maven-deps.json")

    println("Writing Results...")


    println("Exporting Model to $modelPath")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(modelPath.toFile(), modulesMap.values)
    println("Exporting Graph to $graphPath")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(graphPath.toFile(), Graph(nodes, links))

    println("Exporting Relations to $relationsPath")
    relationsPath.writeLines(links.map { "${it.source},${it.target},${it.value}" })

    val newDeps = searchVersions(modulesMap)

    println("Exporting Model to $newmodelPath")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(newmodelPath.toFile(), newDeps.values)

/*    println("Exporting Inspector Lib results to $inspectorLibPath")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter()
        .writeValue(inspectorLibPath.toFile(), modulesMap.entries.associate {
            it.value.relativePath to it.value.dependencies
               .map { it.toInspectorLibDep() }
                .distinct()
        })*/

    println("Exporting Inspector Lib results to $inspectorLibPath")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter()
        .writeValue(inspectorLibPath.toFile(), newDeps.entries.associate {
            it.value.relativePath.substringBeforeLast("\\") to it.value.dependencies
                .map { it.toInspectorLibDep() }
                .distinct()
        })

    println("\nMaven Miner finished successfully! Please view your results in the ./results directory")
}

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
    var currentPath = entry.path.toString().substringBeforeLast("\\")
    var parentPath = currentPath.substringBeforeLast("\\")

    while (parentPath != currentPath) {
        v.map { it.value }.forEach { e ->
            if (e.path.toString() == "$parentPath\\pom.xml") {
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
            if (e.path.toString() == "$parentPath\\pom.xml") {
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
            if (e.path.toString() == "$parentPath\\pom.xml") {
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

fun extractMavenModuleId(mavenModel: Model): MavenModuleId {
    return MavenModuleId(
        mavenModel.groupId ?: mavenModel.parent?.groupId,
        mavenModel.artifactId,
        mavenModel.version ?: mavenModel.parent?.version
    )
}

data class MavenModule(
    val id: MavenModuleId,
    @JsonIgnore
    val path: Path,
    val parent: MavenParent? = null,
    var dependencies: List<MavenDependency> = ArrayList(),
    val properties: Properties,
    val dependencyManagementDependencies: List<MavenDependencyManagementDependency>
) {
    @JsonProperty("path")
    val relativePath = path.toString()
}

open class MavenModuleId(
    val groupID: String?,
    val artifactID: String,
    var version: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MavenModuleId

        if (groupID != other.groupID) return false
        if (artifactID != other.artifactID) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupID?.hashCode() ?: 0
        result = 31 * result + artifactID.hashCode()
        return result
    }
}

class MavenParent(groupID: String?, artifactID: String, version: String?, val relativePath: String? = null) :
    MavenModuleId(groupID, artifactID, version) {
    constructor(parent: Parent) : this(parent.groupId, parent.artifactId, parent.version, parent.relativePath)
}

data class MavenDependency(
    val groupID: String?,
    val artifactID: String,
    var version: String?,
    val optional: Boolean,
) {
    fun toModuleId() = MavenModuleId(groupID, artifactID, version)
    fun toInspectorLibDep() = InspectorLibDependency("$groupID:$artifactID", version)
}

data class MavenDependencyManagementDependency(
    val groupID: String,
    val artifactID: String,
    var version: String,
    val type: String?,
    val classifier: String?,
    val scope: String?,
    val exclusions: List<Exclusion>?
)

data class Graph(
    val nodes: List<GraphNode>,
    val links: List<GraphLink>
)

data class GraphNode(
    val name: String,
    val component: Number = 1
)

data class GraphLink(
    val source: String,
    val target: String,
    val value: Number = 1
)

data class InspectorLibDependency(
    val name: String,
    val version: String?,
    val provider: String = "maven"
)

