package com.massimotter.weave.backend.service.files;

public record DownloadedFile(String filename, String mimeType, byte[] content) {
}
