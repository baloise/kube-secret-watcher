# kube-secret-watcher
A simple Quarkus based application able to watch secrets and copy them to other namespaces.  
Additionally, there is some Kafka logic to handle coping the CA certificate too.

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/bf6fa237dd934970991ecba2c66db23e)](https://app.codacy.com/app/baloise/kube-secret-watcher?utm_source=github.com&utm_medium=referral&utm_content=baloise/kube-secret-watcher&utm_campaign=Badge_Grade_Dashboard)
[![DepShield Badge](https://depshield.sonatype.org/badges/baloise/kube-secret-watcher/depshield.svg)](https://depshield.github.io)
![Build Status](https://github.com/baloise/kube-secret-watcher/workflows/CI/badge.svg)

## The [docs](docs/index.md)

## Docker
The docker image can be found at [baloise/kube-secert-watcher](https://hub.docker.com/r/baloise/kube-secret-watcher)