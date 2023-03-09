package org.dxworks.mavenminer.deptree

import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fr.dutra.tools.maven.deptree.core.InputType
import fr.dutra.tools.maven.deptree.core.Node
import fr.dutra.tools.maven.deptree.core.Visitor
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.dxworks.mavenminer.MavenModule
import org.dxworks.mavenminer.MavenModuleId
import org.dxworks.mavenminer.dto.DepinderDependency
import org.dxworks.mavenminer.dto.DepinderProject
import java.io.File
import java.io.FileNotFoundException

val jsonWriter: ObjectWriter = jacksonObjectMapper().writerWithDefaultPrettyPrinter()

fun transform(fileOrFolderArg: String) {
//    val convertArg = args[0];
//    if (convertArg != "convert" || args.size < 2 || args.size > 4) {
//        println(usage)
//        exitProcess(1)
//    }

//    val modelFilePath = args.find { it.startsWith("-model=") }?.removePrefix("-model=")
//    val glob = args.find { it.startsWith("-pattern=") }?.removePrefix("-pattern=") ?: "*"
//    val globMatcher = IgnorerBuilder(listOf(glob)).compile()

    val file = File(fileOrFolderArg)
//    val modules: List<MavenModule> = extractModules(modelFilePath?.let { File(it) })

    when {
//        file.isFile -> convertDepTreeFileToJson(file, modules)
        file.isDirectory -> file.walkTopDown()
            .filter { it.isFile }
            .filter { it.name == "pom.xml" }
            .map { it.parentFile }
            .forEach { convertDepTreeFileToJson(it) }
//            .filter { globMatcher.accepts(it.name) }
//            .forEach { convertDepTreeFileToJson(it, modules) }

        else -> throw FileNotFoundException(file.absolutePath)
    }
}

//fun extractModules(modelFile: File?): List<MavenModule> {
//    if (modelFile == null)
//        return emptyList()
//    return try {
//        jacksonObjectMapper().readValue(modelFile)
//    } catch (e: Exception) {
//        println("Warning: could not parse maven model file: ${modelFile.absolutePath}!")
//        return emptyList()
//    }
//}


fun convertDepTreeFileToJson(pomXmlDirectory: File) {

    val deptreeFile = pomXmlDirectory.resolve("deptree.txt")
    val pomFile = pomXmlDirectory.resolve("pom.xml")
    val mavenReader = MavenXpp3Reader()
    val mavenModel = mavenReader.read(pomFile.inputStream())

    val deps = if (deptreeFile.exists()) parseMavenDependencyTree(deptreeFile) else HashMap()

    val project = DepinderProject(
        "${mavenModel.groupId ?: mavenModel.parent.groupId}:${mavenModel.artifactId}",
        mavenModel.version ?: mavenModel.parent.version,
        pomFile.absolutePath,
        deps
    )

    val resultFilePath = pomXmlDirectory.resolve("pom.json")
    jsonWriter.writeValue(resultFilePath, project)
}

fun parseMavenDependencyTree(file: File): Map<String, DepinderDependency> {

    val tree = InputType.TEXT.newParser().parse(file.reader())
    val visitor = MavenDependencyTreeVisitor()
    visitor.visit(tree)

    return visitor.dependencies
}

fun getPathToProject(mavenModuleId: MavenModuleId, modules: List<MavenModule>): String {
    val mavenProjectPath =
        modules.find { it.id.groupID == mavenModuleId.groupID && it.id.artifactID == mavenModuleId.artifactID }
            ?.let { it.path.removeSuffix("pom.xml").removeSuffix("/") }
    if (mavenProjectPath == null)
        println("Warning: could not find $mavenModuleId in the maven model!")

    return mavenProjectPath ?: mavenModuleId.artifactID
}

class MavenDependencyTreeVisitor : Visitor {
    lateinit var rootNode: Node
    val dependencies: MutableMap<String, DepinderDependency> = HashMap() // dependency id as key

    override fun visit(node: Node) {
        if (!this::rootNode.isInitialized) {
            rootNode = node
        } else {
            val depinderDependency = node.toDTO()
            if (dependencies.containsKey(depinderDependency.id))
                dependencies[depinderDependency.id]!!.requestedBy += node.parent.toDTO().id
            else {
                depinderDependency.requestedBy += node.parent.toDTO().id
                dependencies[depinderDependency.id] = depinderDependency
            }
        }
        node.childNodes.forEach { visit(it) }
    }
}

fun Node.toDTO(): DepinderDependency =
    DepinderDependency(
        name = "$groupId:$artifactId",
        version = version,
        classifier = classifier,
        description = description,
        omitted = isOmitted,
        type = scope,
    )
