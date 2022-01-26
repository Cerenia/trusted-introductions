package org.thoughtcrime.securesms.trustedIntroductions;

import android.animation.LayoutTransition;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.contacts.ContactChip;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.LetterHeaderDecoration;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.groups.ui.GroupLimitDialog;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.FixedViewsAdapter;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapterStickyHeader;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * In order to keep the tight coupling to a minimum, such that we can continue syncing against the upstream repo as it evolves, we opted to
 * copy some of the code instead of adapting the originals in the repo, which would more readily lead to merge conflicts down the line.
 * This is an adaptation of ContactSelectionListFragment, but it's always a multiselect and the data is loaded from an external cursor
 * instead of using DisplayMode.
 */
public class IntroductionContactsSelectionListFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>{

  private static final String TAG = Log.tag(IntroductionContactsSelectionListFragment.class);

  private static final int CHIP_GROUP_EMPTY_CHILD_COUNT  = 1;
  private static final int CHIP_GROUP_REVEAL_DURATION_MS = 150;

  public static final String RECENTS           = "recents";
  public static final String DISPLAY_CHIPS     = "display_chips";

  private ConstraintLayout                     constraintLayout;
  private TrustedIntroductionContactsViewModel viewModel;
  private ContactSelectionListAdapter          cursorRecyclerViewAdapter;
  private RecyclerView                                recyclerView;
  private String                                                 cursorFilter;
  private   ContactSelectionListFragment.OnContactSelectedListener       onContactSelectedListener;
  private ChipGroup                                                    chipGroup;
  private   HorizontalScrollView                                         chipGroupScrollContainer;
  private ContactSelectionListFragment.OnSelectionLimitReachedListener onSelectionLimitReachedListener;
  private SelectionLimits                                              selectionLimit = SelectionLimits.NO_LIMITS;

  private GlideRequests    glideRequests;
  private           Set<RecipientId>         currentSelection;
  private           RecyclerViewFastScroller fastScroller;
  @Nullable private FixedViewsAdapter        headerAdapter;
  @Nullable private   ContactSelectionListFragment.ListCallback   listCallback;
  @Nullable private ContactSelectionListFragment.ScrollCallback scrollCallback;


  private @NonNull Bundle safeArguments() {
    return getArguments() != null ? getArguments() : new Bundle();
  }

  public @NonNull List<SelectedContact> getSelectedContacts() {
    if (cursorRecyclerViewAdapter == null) {
      return Collections.emptyList();
    }

    return cursorRecyclerViewAdapter.getSelectedContacts();
  }

  public int getSelectedContactsCount() {
    if (cursorRecyclerViewAdapter == null) {
      return 0;
    }

    return cursorRecyclerViewAdapter.getSelectedContactsCount();
  }

  private void initializeCursor() {
    glideRequests = GlideApp.with(this);
    // TODO: I believe this is where I can plug my custom cursor
    cursorRecyclerViewAdapter = new ContactSelectionListAdapter(requireContext(),
                                                                glideRequests,
                                                                null,
                                                                new ListClickListener(),
                                                                true,
                                                                currentSelection);

    RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();

    // TODO: needed?
    /*
    if (listCallback != null) {
      headerAdapter = new FixedViewsAdapter(createNewGroupItem(listCallback));
      headerAdapter.hide();
      concatenateAdapter.addAdapter(headerAdapter);
    }*/

    concatenateAdapter.addAdapter(cursorRecyclerViewAdapter);

    // TODO: needed?
    /*
    if (listCallback != null) {
      footerAdapter = new FixedViewsAdapter(createInviteActionView(listCallback));
      footerAdapter.hide();
      concatenateAdapter.addAdapter(footerAdapter);
    }*/

    recyclerView.addItemDecoration(new LetterHeaderDecoration(requireContext(), this::hideLetterHeaders));
    recyclerView.setAdapter(concatenateAdapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          if (scrollCallback != null) {
            scrollCallback.onBeginScroll();
          }
        }
      }
    });

    if (onContactSelectedListener != null) {
      onContactSelectedListener.onSelectionChanged();
    }
  }

  private boolean hideLetterHeaders() {
    return hasQueryFilter() || shouldDisplayRecents();
  }

  public boolean hasQueryFilter() {
    return !TextUtils.isEmpty(cursorFilter);
  }

  private boolean shouldDisplayRecents() {
    return safeArguments().getBoolean(RECENTS, requireActivity().getIntent().getBooleanExtra(RECENTS, false));
  }

  @NonNull @Override public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
    // TODO: How to implement?
    return null;
  }

  @Override public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
   // TODO: This definitely needs to be done, cursor returned by manager.
  }

  @Override public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    cursorRecyclerViewAdapter.changeCursor(null);
    fastScroller.setVisibility(View.GONE);
  }


  /**
   * Taken and adapted from ContactSelectionListFragment.java
   */
  private class ListClickListener implements ContactSelectionListAdapter.ItemClickListener {
    @Override
    public void onItemClick(ContactSelectionListItem contact) {
      SelectedContact selectedContact = contact.isUsernameType() ? SelectedContact.forUsername(contact.getRecipientId().orNull(), contact.getNumber())
                                                                 : SelectedContact.forPhone(contact.getRecipientId().orNull(), contact.getNumber());


      if (!cursorRecyclerViewAdapter.isSelectedContact(selectedContact)) {
        if (selectionHardLimitReached()) {
          if (onSelectionLimitReachedListener != null) {
            onSelectionLimitReachedListener.onHardLimitReached(selectionLimit.getHardLimit());
          } else {
            GroupLimitDialog.showHardLimitMessage(requireContext());
          }
          return;
        }

        if (contact.isUsernameType()) {
          AlertDialog loadingDialog = SimpleProgressDialog.show(requireContext());

          SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
            return UsernameUtil.fetchAciForUsername(requireContext(), contact.getNumber());
          }, uuid -> {
            loadingDialog.dismiss();
            if (uuid.isPresent()) {
              Recipient recipient = Recipient.externalUsername(requireContext(), uuid.get(), contact.getNumber());
              SelectedContact selected = SelectedContact.forUsername(recipient.getId(), contact.getNumber());

              if (onContactSelectedListener != null) {
                onContactSelectedListener.onBeforeContactSelected(Optional.of(recipient.getId()), null, allowed -> {
                  if (allowed) {
                    markContactSelected(selected);
                    cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
                  }
                });
              } else {
                markContactSelected(selected);
                cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
              }
            } else {
              new MaterialAlertDialogBuilder(requireContext())
                  .setTitle(R.string.ContactSelectionListFragment_username_not_found)
                  .setMessage(getString(R.string.ContactSelectionListFragment_s_is_not_a_signal_user, contact.getNumber()))
                  .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                  .show();
            }
          });
        } else {
          if (onContactSelectedListener != null) {
            onContactSelectedListener.onBeforeContactSelected(contact.getRecipientId(), contact.getNumber(), allowed -> {
              if (allowed) {
                markContactSelected(selectedContact);
                cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
              }
            });
          } else {
            markContactSelected(selectedContact);
            cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
          }
        }
      } else {
        markContactUnselected(selectedContact);
        cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);

        if (onContactSelectedListener != null) {
          onContactSelectedListener.onContactDeselected(contact.getRecipientId(), contact.getNumber());
        }
      }
    }
  }

  private boolean selectionHardLimitReached() {
    return getChipCount() + currentSelection.size() >= selectionLimit.getHardLimit();
  }

  private boolean selectionWarningLimitReachedExactly() {
    return getChipCount() + currentSelection.size() == selectionLimit.getRecommendedLimit();
  }

  private int getChipCount() {
    int count = chipGroup.getChildCount() - CHIP_GROUP_EMPTY_CHILD_COUNT;
    if (count < 0) throw new AssertionError();
    return count;
  }

  private void registerChipRecipientObserver(@NonNull ContactChip chip, @Nullable LiveRecipient recipient) {
    if (recipient != null) {
      recipient.observe(getViewLifecycleOwner(), resolved -> {
        if (chip.isAttachedToWindow()) {
          chip.setAvatar(glideRequests, resolved, null);
          chip.setText(resolved.getShortDisplayName(chip.getContext()));
        }
      });
    }
  }

  private void markContactSelected(@NonNull SelectedContact selectedContact) {
    cursorRecyclerViewAdapter.addSelectedContact(selectedContact);
    addChipForSelectedContact(selectedContact);
    if (onContactSelectedListener != null) {
      onContactSelectedListener.onSelectionChanged();
    }
  }

  private void addChipForSelectedContact(@NonNull SelectedContact selectedContact) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   ()       -> Recipient.resolved(selectedContact.getOrCreateRecipientId(requireContext())),
                   resolved -> addChipForRecipient(resolved, selectedContact));
  }

  private void addChipForRecipient(@NonNull Recipient recipient, @NonNull SelectedContact selectedContact) {
    final ContactChip chip = new ContactChip(requireContext());

    if (getChipCount() == 0) {
      setChipGroupVisibility(ConstraintSet.VISIBLE);
    }

    chip.setText(recipient.getShortDisplayName(requireContext()));
    chip.setContact(selectedContact);
    chip.setCloseIconVisible(true);
    chip.setOnCloseIconClickListener(view -> {
      markContactUnselected(selectedContact);

      if (onContactSelectedListener != null) {
        onContactSelectedListener.onContactDeselected(Optional.of(recipient.getId()), recipient.getE164().orNull());
      }
    });

    chipGroup.getLayoutTransition().addTransitionListener(new LayoutTransition.TransitionListener() {
      @Override
      public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
      }

      @Override
      public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
        if (getView() == null || !requireView().isAttachedToWindow()) {
          Log.w(TAG, "Fragment's view was detached before the animation completed.");
          return;
        }

        if (view == chip && transitionType == LayoutTransition.APPEARING) {
          chipGroup.getLayoutTransition().removeTransitionListener(this);
          registerChipRecipientObserver(chip, recipient.live());
          chipGroup.post(IntroductionContactsSelectionListFragment.this::smoothScrollChipsToEnd);
        }
      }
    });

    chip.setAvatar(glideRequests, recipient, () -> addChip(chip));
  }

  private void addChip(@NonNull ContactChip chip) {
    chipGroup.addView(chip);
    if (selectionWarningLimitReachedExactly()) {
      if (onSelectionLimitReachedListener != null) {
        onSelectionLimitReachedListener.onSuggestedLimitReached(selectionLimit.getRecommendedLimit());
      } else {
        GroupLimitDialog.showRecommendedLimitMessage(requireContext());
      }
    }
  }

  private void setChipGroupVisibility(int visibility) {
    if (!safeArguments().getBoolean(DISPLAY_CHIPS, requireActivity().getIntent().getBooleanExtra(DISPLAY_CHIPS, true))) {
      return;
    }

    TransitionManager.beginDelayedTransition(constraintLayout, new AutoTransition().setDuration(CHIP_GROUP_REVEAL_DURATION_MS));

    ConstraintSet constraintSet = new ConstraintSet();
    constraintSet.clone(constraintLayout);
    constraintSet.setVisibility(R.id.chipGroupScrollContainer, visibility);
    constraintSet.applyTo(constraintLayout);
  }

  private void smoothScrollChipsToEnd() {
    int x = ViewUtil.isLtr(chipGroupScrollContainer) ? chipGroup.getWidth() : 0;
    chipGroupScrollContainer.smoothScrollTo(x, 0);
  }

  private void markContactUnselected(@NonNull SelectedContact selectedContact) {
    cursorRecyclerViewAdapter.removeFromSelectedContacts(selectedContact);
    cursorRecyclerViewAdapter.notifyItemRangeChanged(0, cursorRecyclerViewAdapter.getItemCount(), ContactSelectionListAdapter.PAYLOAD_SELECTION_CHANGE);
    removeChipForContact(selectedContact);

    if (onContactSelectedListener != null) {
      onContactSelectedListener.onSelectionChanged();
    }
  }

  private void removeChipForContact(@NonNull SelectedContact contact) {
    for (int i = chipGroup.getChildCount() - 1; i >= 0; i--) {
      View v = chipGroup.getChildAt(i);
      if (v instanceof ContactChip && contact.matches(((ContactChip) v).getContact())) {
        chipGroup.removeView(v);
      }
    }

    if (getChipCount() == 0) {
      setChipGroupVisibility(ConstraintSet.GONE);
    }
  }
}