package com.zomdroid.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.List;
import java.util.Locale;

import kotlinx.coroutines.Job;

/** Displays and controls persistent Workshop tasks; downloading itself belongs to the service. */
public class WorkshopDownloadCenterFragment extends Fragment {
    private LinearLayout tasksContainer;
    private DownloadCenterManager manager;
    private ModLibraryRepository library;
    private Job observation;

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
                if (!isAdded()) return;
                android.app.Activity activity = getActivity();
                if (activity != null) activity.runOnUiThread(() -> {
                    if (tasksContainer != null) render(tasks);
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (observation != null) observation.cancel(null);
        observation = null;
        tasksContainer = null;
        super.onDestroyView();
    }

    private void render(List<DownloadCenterTask> tasks) {
        tasksContainer.removeAllViews();
        if (tasks.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.workshop_download_center_empty);
            empty.setPadding(0, 24, 0, 24);
            tasksContainer.addView(empty);
            return;
        }
        for (DownloadCenterTask task : tasks) addTaskView(task);
    }

    private void addTaskView(DownloadCenterTask task) {
        View row = getLayoutInflater().inflate(
                R.layout.item_workshop_download_task, tasksContainer, false);
        TextView title = row.findViewById(R.id.workshop_task_title);
        TextView status = row.findViewById(R.id.workshop_task_status);
        TextView log = row.findViewById(R.id.workshop_task_log);
        ProgressBar progress = row.findViewById(R.id.workshop_task_progress);
        title.setText(task.getTitle() == null || task.getTitle().trim().isEmpty()
                ? getString(R.string.workshop_download_center_item, task.getPublishedFileId())
                : task.getTitle());
        status.setText(formatStatus(task));
        log.setText(String.join("\n", task.getLogs()));
        if (task.getTotalBytes() != null && task.getTotalBytes() > 0) {
            progress.setIndeterminate(false);
            progress.setProgress((int) Math.min(100L,
                    task.getWrittenBytes() * 100L / task.getTotalBytes()));
        } else {
            progress.setIndeterminate(task.getState() == DownloadCenterTaskState.Running);
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
        tasksContainer.addView(row);
    }

    private String formatStatus(DownloadCenterTask task) {
        if (task.getState() == DownloadCenterTaskState.Failed && task.getErrorMessage() != null) {
            return task.getState().name() + " — " + task.getErrorMessage();
        }
        return String.format(Locale.US, "%s · %s", task.getState().name(), task.getPhase());
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
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_library_overwrite_title)
                .setMessage(R.string.workshop_library_overwrite_message)
                .setNegativeButton(R.string.workshop_library_install_without_backup,
                        (dialog, which) -> install(task, instance, false))
                .setPositiveButton(R.string.workshop_library_install_with_backup,
                        (dialog, which) -> install(task, instance, true))
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void install(DownloadCenterTask task, GameInstance instance, boolean keepBackup) {
        try {
            if (task.getOutputPath() == null) throw new IllegalStateException("Completed archive path is missing");
            ModLibraryEntry entry = library.entriesFor(task.getAppId(), task.getPublishedFileId()).stream()
                    .filter(candidate -> task.getOutputPath() != null && task.getOutputPath().equals(candidate.getCompletedPath()))
                    .findFirst().orElseGet(() -> library.recordCompleted(
                            task.getAppId(), task.getPublishedFileId(), task.getTitle() == null ? "" : task.getTitle(), "", "", null,
                            new File(task.getOutputPath()), java.util.Collections.emptyList(), "steam"));
            requireContext().startForegroundService(WorkshopLibraryInstaller.buildIntent(
                    requireContext(), entry, instance, keepBackup));
        } catch (Throwable error) {
            Toast.makeText(requireContext(),
                    getString(R.string.workshop_download_center_install_error, error.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
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
