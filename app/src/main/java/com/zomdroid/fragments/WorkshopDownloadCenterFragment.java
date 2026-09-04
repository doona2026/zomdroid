package com.zomdroid.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AnimationUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.zomdroid.R;
import com.zomdroid.game.GameInstance;
import com.zomdroid.game.GameInstanceManager;
import com.zomdroid.workshop.library.ModLibraryEntry;
import com.zomdroid.workshop.library.ModLibraryRepository;
import com.zomdroid.workshop.install.WorkshopLibraryInstaller;
import com.zomdroid.workshop.download.DownloadCenterManager;
import com.zomdroid.workshop.download.DownloadCenterManagerProvider;
import com.zomdroid.workshop.download.DownloadCenterTask;
import com.zomdroid.workshop.download.DownloadCenterTaskObserver;
import com.zomdroid.workshop.download.DownloadCenterTaskState;
import com.zomdroid.workshop.download.WorkshopDownloadForegroundService;
import com.zomdroid.workshop.thirdparty.GgntwFallbackRuntime;
import com.zomdroid.workshop.auth.SteamAuthRuntime;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlinx.coroutines.Job;

/** Displays and controls persistent Workshop tasks; downloading itself belongs to the service. */
public class WorkshopDownloadCenterFragment extends Fragment {
    private LinearLayout tasksContainer;
    private DownloadCenterManager manager;
    private ModLibraryRepository library;
    private Job observation;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService installPreflightExecutor = Executors.newSingleThreadExecutor();
    private List<DownloadCenterTask> latestTasks = java.util.Collections.emptyList();
    private final Map<String, View> taskViews = new LinkedHashMap<>();
    private TextView emptyView;
    private boolean renderScheduled;
    private final Runnable scheduledRender = () -> {
        renderScheduled = false;
        if (tasksContainer != null) render(latestTasks);
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_workshop_download_center, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tasksContainer = view.findViewById(R.id.workshop_download_tasks_container);
        manager = DownloadCenterManagerProvider.get(requireContext());
        library = new ModLibraryRepository(requireContext());
        view.findViewById(R.id.workshop_download_center_start).setOnClickListener(
                ignored -> WorkshopDownloadForegroundService.start(requireContext()));
        observation = manager.observe(new DownloadCenterTaskObserver() {
            @Override
            public void onTasksChanged(List<DownloadCenterTask> tasks) {
                mainHandler.post(() -> scheduleRender(tasks));
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (observation != null) observation.cancel(null);
        observation = null;
        mainHandler.removeCallbacks(scheduledRender);
        renderScheduled = false;
        latestTasks = java.util.Collections.emptyList();
        taskViews.clear();
        emptyView = null;
        tasksContainer = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        installPreflightExecutor.shutdownNow();
        super.onDestroy();
    }

    private void scheduleRender(List<DownloadCenterTask> tasks) {
        if (tasksContainer == null) return;
        latestTasks = new ArrayList<>(tasks);
        if (renderScheduled) return;
        renderScheduled = true;
        mainHandler.postDelayed(scheduledRender, 150L);
    }

    private void render(List<DownloadCenterTask> tasks) {
        if (tasks.isEmpty()) {
            for (View row : taskViews.values()) tasksContainer.removeView(row);
            taskViews.clear();
            if (emptyView == null) {
                emptyView = new TextView(requireContext());
                emptyView.setText(R.string.workshop_download_center_empty);
                emptyView.setPadding(0, 24, 0, 24);
                tasksContainer.addView(emptyView);
            }
            return;
        }
        if (emptyView != null) {
            tasksContainer.removeView(emptyView);
            emptyView = null;
        }

        Set<String> activeIds = new HashSet<>();
        for (DownloadCenterTask task : tasks) activeIds.add(task.getId());
        java.util.Iterator<Map.Entry<String, View>> iterator = taskViews.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, View> entry = iterator.next();
            if (!activeIds.contains(entry.getKey())) {
                tasksContainer.removeView(entry.getValue());
                iterator.remove();
            }
        }

        for (int index = 0; index < tasks.size(); index++) {
            DownloadCenterTask task = tasks.get(index);
            View row = taskViews.get(task.getId());
            if (row == null) {
                row = createTaskView();
                taskViews.put(task.getId(), row);
                tasksContainer.addView(row, index);
                row.startAnimation(AnimationUtils.loadAnimation(
                        requireContext(), R.anim.workshop_content_enter));
            } else if (tasksContainer.indexOfChild(row) != index) {
                tasksContainer.removeView(row);
                tasksContainer.addView(row, index);
            }
            bindTaskView(row, task);
        }
    }

    private View createTaskView() {
        return getLayoutInflater().inflate(
                R.layout.item_workshop_download_task, tasksContainer, false);
    }

    private void bindTaskView(View row, DownloadCenterTask task) {
        TextView title = row.findViewById(R.id.workshop_task_title);
        TextView status = row.findViewById(R.id.workshop_task_status);
        TextView log = row.findViewById(R.id.workshop_task_log);
        TextView progressText = row.findViewById(R.id.workshop_task_progress_text);
        ProgressBar progress = row.findViewById(R.id.workshop_task_progress);
        title.setText(task.getTitle() == null || task.getTitle().trim().isEmpty()
                ? getString(R.string.workshop_download_center_item, task.getPublishedFileId())
                : task.getTitle());
        status.setText(formatStatus(task));
        log.setText(String.join("\n", task.getLogs()));
        Long totalBytes = task.getTotalBytes();
        long writtenBytes = Math.max(0L, task.getWrittenBytes());
        if (totalBytes != null && totalBytes > 0) {
            long boundedWrittenBytes = Math.min(writtenBytes, totalBytes);
            int percent = (int) Math.min(100L, Math.round(
                    boundedWrittenBytes * 100.0d / totalBytes));
            progress.setIndeterminate(false);
            progress.setProgress(percent);
            progressText.setText(getString(R.string.workshop_download_progress_format,
                    percent, formatBytes(boundedWrittenBytes), formatBytes(totalBytes)));
        } else {
            progress.setIndeterminate(task.getState() == DownloadCenterTaskState.Running);
            progressText.setText(getString(R.string.workshop_download_progress_unknown_format,
                    formatBytes(writtenBytes)));
        }

        Button pause = row.findViewById(R.id.workshop_task_pause);
        Button resume = row.findViewById(R.id.workshop_task_resume);
        Button retry = row.findViewById(R.id.workshop_task_retry);
        Button cancel = row.findViewById(R.id.workshop_task_cancel);
        Button delete = row.findViewById(R.id.workshop_task_delete);
        Button install = row.findViewById(R.id.workshop_task_install);
        Button fallback = row.findViewById(R.id.workshop_task_fallback);
        pause.setVisibility(task.getState() == DownloadCenterTaskState.Running
                || task.getState() == DownloadCenterTaskState.Queued ? View.VISIBLE : View.GONE);
        resume.setVisibility(task.getState() == DownloadCenterTaskState.Paused ? View.VISIBLE : View.GONE);
        retry.setVisibility(task.getState() == DownloadCenterTaskState.Failed
                || task.getState() == DownloadCenterTaskState.Cancelled ? View.VISIBLE : View.GONE);
        cancel.setVisibility(task.getState() == DownloadCenterTaskState.Running
                || task.getState() == DownloadCenterTaskState.Queued
                || task.getState() == DownloadCenterTaskState.Paused ? View.VISIBLE : View.GONE);
        install.setVisibility(task.getState() == DownloadCenterTaskState.Success
                && task.getOutputPath() != null ? View.VISIBLE : View.GONE);
        fallback.setVisibility(task.getState() == DownloadCenterTaskState.Failed ? View.VISIBLE : View.GONE);
        pause.setOnClickListener(ignored -> command(WorkshopDownloadForegroundService.ACTION_PAUSE, task.getId()));
        resume.setOnClickListener(ignored -> command(WorkshopDownloadForegroundService.ACTION_RESUME, task.getId()));
        retry.setOnClickListener(ignored -> command(WorkshopDownloadForegroundService.ACTION_RETRY, task.getId()));
        cancel.setOnClickListener(ignored -> command(WorkshopDownloadForegroundService.ACTION_CANCEL, task.getId()));
        delete.setOnClickListener(ignored -> manager.delete(task.getId()));
        install.setOnClickListener(ignored -> chooseInstance(task));
        fallback.setOnClickListener(ignored -> showFailureActions(task));
    }

    private String formatStatus(DownloadCenterTask task) {
        if (task.getState() == DownloadCenterTaskState.Failed && task.getErrorMessage() != null) {
            return task.getState().name() + " — " + task.getErrorMessage();
        }
        return String.format(Locale.US, "%s · %s", task.getState().name(), task.getPhase());
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024.0d);
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0d * 1024.0d));
        }
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0d * 1024.0d * 1024.0d));
    }

    private void command(String action, String taskId) {
        WorkshopDownloadForegroundService.command(requireContext(), action, taskId);
    }

    private void chooseInstance(DownloadCenterTask task) {
        List<GameInstance> instances = GameInstanceManager.requireSingleton().getInstances();
        if (instances == null || instances.isEmpty()) {
            Toast.makeText(requireContext(), R.string.workshop_download_center_no_instance,
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[instances.size()];
        for (int i = 0; i < instances.size(); i++) names[i] = instances.get(i).getName();
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_download_center_choose_instance)
                .setItems(names, (dialog, which) -> confirmInstall(task, instances.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmInstall(DownloadCenterTask task, GameInstance instance) {
        final ModLibraryEntry entry;
        try {
            entry = resolveLibraryEntry(task);
        } catch (Throwable error) {
            showInstallError(error);
            return;
        }

        // ZIPs can be several gigabytes. Inspect only their central directory off the UI thread;
        // this also makes the first-install path independent from the task title or ZIP filename.
        installPreflightExecutor.execute(() -> {
            try {
                List<String> existing = WorkshopLibraryInstaller.findExistingModNames(entry, instance);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (existing.isEmpty()) {
                        install(task, instance, entry, false);
                    } else {
                        showOverwriteDialog(task, instance, entry, existing);
                    }
                });
            } catch (Throwable error) {
                mainHandler.post(() -> {
                    if (isAdded()) showInstallError(error);
                });
            }
        });
    }

    private void showOverwriteDialog(DownloadCenterTask task, GameInstance instance,
                                     ModLibraryEntry entry, List<String> existing) {
        String names = android.text.TextUtils.join(", ", existing);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_library_overwrite_title)
                .setMessage(getString(R.string.workshop_library_overwrite_message_named, names))
                .setNegativeButton(R.string.workshop_library_install_without_backup,
                        (dialog, which) -> install(task, instance, entry, false))
                .setPositiveButton(R.string.workshop_library_install_with_backup,
                        (dialog, which) -> install(task, instance, entry, true))
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private ModLibraryEntry resolveLibraryEntry(DownloadCenterTask task) {
        if (task.getOutputPath() == null) throw new IllegalStateException("Completed archive path is missing");
        return library.entriesFor(task.getAppId(), task.getPublishedFileId()).stream()
                .filter(candidate -> task.getOutputPath().equals(candidate.getCompletedPath()))
                .findFirst().orElseGet(() -> library.recordCompleted(
                        task.getAppId(), task.getPublishedFileId(), task.getTitle() == null ? "" : task.getTitle(), "", "", null,
                        new File(task.getOutputPath()), java.util.Collections.emptyList(), "steam"));
    }

    private void install(DownloadCenterTask task, GameInstance instance, ModLibraryEntry entry,
                         boolean keepBackup) {
        try {
            requireContext().startForegroundService(WorkshopLibraryInstaller.buildIntent(
                    requireContext(), entry, instance, keepBackup));
        } catch (Throwable error) {
            showInstallError(error);
        }
    }

    private void showInstallError(Throwable error) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(),
                getString(R.string.workshop_download_center_install_error, error.getMessage()),
                Toast.LENGTH_LONG).show();
    }

    private void showFallbackNotice(DownloadCenterTask task) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_download_center_fallback_title)
                .setMessage(R.string.workshop_download_center_fallback_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.workshop_download_center_fallback_confirm,
                        (dialog, which) -> startFallbackDownload(task))
                .show();
    }

    private void startFallbackDownload(DownloadCenterTask task) {
        Toast.makeText(requireContext(), R.string.workshop_download_center_fallback_started, Toast.LENGTH_SHORT).show();
        GgntwFallbackRuntime.download(requireContext(), task, new GgntwFallbackRuntime.Callback() {
            @Override public void onSuccess() {
                if (isAdded()) Toast.makeText(requireContext(), R.string.workshop_download_center_fallback_complete, Toast.LENGTH_LONG).show();
            }
            @Override public void onError(String message) {
                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showFailureActions(DownloadCenterTask task) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_download_center_auth_title)
                .setMessage(R.string.workshop_download_center_auth_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.workshop_download_center_open_accounts,
                        (dialog, which) -> androidx.navigation.fragment.NavHostFragment.findNavController(this)
                                .navigate(R.id.action_open_workshop_account))
                .setNeutralButton(R.string.workshop_download_center_keep_third_party,
                        (dialog, which) -> showFallbackNotice(task))
                .show();
    }
}
