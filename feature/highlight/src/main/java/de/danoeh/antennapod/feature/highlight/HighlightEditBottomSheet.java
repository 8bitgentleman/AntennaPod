package de.danoeh.antennapod.feature.highlight;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import de.danoeh.antennapod.feature.highlight.databinding.FragmentHighlightEditBottomSheetBinding;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.PodcastHighlight;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class HighlightEditBottomSheet extends BottomSheetDialogFragment {
    public static final String TAG = "HighlightEditBottomSheet";
    private static final String ARG_HIGHLIGHT_ID = "highlight_id";

    private FragmentHighlightEditBottomSheetBinding viewBinding;
    private PodcastHighlight highlight;
    private Runnable onDismissedCallback;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Disposable loadDisposable;

    public static HighlightEditBottomSheet newInstance(long highlightId) {
        HighlightEditBottomSheet sheet = new HighlightEditBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_HIGHLIGHT_ID, highlightId);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnDismissedCallback(Runnable callback) {
        this.onDismissedCallback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewBinding = FragmentHighlightEditBottomSheetBinding.inflate(inflater, container, false);
        return viewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        long highlightId = getArguments() != null ? getArguments().getLong(ARG_HIGHLIGHT_ID, -1) : -1;

        loadDisposable = Maybe.fromCallable(() -> DBReader.getPodcastHighlight(highlightId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(h -> {
                    highlight = h;
                    populateFields();
                }, throwable -> {});

        viewBinding.butSave.setOnClickListener(v -> saveChanges());
        viewBinding.butDelete.setOnClickListener(v -> deleteHighlight());
        viewBinding.butShare.setOnClickListener(v -> shareText());
        viewBinding.butCopy.setOnClickListener(v -> copyToClipboard());
        viewBinding.butPlayFromHere.setOnClickListener(v -> playFromHere());
    }

    private void populateFields() {
        if (highlight == null) {
            return;
        }
        viewBinding.edittxtText.setText(highlight.getText());
        if (highlight.getNote() != null) {
            viewBinding.edittxtNote.setText(highlight.getNote());
        }
        String date = DateFormat.getDateInstance().format(new Date(highlight.getCapturedAt()));
        String timestamp = formatPosition(highlight.getPositionSec());
        viewBinding.txtvHighlightMeta.setText(
                highlight.getPodcastName() + " — " + highlight.getEpisodeTitle()
                + "\n" + timestamp + " · " + date);
    }

    private void saveChanges() {
        if (highlight == null) {
            return;
        }
        String text = viewBinding.edittxtText.getText() != null
                ? viewBinding.edittxtText.getText().toString() : "";
        String note = viewBinding.edittxtNote.getText() != null
                ? viewBinding.edittxtNote.getText().toString() : "";

        executor.execute(() -> {
            DBWriter.updatePodcastHighlightTextAndNote(highlight.getId(), text, note);
            highlight.setText(text);
            highlight.setNote(note);
            highlight.setSyncedReadwise(PodcastHighlight.SYNC_PENDING);

            String token = ReadwisePreferences.getApiToken();
            if (token != null && !token.isEmpty()) {
                ReadwiseSyncService syncService = new ReadwiseSyncService(token);
                syncService.syncHighlight(highlight, new ReadwiseSyncService.Callback() {
                    @Override
                    public void onSuccess() {
                        DBWriter.updatePodcastHighlightSyncStatus(
                                highlight.getId(), PodcastHighlight.SYNC_SYNCED);
                        showToastOnMain(R.string.highlight_synced_toast);
                    }

                    @Override
                    public void onError(int httpCode, String message, int retryAfterSec) {
                        DBWriter.updatePodcastHighlightSyncStatus(
                                highlight.getId(), PodcastHighlight.SYNC_ERROR);
                        showToastOnMain(R.string.capture_error_toast);
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        DBWriter.updatePodcastHighlightSyncStatus(
                                highlight.getId(), PodcastHighlight.SYNC_ERROR);
                        showToastOnMain(R.string.capture_network_error_toast);
                    }
                });
            } else {
                showToastOnMain(R.string.highlight_saved_toast);
            }
        });
        dismiss();
    }

    private void deleteHighlight() {
        if (highlight == null) {
            return;
        }
        DBWriter.deletePodcastHighlight(highlight.getId());
        Toast.makeText(requireContext(), R.string.highlight_deleted_toast, Toast.LENGTH_SHORT).show();
        dismiss();
    }

    private void shareText() {
        String text = viewBinding.edittxtText.getText() != null
                ? viewBinding.edittxtText.getText().toString() : "";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, null));
    }

    private void copyToClipboard() {
        String text = viewBinding.edittxtText.getText() != null
                ? viewBinding.edittxtText.getText().toString() : "";
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("highlight", text));
        Toast.makeText(requireContext(), R.string.capture_copied_toast, Toast.LENGTH_SHORT).show();
    }

    private void playFromHere() {
        if (highlight == null) {
            return;
        }
        long feedItemId = highlight.getFeedItemId();
        int positionMs = highlight.getPositionSec() * 1000;
        loadDisposable = Maybe.fromCallable(() -> DBReader.getFeedItem(feedItemId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(feedItem -> {
                    FeedMedia media = feedItem.getMedia();
                    if (media == null) {
                        return;
                    }
                    new PlaybackServiceStarter(requireContext(), media)
                            .callEvenIfRunning(true)
                            .start();
                    PlaybackController.bindToMedia3Service(requireContext(),
                            controller -> controller.seekTo(positionMs));
                    dismiss();
                }, throwable -> {});
    }

    private void showToastOnMain(int resId) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show());
        }
    }

    private String formatPosition(int totalSec) {
        int h = totalSec / 3600;
        int m = (totalSec % 3600) / 60;
        int s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.getDefault(), "%d:%02d", m, s);
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissedCallback != null) {
            onDismissedCallback.run();
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
        if (loadDisposable != null) {
            loadDisposable.dispose();
        }
        executor.shutdown();
        viewBinding = null;
    }
}
