# Documentation
A Kubernetes Operator handling lifecycle events of Kafka user secrets, deploying them into other namespaces.

# Usage
You can watch and distribute secrets (not just Kafka secrets) by addition a label `dist-namespace-x` to the secret.
```yaml
apiVersion: v1
kind: Secret
metadata:
  labels:
    dist-namespace-1: kube-namespace-1
    dist-namespace-2: kube-namespace-2
    ...
    dist-namespace-x: kube-namespace-x
  name: secret-name
  namespace: origin-namespace
data: ...your secret data...
```

## Prerequisite for distribution
### A role for managing secrets
You need a `Role`. It can be one in the target namespace or a `ClusterRole`. The role has to look like the one below.  
This `Role` is needed to manage secrets in the target namespace, later.
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: kube-secret-watcher
rules:
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get","list","create","update","delete","watch"]
```

### Permission to manage secrets in the target namespace (RoleBinding)
In addition to the role you need to grant the `ServiceAccount` of `kube-secret-watcher` the above `Role` in the target namespace.
```yaml
apiVersion: "rbac.authorization.k8s.io/v1"
kind: "RoleBinding"
metadata:
  annotations:
    app.quarkus.io/vcs-url: "https://github.com/baloise/kube-secret-watcher.git"
  labels:
    app.kubernetes.io/name: "kube-secret-watcher"
  name: "kube-secret-watcher"
roleRef:
  kind: "ClusterRole"
  apiGroup: "rbac.authorization.k8s.io"
  name: "kube-secret-watcher"
subjects:
  - kind: "ServiceAccount"
    name: "kube-secret-watcher"
    namespace: "<kube-secret-watcher namespace>" # This is the origin namespace where the kube-secret-watcher is deployed and watches secrets
```

### A serviceAccount
The `ServiceAccount` is a technical account which will be used by the `kube-secret-watcher` to use the Kubernetes API.
```yaml
apiVersion: "v1"
kind: "ServiceAccount"
metadata:
  annotations:
    app.quarkus.io/vcs-url: "https://github.com/baloise/kube-secret-watcher.git"
  labels:
    app.kubernetes.io/name: "kube-secret-watcher"
  name: "kube-secret-watcher"
```

# Development
## Running the application in dev mode
You can run your application in dev mode that enables live coding.
```bash
./mvnw quarkus:dev
```
## Test locally with oauth token
Use the `application.properties` see [configuring](https://quarkus.io/guides/kubernetes-client) the client.

# Build
## Packaging and running the application
The application can be packaged using `./mvnw package`.  
It produces `kube-secret-watcher-1.0-runner.jar` in directory `/target`.  
Be aware that it’s not an _fat-jar_ as the dependencies are in directory `target/lib`.

Run it using `java -jar target/kube-secret-watcher-1.0-runner.jar`.

## Build Docker image
You can build a Docker image by using these commands
```
./mvnw clean package
docker build -f src/main/docker/Dockerfile -t baloise/kube-secret-watcher . 
```

### Use Docker image locally
You can use the following command to start a Docker image of `kube-secret-watcher`
```bash
docker run --rm -ti -v c:/<local-path-to>/.kube/config:/.kube/config -p8080:8080 baloise/kube-secret-watcher
```
For more information visit https://quarkus.io/guides/deploying-to-kubernetes for more information

## Building native executable
You can create a native executable using: `./mvnw package -Pnative`.  
Or, if you don't have GraalVM installed, you can run the native executable build in a container using: `./mvnw package -Pnative -Dquarkus.native.container-build=true`.  
You can then execute your native executable with: `./target/kube-secret-watcher-1.0-runner`.  
If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image.

# Deploy
## Release
TBD

## Kubernetes yaml files
Quarkus will produce Kubernetes ready files when packaging with `./mvnw package` in `target/kubernetes/kubernetes.yml`.  
You can use these files as a base and should adapt them to the above guid.

See also https://quarkus.io/guides/deploying-to-kubernetes for more information.

# Uses
* https://fabric8.io/guide/javaLibraries.html
* https://github.com/fabric8io/kubernetes-client
* https://github.com/fabric8io/kubernetes-client/blob/master/doc/CHEATSHEET.md
* http://www.bouncycastle.org/java.html