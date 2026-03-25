package de.danoeh.antennapod.feature.highlight;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;
import com.google.android.material.snackbar.Snackbar;

import com.bumptech.glide.Glide;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import de.danoeh.antennapod.feature.highlight.databinding.FragmentCaptureBottomSheetBinding;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.feature.highlight.RoamPreferences;

public class CaptureBottomSheet extends BottomSheetDialogFragment {
    public static final String TAG = "CaptureBottomSheet";
    private static final String ARG_FEED_ITEM_ID = "feed_item_id";
    private static final String ARG_POSITION_MS = "position_ms";

    private FragmentCaptureBottomSheetBinding viewBinding;
    private HighlightCaptureViewModel viewModel;
    private FeedItem feedItem;
    private int positionMs;
    private Runnable dismissListener;
    private Runnable openIntegrationsListener;

    public static CaptureBottomSheet newInstance(FeedItem feedItem, int positionMs) {
        CaptureBottomSheet sheet = new CaptureBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_FEED_ITEM_ID, feedItem.getId());
        args.putInt(ARG_POSITION_MS, positionMs);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        positionMs = getArguments() != null ? getArguments().getInt(ARG_POSITION_MS, 0) : 0;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewBinding = FragmentCaptureBottomSheetBinding.inflate(inflater, container, false);
        return viewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HighlightCaptureViewModel.class);

        long feedItemId = getArguments() != null ? getArguments().getLong(ARG_FEED_ITEM_ID, -1) : -1;
        io.reactivex.rxjava3.core.Maybe.fromCallable(() -> de.danoeh.antennapod.storage.database.DBReader.getFeedItem(feedItemId))
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(item -> {
                    feedItem = item;
                    viewModel.init(feedItem, positionMs);
                    updateEpisodeInfo();
                }, throwable -> {});

        setupLookbackSlider();

        viewBinding.butTranscribe.setOnClickListener(v -> viewModel.startTranscription());
        viewBinding.butCancelPreStep.setOnClickListener(v -> dismiss());
        viewBinding.butCancelTranscription.setOnClickListener(v -> {
            viewModel.cancelTranscription();
            dismiss();
        });
        boolean hasReadwise = ReadwisePreferences.hasApiToken();
        boolean hasRoam = RoamPreferences.isConfigured();
        viewBinding.butSaveReadwise.setVisibility(hasReadwise ? View.VISIBLE : View.GONE);
        viewBinding.butSaveRoam.setVisibility(hasRoam ? View.VISIBLE : View.GONE);
        viewBinding.butSetupIntegrations.setVisibility(!hasReadwise && !hasRoam ? View.VISIBLE : View.GONE);

        viewBinding.butSetupIntegrations.setOnClickListener(v -> {
            if (openIntegrationsListener != null) {
                openIntegrationsListener.run();
            }
        });

        viewBinding.butSaveReadwise.setOnClickListener(v -> {
            if (!ReadwisePreferences.hasApiToken()) {
                showTokenMissingDialog();
                return;
            }
            String text = viewBinding.edittxtTranscript.getText() != null
                    ? viewBinding.edittxtTranscript.getText().toString() : "";
            String note = viewBinding.edittxtNote.getText() != null
                    ? viewBinding.edittxtNote.getText().toString() : "";
            viewModel.saveHighlight(text, note);
        });
        viewBinding.butSaveRoam.setOnClickListener(v -> {
            if (!RoamPreferences.isConfigured()) {
                showRoamNotConfiguredDialog();
                return;
            }
            String text = viewBinding.edittxtTranscript.getText() != null
                    ? viewBinding.edittxtTranscript.getText().toString() : "";
            String note = viewBinding.edittxtNote.getText() != null
                    ? viewBinding.edittxtNote.getText().toString() : "";
            viewModel.saveToRoam(text, note);
        });
        viewBinding.butShare.setOnClickListener(v -> {
            String text = viewBinding.edittxtTranscript.getText() != null
                    ? viewBinding.edittxtTranscript.getText().toString() : "";
            shareText(text);
        });
        viewBinding.butCopy.setOnClickListener(v -> {
            String text = viewBinding.edittxtTranscript.getText() != null
                    ? viewBinding.edittxtTranscript.getText().toString() : "";
            copyToClipboard(text);
        });
        viewBinding.butCancelEditing.setOnClickListener(v -> dismiss());

        viewModel.getState().observe(getViewLifecycleOwner(), this::onStateChanged);
        viewModel.getTranscript().observe(getViewLifecycleOwner(), transcript -> {
            viewBinding.edittxtTranscript.setText(transcript);
        });
        viewModel.getSaved().observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(requireContext(), R.string.capture_saved_toast, Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });
        viewModel.getSyncErrorCode().observe(getViewLifecycleOwner(), code -> {
            if (code == null) {
                return;
            }
            if (code == 401) {
                ReadwisePreferences.setApiToken(null);
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.readwise_token_invalid_dialog)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton(R.string.readwise_get_token_button, (d, w) ->
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://readwise.io/access_token"))))
                        .show();
            } else if (code == 429) {
                Integer retryAfter = viewModel.getRetryAfterSec().getValue();
                String msg = retryAfter != null && retryAfter > 0
                        ? getString(R.string.readwise_rate_limited_toast, retryAfter)
                        : getString(R.string.capture_error_toast);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(requireContext(), R.string.capture_error_toast, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(requireContext(), R.string.capture_network_error_toast, Toast.LENGTH_LONG).show();
            }
        });
        viewModel.getIsStreaming().observe(getViewLifecycleOwner(), isStreaming -> {
            if (Boolean.TRUE.equals(isStreaming)) {
                Snackbar.make(viewBinding.getRoot(), R.string.capture_streaming_warning, Snackbar.LENGTH_LONG).show();
            }
        });
        viewModel.getModelNotDownloaded().observe(getViewLifecycleOwner(), notDownloaded -> {
            if (Boolean.TRUE.equals(notDownloaded)) {
                Snackbar.make(viewBinding.getRoot(), R.string.whisper_model_not_downloaded, Snackbar.LENGTH_LONG).show();
            }
        });
        viewModel.getRoamSaved().observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(requireContext(), R.string.capture_saved_roam_toast, Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });
        viewModel.getRoamSyncErrorCode().observe(getViewLifecycleOwner(), code -> {
            if (code == null) {
                return;
            }
            if (code == 401 || code == 403) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.roam_token_invalid_dialog)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                Toast.makeText(requireContext(), R.string.capture_error_roam_toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupLookbackSlider() {
        int initialLookback = viewModel.getLookbackSec();
        int progress = initialLookback - WhisperPreferences.MIN_LOOKBACK_SEC;
        viewBinding.seekLookback.setProgress(progress);
        updateLookbackLabel(initialLookback);
        viewBinding.seekLookback.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sec = progress + WhisperPreferences.MIN_LOOKBACK_SEC;
                viewModel.setLookbackSec(sec);
                updateLookbackLabel(sec);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateLookbackLabel(int sec) {
        viewBinding.txtvLookbackValue.setText(getString(R.string.capture_lookback_label, sec));
    }

    private void updateEpisodeInfo() {
        if (feedItem == null) {
            return;
        }
        String podcastName = feedItem.getFeed() != null ? feedItem.getFeed().getTitle() : "";
        String episodeTitle = feedItem.getTitle() != null ? feedItem.getTitle() : "";
        viewBinding.txtvEpisodeInfo.setText(podcastName + " — " + episodeTitle);
        String imageUrl = feedItem.getImageUrl();
        if (imageUrl != null) {
            Glide.with(this).load(imageUrl).into(viewBinding.imgvEpisodeArtwork);
        }
    }

    private void onStateChanged(HighlightCaptureViewModel.State state) {
        viewBinding.layoutPreStep.setVisibility(state == HighlightCaptureViewModel.State.PRE_STEP ? View.VISIBLE : View.GONE);
        viewBinding.layoutTranscribing.setVisibility(state == HighlightCaptureViewModel.State.TRANSCRIBING ? View.VISIBLE : View.GONE);
        viewBinding.layoutEditing.setVisibility(state == HighlightCaptureViewModel.State.EDITING ? View.VISIBLE : View.GONE);
        if (state == HighlightCaptureViewModel.State.EDITING && feedItem != null) {
            String episodeTitle = feedItem.getTitle() != null ? feedItem.getTitle() : "";
            viewBinding.txtvEpisodeInfo.setText(
                    getString(R.string.capture_timestamp_in_episode, formatPosition(positionMs), episodeTitle));
        }
    }

    private String formatPosition(int ms) {
        int totalSec = ms / 1000;
        int h = totalSec / 3600;
        int m = (totalSec % 3600) / 60;
        int s = totalSec % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
    }

    private void showRoamNotConfiguredDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.roam_not_configured_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showTokenMissingDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.readwise_token_missing_message)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.readwise_get_token_button, (d, w) ->
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://readwise.io/access_token"))))
                .show();
    }

    private void shareText(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, null));
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("highlight", text));
        Toast.makeText(requireContext(), R.string.capture_copied_toast, Toast.LENGTH_SHORT).show();
    }

    public void setDismissListener(Runnable listener) {
        this.dismissListener = listener;
    }

    public void setOpenIntegrationsListener(Runnable listener) {
        this.openIntegrationsListener = listener;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (dismissListener != null) {
            dismissListener.run();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;
    }
}
