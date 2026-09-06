package com.zomdroid.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.zomdroid.C;
import com.zomdroid.R;
import com.zomdroid.game.GameInstance;
import com.zomdroid.game.GameInstanceManager;
import com.zomdroid.ui.MotionAnimations;
import com.zomdroid.workshop.WorkshopFileAccess;
import com.zomdroid.workshop.data.WorkshopCatalogRuntime;
import com.zomdroid.workshop.data.WorkshopItemDetail;
import com.zomdroid.workshop.install.WorkshopLibraryInstaller;
import com.zomdroid.workshop.library.InstalledMod;
import com.zomdroid.workshop.library.InstalledModFileActionResult;
import com.zomdroid.workshop.library.InstalledModFileActions;
import com.zomdroid.workshop.library.InstalledModQuery;
import com.zomdroid.workshop.library.InstalledModScanResult;
import com.zomdroid.workshop.library.InstalledModScanner;
import com.zomdroid.workshop.library.InstalledModSortOrder;
import com.zomdroid.workshop.library.InstalledModWorkshopMatcher;
import com.zomdroid.workshop.library.ModLibraryEntry;
import com.zomdroid.workshop.library.ModLibraryRepository;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkshopModLibraryFragment extends Fragment {
    private static final String SELECTED_INSTANCE_PREF = "workshopModLibraryInstance";

    private LinearLayout list;
    private LinearLayout installedList;
    private Spinner instanceSpinner;
    private Spinner sortSpinner;
    private EditText installedSearch;
    private TextView installedSummary;
    private TextView installedEmpty;
    private ProgressBar installedProgress;
    private ImageButton installedRefresh;
    private Button installedReturnLauncher;

    private ModLibraryRepository repository;
    private final ExecutorService installPreflightExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService installedScanExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final InstalledModScanner installedModScanner = new InstalledModScanner();

    private List<GameInstance> instances = Collections.emptyList();
    private GameInstance selectedInstance;
    private List<InstalledMod> installedMods = Collections.emptyList();
    private InstalledModScanResult installedScanResult;
    private InstalledModSortOrder selectedSortOrder = InstalledModSortOrder.NAME_ASC;
    private long installedScanGeneration;
    private long installedLastScanAt;
    private boolean suppressInstanceSelection;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_workshop_mod_library, container, false);
        list = view.findViewById(R.id.workshop_library_list);
        installedList = view.findViewById(R.id.workshop_installed_list);
        instanceSpinner = view.findViewById(R.id.workshop_installed_instance_spinner);
        sortSpinner = view.findViewById(R.id.workshop_installed_sort_spinner);
        installedSearch = view.findViewById(R.id.workshop_installed_search_et);
        installedSummary = view.findViewById(R.id.workshop_installed_summary);
        installedEmpty = view.findViewById(R.id.workshop_installed_empty);
        installedProgress = view.findViewById(R.id.workshop_installed_progress);
        installedRefresh = view.findViewById(R.id.workshop_installed_refresh_ib);
        installedReturnLauncher = view.findViewById(R.id.workshop_installed_return_launcher);
        repository = new ModLibraryRepository(requireContext());

        view.findViewById(R.id.workshop_library_cleanup).setOnClickListener(v -> confirmCleanup());
        installedRefresh.setOnClickListener(v -> refreshInstalledMods());
        installedReturnLauncher.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        setupInstalledControls();
        render();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (installedList != null) {
            refreshInstances();
            refreshInstalledMods();
        }
    }

    private void setupInstalledControls() {
        refreshInstances();

        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSortOrder = sortOrderForPosition(position);
                renderInstalledList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedSortOrder = InstalledModSortOrder.NAME_ASC;
            }
        });
        installedSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderInstalledList(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        instanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressInstanceSelection || position < 0 || position >= instances.size()) return;
                selectedInstance = instances.get(position);
                requireContext().getSharedPreferences(C.shprefs.NAME, android.content.Context.MODE_PRIVATE)
                        .edit().putString(SELECTED_INSTANCE_PREF, selectedInstance.getName()).apply();
                installedMods = Collections.emptyList();
                installedScanResult = null;
                renderInstalledStateForInstance();
                refreshInstalledMods();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void refreshInstances() {
        String previousInstanceName = selectedInstance == null ? null : selectedInstance.getName();
        ArrayList<GameInstance> current = GameInstanceManager.requireSingleton().getInstances();
        instances = current == null ? Collections.emptyList() : new ArrayList<>(current);
        ArrayList<String> names = new ArrayList<>();
        for (GameInstance instance : instances) names.add(instance.getName());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        suppressInstanceSelection = true;
        instanceSpinner.setAdapter(adapter);

        if (instances.isEmpty()) {
            selectedInstance = null;
            installedMods = Collections.emptyList();
            installedScanResult = null;
            suppressInstanceSelection = false;
            renderInstalledStateForInstance();
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(
                C.shprefs.NAME, android.content.Context.MODE_PRIVATE);
        String savedName = prefs.getString(SELECTED_INSTANCE_PREF, null);
        int selectedIndex = 0;
        for (int i = 0; i < instances.size(); i++) {
            if (instances.get(i).getName().equals(savedName)) {
                selectedIndex = i;
                break;
            }
        }
        selectedInstance = instances.get(selectedIndex);
        instanceSpinner.setSelection(selectedIndex, false);
        suppressInstanceSelection = false;
        prefs.edit().putString(SELECTED_INSTANCE_PREF, selectedInstance.getName()).apply();
        if (previousInstanceName == null || !previousInstanceName.equals(selectedInstance.getName())) {
            installedMods = Collections.emptyList();
            installedScanResult = null;
        }
        renderInstalledStateForInstance();
    }

    private InstalledModSortOrder sortOrderForPosition(int position) {
        if (position == 1) return InstalledModSortOrder.LAST_MODIFIED_DESC;
        if (position == 2) return InstalledModSortOrder.LAST_MODIFIED_ASC;
        return InstalledModSortOrder.NAME_ASC;
    }

    private void refreshInstalledMods() {
        if (installedList == null) return;
        if (selectedInstance == null) {
            renderInstalledStateForInstance();
            return;
        }

        final GameInstance requestedInstance = selectedInstance;
        final String requestedInstanceName = requestedInstance.getName();
        final long request = ++installedScanGeneration;
        installedProgress.setVisibility(View.VISIBLE);
        installedEmpty.setVisibility(View.GONE);
        installedReturnLauncher.setVisibility(View.GONE);
        installedSummary.setText(R.string.workshop_installed_scanning);
        installedScanExecutor.execute(() -> {
            try {
                InstalledModScanResult scan = installedModScanner.scan(
                        requestedInstanceName, requestedInstance.getHomePath());
                List<ModLibraryEntry> entries = repository.snapshot().getEntries();
                List<InstalledMod> matched = InstalledModWorkshopMatcher.annotate(scan.getMods(), entries);
                mainHandler.post(() -> {
                    if (!isAdded() || getView() == null || request != installedScanGeneration ||
                            selectedInstance == null || !requestedInstanceName.equals(selectedInstance.getName())) return;
                    installedScanResult = scan;
                    installedMods = matched;
                    installedLastScanAt = System.currentTimeMillis();
                    installedProgress.setVisibility(View.GONE);
                    renderInstalledStateForInstance();
                });
            } catch (Throwable error) {
                mainHandler.post(() -> {
                    if (!isAdded() || getView() == null || request != installedScanGeneration ||
                            selectedInstance == null || !requestedInstanceName.equals(selectedInstance.getName())) return;
                    installedProgress.setVisibility(View.GONE);
                    installedSummary.setText(getString(
                            R.string.workshop_installed_scan_failed, error.getMessage()));
                    installedEmpty.setText(R.string.workshop_installed_retry);
                    installedEmpty.setVisibility(View.VISIBLE);
                    installedReturnLauncher.setVisibility(View.GONE);
                    renderInstalledList();
                });
            }
        });
    }

    private void renderInstalledStateForInstance() {
        if (selectedInstance == null) {
            instanceSpinner.setVisibility(View.GONE);
            installedSummary.setText(R.string.workshop_installed_no_instance);
            installedEmpty.setText(R.string.workshop_installed_return_launcher);
            installedEmpty.setVisibility(View.VISIBLE);
            installedReturnLauncher.setVisibility(View.VISIBLE);
            installedProgress.setVisibility(View.GONE);
            installedList.removeAllViews();
            installedList.setVisibility(View.GONE);
            return;
        }

        instanceSpinner.setVisibility(View.VISIBLE);
        installedReturnLauncher.setVisibility(View.GONE);
        installedList.setVisibility(View.VISIBLE);
        if (installedScanResult == null) {
            installedList.removeAllViews();
            installedEmpty.setVisibility(View.GONE);
            installedSummary.setText(R.string.workshop_installed_scanning);
        } else if (!installedScanResult.getModsDirectoryExists()) {
            installedList.removeAllViews();
            installedSummary.setText(getString(R.string.workshop_installed_summary_directory_missing,
                    selectedInstance.getName()));
            installedEmpty.setText(R.string.workshop_installed_missing_directory);
            installedEmpty.setVisibility(View.VISIBLE);
        } else {
            String scannedAt = installedLastScanAt <= 0L ? "-" : formatDate(installedLastScanAt);
            installedSummary.setText(getString(R.string.workshop_installed_summary_format,
                    selectedInstance.getName(), installedMods.size(),
                    installedScanResult.getIgnoredDirectoryCount(), scannedAt));
            renderInstalledList();
        }
    }

    private void renderInstalledList() {
        if (installedList == null || selectedInstance == null || installedScanResult == null ||
                !installedScanResult.getModsDirectoryExists()) return;
        List<InstalledMod> visible = InstalledModQuery.filterAndSort(
                installedMods, installedSearch == null ? "" : installedSearch.getText().toString(), selectedSortOrder);
        installedList.setTag(R.id.motion_content_animated, null);
        installedList.removeAllViews();
        if (visible.isEmpty()) {
            installedEmpty.setText(installedMods.isEmpty()
                    ? R.string.workshop_installed_empty
                    : R.string.workshop_installed_no_matches);
            installedEmpty.setVisibility(View.VISIBLE);
            return;
        }
        installedEmpty.setVisibility(View.GONE);
        for (InstalledMod mod : visible) addInstalledMod(mod);
        installedList.post(() -> MotionAnimations.animateFirstVisibleChildren(installedList));
    }

    private void addInstalledMod(InstalledMod mod) {
        View row = getLayoutInflater().inflate(R.layout.item_installed_mod, installedList, false);
        ImageView icon = row.findViewById(R.id.installed_mod_icon);
        icon.setImageResource(R.drawable.mt_icon_mods);
        if (mod.getThumbnailPath() != null) {
            Uri imageUri = Uri.fromFile(new File(mod.getThumbnailPath()));
            icon.setImageURI(imageUri);
        }
        ((TextView) row.findViewById(R.id.installed_mod_title)).setText(mod.getName());
        ((TextView) row.findViewById(R.id.installed_mod_meta)).setText(getString(
                R.string.workshop_installed_meta_format,
                mod.getModId() == null ? getString(R.string.workshop_installed_id_missing) : mod.getModId(),
                android.text.format.Formatter.formatFileSize(requireContext(), mod.getSizeBytes()),
                formatDate(mod.getLastModifiedEpochMillis())));
        ((TextView) row.findViewById(R.id.installed_mod_path)).setText(mod.getRelativePath());
        ((TextView) row.findViewById(R.id.installed_mod_status)).setText(installedStatus(mod));
        row.findViewById(R.id.installed_mod_card).setOnClickListener(v -> showInstalledModDetails(mod));
        row.findViewById(R.id.installed_mod_more_ib).setOnClickListener(v -> showInstalledModMenu(v, mod));
        installedList.addView(row);
    }

    private String installedStatus(InstalledMod mod) {
        ArrayList<String> status = new ArrayList<>();
        if (!mod.getMetadataComplete()) status.add(getString(R.string.workshop_installed_incomplete));
        if (mod.getDuplicateModId()) status.add(getString(R.string.workshop_installed_duplicate_id));
        if (mod.getMatchedWorkshopId() != null) {
            status.add(getString(R.string.workshop_installed_workshop_format,
                    mod.getMatchedWorkshopTitle(), mod.getMatchedWorkshopId()));
        } else {
            status.add(getString(R.string.workshop_installed_unmatched));
        }
        return TextUtils.join(" · ", status);
    }

    private String formatDate(long epochMillis) {
        if (epochMillis <= 0L) return "-";
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                .format(new Date(epochMillis));
    }

    private void showInstalledModMenu(View anchor, InstalledMod mod) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.menu_installed_mod, popup.getMenu());
        popup.getMenu().findItem(R.id.action_installed_mod_workshop)
                .setVisible(mod.getMatchedWorkshopId() != null);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_installed_mod_details) showInstalledModDetails(mod);
            else if (id == R.id.action_installed_mod_workshop) openWorkshopDetails(mod);
            else if (id == R.id.action_installed_mod_open_folder) openInstalledModFolder(mod);
            else if (id == R.id.action_installed_mod_delete) confirmInstalledModDelete(mod);
            else return false;
            return true;
        });
        popup.show();
    }

    private void showInstalledModDetails(InstalledMod mod) {
        StringBuilder details = new StringBuilder();
        appendDetail(details, R.string.workshop_installed_details_instance, mod.getInstanceName());
        appendDetail(details, R.string.workshop_installed_details_folder, mod.getRootPath());
        appendDetail(details, R.string.workshop_installed_details_relative_path, mod.getRelativePath());
        appendDetail(details, R.string.workshop_installed_details_id,
                mod.getModId() == null ? getString(R.string.workshop_installed_id_missing) : mod.getModId());
        appendDetail(details, R.string.workshop_installed_details_size,
                android.text.format.Formatter.formatFileSize(requireContext(), mod.getSizeBytes()));
        appendDetail(details, R.string.workshop_installed_details_modified, formatDate(mod.getLastModifiedEpochMillis()));
        if (!mod.getDescription().isBlank()) {
            appendDetail(details, R.string.workshop_installed_details_description, mod.getDescription());
        }
        if (!mod.getInfoFields().isEmpty()) {
            details.append('\n').append(getString(R.string.workshop_installed_details_mod_info)).append('\n');
            for (Map.Entry<String, String> field : mod.getInfoFields().entrySet()) {
                details.append(field.getKey()).append(" = ").append(field.getValue()).append('\n');
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(mod.getName())
                .setMessage(details.toString().trim())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void appendDetail(StringBuilder builder, int labelId, String value) {
        builder.append(getString(labelId)).append(": ").append(value).append('\n');
    }

    private void openWorkshopDetails(InstalledMod mod) {
        Long workshopId = mod.getMatchedWorkshopId();
        if (workshopId == null) return;
        ModLibraryEntry entry = repository.snapshot().getEntries().stream()
                .filter(candidate -> candidate.getPublishedFileId() == workshopId)
                .findFirst().orElse(null);
        if (entry == null) {
            Toast.makeText(requireContext(), R.string.workshop_installed_unmatched, Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle args = new Bundle();
        args.putInt("app_id", (int) entry.getAppId());
        args.putLong("published_file_id", entry.getPublishedFileId());
        args.putString("title", entry.getTitle());
        args.putString("author", "");
        args.putString("preview_url", entry.getPreviewUrl());
        args.putString("description", entry.getDescription());
        NavHostFragment.findNavController(this).navigate(
                R.id.workshop_detail_fragment, args, MotionAnimations.forwardNavOptions());
    }

    private void openInstalledModFolder(InstalledMod mod) {
        if (tryOpenDirectory(mod.getRootPath())) return;
        File modsDirectory = new File(selectedInstance.getHomePath(), "Zomboid/mods");
        if (tryOpenDirectory(modsDirectory.getPath())) return;
        Toast.makeText(requireContext(), getString(
                R.string.workshop_installed_open_folder_failed, mod.getRootPath()), Toast.LENGTH_LONG).show();
    }

    private boolean tryOpenDirectory(String path) {
        try {
            Uri folderUri = DocumentsContract.buildDocumentUri(C.STORAGE_PROVIDER_AUTHORITY, path);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void confirmInstalledModDelete(InstalledMod mod) {
        String message = getString(R.string.workshop_installed_delete_message_format,
                mod.getName(), mod.getInstanceName(), mod.getRootPath());
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_installed_delete_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.workshop_installed_action_delete,
                        (dialog, which) -> deleteInstalledMod(mod))
                .show();
    }

    private void deleteInstalledMod(InstalledMod mod) {
        if (selectedInstance == null) return;
        final GameInstance instance = selectedInstance;
        final List<InstalledMod> scanned = new ArrayList<>(installedMods);
        final File modsDirectory = new File(instance.getHomePath(), "Zomboid/mods");
        installedScanExecutor.execute(() -> {
            InstalledModFileActionResult result = InstalledModFileActions.delete(modsDirectory, mod, scanned);
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null || selectedInstance != instance) return;
                if (result.getSuccess()) {
                    Toast.makeText(requireContext(), getString(
                            R.string.workshop_installed_delete_success, mod.getName()), Toast.LENGTH_SHORT).show();
                    refreshInstalledMods();
                } else {
                    Toast.makeText(requireContext(), getString(
                            R.string.workshop_installed_delete_failed, result.getMessage()), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        installedScanGeneration++;
        MotionAnimations.cancel(list, installedList);
        list = null;
        installedList = null;
        instanceSpinner = null;
        sortSpinner = null;
        installedSearch = null;
        installedSummary = null;
        installedEmpty = null;
        installedProgress = null;
        installedRefresh = null;
        installedReturnLauncher = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        installPreflightExecutor.shutdownNow();
        installedScanExecutor.shutdownNow();
        super.onDestroy();
    }

    private void render() {
        renderSharedLibrary();
        renderInstalledStateForInstance();
    }

    private void renderSharedLibrary() {
        list.removeAllViews();
        List<ModLibraryEntry> entries = repository.snapshot().getEntries();
        if (entries.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.workshop_library_empty);
            list.addView(empty);
            return;
        }
        for (ModLibraryEntry entry : entries) addEntry(entry);
        list.post(() -> MotionAnimations.animateFirstVisibleChildren(list));
    }

    private void addEntry(ModLibraryEntry entry) {
        View row = getLayoutInflater().inflate(R.layout.item_workshop_library, list, false);
        ((TextView) row.findViewById(R.id.workshop_library_title)).setText(entry.getTitle());
        ((TextView) row.findViewById(R.id.workshop_library_meta)).setText(
                getString(R.string.workshop_library_meta, entry.getPublishedFileId(), entry.getSource(), entry.getInstalledInstances().size()));
        ((TextView) row.findViewById(R.id.workshop_library_description)).setText(entry.getDescription());
        row.findViewById(R.id.workshop_library_more_ib).setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(requireContext(), v);
            popupMenu.getMenuInflater().inflate(R.menu.menu_workshop_library, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_workshop_library_install) chooseInstance(entry);
                else if (itemId == R.id.action_workshop_library_check_update) checkUpdate(entry);
                else if (itemId == R.id.action_workshop_library_share) share(entry);
                else if (itemId == R.id.action_workshop_library_delete) confirmDelete(entry);
                else return false;
                return true;
            });
            popupMenu.show();
        });
        list.addView(row);
    }

    private void share(ModLibraryEntry entry) {
        try {
            Uri uri = WorkshopFileAccess.contentUriForCompletedFile(
                    requireContext(), new File(entry.getCompletedPath()));
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
        List<GameInstance> available = GameInstanceManager.requireSingleton().getInstances();
        if (available == null || available.isEmpty()) {
            Toast.makeText(requireContext(), R.string.workshop_download_center_no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[available.size()];
        for (int i = 0; i < available.size(); i++) names[i] = available.get(i).getName();
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_library_choose_instance)
                .setItems(names, (dialog, which) -> confirmInstall(entry, available.get(which)))
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
                mainHandler.post(() -> { if (isAdded()) showInstallError(error); });
            }
        });
    }

    private void showOverwriteDialog(ModLibraryEntry entry, GameInstance instance, List<String> existing) {
        String names = TextUtils.join(", ", existing);
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
            Toast.makeText(requireContext(), getString(
                    R.string.workshop_download_center_install_error, error.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void showInstallError(Throwable error) {
        Toast.makeText(requireContext(), getString(
                R.string.workshop_download_center_install_error, error.getMessage()), Toast.LENGTH_LONG).show();
    }

    private void checkUpdate(ModLibraryEntry entry) {
        if (!repository.needsUpdateCheck(entry, System.currentTimeMillis(), 6L * 60L * 60L * 1000L)) {
            Toast.makeText(requireContext(), R.string.workshop_library_check_later, Toast.LENGTH_SHORT).show();
            return;
        }
        repository.markChecked(entry, System.currentTimeMillis());
        WorkshopCatalogRuntime.detail(requireContext(), WorkshopCatalogRuntime.item(
                (int) entry.getAppId(), entry.getPublishedFileId(), entry.getTitle(), "",
                entry.getPreviewUrl(), entry.getDescription()), true, new WorkshopCatalogRuntime.DetailCallback() {
            @Override public void onSuccess(WorkshopItemDetail detail) {
                if (!isAdded()) return;
                if (detail.getTimeUpdatedEpochSeconds() != null &&
                        (entry.getUpdatedAtEpochSeconds() == null || detail.getTimeUpdatedEpochSeconds() > entry.getUpdatedAtEpochSeconds())) {
                    Toast.makeText(requireContext(), R.string.workshop_library_update_available, Toast.LENGTH_LONG).show();
                } else Toast.makeText(requireContext(), R.string.workshop_library_up_to_date, Toast.LENGTH_SHORT).show();
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
                                        entry.getUpdatedAtEpochSeconds() > current.getUpdatedAtEpochSeconds()))) newest.put(key, entry);
                    }
                    int removed = 0;
                    for (ModLibraryEntry entry : newest.values()) {
                        removed += repository.pruneOldVersions(
                                entry.getAppId(), entry.getPublishedFileId(), entry.getVersionKey());
                    }
                    render();
                    Toast.makeText(requireContext(), getString(
                            R.string.workshop_library_cleanup_done, removed), Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
