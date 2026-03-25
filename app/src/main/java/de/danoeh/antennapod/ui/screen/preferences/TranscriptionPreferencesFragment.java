package de.danoeh.antennapod.ui.screen.preferences;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;
import androidx.preference.ListPreference;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.feature.highlight.ModelDownloadManager;
import de.danoeh.antennapod.feature.highlight.WhisperPreferences;
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment;

public class TranscriptionPreferencesFragment extends AnimatedPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_transcription);
        setupPreferences();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar()
                .setTitle(R.string.pref_screen_transcription_title);
    }

    private void setupPreferences() {
        findPreference("pref_whisper_download_model").setOnPreferenceClickListener(preference -> {
            String model = WhisperPreferences.getWhisperModel();
            ModelDownloadManager.downloadModel(requireContext(), model);
            Toast.makeText(requireContext(), R.string.whisper_download_notification_title, Toast.LENGTH_SHORT).show();
            return true;
        });
        findPreference("pref_whisper_delete_model").setOnPreferenceClickListener(preference -> {
            String model = WhisperPreferences.getWhisperModel();
            ModelDownloadManager.deleteModel(requireContext(), model);
            Toast.makeText(requireContext(), R.string.whisper_model_not_downloaded, Toast.LENGTH_SHORT).show();
            return true;
        });
        setWhisperModelRamRecommendation();
    }

    private void setWhisperModelRamRecommendation() {
        ActivityManager am = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memInfo);
        long totalGb = memInfo.totalMem / (1024L * 1024L * 1024L);
        String recommendedModel;
        if (totalGb < 3) {
            recommendedModel = getString(R.string.whisper_model_tiny);
        } else if (totalGb < 6) {
            recommendedModel = getString(R.string.whisper_model_base);
        } else {
            recommendedModel = getString(R.string.whisper_model_base);
        }
        ListPreference modelPref = findPreference("pref_whisper_model");
        if (modelPref != null) {
            modelPref.setSummary(getString(R.string.whisper_model_recommendation, recommendedModel));
        }
    }
}
