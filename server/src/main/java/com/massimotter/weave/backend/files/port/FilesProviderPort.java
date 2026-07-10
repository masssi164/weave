package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesDomain.FileContent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.util.Optional;

public interface FilesProviderPort {

    boolean configured();

    ProviderReadiness readiness();

    ProviderConformanceProfile conformanceProfile();

    VersionedListing list(FilePath path);

    Optional<VersionedFile> find(FilePath path);

    FileContent read(FileId id);

    FileObject write(FileWrite write);

    FileObject createCollection(FilePath path);

    FileObject copy(FilePath source, FilePath destination, boolean overwrite);

    FileObject move(FilePath source, FilePath destination, boolean overwrite);

    void delete(FilePath path, FileVersion expectedVersion);
}
