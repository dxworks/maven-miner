package org.dxworks.mavenminer

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.maven.model.Model
import org.apache.maven.model.Parent
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList
import kotlin.io.path.*
import kotlin.streams.toList

@OptIn(ExperimentalPathApi::class)
fun main(args: Array<String>) {
    if (args.size != 1) {
        error("Bad arguments! Please provide only one argument, namely the path to the folder containing the source code.")
    }

    val baseFolderArg = args[0]

    val baseFolder = File(baseFolderArg)

    println("Starting Maven Miner\n")
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
                val deps = mavenModel.dependencies.mapNotNull { MavenDependency(it.groupId, it.artifactId, it.version, it.isOptional) }
                MavenModule(mavenModuleId, baseFolderPath.relativize(path), mavenModel.parent?.let { MavenParent(it) }, deps, mavenModel.properties)
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

    println("Writing Results...")


    println("Exporting Model to $modelPath")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(modelPath.toFile(), modulesMap.values)
    println("Exporting Graph to $graphPath")
    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(graphPath.toFile(), Graph(nodes, links))

    println("Exporting Relations to $relationsPath")
    relationsPath.writeLines(links.map { "${it.source},${it.target},${it.value}" })

    println("\nMaven Miner finished successfully! Please view your results at $resultsPath")
}

fun extractMavenModuleId(mavenModel: Model): MavenModuleId {
    return MavenModuleId(mavenModel.groupId ?: mavenModel.parent?.groupId,
            mavenModel.artifactId,
            mavenModel.version ?: mavenModel.parent?.version)
}

data class MavenModule(
        val id: MavenModuleId,
        @JsonIgnore
        val path: Path,
        val parent: MavenParent? = null,
        var dependencies: List<MavenDependency> = ArrayList(),
        val properties: Properties
) {
    @JsonProperty("path")
    val relativePath = path.toString()
}

open class MavenModuleId(
        val groupID: String?,
        val artifactID: String,
        val version: String?,
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

class MavenParent(groupID: String?, artifactID: String, version: String?, val relativePath: String? = null)
    : MavenModuleId(groupID, artifactID, version) {
    constructor(parent: Parent) : this(parent.groupId, parent.artifactId, parent.version, parent.relativePath)
}

data class MavenDependency(
        val groupID: String?,
        val artifactID: String,
        val version: String?,
        val optional: Boolean,
) {
    fun toModuleId() = MavenModuleId(groupID, artifactID, version)
}

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

