# kube-secret-watcher
A simple Quarkus based application able to watch secrets and copy them to other namespaces.  
Additionally, there is some Kafka logic to handle coping the CA certificate too.

[![DepShield Badge](https://depshield.sonatype.org/badges/baloise/kube-secret-watcher/depshield.svg)](https://depshield.github.io)
![Build Status](https://github.com/baloise/kube-secret-watcher/workflows/CI/badge.svg)

## The [docs](docs/index.md)

## Docker
The docker image can be found at [baloise/kube-secert-watcher](https://hub.docker.com/r/baloise/kube-secret-watcher)