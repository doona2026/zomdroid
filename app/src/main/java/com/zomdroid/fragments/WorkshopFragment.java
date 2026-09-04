package com.zomdroid.fragments;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zomdroid.R;
import com.zomdroid.workshop.data.WorkshopBrowseItem;
import com.zomdroid.workshop.data.WorkshopBrowsePage;
import com.zomdroid.workshop.data.WorkshopCatalogRuntime;

import java.util.ArrayList;
import java.util.List;

public class WorkshopFragment extends Fragment {
    public static final String ARG_TARGET_INSTANCE_NAME = "target_instance_name";
    public static final String ARG_TARGET_BUILD_VERSION = "target_build_version";

    private EditText search;
    private Spinner sort;
    private ProgressBar progress;
    private TextView status;
    private TextView pageLabel;
    private Button previous;
    private Button next;
    private Button toTop;
    private RecyclerView list;
    private WorkshopAdapter adapter;
    private Parcelable pendingListState;
    private int appliedSort = -1;
    private int viewGeneration;
    private int page = 1;
    private boolean hasNext;
    private final List<WorkshopBrowseItem> cachedItems = new ArrayList<>();

    private static final String STATE_SEARCH = "workshop_search";
    private static final String STATE_SORT = "workshop_sort";
    private static final String STATE_PAGE = "workshop_page";
    private static final String STATE_HAS_NEXT = "workshop_has_next";
    private static final String STATE_LIST = "workshop_list_state";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        viewGeneration++;
        View view = inflater.inflate(R.layout.fragment_workshop, container, false);
        list = view.findViewById(R.id.workshop_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        View header = inflater.inflate(R.layout.item_workshop_header, list, false);
        View footer = inflater.inflate(R.layout.item_workshop_footer, list, false);
        search = header.findViewById(R.id.workshop_search);
        sort = header.findViewById(R.id.workshop_sort);
        progress = header.findViewById(R.id.workshop_progress);
        status = header.findViewById(R.id.workshop_status);
        pageLabel = footer.findViewById(R.id.workshop_page);
        previous = footer.findViewById(R.id.workshop_previous);
        next = footer.findViewById(R.id.workshop_next);
        toTop = view.findViewById(R.id.workshop_to_top);
        adapter = new WorkshopAdapter(header, footer);
        list.setAdapter(adapter);
        sort.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.workshop_sort_options)));
        if (state != null) {
            search.setText(state.getString(STATE_SEARCH, ""));
            sort.setSelection(Math.max(0, Math.min(
                    state.getInt(STATE_SORT, 0),
                    sort.getCount() - 1
            )));
            page = Math.max(1, state.getInt(STATE_PAGE, 1));
            hasNext = state.getBoolean(STATE_HAS_NEXT, false);
            pendingListState = state.getParcelable(STATE_LIST);
        }
        appliedSort = sort.getSelectedItemPosition();
        sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View itemView, int position, long id) {
                if (position == appliedSort) return;
                appliedSort = position;
                refreshFromFirstPage();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        header.findViewById(R.id.workshop_search_btn).setOnClickListener(v -> {
            refreshFromFirstPage();
        });
        previous.setOnClickListener(v -> load(page - 1));
        next.setOnClickListener(v -> load(page + 1));
        toTop.setOnClickListener(v -> scrollToTop());
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateToTopVisibility();
            }
        });
        updateToTopVisibility();
        if (cachedItems.isEmpty()) {
            updatePaging();
            load(page);
        } else {
            adapter.setItems(cachedItems);
            status.setText(R.string.workshop_loaded);
            updatePaging();
            restoreListState();
        }
        return view;
    }

    private void refreshFromFirstPage() {
        if (!isAdded() || search == null) return;
        cachedItems.clear();
        hasNext = false;
        pendingListState = null;
        WorkshopCatalogRuntime.clearBrowseCache();
        load(1);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SEARCH, search == null ? "" : search.getText().toString());
        outState.putInt(STATE_SORT, sort == null ? 0 : sort.getSelectedItemPosition());
        outState.putInt(STATE_PAGE, page);
        outState.putBoolean(STATE_HAS_NEXT, hasNext);
        if (list != null && list.getLayoutManager() != null) {
            outState.putParcelable(STATE_LIST, list.getLayoutManager().onSaveInstanceState());
        }
    }

    private void load(int requestedPage) {
        if (!isAdded() || requestedPage < 1) return;
        int requestViewGeneration = viewGeneration;
        page = requestedPage;
        scrollToTop();
        progress.setVisibility(View.VISIBLE);
        status.setText(R.string.workshop_loading);
        updatePaging();
        WorkshopCatalogRuntime.browse(requireContext(), search.getText().toString().trim(), sort.getSelectedItemPosition(), page,
                new WorkshopCatalogRuntime.BrowseCallback() {
                    @Override public void onSuccess(WorkshopBrowsePage result) {
                        if (!isAdded() || requestViewGeneration != viewGeneration || getView() == null) return;
                        progress.setVisibility(View.GONE);
                        cachedItems.clear();
                        cachedItems.addAll(result.getItems());
                        adapter.setItems(result.getItems());
                        page = result.getPage();
                        hasNext = result.getHasNextPage();
                        status.setText(result.getItems().isEmpty() ? R.string.workshop_empty : R.string.workshop_loaded);
                        updatePaging();
                        restoreListState();
                    }
                    @Override public void onError(String message) {
                        if (!isAdded() || requestViewGeneration != viewGeneration || getView() == null) return;
                        progress.setVisibility(View.GONE);
                        status.setText(message);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        updatePaging();
                    }
            });
    }

    private void scrollToTop() {
        if (list == null) return;
        list.stopScroll();
        list.scrollToPosition(0);
        updateToTopVisibility();
    }

    private void updateToTopVisibility() {
        if (list == null || toTop == null) return;
        toTop.setVisibility(list.canScrollVertically(-1) ? View.VISIBLE : View.GONE);
    }

    private void restoreListState() {
        if (pendingListState == null || list == null || list.getLayoutManager() == null) return;
        Parcelable state = pendingListState;
        pendingListState = null;
        list.post(() -> {
            if (list == null || list.getLayoutManager() == null) return;
            list.getLayoutManager().onRestoreInstanceState(state);
            updateToTopVisibility();
        });
    }

    private void updatePaging() {
        if (pageLabel == null) return;
        pageLabel.setText(getString(R.string.workshop_page_format, page));
        previous.setEnabled(page > 1 && progress.getVisibility() != View.VISIBLE);
        next.setEnabled(hasNext && progress.getVisibility() != View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        if (list != null && list.getLayoutManager() != null) {
            pendingListState = list.getLayoutManager().onSaveInstanceState();
        }
        viewGeneration++;
        list = null;
        search = null;
        sort = null;
        progress = null;
        status = null;
        pageLabel = null;
        previous = null;
        next = null;
        toTop = null;
        adapter = null;
        super.onDestroyView();
    }

    private final class WorkshopAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;
        private static final int TYPE_FOOTER = 2;
        private final List<WorkshopBrowseItem> items = new ArrayList<>();
        private final View header;
        private final View footer;

        WorkshopAdapter(View header, View footer) {
            this.header = header;
            this.footer = footer;
        }

        void setItems(List<WorkshopBrowseItem> nextItems) { items.clear(); items.addAll(nextItems); notifyDataSetChanged(); }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == TYPE_HEADER) return new RecyclerView.ViewHolder(header) { };
            if (type == TYPE_FOOTER) return new RecyclerView.ViewHolder(footer) { };
            return new WorkshopHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_workshop, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == TYPE_ITEM) {
                ((WorkshopHolder) holder).bind(items.get(position - 1));
            }
        }
        @Override public int getItemViewType(int position) {
            if (position == 0) return TYPE_HEADER;
            if (position == items.size() + 1) return TYPE_FOOTER;
            return TYPE_ITEM;
        }
        @Override public int getItemCount() { return items.size() + 2; }
    }

    private final class WorkshopHolder extends RecyclerView.ViewHolder {
        private final android.widget.ImageView image;
        private final TextView title;
        private final TextView author;
        private final TextView description;
        WorkshopHolder(View view) {
            super(view);
            image = view.findViewById(R.id.workshop_item_image);
            title = view.findViewById(R.id.workshop_item_title);
            author = view.findViewById(R.id.workshop_item_author);
            description = view.findViewById(R.id.workshop_item_description);
            view.findViewById(R.id.workshop_item_details).setOnClickListener(v -> openDetails());
            view.setOnClickListener(v -> openDetails());
        }
        void bind(WorkshopBrowseItem item) {
            itemView.setTag(item);
            title.setText(item.getTitle());
            author.setText(getString(R.string.workshop_author_format, item.getAuthorName()));
            description.setText(item.getDescriptionSnippet());
            image.setImageDrawable(null);
            WorkshopCatalogRuntime.loadImage(requireContext(), item.getPreviewImageUrl(), image);
        }
        private void openDetails() {
            WorkshopBrowseItem item = (WorkshopBrowseItem) itemView.getTag();
            if (item == null) return;
            Bundle args = new Bundle();
            args.putInt("app_id", WorkshopCatalogRuntime.appId(item));
            args.putLong("published_file_id", WorkshopCatalogRuntime.publishedFileId(item));
            args.putString("title", item.getTitle());
            args.putString("author", item.getAuthorName());
            args.putString("preview_url", item.getPreviewImageUrl());
            args.putString("description", item.getDescriptionSnippet());
            Bundle parent = getArguments();
            if (parent != null) {
                args.putString(ARG_TARGET_INSTANCE_NAME, parent.getString(ARG_TARGET_INSTANCE_NAME));
                args.putString(ARG_TARGET_BUILD_VERSION, parent.getString(ARG_TARGET_BUILD_VERSION));
            }
            NavHostFragment.findNavController(WorkshopFragment.this).navigate(R.id.action_workshop_detail, args);
        }
    }
}
