package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Data;

public class ManageAdapter extends ListAdapter<Pair<TI_Data, ManageViewModel.IntroducerInformation>, ManageAdapter.IntroductionViewHolder> {

  private final LayoutInflater layoutInflater;
  private final ManageAdapter.ItemClickListener clickListener;
  private final ManageActivity.IntroductionScreenType type;


  ManageAdapter(@NonNull Context context, ManageAdapter.ItemClickListener clickListener, ManageActivity.IntroductionScreenType t){
    super(new DiffUtil.ItemCallback<Pair<TI_Data, ManageViewModel.IntroducerInformation>>() {
      @Override public boolean areItemsTheSame(@NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> oldItem, @NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> newItem) {
        return oldItem.first.getId().compareTo(newItem.first.getId()) == 0;
      }

      @Override public boolean areContentsTheSame(@NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> oldPair, @NonNull Pair<TI_Data, ManageViewModel.IntroducerInformation> newPair) {
        TI_Data oldItem = oldPair.first;
        TI_Data newItem = newPair.first;
        // Ignoring Identity key, since this is already covered by public Key
        return oldItem.getId().equals(newItem.getId()) &&
               (oldItem.getIntroducerId() == null || newItem.getIntroducerId() == null || oldItem.getIntroducerId().equals(newItem.getIntroducerId())) &&
               (oldItem.getIntroduceeId() == null || newItem.getIntroduceeId() == null || oldItem.getIntroduceeId().equals(newItem.getIntroduceeId())) &&
               oldItem.getIntroduceeServiceId().equals(newItem.getIntroduceeServiceId()) &&
               oldItem.getIntroduceeName().equals(newItem.getIntroduceeName()) &&
               oldItem.getIntroduceeNumber().equals(newItem.getIntroduceeNumber()) &&
               oldItem.getIntroduceeIdentityKey().equals(newItem.getIntroduceeIdentityKey()) &&
               oldItem.getPredictedSecurityNumber().equals(newItem.getPredictedSecurityNumber()) &&
               oldItem.getTimestamp() == newItem.getTimestamp();
      }
    });
    this.layoutInflater = LayoutInflater.from(context);
    this.clickListener = clickListener;
    this.type = t;
  }

  // TODO: in case you want to fancify this by adding header list items, add int viewType here
  @NonNull @Override public IntroductionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new IntroductionViewHolder(layoutInflater.inflate(R.layout.ti_manage_list_item, parent, false), clickListener);
  }

  @Override public void onBindViewHolder(@NonNull IntroductionViewHolder holder, int position) {
    Pair<TI_Data, ManageViewModel.IntroducerInformation> current = getItem(position);
    holder.bind(current.first, current.second, type);
  }

  static class IntroductionViewHolder extends RecyclerView.ViewHolder {

    public IntroductionViewHolder(@NonNull View itemView, @NonNull final ManageAdapter.ItemClickListener clickListener) {
      super(itemView);
      itemView.setOnClickListener(v -> {
        clickListener.onItemClick(getView());
      });
      itemView.setOnLongClickListener(v -> {
        clickListener.onItemLongClick(getView());
        return true; // TODO: what does it mean, 'has consumed the long click'? does it need to be a boolean return? Not sure what would go wrong..
        // TODO: Possibly because of decorator items that are not supposed to be responsive?
        // https://developer.android.com/reference/android/view/View.OnLongClickListener
      });
    }

    ManageListItem getView() {
      return (ManageListItem) itemView;
    }

    public void bind(@NonNull TI_Data d, @NonNull ManageViewModel.IntroducerInformation i, @NonNull ManageActivity.IntroductionScreenType t){
      getView().set(d, i, t);
    }

    public void setEnabled(boolean enabled){
      getView().setEnabled(enabled);
    }
  }

  public interface ItemClickListener {
    // Show predicted security nr?
    void onItemClick(ManageListItem item);
    // Delete Introduction?
    void onItemLongClick(ManageListItem item);
  }
}
