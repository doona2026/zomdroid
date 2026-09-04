package com.zomdroid.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.zomdroid.R;
import com.zomdroid.workshop.auth.SteamAccountsSnapshot;
import com.zomdroid.workshop.auth.SteamAccountSummary;
import com.zomdroid.workshop.auth.SteamAuthRuntime;

public class WorkshopAccountFragment extends Fragment {
    private LinearLayout accounts;
    private TextView status;
    private EditText username;
    private EditText password;
    private boolean awaitingConfirmation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View view = inflater.inflate(R.layout.fragment_workshop_account, container, false);
        accounts = view.findViewById(R.id.workshop_account_list);
        status = view.findViewById(R.id.workshop_account_status);
        username = view.findViewById(R.id.workshop_account_username);
        password = view.findViewById(R.id.workshop_account_password);
        view.findViewById(R.id.workshop_account_sign_in).setOnClickListener(v -> signIn());
        load();
        return view;
    }

    private void load() {
        SteamAuthRuntime.loadSnapshot(requireContext(), new Callback());
    }

    private void signIn() {
        String user = username.getText().toString().trim();
        String pass = password.getText().toString();
        if (user.isEmpty() || pass.isEmpty()) {
            status.setText(R.string.workshop_account_missing_credentials);
            return;
        }
        setBusy(true, R.string.workshop_account_signing_in);
        SteamAuthRuntime.signIn(requireContext(), user, pass, null, new Callback());
    }

    private void showGuardDialog(boolean confirmation) {
        View content = getLayoutInflater().inflate(R.layout.dialog_steam_guard, null);
        TextView message = content.findViewById(R.id.steam_guard_message);
        EditText code = content.findViewById(R.id.steam_guard_code);
        message.setText(confirmation ? R.string.workshop_account_confirmation_message : R.string.workshop_account_guard_message);
        code.setVisibility(confirmation ? View.GONE : View.VISIBLE);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.workshop_account_guard_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, (d, w) -> SteamAuthRuntime.cancel(requireContext()))
                .setPositiveButton(confirmation ? R.string.workshop_account_wait : R.string.workshop_account_submit_code, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (confirmation) {
                awaitingConfirmation = true;
                dialog.dismiss();
                setBusy(true, R.string.workshop_account_waiting_confirmation);
                SteamAuthRuntime.waitForConfirmation(requireContext(), new Callback());
            } else if (code.getText().toString().trim().isEmpty()) {
                code.setError(getString(R.string.workshop_account_guard_code_required));
            } else {
                dialog.dismiss();
                setBusy(true, R.string.workshop_account_submitting_code);
                SteamAuthRuntime.submitGuardCode(requireContext(), code.getText().toString().trim(), new Callback());
            }
        }));
        dialog.show();
    }

    private void render(SteamAccountsSnapshot snapshot) {
        accounts.removeAllViews();
        for (SteamAccountSummary account : snapshot.getAccounts()) {
            TextView row = new TextView(requireContext());
            String label = account.getAccountName() + " (" + account.getSteamId() + ")" +
                    (account.isActive() ? "  ✓" : "") +
                    (account.getRequiresReauthentication() ? "  " + getString(R.string.workshop_account_reauth_required) : "");
            row.setText(label);
            row.setTextSize(16);
            row.setPadding(12, 18, 12, 18);
            row.setOnClickListener(v -> SteamAuthRuntime.setActive(requireContext(), account.getAccountId(), new Callback()));
            accounts.addView(row);
            Button remove = new Button(requireContext());
            remove.setText(R.string.workshop_account_remove);
            remove.setOnClickListener(v -> SteamAuthRuntime.remove(requireContext(), account.getAccountId(), new Callback()));
            accounts.addView(remove);
        }
        accounts.scheduleLayoutAnimation();
        if (snapshot.getAccounts().isEmpty()) status.setText(R.string.workshop_account_anonymous);
    }

    private void setBusy(boolean busy, int message) {
        status.setText(message);
        requireView().findViewById(R.id.workshop_account_sign_in).setEnabled(!busy);
    }

    private class Callback implements SteamAuthRuntime.Callback {
        @Override public void onResult(SteamAuthRuntime.Result result) {
            if (!isAdded()) return;
            if ("snapshot".equals(result.getKind()) || "success".equals(result.getKind())) {
                awaitingConfirmation = false;
                setBusy(false, R.string.workshop_account_ready);
                render(result.getSnapshot());
                password.setText("");
            } else if ("guard_code".equals(result.getKind())) {
                setBusy(false, R.string.workshop_account_guard_required);
                showGuardDialog(false);
            } else if ("device_confirmation".equals(result.getKind())) {
                setBusy(false, R.string.workshop_account_confirmation_required);
                showGuardDialog(true);
            } else {
                setBusy(false, R.string.workshop_account_error);
                status.append("\n" + (result.getMessage() == null ? "" : result.getMessage()));
            }
        }
    }
}
