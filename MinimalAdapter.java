package org.thoughtcrime.securesms.trustedIntroductions;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

public class MinimalAdapter extends ListAdapter<String, MinimalAdapter.StringViewHolder> {

  private final @NonNull Context context;

  private final LayoutInflater                                layoutInflater;
  private final MinimalAdapter.ItemClickListener clickListener;
  private final GlideRequests                                 glideRequests;

  MinimalAdapter(@NonNull Context context,
                              @NonNull GlideRequests glideRequests,
                              @Nullable MinimalAdapter.ItemClickListener clickListener)
  {
    super(new DiffUtil.ItemCallback<String>() {
      @Override public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
        return oldItem.equals(newItem);
      }

      @Override public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
        return oldItem.equals(newItem);
      }
    });
    this.context = context;
    this.layoutInflater  = LayoutInflater.from(context);
    this.glideRequests   = glideRequests;
    this.clickListener   = clickListener;
  }

  @NonNull public StringViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new StringViewHolder(layoutInflater.inflate(R.layout.contact_selection_list_item_simple, parent, false), clickListener);
  }


  @Override public void onBindViewHolder(@NonNull StringViewHolder holder, int position) {
    String current = getItem(position);
    holder.unbind(glideRequests);
    holder.bind(glideRequests, null,  current);
  }

  static class StringViewHolder extends MappingViewHolder<String> {

    StringViewHolder(@NonNull final View itemView,
                     @Nullable final MinimalAdapter.ItemClickListener clickListener)
    {
      super(itemView);
      itemView.setOnClickListener(v -> {
        if (clickListener != null) clickListener.onItemClick(getView());
      });
    }

    ListItemSimple getView() {
      return (ListItemSimple) itemView;
    }

    public void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, String name){
      getView().set(glideRequests, name);
    }

    public void unbind(@NonNull GlideRequests glideRequests){
      getView().unbind();
    }

    public void setEnabled(boolean enabled){
      getView().setEnabled(enabled);
    }

    @Override public void bind(@NonNull String s) {

    }

    /**
    @Override
    public void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, String about, boolean checkBoxVisible) {
      getView().set(name);
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {
      getView().unbind();
    }

    @Override
    public void setChecked(boolean checked) {
      getView().setChecked();
    }

    @Override
    public void animateChecked(boolean checked) {
      //getView().setChecked(checked, true);
    }

    @Override
    public void setEnabled(boolean enabled) {
      getView().setEnabled(enabled);
    }
    **/
  }

  private static final class RecipientDiffCallback extends DiffUtil.ItemCallback<Recipient> {

    @Override
    public boolean areItemsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
      return oldItem.equals(newItem);
    }

    @Override
    public boolean areContentsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
      return oldItem.equals(newItem);
    }
  }
  public interface ItemClickListener {
    void onItemClick(ListItemSimple item);
  }
}
