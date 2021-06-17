package org.dxworks.mavenminer

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonKey
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.maven.model.Exclusion
import org.apache.maven.model.Model
import org.apache.maven.model.Parent
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.dxworks.mavenminer.deptree.transform
import org.dxworks.mavenminer.dto.InspectorLibDependency
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.streams.toList
import kotlin.system.exitProcess


const val usage = """
    Bad arguments! Please provide at least one argument
        * If you want to mine a folder provide one argument, namely the path to the folder containing the source code.
        * If you want to convert dependency-tree files to il-deps.json files, there are 2 options:
                java -jar mami.jar convert <path_to_deptree_file> <path_to_maven_model>
                java -jar mami.jar convert <path_to_deptree_folder> <path_to_maven_model> -> all files from that folder will be treated as dependency tree files and mami will try to transform them
                java -jar mami.jar convert <path_to_deptree_folder> <file_pattern_glob> <path_to_maven_model> -> all files matching the glob from that folder will be treated as dependency tree files and mami will try to transform them
                    example: java -jar mami.jar convert /path/to/folder *.txt /path/to/maven/model/json/file
"""

fun main(args: Array<String>) {
    if (args.size < 1) {
        println(usage)
        exitProcess(1)
    }

    if (args.size == 1)
        mine(args)
    else
        transform(args)
}

@OptIn(ExperimentalPathApi::class)
private fun mine(args: Array<String>) {
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
                baseFolderPath.relativize(path).toString(),
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

    println("Exporting Inspector Lib results to $inspectorLibPath")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter()
        .writeValue(inspectorLibPath.toFile(), modulesMap.entries.associate {
            it.value.path to it.value.dependencies
                .map { it.toInspectorLibDep() }
                .distinct()
        })

//    val newmodelPath = Path.of("results", "new-maven-model.json")
//    val newDeps = searchVersions(modulesMap)
//
//    println("Exporting Model to $newmodelPath")
//    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(newmodelPath.toFile(), newDeps.values)

//    println("Exporting Inspector Lib results to $inspectorLibPath")
//    jacksonObjectMapper().writerWithDefaultPrettyPrinter()
//        .writeValue(inspectorLibPath.toFile(), newDeps.entries.associate {
//            it.value.relativePath.substringBeforeLast("\\") to it.value.dependencies
//                .map { it.toInspectorLibDep() }
//                .distinct()
//        })

    println("\nMaven Miner finished successfully! Please view your results in the ./results directory")
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
    val path: String,
    val parent: MavenParent? = null,
    var dependencies: List<MavenDependency> = ArrayList(),
    val properties: Properties,
    val dependencyManagementDependencies: List<MavenDependencyManagementDependency>
)

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

