package ch.basler.kube.secretwatcher;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Watcher.Action;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RetrySecret {
  private Action action;
  private Secret secret;
}
