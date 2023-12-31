package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.trustedIntroductions.TI_Utils;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.util.HashMap;
import java.util.List;

import static org.thoughtcrime.securesms.trustedIntroductions.receive.ManageListFragment.FORGOTTEN_INTRODUCER;

/**
 * Opens an Activity for Managing Trusted Introductions.
 * Will either open just the Introductions made by a specific contact
 * or all Introductions depending on how you navigated to that screen.
 */
public class ManageActivity extends PassphraseRequiredActivity {

  private static final String TAG = String.format(TI_Utils.TI_LOG_TAG, Log.tag(ManageActivity.class));

  private static final String ACTIVE_TAB = "initial_active";
  private TabLayout tabLayout;

  private final HashMap<Integer, String> tabTitles = new HashMap<Integer, String>();

  public static enum ActiveTab {
    NEW,
    LIBRARY;

    public static ActiveTab fromString(String state) {
      if(state.equals(LIBRARY.toString())) return LIBRARY;
      if(state.equals(NEW.toString())) return NEW;
      else{
        throw new AssertionError("No such screen state!");
      }
    }

    public static ActiveTab fromInt(int position){
      switch(position) {
        case 0:
          return NEW;
        case 1:
          return LIBRARY;
        default:
          throw new AssertionError("Invalid Tab position!");
      }
    }

    public static int toInt(ActiveTab tab){
      switch(tab){
        case NEW:
          return 0;
        case LIBRARY:
          return 1;
        default:
          throw new AssertionError("Unknown Tab!");
      }
    }

  }

  private Toolbar            toolbar;
  private ContactFilterView contactFilterView;
  private ViewPager2        pager;
  private ManageViewModel viewModel;

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  /**
   * @param initialActive Which tab to focus initially.
   */
  public static @NonNull Intent createIntent(@NonNull Context context, @NonNull ActiveTab initialActive){
    Intent intent = new Intent(context, ManageActivity.class);
    intent.putExtra(ACTIVE_TAB, ActiveTab.toInt(initialActive));
    return intent;
  }


  // TODO: You are probably overriding the wrong function... onCreate(Bundle savedInstanceState) is final..
  @Override protected void onCreate(Bundle savedInstanceState, boolean ready){
    super.onCreate(savedInstanceState, ready);
    FORGOTTEN_INTRODUCER = getString(R.string.ManageIntroductionsListItem__Forgotten_Introducer);
    // Initialize navigation titles
    tabTitles.put(0, getString(R.string.ManageIntroductionsActivity__Navigation_Tab_new));
    tabTitles.put(1, getString(R.string.ManageIntroductionsActivity__Navigation_Tab_library));
    tabTitles.put(2, getString(R.string.ManageIntroductionsActivity__Navigation_Tab_all));
    ManageViewModel.Factory factory = new ManageViewModel.Factory(FORGOTTEN_INTRODUCER);
    viewModel = new ViewModelProvider(this, factory).get(ManageViewModel.class);
    viewModel.loadIntroductions();
    // TODO: also add icons?
    dynamicTheme.onCreate(this);
    setContentView(R.layout.ti_manage_activity);

    // Bind views
    toolbar = findViewById(R.id.toolbar);
    contactFilterView = findViewById(R.id.introduction_filter_edit_text);

    initializeToolbar();

    ManagePagerAdapter adapter = new ManagePagerAdapter(this);
    adapter.initializeViewModelOwner(this);
    pager = findViewById(R.id.pager);
    pager.setAdapter(adapter);
    contactFilterView.setHint(R.string.ManageIntroductionsActivity__Filter_hint);
    tabLayout = this.findViewById(R.id.tab_navigation);
    setActiveTab(savedInstanceState);
    new TabLayoutMediator(tabLayout, pager,
                          (tab, position) -> tab.setText(tabTitles.get(position))
    ).attach();
    getWindow().setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
  }


  @Override public void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putInt(ACTIVE_TAB, tabLayout.getSelectedTabPosition());
    super.onSaveInstanceState(outState);
  }

  /**
   * Load active tab from bundle OR intent
   * @param savedInstanceState bundle that MAY hold instance state.
   */
  private void setActiveTab(@Nullable Bundle savedInstanceState){
    if(savedInstanceState == null){
      tabLayout.selectTab(tabLayout.getTabAt(getIntent().getIntExtra(ACTIVE_TAB, 0)));
    } else{
      tabLayout.selectTab(tabLayout.getTabAt(savedInstanceState.getInt(ACTIVE_TAB)));
    }
  }

  private void initializeToolbar() {
    setSupportActionBar(toolbar);
    toolbar.setTitle(R.string.ManageIntroductionsActivity__Toolbar_Title);
    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    getSupportActionBar().setIcon(null);
    getSupportActionBar().setLogo(null);
    toolbar.setNavigationIcon(R.drawable.ic_arrow_left_24);
    toolbar.setNavigationOnClickListener(v -> {
      setResult(RESULT_CANCELED);
      finish();
    });
  }

  private class ManagePagerAdapter extends FragmentStateAdapter implements ContactFilterView.OnFilterChangedListener {

    private final int PAGES_NUM = 2;
    private ViewModelStoreOwner           owner;

    public ManagePagerAdapter(FragmentActivity activity){
      super(activity);
      contactFilterView.setOnFilterChangedListener(this);
    }

    public ManagePagerAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
      super(fragmentManager, lifecycle);
      // Observe
      contactFilterView.setOnFilterChangedListener(this);
    }

    void initializeViewModelOwner(ViewModelStoreOwner owner){
      this.owner = owner;
    }

    @NonNull @Override public Fragment createFragment(int position) {
      return new ManageListFragment(owner, ActiveTab.fromInt(position));
    }

    @Override public int getItemCount() {
      return PAGES_NUM;
    }

    @Override public void onFilterChanged(String filter) {
      List<Fragment> allFragments = getSupportFragmentManager().getFragments();
      for (Fragment f: allFragments) {
        if(f instanceof ManageListFragment){
          ((ManageListFragment)f).onFilterChanged(filter);
        }
      }
    }
  }
}

