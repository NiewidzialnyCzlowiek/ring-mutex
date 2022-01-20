# ring-mutex
Algorithm for mutual exclusion in a bidirectional ring network topology with unreliable communication channels. 

The program simulates communication channel omission. You can set the omission rate using  ```ackOmissionRate``` config parameter.

# Build and run
### Build
Please use openjdk11 to build and run this project

You can build this project using ```gradle build``` command

### Run jar
You can run the project using ```java -jar ./build/libs/ring-mutex-peer-1.0-SNAPSHOT.jar``` after building the project

Configuration of each peer can be easily overwritten by passing variables to jvm e.g. ```-DfollowerAddress=127.0.0.1:50001```

### Run using Kubernetes
To simulate a ring of peers easily you can apply the [kubernetes deployment](./kubernetes/3-peer-ring.yaml) file from this repository
