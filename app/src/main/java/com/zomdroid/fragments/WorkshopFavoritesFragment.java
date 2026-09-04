package com.zomdroid.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import com.zomdroid.workshop.data.WorkshopCatalogRuntime;
import com.zomdroid.workshop.favorites.WorkshopFavorite;
import com.zomdroid.workshop.favorites.WorkshopFavoritesRepository;

import java.util.ArrayList;
import java.util.List;

/** Displays locally saved Workshop items without requiring a network request. */
public class WorkshopFavoritesFragment extends Fragment {
    private RecyclerView list;
    private TextView empty;
    private FavoritesAdapter adapter;
    private WorkshopFavoritesRepository favoritesRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_workshop_favorites, container, false);
        list = view.findViewById(R.id.workshop_favorites_list);
        empty = view.findViewById(R.id.workshop_favorites_empty);
        favoritesRepository = new WorkshopFavoritesRepository(requireContext());
        adapter = new FavoritesAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        renderFavorites();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (list != null) renderFavorites();
    }

    private void renderFavorites() {
        List<WorkshopBrowseItem> items = new ArrayList<>();
        for (WorkshopFavorite favorite : favoritesRepository.list()) {
            items.add(WorkshopCatalogRuntime.item(
                    Math.toIntExact(favorite.getAppId()),
                    favorite.getPublishedFileId(),
                    favorite.getTitle(),
                    favorite.getAuthorName(),
                    favorite.getPreviewImageUrl(),
                    favorite.getDescription()));
        }
        adapter.setItems(items);
        boolean hasFavorites = !items.isEmpty();
        list.setVisibility(hasFavorites ? View.VISIBLE : View.GONE);
        empty.setVisibility(hasFavorites ? View.GONE : View.VISIBLE);
        if (hasFavorites) list.scheduleLayoutAnimation();
    }

    private void openDetails(WorkshopBrowseItem item) {
        Bundle args = new Bundle();
        args.putInt("app_id", WorkshopCatalogRuntime.appId(item));
        args.putLong("published_file_id", WorkshopCatalogRuntime.publishedFileId(item));
        args.putString("title", item.getTitle());
        args.putString("author", item.getAuthorName());
        args.putString("preview_url", item.getPreviewImageUrl());
        args.putString("description", item.getDescriptionSnippet());
        Bundle parent = getArguments();
        if (parent != null) {
            args.putString(WorkshopFragment.ARG_TARGET_INSTANCE_NAME,
                    parent.getString(WorkshopFragment.ARG_TARGET_INSTANCE_NAME));
            args.putString(WorkshopFragment.ARG_TARGET_BUILD_VERSION,
                    parent.getString(WorkshopFragment.ARG_TARGET_BUILD_VERSION));
        }
        NavHostFragment.findNavController(this).navigate(R.id.action_workshop_favorite_detail, args);
    }

    @Override
    public void onDestroyView() {
        list = null;
        empty = null;
        adapter = null;
        super.onDestroyView();
    }

    private final class FavoritesAdapter extends RecyclerView.Adapter<FavoriteHolder> {
        private final List<WorkshopBrowseItem> items = new ArrayList<>();

        void setItems(List<WorkshopBrowseItem> nextItems) {
            items.clear();
            items.addAll(nextItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FavoriteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new FavoriteHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_workshop, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull FavoriteHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class FavoriteHolder extends RecyclerView.ViewHolder {
        private final ImageView image;
        private final TextView title;
        private final TextView author;
        private final TextView description;
        private final ImageButton favorite;

        FavoriteHolder(View view) {
            super(view);
            image = view.findViewById(R.id.workshop_item_image);
            title = view.findViewById(R.id.workshop_item_title);
            author = view.findViewById(R.id.workshop_item_author);
            description = view.findViewById(R.id.workshop_item_description);
            favorite = view.findViewById(R.id.workshop_item_favorite);
            view.findViewById(R.id.workshop_item_details).setOnClickListener(v -> openDetailsFromTag());
            view.setOnClickListener(v -> openDetailsFromTag());
            favorite.setOnClickListener(v -> removeFavorite());
        }

        void bind(WorkshopBrowseItem item) {
            itemView.setTag(item);
            title.setText(item.getTitle());
            author.setText(getString(R.string.workshop_author_format, item.getAuthorName()));
            description.setText(item.getDescriptionSnippet());
            image.setImageDrawable(null);
            WorkshopCatalogRuntime.loadImage(requireContext(), item.getPreviewImageUrl(), image);
            favorite.setImageResource(R.drawable.ic_favorite_filled);
            favorite.setContentDescription(getString(R.string.workshop_favorite_remove));
        }

        private void openDetailsFromTag() {
            WorkshopBrowseItem item = (WorkshopBrowseItem) itemView.getTag();
            if (item != null) openDetails(item);
        }

        private void removeFavorite() {
            WorkshopBrowseItem item = (WorkshopBrowseItem) itemView.getTag();
            if (item == null || favoritesRepository == null) return;
            if (favoritesRepository.remove(
                    WorkshopCatalogRuntime.appId(item),
                    WorkshopCatalogRuntime.publishedFileId(item))) {
                Toast.makeText(requireContext(), R.string.workshop_favorite_removed,
                        Toast.LENGTH_SHORT).show();
                renderFavorites();
            }
        }
    }
}
