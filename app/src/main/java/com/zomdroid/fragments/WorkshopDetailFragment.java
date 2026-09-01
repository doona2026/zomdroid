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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zomdroid.R;
import com.zomdroid.workshop.data.WorkshopBrowseItem;
import com.zomdroid.workshop.data.WorkshopCatalogRuntime;
import com.zomdroid.workshop.data.WorkshopComment;
import com.zomdroid.workshop.data.WorkshopCommentPage;
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
    private ImageView image;
    private Button download;
    private Button dependencyDownload;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_workshop_detail, container, false);
        status = view.findViewById(R.id.workshop_detail_status);
        image = view.findViewById(R.id.workshop_detail_image);
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
        WorkshopCatalogRuntime.loadImage(requireContext(), item.getPreviewImageUrl(), image);
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
        ((TextView) requireView().findViewById(R.id.workshop_detail_description)).setText(value.getDescription());
        ((TextView) requireView().findViewById(R.id.workshop_detail_changes)).setText(value.getChangeNotes());
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

    private void renderDependencies(List<WorkshopRequiredItem> dependencies) {
        LinearLayout container = requireView().findViewById(R.id.workshop_detail_dependencies);
        container.removeAllViews();
        for (WorkshopRequiredItem dependency : dependencies) {
            View row = getLayoutInflater().inflate(R.layout.item_workshop_dependency, container, false);
            ((TextView) row.findViewById(R.id.workshop_dependency_title)).setText(dependency.getTitle());
            row.findViewById(R.id.workshop_dependency_download).setOnClickListener(v -> enqueue(WorkshopCatalogRuntime.requiredPublishedFileId(dependency), dependency.getTitle()));
            container.addView(row);
        }
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
