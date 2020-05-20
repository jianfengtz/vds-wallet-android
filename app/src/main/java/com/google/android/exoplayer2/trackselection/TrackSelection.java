/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.trackselection;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import java.util.List;

/**
 * A track selection consisting of a static subset of selected tracks belonging to a
 * {@link TrackGroup}, and a possibly varying individual selected track from the subset.
 * <p>
 * Tracks belonging to the subset are exposed in decreasing bandwidth order. The individual selected
 * track may change as a result of calling {@link #updateSelectedTrack(long, long, long)}.
 */
public interface TrackSelection {

  /**
   * Factory for {@link TrackSelection} instances.
   */
  interface Factory {

    /**
     * Creates a new selection.
     *
     * @param group The {@link TrackGroup}. Must not be null.
     * @param bandwidthMeter A {@link BandwidthMeter} which can be used to select tracks.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *     null or empty. May be in any order.
     * @return The created selection.
     */
    TrackSelection createTrackSelection(
        TrackGroup group, BandwidthMeter bandwidthMeter, int... tracks);
  }

  /**
   * Enables the track selection.
   * <p>
   * This method may not be called when the track selection is already enabled.
   */
  void enable();

  /**
   * Disables this track selection.
   * <p>
   * This method may only be called when the track selection is already enabled.
   */
  void disable();

  /**
   * Returns the {@link TrackGroup} to which the selected tracks belong.
   */
  TrackGroup getTrackGroup();

  // Static subset of selected tracks.

  /**
   * Returns the number of tracks in the selection.
   */
  int length();

  /**
   * Returns the format of the track at a given index in the selection.
   *
   * @param index The index in the selection.
   * @return The format of the selected track.
   */
  Format getFormat(int index);

  /**
   * Returns the index in the track group of the track at a given index in the selection.
   *
   * @param index The index in the selection.
   * @return The index of the selected track.
   */
  int getIndexInTrackGroup(int index);

  /**
   * Returns the index in the selection of the track with the specified format. The format is
   * located by identity so, for example, {@code selection.indexOf(selection.getFormat(index)) ==
   * index} even if multiple selected tracks have formats that contain the same values.
   *
   * @param format The format.
   * @return The index in the selection, or {@link C#INDEX_UNSET} if the track with the specified
   *     format is not part of the selection.
   */
  int indexOf(Format format);

  /**
   * Returns the index in the selection of the track with the specified index in the track group.
   *
   * @param indexInTrackGroup The index in the track group.
   * @return The index in the selection, or {@link C#INDEX_UNSET} if the track with the specified
   *     index is not part of the selection.
   */
  int indexOf(int indexInTrackGroup);

  // Individual selected track.

  /**
   * Returns the {@link Format} of the individual selected track.
   */
  Format getSelectedFormat();

  /**
   * Returns the index in the track group of the individual selected track.
   */
  int getSelectedIndexInTrackGroup();

  /**
   * Returns the index of the selected track.
   */
  int getSelectedIndex();

  /**
   * Returns the reason for the current track selection.
   */
  int getSelectionReason();

  /** Returns optional data associated with the current track selection. */
  @Nullable Object getSelectionData();

  // Adaptation.

  /**
   * Called to notify the selection of the current playback speed. The playback speed may affect
   * adaptive track selection.
   *
   * @param speed The playback speed.
   */
  void onPlaybackSpeed(float speed);

  /**
   * Updates the selected track.
   * <p>
   * This method may only be called when the selection is enabled.
   *
   * @param playbackPositionUs The current playback position in microseconds. If playback of the
   *     period to which this track selection belongs has not yet started, the value will be the
   *     starting position in the period minus the duration of any media in previous periods still
   *     to be played.
   * @param bufferedDurationUs The duration of media currently buffered from the current playback
   *     position, in microseconds. Note that the next load position can be calculated as
   *     {@code (playbackPositionUs + bufferedDurationUs)}.
   * @param availableDurationUs The duration of media available for buffering from the current
   *     playback position, in microseconds, or {@link C#TIME_UNSET} if media can be buffered
   *     to the end of the current period. Note that if not set to {@link C#TIME_UNSET}, the
   *     position up to which media is available for buffering can be calculated as
   *     {@code (playbackPositionUs + availableDurationUs)}.
   */
  void updateSelectedTrack(long playbackPositionUs, long bufferedDurationUs,
      long availableDurationUs);

  /**
   * May be called periodically by sources that load media in discrete {@link MediaChunk}s and
   * support discarding of buffered chunks in order to re-buffer using a different selected track.
   * Returns the number of chunks that should be retained in the queue.
   * <p>
   * To avoid excessive re-buffering, implementations should normally return the size of the queue.
   * An example of a case where a smaller value may be returned is if network conditions have
   * improved dramatically, allowing chunks to be discarded and re-buffered in a track of
   * significantly higher quality. Discarding chunks may allow faster switching to a higher quality
   * track in this case. This method may only be called when the selection is enabled.
   *
   * @param playbackPositionUs The current playback position in microseconds. If playback of the
   *     period to which this track selection belongs has not yet started, the value will be the
   *     starting position in the period minus the duration of any media in previous periods still
   *     to be played.
   * @param queue The queue of buffered {@link MediaChunk}s. Must not be modified.
   * @return The number of chunks to retain in the queue.
   */
  int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue);

  /**
   * Attempts to blacklist the track at the specified index in the selection, making it ineligible
   * for selection by calls to {@link #updateSelectedTrack(long, long, long)} for the specified
   * period of time. Blacklisting will fail if all other tracks are currently blacklisted. If
   * blacklisting the currently selected track, note that it will remain selected until the next
   * call to {@link #updateSelectedTrack(long, long, long)}.
   * <p>
   * This method may only be called when the selection is enabled.
   *
   * @param index The index of the track in the selection.
   * @param blacklistDurationMs The duration of time for which the track should be blacklisted, in
   *     milliseconds.
   * @return Whether blacklisting was successful.
   */
  boolean blacklist(int index, long blacklistDurationMs);

}
