package org.thoughtcrime.securesms.trustedIntroductions.jobs;

import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

public abstract class TI_JobCallbackData implements TI_Serialize {
  public abstract TrustedIntroductionsRetreiveIdentityJob.TI_RetrieveIDJobResult getIdentityResult();
  abstract public TI_Data getIntroduction();
}
