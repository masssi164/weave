package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import java.util.Arrays;
import java.util.Objects;

/**
 * Explicitly unqualified, adapter-private bridge for legacy external-provider diagnostics.
 *
 * <p>This is not a canonical Files port and must not be used by the native adapter, product
 * facade, or WebDAV controller. Qualified content uses {@code FilesStreamingContentPort} only.</p>
 */
public interface UnqualifiedLegacyFilesContentAdapter {

    LegacyFileContent readLegacy(FileId id);

    FileObject writeLegacy(LegacyFileWrite write);

    record LegacyFileContent(FileObject item, byte[] bytes) {
        public LegacyFileContent {
            if (item == null || item.kind() != Kind.FILE) {
                throw new IllegalArgumentException("legacy content requires a file item");
            }
            bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }

    record LegacyFileWrite(FilePath path, byte[] bytes, String mediaType) {
        public LegacyFileWrite {
            Objects.requireNonNull(path, "path must not be null");
            if (path.root()) {
                throw new IllegalArgumentException("legacy write requires a non-root path");
            }
            bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
            mediaType = mediaType == null || mediaType.isBlank()
                    ? "application/octet-stream"
                    : mediaType.trim();
        }

        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }
}
