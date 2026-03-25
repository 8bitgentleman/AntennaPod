package de.danoeh.antennapod.feature.highlight;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import de.danoeh.antennapod.feature.highlight.databinding.FragmentHighlightsBinding;
import de.danoeh.antennapod.feature.highlight.databinding.ItemHighlightBinding;
import de.danoeh.antennapod.feature.highlight.databinding.ItemHighlightEpisodeHeaderBinding;
import de.danoeh.antennapod.feature.highlight.databinding.ItemHighlightPodcastHeaderBinding;
import de.danoeh.antennapod.model.feed.PodcastHighlight;
import de.danoeh.antennapod.storage.database.DBReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class HighlightsFragment extends Fragment {
    public static final String TAG = "HighlightsFragment";

    private static final int TYPE_PODCAST = 0;
    private static final int TYPE_EPISODE = 1;
    private static final int TYPE_HIGHLIGHT = 2;

    private FragmentHighlightsBinding viewBinding;
    private GroupedHighlightsAdapter adapter;
    private Disposable disposable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewBinding = FragmentHighlightsBinding.inflate(inflater, container, false);
        return viewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewBinding.toolbar.setTitle(R.string.highlights_label);
        viewBinding.toolbar.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        adapter = new GroupedHighlightsAdapter();
        viewBinding.recyclerViewHighlights.setLayoutManager(new LinearLayoutManager(requireContext()));
        viewBinding.recyclerViewHighlights.setAdapter(adapter);

        loadHighlights();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHighlights();
    }

    private void loadHighlights() {
        disposable = Maybe.fromCallable(DBReader::getPodcastHighlights)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(highlights -> adapter.setItems(buildGroupedList(highlights)),
                        throwable -> {});
    }

    private List<Object> buildGroupedList(List<PodcastHighlight> highlights) {
        LinkedHashMap<String, LinkedHashMap<String, List<PodcastHighlight>>> grouped =
                new LinkedHashMap<>();

        for (PodcastHighlight h : highlights) {
            String podcast = h.getPodcastName() != null ? h.getPodcastName() : "";
            String episode = h.getEpisodeTitle() != null ? h.getEpisodeTitle() : "";
            if (!grouped.containsKey(podcast)) {
                grouped.put(podcast, new LinkedHashMap<>());
            }
            LinkedHashMap<String, List<PodcastHighlight>> episodeMap = grouped.get(podcast);
            if (!episodeMap.containsKey(episode)) {
                episodeMap.put(episode, new ArrayList<>());
            }
            episodeMap.get(episode).add(h);
        }

        List<Object> items = new ArrayList<>();
        for (Map.Entry<String, LinkedHashMap<String, List<PodcastHighlight>>> podcastEntry
                : grouped.entrySet()) {
            String podcastName = podcastEntry.getKey();
            String coverUrl = null;
            List<PodcastHighlight> allForPodcast = new ArrayList<>();
            for (List<PodcastHighlight> episodeHighlights : podcastEntry.getValue().values()) {
                allForPodcast.addAll(episodeHighlights);
            }
            if (!allForPodcast.isEmpty()) {
                coverUrl = allForPodcast.get(0).getCoverImageUrl();
            }
            items.add(new PodcastHeader(podcastName, coverUrl));

            for (Map.Entry<String, List<PodcastHighlight>> episodeEntry
                    : podcastEntry.getValue().entrySet()) {
                items.add(new EpisodeHeader(episodeEntry.getKey()));
                List<PodcastHighlight> episodeHighlights = episodeEntry.getValue();
                Collections.sort(episodeHighlights,
                        (a, b) -> Integer.compare(a.getPositionSec(), b.getPositionSec()));
                items.addAll(episodeHighlights);
            }
        }
        return items;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (disposable != null) {
            disposable.dispose();
        }
        viewBinding = null;
    }

    static class PodcastHeader {
        final String name;
        final String coverUrl;

        PodcastHeader(String name, String coverUrl) {
            this.name = name;
            this.coverUrl = coverUrl;
        }
    }

    static class EpisodeHeader {
        final String title;

        EpisodeHeader(String title) {
            this.title = title;
        }
    }

    private class GroupedHighlightsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Object> items = Collections.emptyList();

        void setItems(List<Object> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object item = items.get(position);
            if (item instanceof PodcastHeader) return TYPE_PODCAST;
            if (item instanceof EpisodeHeader) return TYPE_EPISODE;
            return TYPE_HIGHLIGHT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_PODCAST) {
                return new PodcastViewHolder(
                        ItemHighlightPodcastHeaderBinding.inflate(inflater, parent, false));
            } else if (viewType == TYPE_EPISODE) {
                return new EpisodeViewHolder(
                        ItemHighlightEpisodeHeaderBinding.inflate(inflater, parent, false));
            } else {
                return new HighlightViewHolder(
                        ItemHighlightBinding.inflate(inflater, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = items.get(position);
            if (holder instanceof PodcastViewHolder) {
                PodcastHeader header = (PodcastHeader) item;
                ((PodcastViewHolder) holder).bind(header);
            } else if (holder instanceof EpisodeViewHolder) {
                EpisodeHeader header = (EpisodeHeader) item;
                ((EpisodeViewHolder) holder).binding.txtvEpisodeTitle.setText(header.title);
            } else if (holder instanceof HighlightViewHolder) {
                PodcastHighlight highlight = (PodcastHighlight) item;
                ((HighlightViewHolder) holder).bind(highlight);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class PodcastViewHolder extends RecyclerView.ViewHolder {
            final ItemHighlightPodcastHeaderBinding binding;

            PodcastViewHolder(ItemHighlightPodcastHeaderBinding b) {
                super(b.getRoot());
                binding = b;
            }

            void bind(PodcastHeader header) {
                binding.txtvPodcastName.setText(header.name);
                if (header.coverUrl != null) {
                    Glide.with(binding.imgvPodcastArtwork)
                            .load(header.coverUrl)
                            .into(binding.imgvPodcastArtwork);
                } else {
                    binding.imgvPodcastArtwork.setImageDrawable(null);
                }
            }
        }

        class EpisodeViewHolder extends RecyclerView.ViewHolder {
            final ItemHighlightEpisodeHeaderBinding binding;

            EpisodeViewHolder(ItemHighlightEpisodeHeaderBinding b) {
                super(b.getRoot());
                binding = b;
            }
        }

        class HighlightViewHolder extends RecyclerView.ViewHolder {
            final ItemHighlightBinding binding;

            HighlightViewHolder(ItemHighlightBinding b) {
                super(b.getRoot());
                binding = b;
            }

            void bind(PodcastHighlight highlight) {
                binding.txtvHighlightText.setText(highlight.getText());
                binding.txtvTimestamp.setText(formatPosition(highlight.getPositionSec()));
                if (highlight.getSyncedReadwise() == PodcastHighlight.SYNC_PENDING) {
                    binding.txtvSyncStatus.setText(R.string.highlight_pending_label);
                    binding.txtvSyncStatus.setVisibility(View.VISIBLE);
                } else if (highlight.getSyncedReadwise() == PodcastHighlight.SYNC_ERROR) {
                    binding.txtvSyncStatus.setText(R.string.highlight_sync_error_label);
                    binding.txtvSyncStatus.setVisibility(View.VISIBLE);
                } else {
                    binding.txtvSyncStatus.setVisibility(View.GONE);
                }
                binding.getRoot().setOnClickListener(v -> {
                    HighlightEditBottomSheet sheet =
                            HighlightEditBottomSheet.newInstance(highlight.getId());
                    sheet.setOnDismissedCallback(() -> loadHighlights());
                    sheet.show(getChildFragmentManager(), HighlightEditBottomSheet.TAG);
                });
            }
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
}
