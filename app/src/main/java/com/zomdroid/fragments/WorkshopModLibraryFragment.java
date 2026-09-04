package com.zomdroid.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.zomdroid.R;
import com.zomdroid.game.GameInstance;
import com.zomdroid.game.GameInstanceManager;
import com.zomdroid.workshop.data.WorkshopCatalogRuntime;
import com.zomdroid.workshop.data.WorkshopItemDetail;
import com.zomdroid.workshop.library.ModLibraryEntry;
import com.zomdroid.workshop.library.ModLibraryRepository;
import com.zomdroid.workshop.install.WorkshopLibraryInstaller;
import com.zomdroid.workshop.install.WorkshopInstallCoordinator;
import com.zomdroid.workshop.WorkshopFileAccess;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkshopModLibraryFragment extends Fragment {
    private LinearLayout list;
    private ModLibraryRepository repository;
    private final ExecutorService installPreflightExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_workshop_mod_library, container, false);
        list = view.findViewById(R.id.workshop_library_list);
        repository = new ModLibraryRepository(requireContext());
        view.findViewById(R.id.workshop_library_cleanup).setOnClickListener(v -> confirmCleanup());
        render();
        return view;
    }

    @Override
    public void onDestroy() {
        installPreflightExecutor.shutdownNow();
        super.onDestroy();
    }

    private void render() {
        list.removeAllViews();
        List<ModLibraryEntry> entries = repository.snapshot().getEntries();
        if (entries.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.workshop_library_empty);
            list.addView(empty);
            return;
        }
        for (ModLibraryEntry entry : entries) addEntry(entry);
    }

    private void addEntry(ModLibraryEntry entry) {
        View row = getLayoutInflater().inflate(R.layout.item_workshop_library, list, false);
        ((TextView) row.findViewById(R.id.workshop_library_title)).setText(entry.getTitle());
        ((TextView) row.findViewById(R.id.workshop_library_meta)).setText(
                getString(R.string.workshop_library_meta, entry.getPublishedFileId(), entry.getSource(), entry.getInstalledInstances().size()));
        ((TextView) row.findViewById(R.id.workshop_library_description)).setText(entry.getDescription());
        row.findViewById(R.id.workshop_library_install).setOnClickListener(v -> chooseInstance(entry));
        row.findViewById(R.id.workshop_library_update).setOnClickListener(v -> checkUpdate(entry));
        row.findViewById(R.id.workshop_library_share).setOnClickListener(v -> share(entry));
        row.findViewById(R.id.workshop_library_delete).setOnClickListener(v -> confirmDelete(entry));
        list.addView(row);
    }

    private void share(ModLibraryEntry entry) {
        try {
            Uri uri = WorkshopFileAccess.contentUriForCompletedFile(
                    requireContext(), new java.io.File(entry.getCompletedPath()));
            Intent share = new Intent(Intent.ACTION_SEND)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.workshop_library_share)));
        } catch (Throwable error) {
            Toast.makeText(requireContext(),
                    getString(R.string.workshop_download_center_install_error, error.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void chooseInstance(ModLibraryEntry entry) {
        List<GameInstance> instances = GameInstanceManager.requireSingleton().getInstances();
        if (instances == null || instances.isEmpty()) {
            Toast.makeText(requireContext(), R.string.workshop_download_center_no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[instances.size()];
        for (int i = 0; i < instances.size(); i++) names[i] = instances.get(i).getName();
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_library_choose_instance)
                .setItems(names, (dialog, which) -> confirmInstall(entry, instances.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmInstall(ModLibraryEntry entry, GameInstance instance) {
        installPreflightExecutor.execute(() -> {
            try {
                List<String> existing = WorkshopLibraryInstaller.findExistingModNames(entry, instance);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (existing.isEmpty()) install(entry, instance, false);
                    else showOverwriteDialog(entry, instance, existing);
                });
            } catch (Throwable error) {
                mainHandler.post(() -> {
                    if (isAdded()) showInstallError(error);
                });
            }
        });
    }

    private void showOverwriteDialog(ModLibraryEntry entry, GameInstance instance, List<String> existing) {
        String names = android.text.TextUtils.join(", ", existing);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_library_overwrite_title)
                .setMessage(getString(R.string.workshop_library_overwrite_message_named, names))
                .setNegativeButton(R.string.workshop_library_install_without_backup,
                        (dialog, which) -> install(entry, instance, false))
                .setPositiveButton(R.string.workshop_library_install_with_backup,
                        (dialog, which) -> install(entry, instance, true))
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void install(ModLibraryEntry entry, GameInstance instance, boolean keepBackup) {
        try {
            requireContext().startForegroundService(WorkshopLibraryInstaller.buildIntent(
                    requireContext(), entry, instance, keepBackup));
            Toast.makeText(requireContext(), R.string.workshop_library_install_started, Toast.LENGTH_SHORT).show();
            render();
        } catch (Throwable error) {
            Toast.makeText(requireContext(), getString(R.string.workshop_download_center_install_error, error.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void showInstallError(Throwable error) {
        Toast.makeText(requireContext(), getString(R.string.workshop_download_center_install_error, error.getMessage()), Toast.LENGTH_LONG).show();
    }

    private void checkUpdate(ModLibraryEntry entry) {
        if (!repository.needsUpdateCheck(entry, System.currentTimeMillis(), 6L * 60L * 60L * 1000L)) {
            Toast.makeText(requireContext(), R.string.workshop_library_check_later, Toast.LENGTH_SHORT).show();
            return;
        }
        repository.markChecked(entry, System.currentTimeMillis());
        WorkshopCatalogRuntime.detail(
                requireContext(),
                WorkshopCatalogRuntime.item((int) entry.getAppId(), entry.getPublishedFileId(), entry.getTitle(), "", entry.getPreviewUrl(), entry.getDescription()),
                true,
                new WorkshopCatalogRuntime.DetailCallback() {
                    @Override public void onSuccess(WorkshopItemDetail detail) {
                        if (!isAdded()) return;
                        if (detail.getTimeUpdatedEpochSeconds() != null &&
                                (entry.getUpdatedAtEpochSeconds() == null || detail.getTimeUpdatedEpochSeconds() > entry.getUpdatedAtEpochSeconds())) {
                            Toast.makeText(requireContext(), R.string.workshop_library_update_available, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(requireContext(), R.string.workshop_library_up_to_date, Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onError(String message) {
                        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void confirmDelete(ModLibraryEntry entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_library_delete_title)
                .setMessage(R.string.workshop_library_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> { repository.remove(entry, true); render(); })
                .show();
    }

    private void confirmCleanup() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_library_cleanup_title)
                .setMessage(R.string.workshop_library_cleanup_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Map<String, ModLibraryEntry> newest = new HashMap<>();
                    for (ModLibraryEntry entry : repository.snapshot().getEntries()) {
                        String key = entry.getAppId() + ":" + entry.getPublishedFileId();
                        ModLibraryEntry current = newest.get(key);
                        if (current == null || (entry.getUpdatedAtEpochSeconds() != null &&
                                (current.getUpdatedAtEpochSeconds() == null ||
                                        entry.getUpdatedAtEpochSeconds() > current.getUpdatedAtEpochSeconds()))) {
                            newest.put(key, entry);
                        }
                    }
                    int removed = 0;
                    for (ModLibraryEntry entry : newest.values()) {
                        removed += repository.pruneOldVersions(
                                entry.getAppId(), entry.getPublishedFileId(), entry.getVersionKey());
                    }
                    render();
                    Toast.makeText(requireContext(),
                            getString(R.string.workshop_library_cleanup_done, removed),
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
