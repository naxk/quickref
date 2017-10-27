package io.github.easyintent.quickref.fragment;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback;
import com.bignerdranch.android.multiselector.MultiSelector;

import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.FragmentArg;
import org.androidannotations.annotations.IgnoreWhen;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import io.github.easyintent.quickref.QuickRefActivity;
import io.github.easyintent.quickref.R;
import io.github.easyintent.quickref.config.FavoriteConfig;
import io.github.easyintent.quickref.data.ReferenceItem;
import io.github.easyintent.quickref.repository.ReferenceRepository;
import io.github.easyintent.quickref.repository.RepositoryException;
import io.github.easyintent.quickref.repository.RepositoryFactory;

import static io.github.easyintent.quickref.fragment.Dialog.info;


@EFragment(R.layout.fragment_reference_list)
public class ReferenceListFragment extends Fragment
        implements
            OnItemTapListener<ReferenceItem>,
            ClosableFragment {

    private static final Logger logger = LoggerFactory.getLogger(ReferenceListFragment.class);

    @FragmentArg
    protected String parentId;

    @FragmentArg
    protected String query;

    @FragmentArg
    protected boolean searchMode;

    @ViewById
    protected RecyclerView recyclerView;

    @ViewById
    protected View emptyView;

    @ViewById
    protected ViewSwitcher switcher;

    @ViewById
    protected CardView cardView;

    private RepositoryFactory factory;
    private List<ReferenceItem> list;

    private MultiSelector selector;
    private SelectorCallback selectorCallback;

    /** Create list of reference fragment.
     *
     * @param parentId
     *      Parent item id, or null for top level list.
     * @return
     */
    @NonNull
    public static ReferenceListFragment newListChildrenInstance(@Nullable String parentId) {
        ReferenceListFragment fragment = new ReferenceListFragmentEx();
        Bundle args = new Bundle();
        args.putString("parentId", parentId);
        fragment.setArguments(args);
        return fragment;
    }

    /** Create search fragment.
     *
     * @param query
     *      The search query.
     * @return
     */
    @NonNull
    public static ReferenceListFragment newSearchInstance(@NonNull String query) {
        ReferenceListFragment fragment = new ReferenceListFragmentEx();
        Bundle args = new Bundle();
        args.putString("query", query);
        args.putBoolean("searchMode", true);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        factory = RepositoryFactory.newInstance(getActivity());
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        selector = new MultiSelector();
        selectorCallback = new SelectorCallback(getActivity(), selector);

        if (list == null) {
            load();
        } else {
            show(list);
        }
    }

    @Override
    public boolean allowBack() {
        if (selector.isSelectable()) {
            selector.clearSelections();
            selector.setSelectable(false);
            return false;
        }
        return true;
    }

    private void load() {
        setListShown(false);
        if (searchMode) {
            search(factory, query);
        } else {
            loadCategory(factory, parentId);
        }
    }

    @Background
    protected void search(RepositoryFactory factory, String query) {
        ReferenceRepository repo = factory.createCategoryRepository();
        try {
            list = repo.search(query);
            onLoadDone(true, list, null);
        } catch (RepositoryException e) {
            logger.debug("Failed to search reference", e);
            onLoadDone(false, null, e.getMessage());
        }
    }

    @Background
    protected void loadCategory(RepositoryFactory factory, String parentId) {
        ReferenceRepository repo = factory.createCategoryRepository();
        try {
            list = repo.list(parentId);
            onLoadDone(true, list, null);
        } catch (RepositoryException e) {
            logger.debug("Failed to get reference list", e);
            onLoadDone(false, null, e.getMessage());
        }
    }

    @UiThread
    @IgnoreWhen(IgnoreWhen.State.DETACHED)
    protected void onLoadDone(boolean success, List<ReferenceItem> newList, String message) {
        if (!success) {
            info((AppCompatActivity) getActivity(), "load_list_error", message);
            return;
        }
        show(newList);
    }

    private void show(List<ReferenceItem> list) {
        final ReferenceRecyclerAdapter adapter = new ReferenceRecyclerAdapter(list, selector, this);
        recyclerView.setAdapter(adapter);
        selectorCallback.setList(list); // update selector list

        boolean hasContent = list.size() > 0;
        emptyView.setVisibility(hasContent ? View.GONE : View.VISIBLE);
        cardView.setVisibility(hasContent ? View.VISIBLE : View.GONE);

        setListShown(true);
    }

    private void showItem(ReferenceItem referenceItem) {
        if (referenceItem.hasChildren()) {
            showList(referenceItem);
        } else {
            showDetail(referenceItem);
        }
    }

    private void showDetail(ReferenceItem referenceItem) {

    }

    private void showList(ReferenceItem referenceItem) {
        String title = referenceItem.getTitle();
        String id = referenceItem.getId();
        Intent intent = QuickRefActivity.newListIntent(getContext(), title, id);
        startActivity(intent);
    }

    @Override
    public void onItemTap(ReferenceItem referenceItem, int index) {
        if (referenceItem != null) {
            showItem(referenceItem);
        }
    }

    @Override
    public void onMultiSelectorStart() {
        ((AppCompatActivity) getActivity()).startSupportActionMode(selectorCallback);
    }

    private void setListShown(boolean shown) {
        switcher.setDisplayedChild(shown ? 0 : 1);
    }

    private static class SelectorCallback extends ModalMultiSelectorCallback  {

        private Activity activity;
        private MultiSelector selector;
        private List<ReferenceItem> list;

        public SelectorCallback(Activity activity, MultiSelector selector) {
            super(selector);
            this.activity = activity;
            this.selector = selector;
        }

        public void setList(List<ReferenceItem> list) {
            this.list = list;
        }

        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            super.onCreateActionMode(actionMode, menu);
            activity.getMenuInflater().inflate(R.menu.fragment_reference_select, menu);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            mode.finish();
            switch (item.getItemId()) {
                case R.id.add_favorite:
                    addSelectedItemsToFavorites();
                    break;
            }
            selector.clearSelections();
            return true;
        }

        private void addSelectedItemsToFavorites() {
            int n = list.size();
            List<String> favorites = new ArrayList<>();
            for (int i=0; i<n; i++) {
                if (selector.isSelected(i, 0)) {
                    favorites.add(list.get(i).getId());
                }
            }
            FavoriteConfig favoriteConfig = new FavoriteConfig(activity);
            favoriteConfig.add(favorites);
            Toast.makeText(activity, R.string.msg_favorite_saved, Toast.LENGTH_SHORT).show();
        }

    }

}
