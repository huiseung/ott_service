package com.domain.backend.worker.process;

import com.domain.backend.worker.job.ClaimedMediaJob;

public interface MediaTranscoder {

    void transcode(ClaimedMediaJob job, ScratchWorkspace workspace, MediaProbeResult probeResult);
}
