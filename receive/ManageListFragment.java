package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;
import org.thoughtcrime.securesms.R;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ManageListFragment extends Fragment implements ContactFilterView.OnFilterChangedListener, DeleteIntroductionDialog.DeleteIntroduction, ForgetIntroducerDialog.ForgetIntroducer {

  private String TAG = Log.tag(ManageListFragment.class);

  // TODO: Will probably need that for all screen
  private ProgressWheel showIntroductionsProgress;
  private View layout;
  private ManageViewModel viewModel;
  private ManageAdapter adapter;
  private RecyclerView introductionList;
  private ManageActivity.IntroductionScreenType type;
  private String introducerName;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
    View view = inflater.inflate(R.layout.ti_manage_fragment, container, false);
    introductionList = view.findViewById(R.id.recycler_view);
    introductionList.setClipToPadding(true);
    layout = view;
    introductionList.setAdapter(adapter);
    introductionList.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          // TODO: Add scrollCallback if needed, determined during Integration testing
        }
      }
    });
    // Iff some state restauration is necessary, add an onPreDrawListener to the recycle view @see ContactsSelectionListFragment
    return view;
  }

  /**
   * Called by activity containing the Fragment.
   * Sets fields used by dialogs and RecyclerView, and initializes navigation button accordingly.
   * @param viewModel The underlying persistent data storage (throughout Activity and Fragment Lifecycle).
   */
  public void setViewModel(@NonNull ManageViewModel viewModel){
    this.introducerName = viewModel.getIntroducerName();
    this.type = viewModel.getScreenType();
    this.viewModel = viewModel;
    initializeAdapter(type);
    this.viewModel.getIntroductions().observe(getViewLifecycleOwner(), users -> {
      List<TI_Data> filtered = getFiltered(users, null);
      adapter.submitList(new ArrayList<>(filtered));
    });
  }

  // Make sure the Fragment has been inflated before calling this!
  private void initializeAdapter(ManageActivity.IntroductionScreenType t){
    if(t == ManageActivity.IntroductionScreenType.RECIPIENT_SPECIFIC){
      adapter = new ManageAdapter(requireContext(), new IntroductionClickListener(this, this));
    } else {
      // TODO: all adapter has different list item layouts + a sticky header.
    }
    // TODO: Race condition? Iff not, at least factor out.
    introductionList.setAdapter(adapter);
    introductionList.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          // TODO: Add scrollCallback if needed, determined during Integration testing
        }
      }
    });
  }

  private List<TI_Data> getFiltered(List<TI_Data> introductions, @Nullable String filter){
    List<TI_Data> filtered = introductions != null ? new ArrayList<>(introductions) : new ArrayList<>();
    if(filter != null){
      if(!filter.isEmpty() && filter.compareTo("") != 0){
        Pattern filterPattern = Pattern.compile(Pattern.quote(filter), Pattern.CASE_INSENSITIVE);
        for (TI_Data d: introductions){
          if (!filterPattern.matcher(INTRODUCTION_DATE_PATTERN.format(d.getTimestamp())).find() &&
              !filterPattern.matcher(d.getIntroduceeName()).find() &&
              !filterPattern.matcher(d.getIntroduceeNumber()).find() &&
              !filterPattern.matcher(d.getState().toString()).find()){
            filtered.remove(d);
          }
        }
      }
    }
    return filtered;
  }

  void refreshList(){
    adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), viewModel.getFilter().getValue()));
  }

  @Override public void onFilterChanged(String filter) {
    viewModel.setQueryFilter(filter);
    adapter.submitList(getFiltered(viewModel.getIntroductions().getValue(), filter));
  }

  @Override public void deleteIntroduction(@NonNull Long introductionId) {
    viewModel.deleteIntroduction(introductionId);
  }

  @Override public void forgetIntroducer(@NonNull Long introductionId) {
    viewModel.forgetIntroducer(introductionId);
  }

  private class IntroductionClickListener implements ManageAdapter.ItemClickListener {

    DeleteIntroductionDialog.DeleteIntroduction deleteHandler;
    ForgetIntroducerDialog.ForgetIntroducer     forgetHandler;
    Context c;

    public IntroductionClickListener(DeleteIntroductionDialog.DeleteIntroduction d, ForgetIntroducerDialog.ForgetIntroducer f){
      this.deleteHandler = d;
      this.forgetHandler = f;
      c = requireContext();
    }

    // PRE: Only called on ALL screen
    private String getIntroducerName(ManageListItem item){
      Preconditions.checkArgument(viewModel.getScreenType().equals(ManageActivity.IntroductionScreenType.ALL));
      String itemIntroducerName;
      if(introducerName == null){
        // All screen
        itemIntroducerName = item.getIntroducerName(c);
        // could still be null after iff this introducer information has been cleared.
        itemIntroducerName = (itemIntroducerName == null) ? "forgotten introducer": itemIntroducerName;
      } else {
        itemIntroducerName = introducerName;
      }
      return itemIntroducerName;
    }

    // TODO
    @Override public void onItemClick(ManageListItem item) {
      ForgetIntroducerDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), getIntroducerName(item), item.getDate(), forgetHandler, type);
    }

    @Override public void onItemLongClick(ManageListItem item) {
      DeleteIntroductionDialog.show(c, item.getIntroductionId(), item.getIntroduceeName(), getIntroducerName(item), item.getDate(), deleteHandler);
    }
  }
}
