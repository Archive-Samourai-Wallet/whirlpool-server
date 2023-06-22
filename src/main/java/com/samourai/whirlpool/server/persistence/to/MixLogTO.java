package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.persistence.to.shared.EntityTO;
import javax.persistence.*;

@Entity(name = "mixLog")
public class MixLogTO extends EntityTO {
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "mix_id")
  private MixTO mix;

  private String txid;

  public MixLogTO() {}

  public void update(Mix mix, MixTO mixTO) {
    this.mix = mixTO;

    if (mix.getTx() != null) {
      this.txid = mix.getTx().getHashAsString();
    }
  }

  public String getTxid() {
    return txid;
  }
}
