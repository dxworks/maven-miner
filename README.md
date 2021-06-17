# MaMi (MavenMiner)

MavenMiner, sometimes also called MaMi, is a tool which extracts the basic model of your maven projects. It also creates a graph of relations between the internal modules of your project.
Please visit us on [Github](https://github.com/dxworks/maven-miner).

## Prerequisites
You need to have java version 11 or greater installed on your machine, before trying to run the tool.

## Running the tool
Please get our latest release [here](https://github.com/dxworks/maven-miner/releases). Download the `maven-miner.zip` archive and unzip it in a folder. 

Open a terminal session into this folder and run `java -jar maven-miner.jar <path_to_folder>`, where `<path_to_folder>` represents the fully qualified path of the folder containing all the source code you want MavenMiner to analyze. If you have multiple repositories you want to analyze together, please group them under a single _root folder_, and pass this as an argument to maven-miner. 

This will generate in the `results` folder 4 files:
* `maven-graph.json` ---> a graph representation of your maven modules
* `maven-model.json` ---> a simplified model of your maven modules
* `maven-relations.csv` ---> a csv with the relations between the modules.
* `il-deps.json` ---> an input file for Inspector Lib.

### Transforming dependency tree files to il-deps.json format
If you want to convert dependency-tree files to il-deps.json files, there are 2 options:
* `java -jar mami.jar convert <path_to_deptree_file> <path_to_maven_model>`
* `java -jar mami.jar convert <path_to_deptree_folder> <path_to_maven_model>` -> all files from that folder will be treated as dependency tree files and mami will try to transform them
* `java -jar mami.jar convert <path_to_deptree_folder> <file_pattern_glob> <path_to_maven_model>` -> all files matching the glob from that folder will be treated as dependency tree files and mami will try to transform them
example: `java -jar mami.jar convert /path/to/folder *.txt /path/to/maven/model/json/file`
  
To generate a maven dependency tree file please run the following command:
`mvn dependency:tree -DoutputFile="path/to/deptree/file.txt" -DappendOutput`
The appendOutput parameter is necessary if you have a multi-module project. For more information please see the [official maven dependency plugin documentation](https://maven.apache.org/plugins/maven-dependency-plugin/tree-mojo.html).
