package com.zomdroid.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zomdroid.R;
import com.zomdroid.workshop.data.WorkshopBrowseItem;
import com.zomdroid.workshop.data.WorkshopCatalogRuntime;
import com.zomdroid.workshop.data.WorkshopComment;
import com.zomdroid.workshop.data.WorkshopCommentPage;
import com.zomdroid.workshop.data.WorkshopDescriptionBlock;
import com.zomdroid.workshop.data.WorkshopItemDetail;
import com.zomdroid.workshop.data.WorkshopRequiredItem;
import com.zomdroid.workshop.auth.SteamAuthRuntime;
import com.zomdroid.workshop.download.DownloadCenterManager;
import com.zomdroid.workshop.download.DownloadCenterManagerProvider;
import com.zomdroid.workshop.download.WorkshopDownloadForegroundService;

import java.util.ArrayList;
import java.util.List;

public class WorkshopDetailFragment extends Fragment {
    private WorkshopItemDetail detail;
    private TextView status;
    private RecyclerView image;
    private TextView imagePage;
    private LinearLayoutManager imageLayoutManager;
    private WorkshopImageAdapter imageAdapter;
    private Button download;
    private Button dependencyDownload;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_workshop_detail, container, false);
        status = view.findViewById(R.id.workshop_detail_status);
        image = view.findViewById(R.id.workshop_detail_image);
        imagePage = view.findViewById(R.id.workshop_detail_image_page);
        imageLayoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        imageAdapter = new WorkshopImageAdapter();
        image.setLayoutManager(imageLayoutManager);
        image.setAdapter(imageAdapter);
        new PagerSnapHelper().attachToRecyclerView(image);
        image.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateImageTransforms();
            }

            @Override public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                updateImagePage();
            }
        });
        download = view.findViewById(R.id.workshop_detail_download);
        dependencyDownload = view.findViewById(R.id.workshop_detail_download_dependencies);
        download.setEnabled(false);
        view.findViewById(R.id.workshop_detail_open_steam).setOnClickListener(v -> openSteamPage());
        download.setOnClickListener(v -> enqueueDetail());
        dependencyDownload.setOnClickListener(v -> confirmDependencyDownload());
        load();
        return view;
    }

    private void load() {
        Bundle args = getArguments();
        if (args == null) { status.setText(R.string.workshop_detail_missing); return; }
        WorkshopBrowseItem item = WorkshopCatalogRuntime.item(
                args.getInt("app_id", 108600), args.getLong("published_file_id"),
                args.getString("title", "Workshop item"), args.getString("author", ""),
                args.getString("preview_url", ""), args.getString("description", ""));
        status.setText(R.string.workshop_loading);
        imageAdapter.setItems(item.getPreviewImageUrl().isBlank()
                ? java.util.Collections.emptyList()
                : java.util.Collections.singletonList(item.getPreviewImageUrl()));
        WorkshopCatalogRuntime.detail(requireContext(), item, true, new WorkshopCatalogRuntime.DetailCallback() {
            @Override public void onSuccess(WorkshopItemDetail result) {
                if (!isAdded()) return;
                detail = result;
                render(result);
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                status.setText(message);
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void render(WorkshopItemDetail value) {
        ((TextView) requireView().findViewById(R.id.workshop_detail_title)).setText(value.getTitle());
        ((TextView) requireView().findViewById(R.id.workshop_detail_author)).setText(getString(R.string.workshop_author_format, value.getAuthorName()));
        String size = value.getFileSizeBytes() == null ? "?" : formatBytes(value.getFileSizeBytes());
        String stats = getString(R.string.workshop_meta_format, size,
                value.getSubscriptions() == null ? "?" : value.getSubscriptions().toString(),
                value.getViews() == null ? "?" : value.getViews().toString());
        ((TextView) requireView().findViewById(R.id.workshop_detail_meta)).setText(stats);
        ((TextView) requireView().findViewById(R.id.workshop_detail_tags)).setText(TextUtils.join(" · ", value.getTags()));
        renderDescription(value);
        ((TextView) requireView().findViewById(R.id.workshop_detail_changes)).setText(value.getChangeNotes());
        List<String> galleryImageUrls = value.getGalleryImageUrls();
        if (galleryImageUrls.isEmpty() && !value.getPreviewImageUrl().isBlank()) {
            galleryImageUrls = java.util.Collections.singletonList(value.getPreviewImageUrl());
        }
        imageAdapter.setItems(galleryImageUrls);
        image.scheduleLayoutAnimation();
        download.setEnabled(true);
        dependencyDownload.setVisibility(value.getRequiredItems().isEmpty() ? View.GONE : View.VISIBLE);
        renderDependencies(value.getRequiredItems());
        renderComments(value.getComments());
        if (value.getCommentThreadContext() != null) {
            WorkshopCatalogRuntime.comments(requireContext(), value, 1, new WorkshopCatalogRuntime.CommentsCallback() {
                @Override public void onSuccess(WorkshopCommentPage page) { if (isAdded()) renderComments(page.getComments()); }
                @Override public void onError(String message) { if (isAdded()) renderComments(java.util.Collections.emptyList()); }
            });
        }
        status.setText(R.string.workshop_loaded);
    }

    private void renderDescription(WorkshopItemDetail value) {
        LinearLayout container = requireView().findViewById(R.id.workshop_detail_description);
        container.removeAllViews();
        List<WorkshopDescriptionBlock> blocks = value.getDescriptionBlocks();
        if (blocks.isEmpty()) {
            addDescriptionText(container, value.getDescription());
            container.scheduleLayoutAnimation();
            return;
        }
        for (WorkshopDescriptionBlock block : blocks) {
            if (!block.getText().isBlank()) {
                addDescriptionText(container, block.getText());
            }
            if (block.getImageUrl() != null && !block.getImageUrl().isBlank()) {
                ImageView imageView = new ImageView(requireContext());
                imageView.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                imageView.setAdjustViewBounds(true);
                imageView.setMaxHeight(dp(420));
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setContentDescription(getString(R.string.workshop_description_image));
                container.addView(imageView);
                WorkshopCatalogRuntime.loadImage(requireContext(), block.getImageUrl(), imageView);
            }
        }
        container.scheduleLayoutAnimation();
    }

    private void addDescriptionText(LinearLayout container, String text) {
        TextView textView = new TextView(requireContext());
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setTextSize(15);
        textView.setLineSpacing(dp(4), 1f);
        textView.setText(text);
        container.addView(textView);
    }

    private void updateImagePage() {
        if (imageAdapter == null || imagePage == null || imageLayoutManager == null) return;
        int count = imageAdapter.getItemCount();
        image.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        imagePage.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
        if (count > 1) {
            int position = imageLayoutManager.findFirstCompletelyVisibleItemPosition();
            if (position == RecyclerView.NO_POSITION) {
                position = imageLayoutManager.findFirstVisibleItemPosition();
            }
            imagePage.setText(getString(
                    R.string.workshop_image_page_format,
                    Math.max(0, position) + 1,
                    count));
        }
    }

    private void updateImageTransforms() {
        if (image == null || image.getWidth() == 0) return;
        float center = image.getWidth() / 2f;
        float maxDistance = Math.max(1f, image.getWidth());
        for (int index = 0; index < image.getChildCount(); index++) {
            View child = image.getChildAt(index);
            float childCenter = (child.getLeft() + child.getRight()) / 2f;
            float distance = Math.min(1f, Math.abs(childCenter - center) / maxDistance);
            float scale = 1f - distance * 0.08f;
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(1f - distance * 0.35f);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class WorkshopImageAdapter extends RecyclerView.Adapter<WorkshopImageHolder> {
        private final List<String> urls = new ArrayList<>();

        void setItems(List<String> nextUrls) {
            urls.clear();
            for (String url : nextUrls) {
                if (url != null && !url.isBlank() && !urls.contains(url)) {
                    urls.add(url);
                }
            }
            notifyDataSetChanged();
            if (!urls.isEmpty()) {
                image.scrollToPosition(0);
            }
            image.scheduleLayoutAnimation();
            updateImageTransforms();
            updateImagePage();
        }

        @NonNull @Override public WorkshopImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(300)));
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new WorkshopImageHolder(imageView);
        }

        @Override public void onBindViewHolder(@NonNull WorkshopImageHolder holder, int position) {
            holder.bind(urls.get(position));
        }

        @Override public int getItemCount() { return urls.size(); }
    }

    private final class WorkshopImageHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;

        WorkshopImageHolder(View view) {
            super(view);
            imageView = (ImageView) view;
        }

        void bind(String url) {
            imageView.setTag(url);
            imageView.setImageDrawable(null);
            WorkshopCatalogRuntime.loadImage(requireContext(), url, imageView);
        }
    }

    private void renderDependencies(List<WorkshopRequiredItem> dependencies) {
        LinearLayout container = requireView().findViewById(R.id.workshop_detail_dependencies);
        container.removeAllViews();
        for (WorkshopRequiredItem dependency : dependencies) {
            View row = getLayoutInflater().inflate(R.layout.item_workshop_dependency, container, false);
            ((TextView) row.findViewById(R.id.workshop_dependency_title)).setText(dependency.getTitle());
            row.findViewById(R.id.workshop_dependency_download).setOnClickListener(v -> enqueue(WorkshopCatalogRuntime.requiredPublishedFileId(dependency), dependency.getTitle()));
            container.addView(row);
        }
        container.scheduleLayoutAnimation();
    }

    private void renderComments(List<WorkshopComment> comments) {
        LinearLayout container = requireView().findViewById(R.id.workshop_detail_comments);
        container.removeAllViews();
        for (WorkshopComment comment : comments) {
            View row = getLayoutInflater().inflate(R.layout.item_workshop_comment, container, false);
            ((TextView) row.findViewById(R.id.workshop_comment_author)).setText(comment.getAuthorName());
            ((TextView) row.findViewById(R.id.workshop_comment_content)).setText(comment.getContent());
            container.addView(row);
        }
        if (comments.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.workshop_comments_unavailable);
            container.addView(empty);
        }
        container.scheduleLayoutAnimation();
    }

    private void openSteamPage() {
        if (detail == null) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(detail.getWorkshopUrl()))); }
        catch (Exception e) { Toast.makeText(requireContext(), R.string.workshop_open_steam_failed, Toast.LENGTH_SHORT).show(); }
    }

    private void enqueueDetail() {
        if (detail != null) {
            enqueue(
                    WorkshopCatalogRuntime.detailPublishedFileId(detail),
                    detail.getTitle(),
                    detail.getDescription(),
                    detail.getPreviewImageUrl(),
                    detail.getTimeUpdatedEpochSeconds());
        }
    }

    private void enqueue(long publishedFileId, String title) {
        enqueue(publishedFileId, title, null, null, null);
    }

    private void enqueue(long publishedFileId, String title, String description,
                         String previewUrl, Long updatedAtEpochSeconds) {
        DownloadCenterManager manager = DownloadCenterManagerProvider.get(requireContext());
        Bundle args = getArguments();
        manager.enqueueForInstanceWithMetadata(
                108600L,
                publishedFileId,
                title,
                description,
                previewUrl,
                updatedAtEpochSeconds,
                SteamAuthRuntime.currentAccountId(requireContext()),
                args == null ? null : args.getString(WorkshopFragment.ARG_TARGET_INSTANCE_NAME),
                args == null ? null : args.getString(WorkshopFragment.ARG_TARGET_BUILD_VERSION));
        WorkshopDownloadForegroundService.start(requireContext());
        Toast.makeText(requireContext(), R.string.workshop_enqueued, Toast.LENGTH_SHORT).show();
    }

    private void confirmDependencyDownload() {
        if (detail == null || detail.getRequiredItems().isEmpty()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.workshop_download_dependencies)
                .setMessage(getString(R.string.workshop_dependencies_confirm, detail.getRequiredItems().size()))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    for (WorkshopRequiredItem item : detail.getRequiredItems()) {
                        enqueue(WorkshopCatalogRuntime.requiredPublishedFileId(item), item.getTitle());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
