package com.samourai.whirlpool.server.controllers.web.beans;

import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import java.util.Comparator;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class ClientInput implements Comparable<ClientInput> {
  public RegisteredInput registeredInput;
  public Mix mix;
  public boolean confirmed;

  public ClientInput(RegisteredInput registeredInput, Mix mix, boolean confirmed) {
    this.registeredInput = registeredInput;
    this.mix = mix;
    this.confirmed = confirmed;
  }

  public static Comparator computeComparator(Pageable pageable) {
    Comparator comparator = Comparator.comparing(ClientInput::getSince);
    if (pageable.getSort().isSorted()) {
      Comparator nullSafe = Comparator.nullsLast(Comparator.naturalOrder());
      Sort.Order order = pageable.getSort().get().iterator().next();
      switch (ClientInputSort.valueOf(order.getProperty())) {
        case poolId:
          comparator = Comparator.comparing(ClientInput::getPoolId, nullSafe);
          break;
        case mixId:
          comparator = Comparator.comparing(ClientInput::getMixId, nullSafe);
          break;
        case status:
          comparator = Comparator.comparing(ClientInput::getStatus, nullSafe);
          break;
        case typeStr:
          comparator = Comparator.comparing(ClientInput::getTypeStr, nullSafe);
          break;
        case utxoStr:
          comparator = Comparator.comparing(ClientInput::getUtxoStr, nullSafe);
          break;
        case utxoValue:
          comparator = Comparator.comparing(ClientInput::getUtxoValue, nullSafe);
          break;
        case liquidity:
          comparator = Comparator.comparing(ClientInput::isLiquidity, nullSafe);
          break;
        case lastSeen:
          comparator = Comparator.comparing(ClientInput::getLastSeen, nullSafe);
          break;
        case userName:
          comparator = Comparator.comparing(ClientInput::getUserName, nullSafe);
          break;
        case lastUserHash:
          comparator = Comparator.comparing(ClientInput::getLastUserHash, nullSafe);
          break;
      }
      if (order.getDirection().isDescending()) {
        comparator = comparator.reversed();
      }
    }
    return comparator;
  }

  public long getSince() {
    return registeredInput.getSince();
  }

  public String getPoolId() {
    return registeredInput.getPoolId();
  }

  public String getMixId() {
    return mix != null ? mix.getMixId() : null;
  }

  public String getStatus() {
    return mix != null ? (confirmed ? mix.getMixStatus().name() : "CONFIRMING") : "QUEUED";
  }

  public String getTypeStr() {
    return registeredInput.getTypeStr();
  }

  public String getUtxoStr() {
    return registeredInput.getOutPoint().toKey();
  }

  public long getUtxoValue() {
    return registeredInput.getOutPoint().getValue();
  }

  public boolean isLiquidity() {
    return registeredInput.isLiquidity();
  }

  public Long getLastSeen() {
    return registeredInput.isSoroban()
        ? registeredInput.getSorobanInput().getSorobanLastSeen()
        : null;
  }

  public String getUserName() {
    return registeredInput.getUsername();
  }

  public String getLastUserHash() {
    return registeredInput.getLastUserHash();
  }

  @Override
  public int compareTo(ClientInput o) {
    return o.getUserName().compareTo(o.getUserName());
  }
}
