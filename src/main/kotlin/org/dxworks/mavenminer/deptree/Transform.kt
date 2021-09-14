package org.dxworks.mavenminer.deptree

import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import fr.dutra.tools.maven.deptree.core.InputType
import fr.dutra.tools.maven.deptree.core.Node
import fr.dutra.tools.maven.deptree.core.Visitor
import org.dxworks.ignorerLibrary.IgnorerBuilder
import org.dxworks.mavenminer.MavenModule
import org.dxworks.mavenminer.MavenModuleId
import org.dxworks.mavenminer.dto.InspectorLibDependency
import org.dxworks.mavenminer.usage
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.system.exitProcess

val jsonWriter: ObjectWriter = jacksonObjectMapper().writerWithDefaultPrettyPrinter()

fun transform(args: Array<String>) {
    val convertArg = args[0];
    if (convertArg != "convert" || args.size < 2 || args.size > 4) {
        println(usage)
        exitProcess(1)
    }

    val fileOrFolderArg = args[1]
    val modelFilePath = args.find { it.startsWith("-model=") }?.removePrefix("-model=")
    val glob = args.find { it.startsWith("-pattern=") }?.removePrefix("-pattern=") ?: "*"
    val globMatcher = IgnorerBuilder(listOf(glob)).compile()

    val file = File(fileOrFolderArg)
    val modules: List<MavenModule> = extractModules(modelFilePath?.let { File(it) })

    when {
        file.isFile -> convertDepTreeFileToJson(file, modules)
        file.isDirectory -> file.walkTopDown()
            .filter { it.isFile }
            .filter { globMatcher.accepts(it.name) }
            .forEach { convertDepTreeFileToJson(it, modules) }
        else -> throw FileNotFoundException(file.absolutePath)
    }
}

fun extractModules(modelFile: File?): List<MavenModule> {
    if (modelFile == null)
        return emptyList()
    return try {
        jacksonObjectMapper().readValue(modelFile)
    } catch (e: Exception) {
        println("Warning: could not parse maven model file: ${modelFile.absolutePath}!")
        return emptyList()
    }
}


fun convertDepTreeFileToJson(deptreeFile: File, modules: List<MavenModule>) {
    val deps = parseMavenDependencyTree(deptreeFile, modules)
    val resultFilePath = Path.of("results", "${deptreeFile.nameWithoutExtension}-il-deps.json")
    jsonWriter.writeValue(resultFilePath.toFile(), deps)
}

fun parseMavenDependencyTree(file: File, modules: List<MavenModule>): Map<String, List<InspectorLibDependency>> {

    val visitorsList = ArrayList<MavenDependencyTreeVisitor>()

    val treeContent = StringBuilder()
    file.readLines().forEach {
        if (it.matches(Regex("^[a-zA-Z].*$")) && treeContent.isNotBlank()) {
            val tree = InputType.TEXT.newParser().parse(treeContent.toString().reader())
            val visitor = MavenDependencyTreeVisitor()
            visitor.visit(tree)
            treeContent.clear()
            visitorsList.add(visitor)
        }
        treeContent.append(it)
        treeContent.append("\n")
    }
    val tree = InputType.TEXT.newParser().parse(treeContent.toString().reader())
    val visitor = MavenDependencyTreeVisitor()
    visitor.visit(tree)
    visitorsList.add(visitor)

    return visitorsList.associate { getPathToProject(it.root, modules) to it.dependencies }
}

fun getPathToProject(mavenModuleId: MavenModuleId, modules: List<MavenModule>): String {
    val mavenProjectPath = modules.find { it.id.groupID == mavenModuleId.groupID && it.id.artifactID == mavenModuleId.artifactID }
        ?.let { it.path.removeSuffix("pom.xml").removeSuffix("/") }
    if (mavenProjectPath == null)
        println("Warning: could not find $mavenModuleId in the maven model!")

    return mavenProjectPath ?: mavenModuleId.artifactID
}

class MavenDependencyTreeVisitor : Visitor {
    lateinit var root: MavenModuleId
    var dependencies: List<InspectorLibDependency> = ArrayList()

    override fun visit(node: Node) {
        if (!this::root.isInitialized)
            root = MavenModuleId(node.groupId, node.artifactId, node.version)
        else {
            dependencies = dependencies + node.toDTO()
        }

        node.childNodes.forEach { visit(it) }
    }
}

fun Node.toDTO(): InspectorLibDependency =
    InspectorLibDependency(
        name = "$groupId:$artifactId",
        version = version,
    )
