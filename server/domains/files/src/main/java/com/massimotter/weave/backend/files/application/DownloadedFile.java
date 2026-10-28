package com.massimotter.weave.backend.files.application;

public record DownloadedFile(String filename, String mimeType, byte[] content) {
}
