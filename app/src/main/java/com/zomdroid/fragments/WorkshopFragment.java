package com.zomdroid.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private WorkshopAdapter adapter;
    private int page = 1;
    private boolean hasNext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_workshop, container, false);
        search = view.findViewById(R.id.workshop_search);
        sort = view.findViewById(R.id.workshop_sort);
        progress = view.findViewById(R.id.workshop_progress);
        status = view.findViewById(R.id.workshop_status);
        pageLabel = view.findViewById(R.id.workshop_page);
        previous = view.findViewById(R.id.workshop_previous);
        next = view.findViewById(R.id.workshop_next);
        adapter = new WorkshopAdapter();
        RecyclerView list = view.findViewById(R.id.workshop_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        sort.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{"热门", "最新发布", "最近更新", "订阅最多"}));
        view.findViewById(R.id.workshop_search_btn).setOnClickListener(v -> load(1));
        previous.setOnClickListener(v -> load(page - 1));
        next.setOnClickListener(v -> load(page + 1));
        updatePaging();
        load(1);
        return view;
    }

    private void load(int requestedPage) {
        if (!isAdded() || requestedPage < 1) return;
        page = requestedPage;
        progress.setVisibility(View.VISIBLE);
        status.setText(R.string.workshop_loading);
        updatePaging();
        WorkshopCatalogRuntime.browse(requireContext(), search.getText().toString().trim(), sort.getSelectedItemPosition(), page,
                new WorkshopCatalogRuntime.BrowseCallback() {
                    @Override public void onSuccess(WorkshopBrowsePage result) {
                        if (!isAdded()) return;
                        progress.setVisibility(View.GONE);
                        adapter.setItems(result.getItems());
                        page = result.getPage();
                        hasNext = result.getHasNextPage();
                        status.setText(result.getItems().isEmpty() ? R.string.workshop_empty : R.string.workshop_loaded);
                        updatePaging();
                    }
                    @Override public void onError(String message) {
                        if (!isAdded()) return;
                        progress.setVisibility(View.GONE);
                        status.setText(message);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        updatePaging();
                    }
                });
    }

    private void updatePaging() {
        if (pageLabel == null) return;
        pageLabel.setText(getString(R.string.workshop_page_format, page));
        previous.setEnabled(page > 1 && progress.getVisibility() != View.VISIBLE);
        next.setEnabled(hasNext && progress.getVisibility() != View.VISIBLE);
    }

    private final class WorkshopAdapter extends RecyclerView.Adapter<WorkshopHolder> {
        private final List<WorkshopBrowseItem> items = new ArrayList<>();
        void setItems(List<WorkshopBrowseItem> nextItems) { items.clear(); items.addAll(nextItems); notifyDataSetChanged(); }
        @NonNull @Override public WorkshopHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new WorkshopHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_workshop, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull WorkshopHolder holder, int position) { holder.bind(items.get(position)); }
        @Override public int getItemCount() { return items.size(); }
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
