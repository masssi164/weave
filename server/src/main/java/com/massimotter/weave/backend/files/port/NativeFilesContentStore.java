package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.ContentProfile;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Egress;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Ingress;
import com.massimotter.weave.backend.files.port.ReplayableFileContent.StreamFactory;
import java.time.Instant;

/** Adapter-private durable ingress and transient verified-egress storage seam. */
public interface NativeFilesContentStore {

    ContentProfile contentProfile();

    void requireStreamingReady();

    Ingress receive(Long declaredLength, String mediaType, StreamFactory requestBody);

    Egress verify(VerifiedFileRead read);

    /** Reopens the exact same-generation ingress retained after a committed mutation plan. */
    Ingress reopen(String operationRef);

    /** Removes retained ingress only after relational protection has ended. */
    boolean remove(String operationRef);

    /** Performs one bounded, age-fenced private-content scavenging pass. */
    void scavengeBounded(Instant olderThan, int limit);
}
