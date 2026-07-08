package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface FilesStorageAdapter {

    boolean isConfigured();

    default FilesStorageReadiness readinessProbe() {
        return isConfigured()
                ? FilesStorageReadiness.ready()
                : FilesStorageReadiness.degraded("files-storage-not-configured");
    }

    FileListResponse list(String path);

    default VersionedFileListResponse listWithVersionTokens(String path) {
        FileListResponse listing = list(path);
        return new VersionedFileListResponse(listing, null, Map.of());
    }

    default String versionToken(String path) {
        return null;
    }

    FileItemResponse createFolder(CreateFolderRequest request);

    FileUploadResponse upload(String parentPath, MultipartFile file);

    default FileItemResponse put(String path, byte[] content, String mimeType) {
        throw new UnsupportedOperationException("WebDAV PUT is not implemented by this storage adapter");
    }

    DownloadedFile download(String id);

    void delete(String id);
}
