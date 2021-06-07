package org.dxworks.mavenminer.deptree

import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fr.dutra.tools.maven.deptree.core.InputType
import fr.dutra.tools.maven.deptree.core.Node
import fr.dutra.tools.maven.deptree.core.Visitor
import org.dxworks.ignorerLibrary.IgnorerBuilder
import org.dxworks.mavenminer.dto.InspectorLibDependency
import org.dxworks.mavenminer.usage
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.system.exitProcess

val jsonWriter: ObjectWriter = jacksonObjectMapper().writerWithDefaultPrettyPrinter()

fun transform(args: Array<String>) {
    val convertArg = args[0];
    if (convertArg != "convert" || args.size < 2) {
        println(usage)
        exitProcess(1)
    }

    val fileOrFolderArg = args[1]
    val glob = if (args.size == 2) args[1] else "*"
    val globMatcher = IgnorerBuilder(listOf(glob)).compile()

    val file = File(fileOrFolderArg)
    when {
        file.isFile -> convertDepTreeFileToJson(file)
        file.isDirectory -> file.walkTopDown()
            .filter { it.isFile }
            .filter { globMatcher.accepts(it.name) }
            .forEach { convertDepTreeFileToJson(it) }
        else -> throw FileNotFoundException(file.absolutePath)
    }
}


fun convertDepTreeFileToJson(file: File) {
    val deps = parseMavenDependencyTree(file)
    val resultFilePath = Path.of("results", "${file.nameWithoutExtension}-il-deps.json")
    jsonWriter.writeValue(resultFilePath.toFile(), deps)
}

fun parseMavenDependencyTree(file: File): List<InspectorLibDependency> {
    val tree = InputType.TEXT.newParser().parse(file.bufferedReader())
    val visitor = MavenDependencyTreeVisitor()
    visitor.visit(tree)
    return visitor.dependencies
}

class MavenDependencyTreeVisitor : Visitor {
    private lateinit var root: String
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



