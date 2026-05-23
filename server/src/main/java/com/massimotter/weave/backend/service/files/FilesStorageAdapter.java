package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.model.files.CreateFolderRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.model.files.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FilesStorageAdapter {

    boolean isConfigured();

    FileListResponse list(String path);

    FileItemResponse createFolder(CreateFolderRequest request);

    FileUploadResponse upload(String parentPath, MultipartFile file);

    DownloadedFile download(String id);

    void delete(String id);
}
