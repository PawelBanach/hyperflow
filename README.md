#HyperFlow: a distributed workflow execution engine

##Description

HyperFlow provides a model of computation, workflow description language and enactment engine for complex, distributed workflows.

Browse the [wiki pages](https://github.com/balis/hyperflow/wiki) to learn more about the HyperFlow workflow model. 

##Getting started

The latest release of HyperFlow is 1.0.0

###Installation
* Install the latest node.js (http://nodejs.org)
* Install dependencies (in `hyperflow` directory): `npm install -d`
* Install the Redis server 2.6.x or higher (http://redis.io) (tested with version 2.6.x)
* Start the redis server
* If the provenance storage is to be used:
  * Install neo4j 2.x server (http://www.neo4j.org/)
  * Start neo4j on localhost with default access configuration
* Set an environment variable `HFLOW_PATH` to point to your hyperflow root directory.
* Add `$HFLOW_PATH/bin` to your `PATH`.

###Running
* Start the redis server
* Run example workflows from the `examples` directory as follows: <br>```hflow run <wf_directory>```
