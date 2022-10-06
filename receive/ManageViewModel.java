package org.thoughtcrime.securesms.trustedIntroductions.receive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.util.Pair;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class ManageViewModel extends ViewModel {

  private final String TAG = Log.tag(ManageViewModel.class);

  private final ManageManager             manager;
  private final MutableLiveData<String>   filter;
  private final MutableLiveData<List<Pair<TI_Data, IntroducerInformation>>> introductions;
  private final ManageActivity.IntroductionScreenType type;
  @NonNull String forgottenPlaceholder;
  private final String introducerName;
  private boolean      introductionsLoaded;

  ManageViewModel(ManageManager manager, ManageActivity.IntroductionScreenType t, @Nullable String introducerName, @NonNull String forgottenPlaceholder){
    this.manager = manager;
    filter = new MutableLiveData<>("");
    introductions = new MutableLiveData<>();
    type                = t;
    this.introducerName = introducerName;
    introductionsLoaded = false;
    this.forgottenPlaceholder = forgottenPlaceholder;
  }

  public void loadIntroductions(){
    manager.getIntroductions(introductions::postValue);
    introductionsLoaded = true;
  }

  public boolean introductionsLoaded(){
    return introductionsLoaded;
  }

  public @Nullable String getIntroducerName(){
    return introducerName;
  }

  public @NonNull ManageActivity.IntroductionScreenType getScreenType(){
    return type;
  }

  void deleteIntroduction(@NonNull Long introductionId){
    List <Pair<TI_Data, IntroducerInformation>>     oldIntros = introductions.getValue();
    ArrayList<Pair<TI_Data, IntroducerInformation>> newIntros = new ArrayList<>();
    for (Pair<TI_Data, IntroducerInformation> p : oldIntros){
      TI_Data i = p.first;
      if (!i.getIntroducerId().equals(RecipientId.from(introductionId))){
        newIntros.add(p);
      }
    }
    introductions.postValue(newIntros);
    SignalExecutors.BOUNDED.execute(() -> {
      boolean res = SignalDatabase.trustedIntroductions().deleteIntroduction(introductionId);
      if(!res){
        Log.e(TAG, String.format("Deleting Introduction with id %d failed. Was it already deleted?", introductionId));
      }
    });
  }

  void forgetIntroducer(@NonNull Long introductionId){
    List<Pair<TI_Data, IntroducerInformation>> all = introductions.getValue();
    TI_Data curr = all.get(0).first;
    int i = 1;
    while(curr.getId() != introductionId && i < all.size()){
      curr = all.get(i++).first;
    }
    i--;
    if(curr.getId() != introductionId){
      throw new AssertionError(TAG +": the introduction id was not present in the viewModels List");
    }
    curr = new TI_Data(curr.getId(), curr.getState(), RecipientId.UNKNOWN, curr.getIntroduceeId(), curr.getIntroduceeServiceId(), curr.getIntroduceeName(), curr.getIntroduceeNumber(), curr.getIntroduceeIdentityKey(), curr.getPredictedSecurityNumber(), curr.getTimestamp());
    all.remove(i);
    if(type.equals(ManageActivity.IntroductionScreenType.ALL)){
      all.add(new Pair<>(curr, new IntroducerInformation(forgottenPlaceholder, forgottenPlaceholder)));
    }
    introductions.postValue(all);
    final TI_Data finalCurr = curr;
    SignalExecutors.BOUNDED.execute(() -> {
      SignalDatabase.trustedIntroductions().clearIntroducer(finalCurr);
    });
  }

  public void setQueryFilter(String filter) {
    this.filter.setValue(filter);
  }

  public LiveData<String> getFilter(){
    return this.filter;
  }

  public LiveData<List<Pair<TI_Data, IntroducerInformation>>> getIntroductions() {
    return introductions;
  }

  //TODO:
  // accept, reject etc...

  static class IntroducerInformation {
    String name;
    String number;

    public IntroducerInformation(String name, String number){
      this.name = name;
      this.number = number;
    }
  }

  static class Factory implements ViewModelProvider.Factory {

    private final ManageManager manager;
    private final ManageActivity.IntroductionScreenType t;
    private final String introducer;
    private final String forgottenPlaceholder;

    Factory(RecipientId id, ManageActivity.IntroductionScreenType t, @Nullable String introducerName, @NonNull String forgottenPlaceholder) {
      this.t = t;
      this.manager = new ManageManager(id, SignalDatabase.trustedIntroductions());
      introducer = introducerName;
      this.forgottenPlaceholder = forgottenPlaceholder;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return Objects.requireNonNull(modelClass.cast(new ManageViewModel(manager, t, introducer, forgottenPlaceholder)));
    }
  }

}
