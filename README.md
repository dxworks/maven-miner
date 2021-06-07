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
TBA
