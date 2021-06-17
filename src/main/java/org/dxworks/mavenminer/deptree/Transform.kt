package org.dxworks.mavenminer.deptree

import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import fr.dutra.tools.maven.deptree.core.InputType
import fr.dutra.tools.maven.deptree.core.Node
import fr.dutra.tools.maven.deptree.core.Visitor
import org.dxworks.ignorerLibrary.IgnorerBuilder
import org.dxworks.mavenminer.MavenModule
import org.dxworks.mavenminer.dto.InspectorLibDependency
import org.dxworks.mavenminer.usage
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.system.exitProcess

val jsonWriter: ObjectWriter = jacksonObjectMapper().writerWithDefaultPrettyPrinter()

fun transform(args: Array<String>) {
    val convertArg = args[0];
    if (convertArg != "convert" || args.size < 2 || args.size > 3) {
        println(usage)
        exitProcess(1)
    }

    val fileOrFolderArg = args[1]
    val modelFilePath = if (args.size == 2) args[1] else args[2]
    val glob = if (args.size == 3) args[1] else "*"
    val globMatcher = IgnorerBuilder(listOf(glob)).compile()

    val file = File(fileOrFolderArg)
    val modelFile = File(modelFilePath)
    val modules: List<MavenModule> = jacksonObjectMapper().readValue(modelFile)

    when {
        file.isFile -> convertDepTreeFileToJson(file, modules)
        file.isDirectory -> file.walkTopDown()
            .filter { it.isFile }
            .filter { globMatcher.accepts(it.name) }
            .forEach { convertDepTreeFileToJson(it, modules) }
        else -> throw FileNotFoundException(file.absolutePath)
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
        if(it.matches(Regex("^[a-zA-Z].*$")) && treeContent.isNotBlank()) {
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

fun getPathToProject(mavenProject: String, modules: List<MavenModule>): String {
    val segments = mavenProject.split(":")
    if (segments.size < 2) {
        println("Warning: could not find group and artifact id for $mavenProject")
        return mavenProject
    }
    val groupId = segments[0]
    val artifactId = segments[1]

    val mavenProjectPath = modules.find { it.id.groupID == groupId && it.id.artifactID == artifactId }?.let { it.path.removeSuffix("pom.xml").removeSuffix("/") }
    if(mavenProjectPath == null)
        println("Warning: could not find $mavenProject in the maven model!")

    return mavenProjectPath ?: mavenProject
}

class MavenDependencyTreeVisitor : Visitor {
    lateinit var root: String
    var dependencies: List<InspectorLibDependency> = ArrayList()

    override fun visit(node: Node) {
        if (!this::root.isInitialized)
            root = node.artifactCanonicalForm
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
