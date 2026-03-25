package de.danoeh.antennapod.ui.screen.preferences;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.feature.highlight.ReadwisePreferences;
import de.danoeh.antennapod.feature.highlight.ReadwiseSyncService;
import de.danoeh.antennapod.feature.highlight.RoamPreferences;
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment;

public class IntegrationsPreferencesFragment extends AnimatedPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_integrations);
        setupPreferences();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar()
                .setTitle(R.string.pref_screen_integrations_title);
    }

    private void setupPreferences() {
        findPreference("pref_readwise_token").setOnPreferenceClickListener(preference -> {
            showTokenDialog();
            return true;
        });
        findPreference("pref_readwise_validate").setOnPreferenceClickListener(preference -> {
            validateToken();
            return true;
        });
        findPreference("pref_roam_graph").setOnPreferenceClickListener(preference -> {
            showRoamGraphDialog();
            return true;
        });
        findPreference("pref_roam_token").setOnPreferenceClickListener(preference -> {
            showRoamTokenDialog();
            return true;
        });
    }

    private void showTokenDialog() {
        EditText editText = new EditText(requireContext());
        String current = ReadwisePreferences.getApiToken();
        if (current != null) {
            editText.setText(current);
        }
        editText.setHint(R.string.readwise_token_hint);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_readwise_token_title)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        ReadwisePreferences.setApiToken(editText.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRoamGraphDialog() {
        EditText editText = new EditText(requireContext());
        String current = RoamPreferences.getGraphName();
        if (current != null) {
            editText.setText(current);
        }
        editText.setHint(R.string.roam_graph_name_hint);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_roam_graph_title)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        RoamPreferences.setGraphName(editText.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRoamTokenDialog() {
        EditText editText = new EditText(requireContext());
        String current = RoamPreferences.getApiToken();
        if (current != null) {
            editText.setText(current);
        }
        editText.setHint(R.string.roam_api_token_hint);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_roam_token_title)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        RoamPreferences.setApiToken(editText.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void validateToken() {
        String token = ReadwisePreferences.getApiToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(requireContext(), R.string.readwise_token_missing_message, Toast.LENGTH_SHORT).show();
            return;
        }
        ReadwiseSyncService service = new ReadwiseSyncService(token);
        new Thread(() -> service.validateToken(new ReadwiseSyncService.TokenValidationCallback() {
            @Override
            public void onValid() {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), R.string.readwise_token_valid_toast, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onInvalid() {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), R.string.readwise_token_invalid_toast, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onNetworkError(Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), R.string.readwise_token_invalid_toast, Toast.LENGTH_SHORT).show());
            }
        })).start();
    }
}
