package ch.basler.kube.secretwatcher;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.quarkus.runtime.Quarkus;
import io.quarkus.scheduler.Scheduled;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

@Slf4j
@ApplicationScoped
public class SecretResourceWatcher implements Watcher<Secret> {

    public final static String KAFKA_SECRET_WATCHER_API_VERSION = "kube-secret-watcher/v1alpha1";
    private final Map<String, RetrySecret> retrySecrets = new ConcurrentHashMap<>();

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    @Counted(name = "receivedEvents", description = "How many events have been received.")
    @Timed(name = "checksTimer", description = "A measure of how long it takes to handle the event.", unit = MetricUnits.MILLISECONDS)
    public void eventReceived(Action action, Secret secret) {
        log.debug(format("action: %s for secret: %s", action.name(), secret.getMetadata().getName()));

        if(hasDistNamespaceLabels(secret) && isKafkaUserSecret(secret)) {
            log.info(format("%s - KafkaUser secret: %s", action.name(), secret.getMetadata().getName()));
            extractDestinationNamespaces(secret).forEach(namespace ->
                distributeSecret(action, namespace,
                    buildDistributionSecretForKafkaUserSecret(secret), secret));
            extractDestinationNamespaces(secret).forEach(this::addKafkaCaCertSecret);
        } else if (this.hasDistNamespaceLabels(secret)) {
            log.info(format("%s - None KafkaUser secret: %s", action.name(), secret.getMetadata().getName()));
            extractDestinationNamespaces(secret).forEach(namespace ->
                distributeSecret(action, namespace, buildDistributionSecret(secret, Collections.emptyMap()), secret));
        } else {
            log.debug(format("%s - Missing label 'dist-namespace-x' for secret: %s", action.name(), secret.getMetadata().getName()));
        }
    }

    @Override
    public void onClose(WatcherException e) {
        log.error(format("Application exception: %s", e));
        Quarkus.asyncExit();
    }

    @Scheduled(every="30s")
    public void retrySecrets() {
        retrySecrets.values().forEach(retrySecret -> {
            log.info(format("Retry action %s with secret %s", retrySecret.getAction().name(), retrySecret.getSecret().getMetadata().getName()));
            this.eventReceived(retrySecret.getAction(), retrySecret.getSecret());
        });
    }

    private void addKafkaCaCertSecret(String namespace) {
        kubernetesClient.secrets().list().getItems().stream()
            .filter(secret -> "kafka-cluster-ca-cert".equals(secret.getMetadata().getName()))
            .findFirst()
            .ifPresent(secret -> {
                Secret newSecret = buildDistributionSecretForKafkaUserSecret(secret);
                try {
                    kubernetesClient.secrets().inNamespace(namespace)
                        .createOrReplace(newSecret);
                    log.info(format("CA - secret: %s added in namespace: %s", newSecret.getMetadata().getName(), namespace));
                } catch (KubernetesClientException e) {
                    log.info(format("secret: %s exception:", newSecret.getMetadata().getName()));
                    log.info(e.toString());
                }
            });
    }

    private boolean isKafkaUserSecret(Secret secret) {
        if(secret.getMetadata() == null || secret.getMetadata().getLabels() == null) {
            return false;
        }

        Map<String, String > labels = secret.getMetadata().getLabels();
        return labels.containsKey("strimzi.io/kind") && labels.get("strimzi.io/kind").equals("KafkaUser");
    }

    private boolean hasDistNamespaceLabels(Secret secret) {
        if(secret.getMetadata().getLabels() == null) {
            return false;
        }

        return secret.getMetadata().getLabels().keySet()
            .stream()
            .anyMatch(key -> key.startsWith("dist-namespace"));
    }

    private List<String> extractDestinationNamespaces(Secret secret) {
        Map<String,String> labels = secret.getMetadata().getLabels();
        return labels.keySet().stream().filter(key -> key.startsWith("dist-namespace")).map(labels::get).collect(Collectors.toList());
    }

    private Secret buildDistributionSecretForKafkaUserSecret(Secret sourceSecret) {
        Map<String, String > sourceLabels = sourceSecret.getMetadata().getLabels();
        Map<String, String> labels = new HashMap<>();
        if(sourceLabels != null && !sourceLabels.isEmpty()) {
            labels.put("strimzi.io/cluster", sourceLabels.get("strimzi.io/cluster"));
            labels.put("strimzi.io/kind", "KafkaUser");
        }

        return buildDistributionSecret(sourceSecret, labels);
    }

    private void distributeSecret(Action action, String namespace, Secret secret, Secret originalSecret) {

        retrySecrets.remove(secret.getMetadata().getName());

        try {
            switch (action.name()) {
                case "ADDED":
                case "MODIFIED":
                    kubernetesClient.secrets().inNamespace(namespace).createOrReplace(secret);
                    break;
                case "DELETED":
                    kubernetesClient.secrets().inNamespace(namespace).withName(secret.getMetadata().getName()).delete();
                    break;
                default:
                    log.info(format("Unknown Action: %s", action.name()));
                    break;
            }
            log.info(format("%s - secret: %s in namespace: %s", action.name(), secret.getMetadata().getName(), namespace));
        } catch (KubernetesClientException e) {
            log.info(format("secret: %s exception:", secret.getMetadata().getName()));
            log.info(e.toString());
            retrySecrets.put(secret.getMetadata().getName(), new RetrySecret(action, originalSecret));
        }
    }

    private Secret buildDistributionSecret(Secret sourceSecret, Map<String, String> labels) {
        if(sourceSecret == null) {
            return null;
        }

        labels.put("app.kubernetes.io/managed-by", "kube-secret-watcher");

        String secretName = sourceSecret.getMetadata().getName() != null ? kubernetesClient.getNamespace() + "-" + sourceSecret.getMetadata().getName() : "unknown-secret-name";
        OwnerReference ownerReference = getKafkaSecretWatcherOwnerReference(sourceSecret, secretName);

        return new SecretBuilder()
                .withNewMetadata()
                .withName(secretName)
                .withLabels(labels)
                .withOwnerReferences(ownerReference)
                .endMetadata()
                .withData(sourceSecret.getData())
                .build();
    }

    /*
        Set OwnerReference to Source Secret and use apiVersion as link to kube-secret-watcher.
        WithController(false) means that the Kubernetes (OpenShift) Garbage Collector ignores the ownerReference and
        manages this secret as an Object without an owner, for example, allows to delete it freely.
     */
    private OwnerReference getKafkaSecretWatcherOwnerReference(Secret sourceSecret, String secretName) {
        return new OwnerReferenceBuilder()
                .withName(secretName)
                .withKind(sourceSecret.getKind())
                .withController(false)
                .withApiVersion(KAFKA_SECRET_WATCHER_API_VERSION)
                .withUid(sourceSecret.getMetadata().getUid())
                .build();
    }
}
