#!/usr/bin/env python3
"""Use raw file bytes for the two binary Files User operations.

OpenAPI Generator 7.17.0's native Java template serializes a File request
body through Jackson even when the operation declares application/octet-stream.
Keep this correction exact and fail generation if the upstream template changes.
"""

from pathlib import Path
import sys


def main() -> None:
    generated = Path(sys.argv[1])
    api = generated / "src/main/java/com/massimotter/weave/userapi/api/FilesUserApi.java"
    source = api.read_text(encoding="utf-8")
    for operation, verb in (("updateFilesItemContent", "PUT"), ("uploadFilesItemContent", "POST")):
        start = source.index(f"  private HttpRequest.Builder {operation}RequestBuilder(")
        end = source.index("\n  }", start)
        builder = source[start:end]
        old = (
            "      byte[] localVarPostBody = memberVarObjectMapper.writeValueAsBytes(body);\n"
            f'      localVarRequestBuilder.method("{verb}", HttpRequest.BodyPublishers.ofByteArray(localVarPostBody));'
        )
        new = f'      localVarRequestBuilder.method("{verb}", HttpRequest.BodyPublishers.ofFile(body.toPath()));'
        if builder.count(old) != 1:
            raise SystemExit(f"OpenAPI Generator binary body template changed for {operation}")
        source = source[:start] + builder.replace(old, new) + source[end:]
    api.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    main()
