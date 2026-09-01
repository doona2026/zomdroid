/*
 * Adapted for Zomdroid from the Apache-2.0 WorkshopAndroidDownloader core.
 * The Java class remains as a compatibility adapter for the existing screen/state classes.
 */
package com.zomdroid.steam;

import android.content.Context;

import com.zomdroid.workshop.WorkshopFileAccess;
import com.zomdroid.workshop.WorkshopJavaFacade;
import com.zomdroid.workshop.WorkshopPaths;
import com.zomdroid.workshop.WorkshopRuntime;
import com.zomdroid.workshop.core.DownloadedFileInfo;
import com.zomdroid.workshop.download.WorkshopArchiveNaming;
import com.zomdroid.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import kotlinx.coroutines.Job;

/** Bridges the legacy Java Steam download screen to the official Kotlin Workshop engine. */
public final class SteamModDownloader implements Runnable, Cancellable {
    public interface Listener {
        void onProgress(String message);
        default void onPercent(int percent) {}
        void onDone(String message);
    }

    private final Context appContext;
    private final List<Long> workshopIds;
    private final Listener listener;
    private volatile WorkshopJavaFacade facade;
    private volatile Job job;
    private volatile boolean cancelled;
    private volatile int successfulDownloads;
    private volatile long currentWorkshopId;

    public SteamModDownloader(Context context, List<Long> workshopIds, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.workshopIds = new ArrayList<>(workshopIds);
        this.listener = listener;
    }

    public static List<Long> parseWorkshopIds(String raw) {
        Set<Long> ids = new LinkedHashSet<>();
        if (raw == null) return new ArrayList<>(ids);
        for (String token : raw.trim().split("[\\s,]+")) {
            if (token.isEmpty()) continue;
            try {
                long id = Long.parseLong(token);
                if (id > 0) ids.add(id);
            } catch (NumberFormatException ignored) {
                // The UI accepts pasted text; invalid tokens are ignored.
            }
        }
        return new ArrayList<>(ids);
    }

    @Override
    public void run() {
        try {
            facade = WorkshopRuntime.createJavaFacade(appContext);
            job = facade.downloadAnonymous(workshopIds, new WorkshopJavaFacade.Listener() {
                @Override
                public void onItemStarted(long publishedFileId) {
                    currentWorkshopId = publishedFileId;
                }

                @Override
                public void onStateChanged(String state) {
                    progress("Workshop: " + state);
                }

                @Override
                public void onProgress(long writtenBytes, long totalBytes) {
                    if (totalBytes > 0) {
                        int percent = (int) Math.min(100L, writtenBytes * 100L / totalBytes);
                        if (listener != null) listener.onPercent(percent);
                    }
                }

                @Override
                public void onCompleted(List<DownloadedFileInfo> files) {
                    long id = currentWorkshopId;
                    File outputDir = new File(
                            WorkshopPaths.privateStagingRoot(appContext),
                            String.valueOf(id)
                    );
                    String metadata = null;
                    File metadataFile = new File(outputDir, "metadata.json");
                    if (metadataFile.isFile()) {
                        try {
                            metadata = new String(
                                    java.nio.file.Files.readAllBytes(metadataFile.toPath()),
                                    java.nio.charset.StandardCharsets.UTF_8);
                        }
                        catch (Exception ignored) { }
                    }
                    String title = WorkshopArchiveNaming.titleFromMetadata(metadata, id);
                    File destination = new File(
                            WorkshopPaths.completedDownloadsRoot(),
                            WorkshopArchiveNaming.forWorkshop(id, title, System.currentTimeMillis())
                    );
                    try {
                        WorkshopFileAccess.exportCompletedZip(outputDir, files, destination);
                        FileUtils.deleteDirectory(outputDir);
                        successfulDownloads++;
                        progress("Workshop " + id + " saved to " + destination.getAbsolutePath());
                    } catch (Throwable error) {
                        progress("Workshop " + id + " completed but could not be packaged: "
                                + error.getMessage());
                    }
                }

                @Override
                public void onFailed(String message) {
                    progress("Workshop download failed: " + message);
                }

                @Override
                public void onFinished() {
                    if (listener != null) {
                        listener.onDone(cancelled
                                ? "Stopped."
                                : "Mods done: " + successfulDownloads + " downloaded.");
                    }
                }
            });
        } catch (Throwable error) {
            if (listener != null) listener.onDone("Workshop downloader error: " + error.getMessage());
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
        WorkshopJavaFacade currentFacade = facade;
        Job currentJob = job;
        if (currentFacade != null && currentJob != null) currentFacade.cancel(currentJob);
    }

    private void progress(String message) {
        if (listener != null) listener.onProgress(message);
    }

}
