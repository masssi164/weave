package com.massimotter.weave.backend.service.files;

public record FilesStorageReadiness(boolean available, String supportSafeCode) {

    public static FilesStorageReadiness ready() {
        return new FilesStorageReadiness(true, "files-storage-ready");
    }

    public static FilesStorageReadiness degraded(String supportSafeCode) {
        String code = supportSafeCode == null || supportSafeCode.isBlank()
                ? "files-storage-degraded"
                : supportSafeCode.trim();
        return new FilesStorageReadiness(false, code);
    }
}
