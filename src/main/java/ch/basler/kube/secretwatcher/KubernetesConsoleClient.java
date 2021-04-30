package ch.basler.kube.secretwatcher;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
@QuarkusMain
public class KubernetesConsoleClient implements QuarkusApplication {

    @Inject
    SecretResourceWatcher secretResourceWatcher;
    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public int run(String... args) throws Exception {
        log.debug("UserName:" + kubernetesClient.getConfiguration().getUsername());
        kubernetesClient.secrets().watch(this.secretResourceWatcher);
        Quarkus.waitForExit();
        return 0;
    }
}
