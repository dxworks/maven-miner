package org.dxworks.mavenminer

import org.dxworks.mavenminer.deptree.transform

fun main(args: Array<String>) {
//    val modules: List<MavenModule> = extractModules(File("results/maven-model.json"))
//
//    val inspectorLibDependencies = modules
//        .onEach { it.resolveDependencyVersions() }
//        .associate { module ->
//            module.path to
//                    module.dependencies
//                        .map { it.toInspectorLibDep() }
//                        .distinct()
//        }
//
//    jacksonObjectMapper().writerWithDefaultPrettyPrinter()
//        .writeValue(File("results/il-maven-deps-resolved.json"), inspectorLibDependencies)

    transform("")

}