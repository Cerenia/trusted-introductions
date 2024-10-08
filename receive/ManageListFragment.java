package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.RecyclerView;
import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.splitIntroductionDate;
import static org.thoughtcrime.securesms.trustedIntroductions.receive.ManageActivity.ActiveTab.NEW;

import com.google.android.material.button.MaterialButton;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import androidx.tracing.Trace;

public class ManageListFragment extends Fragment implements DeleteIntroductionDialog.DeleteIntroduction, ForgetIntroducerDialog.ForgetIntroducer{

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageListFragment.class));

  // TODO: Needed?
  private ProgressWheel showIntroductionsProgress;
  private ManageViewModel viewModel;
  private ManageAdapter adapter;
  private TextView no_introductions;
  private View                           all_header;
  private ManageActivity.ActiveTab tab = NEW;
  private MaterialButton showConflicting;
  // Don't refresh list 5 times on setup.
  private boolean sCisFirstInit = true;

  private MaterialButton showAccepted;
  private boolean sAisFirstInit = true;

  private MaterialButton showRejected;
  private boolean sRisFirstInit = true;

  private MaterialButton showStale;
  private boolean sSisFirstInit = true;

  // Because final onCreate in AppCompat dissalows me from using a Fragment Factory, I need to use a Bundle for Arguments.
  static String TYPE_KEY = "type_key";

  static String FORGOTTEN_INTRODUCER;

  public ManageListFragment(){
    super();
  }

  public ManageListFragment(@NonNull ViewModelStoreOwner owner, @NonNull ManageActivity.ActiveTab type){
    this.tab = type;
  }

  @Override
  public void onCreate(Bundle b){
    super.onCreate(b);
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.ti_manage_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState){
    super.onViewCreated(view, savedInstanceState);
    ManageViewModel.Factory factory = new ManageViewModel.Factory(FORGOTTEN_INTRODUCER);
    viewModel = new ViewModelProvider(getActivity(), factory).get(ManageViewModel.class);
    if(!viewModel.introductionsLoaded()){
      viewModel.loadIntroductions();
    }
    adapter = new ManageAdapter(requireContext(), new IntroductionClickListener(this, this));
    RecyclerView introductionList = view.findViewById(R.id.recycler_view);
    introductionList.setClipToPadding(true);
    introductionList.setAdapter(adapter);
    no_introductions = view.findViewById(R.id.no_introductions_found);
    all_header = view.findViewById(R.id.manage_fragment_header);
    // Filter state
    showConflicting = view.findViewById(R.id.conflictingFilter);
    showStale = view.findViewById(R.id.staleFilter);
    showAccepted = view.findViewById(R.id.acceptedFilter);
    showRejected = view.findViewById(R.id.rejectedFilter);
    if(savedInstanceState != null && savedInstanceState.getString(TYPE_KEY)!=null){
      tab = ManageActivity.ActiveTab.fromString(savedInstanceState.getString(TYPE_KEY));
    }
    showConflicting.setVisibility(View.VISIBLE);
    showStale.setVisibility(View.VISIBLE);
    if(viewModel != null){
      // viewModel has default values.
      showConflicting.setChecked(viewModel.showConflicting().getValue());
      showStale.setChecked(viewModel.showStale().getValue());
    } else {
      showConflicting.setChecked(true);
      showStale.setChecked(true);
    }
    showConflicting.addOnCheckedChangeListener((button, isChecked) ->  {
      viewModel.setShowConflicting(isChecked);
    });
    showStale.addOnCheckedChangeListener((button, isChecked) ->{
      viewModel.setShowStale(isChecked);
    });
    switch(tab){
      case NEW:
        // Accepted, Rejected and stale don't show up
        showAccepted.setVisibility(View.GONE);
        showRejected.setVisibility(View.GONE);
        showStale.setVisibility(View.GONE);
        break;
      case LIBRARY:
        showAccepted.setVisibility(View.VISIBLE);
        showRejected.setVisibility(View.VISIBLE);
        if(viewModel != null){
          showAccepted.setChecked(viewModel.showTrusted().getValue());
          showRejected.setChecked(viewModel.showDistrusted().getValue());
        } else {
          showAccepted.setChecked(true);
          showRejected.setChecked(true);
        }
        showAccepted.addOnCheckedChangeListener((button, isChecked) ->{
          viewModel.setShowTrusted(isChecked);
        });
        showRejected.addOnCheckedChangeListener((button, isChecked) ->{
          viewModel.setShowDistrusted(isChecked);
        });
    }
    // Filter state Obvservers
    viewModel.showConflicting().observe(getViewLifecycleOwner(), state ->{
      sCisFirstInit = onFilterStateChanged(showConflicting, state, sCisFirstInit);
    });
    viewModel.showStale().observe(getViewLifecycleOwner(), state ->{
      sSisFirstInit = onFilterStateChanged(showStale, state, sSisFirstInit);
    });
    viewModel.showTrusted().observe(getViewLifecycleOwner(), state ->{
      sAisFirstInit = onFilterStateChanged(showAccepted, state, sAisFirstInit);
    });
    viewModel.showDistrusted().observe(getViewLifecycleOwner(), state->{
      sRisFirstInit = onFilterStateChanged(showRejected, state, sRisFirstInit);
    });
    // Introduction Observer
    this.viewModel.getIntroductions().observe(getViewLifecycleOwner(), introductions -> {
      // Screen layout
      if(introductions.size() > 0){
        no_introductions.setVisibility(View.GONE);
        all_header.setVisibility(View.VISIBLE);
      } else {
        no_introductions.setVisibility(View.VISIBLE);
        all_header.setVisibility(View.GONE);
        no_introductions.setText(R.string.ManageIntroductionsFragment__No_Introductions_all);
      }
      refreshList();
    });
  }

  /**
   *
   * @param b which button to set.
   * @param newCheckState the new newCheckState.
   * @param isFirstInit global indicating if the button was ever set before.
   * @return false, the value to be assigned to the global xXisFirstInit of the button if the method succeeds.
   */
  private boolean onFilterStateChanged(MaterialButton b, Boolean newCheckState, Boolean isFirstInit){
    if(b.isChecked() != newCheckState){
      b.setChecked(newCheckState);
    }
    if(!isFirstInit){
      refreshList();
    }
    return false;
  }

  @Override public void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putString(TYPE_KEY, tab.toString());
    super.onSaveInstanceState(outState);
  }

  /**
   * Decides if this datum must be displayed in the fragment. Depends on tabtype and active filters.
   * @param p the datum to be evaluated.
   * @return true if displayed, false if not.
   */
  private boolean isDisplayed(Pair<TI_Data, ManageViewModel.IntroducerInformation> p){
    TI_Database.State s = p.first.getState();
    switch(tab){
      case NEW:
        // Only display pending and conflicting
        if (!(s.isPending() && !s.isStale())){
          return false;
        }
        if (userFiltered(s)){
          return false;
        }
        break;
      case LIBRARY:
        // Display everything but pending
        if((s.isPending())){
          return false;
        }
        if(userFiltered(s)){
          return false;
        }
        break;
      default:
        // fail open
        return true;
    }
    return true;
  }

  /**
   * Checks the state of the introduction against the user filters.
   * @return true if filtered, false otherwise
   */
  private boolean userFiltered(TI_Database.State s){
    if (Boolean.FALSE.equals(viewModel.showConflicting().getValue())) {
      if (s.isConflicting()){
        return true;
      }
    }
    if (Boolean.FALSE.equals(viewModel.showStale().getValue())) {
      if (s.isStale()){
        return true;
      }
    }
    if (Boolean.FALSE.equals(viewModel.showTrusted().getValue())) {
      if (s.isTrusted()) {
        return true;
      }
    }
    if (Boolean.FALSE.equals(viewModel.showDistrusted().getValue())) {
      if (s.isDistrusted()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Filters complete introduction list by Tab type, search filter, button selectors and finally sorts.
   * @param introductions All introductions returned by ViewModel.
   * @param filter The user provided filter.
   * @return the filtered list appropriate for the fragment depending on tab, button selectors and filter.
   */
  private List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> getFiltered(List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> introductions, @Nullable String filter){
    List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> filtered = new ArrayList<>();
    for (Pair<TI_Data, ManageViewModel.IntroducerInformation> p: introductions) {
      if(isDisplayed(p)){
        filtered.add(p);
      }
    }
    if(filter != null){
      if(!filter.isEmpty() && filter.compareTo("") != 0){
        Pattern filterPattern = Pattern.compile("\\A" + filter + ".*", Pattern.CASE_INSENSITIVE);
        for (Pair<TI_Data, ManageViewModel.IntroducerInformation> p: introductions){
          TI_Data                     d = p.first;
          TI_Utils.TimestampDateParts timestampParts = splitIntroductionDate(d.getTimestamp());
          // Split up for debugging. May be one big expr.
          boolean matchYear = filterPattern.matcher(timestampParts.year).find();
          boolean matchMonth = filterPattern.matcher(timestampParts.month).find();
          boolean matchDay = filterPattern.matcher(timestampParts.day).find();
          boolean matchHours = filterPattern.matcher(timestampParts.hours).find();
          boolean matchMinutes = filterPattern.matcher(timestampParts.minutes).find();
          boolean matchSeconds = filterPattern.matcher(timestampParts.seconds).find();
          boolean matchIntroduceeName = filterPattern.matcher(d.getIntroduceeName()).find();
          boolean matchIntroduceeNumber = filterPattern.matcher(d.getIntroduceeNumber()).find();
          boolean matchIntroducerName = filterPattern.matcher(p.second.name).find();
          boolean matchIntroducerNumber = filterPattern.matcher(p.second.number).find();
          if (!(matchYear || matchMonth || matchDay || matchHours || !matchMinutes || matchSeconds || matchIntroduceeName || matchIntroduceeNumber
                || matchIntroducerName || matchIntroducerNumber)){
            filtered.remove(p);
          }
        }
      }
    }
    return sortIntroductions(filtered);
  }

  private static Function<Pair<TI_Data, ManageViewModel.IntroducerInformation>, Long> dateExtractor = p -> p.first.getTimestamp();
  private static Function<Pair<TI_Data, ManageViewModel.IntroducerInformation>, String> introduceeNameExtractor = p -> p.first.getIntroduceeName();
  private static Function<Pair<TI_Data, ManageViewModel.IntroducerInformation>, Long> stateExtractor = p -> (long)p.first.getState().toInt();

  /**
   * Sorts introductions depending on the tab type.
   * NEW: by date
   * LIBRARY: by introducee
   * ALL: by introducer
   * The sorted list.
   */
  private List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> sortIntroductions(List<Pair<TI_Data, ManageViewModel.IntroducerInformation>> filtered){
    switch(tab){
      case NEW:
        filtered.sort(Comparator.comparing(dateExtractor));
        return filtered;
      case LIBRARY:
        // First by state, then introducee, then date
        filtered.sort(Comparator.comparing(stateExtractor).thenComparing(introduceeNameExtractor).thenComparing(dateExtractor));
        return filtered;
      default:
        throw new AssertionError(TAG +"Unknown tab type!");
    }
  }

  void refreshList(){
    if(adapter != null){
      List l = viewModel.getIntroductions().getValue();
      if(l == null){
        Log.e(TAG, "Introductions list not yet loaded when calling refreshList!");
        return;
      }
      adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), viewModel.getTextFilter().getValue()));
    }
  }

  public void onFilterChanged(String filter) {
    if(adapter != null){
      viewModel.setTextFilter(filter);
      List l = viewModel.getIntroductions().getValue();
      if(l == null){
        Log.e(TAG, "Introductions list not yet loaded when calling onFilterChanged!");
        return;
      }
      adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), filter));
    }
  }

  /**
   * Callback if user decides to proceeds with deletion from dialogue.
   * @param introductionId the introduction to delete.
   */
  @Override public void deleteIntroduction(@NonNull Long introductionId) {
    viewModel.deleteIntroduction(introductionId);
  }

  /**
   * Callback if user decides to mask introducer
   * @param introductionId the introduction for which to mask the introducer.
   */
  @Override public void forgetIntroducer(@NonNull Long introductionId) {
    viewModel.forgetIntroducer(introductionId);
  }

  private class IntroductionClickListener implements ManageAdapter.InteractionListener  {

    DeleteIntroductionDialog.DeleteIntroduction deleteHandler;
    ForgetIntroducerDialog.ForgetIntroducer     forgetHandler;
    Context c;

    public IntroductionClickListener(DeleteIntroductionDialog.DeleteIntroduction d, ForgetIntroducerDialog.ForgetIntroducer f){
      this.deleteHandler = d;
      this.forgetHandler = f;
      c = requireContext();
    }

    @Override public void accept(@NonNull Long introductionId) {
      viewModel.acceptIntroduction(introductionId);
    }

    @Override public void reject(@NonNull Long introductionId) {
      viewModel.rejectIntroduction(introductionId);
    }

    @Override public void mask(@NonNull ManageAdapter.IntroductionViewHolder item, String introducerServiceId) {
      if(!introducerServiceId.equals(TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID)){
        ForgetIntroducerDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), item.getIntroducerName(requireContext()), item.getDate(), forgetHandler);
      }
    }

    @Override public void delete(@NonNull ManageAdapter.IntroductionViewHolder item, String introducerServiceId) {
      String introducerName = introducerServiceId.equals(TI_Database.UNKNOWN_INTRODUCER_SERVICE_ID) ? getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer) : Recipient.resolved(TI_Utils.getRecipientIdOrUnknown(introducerServiceId)).getDisplayName(requireContext());
      DeleteIntroductionDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), introducerName, item.getDate(), deleteHandler);
    }

  }
}
