"""Closed RFC 8785 encoder used by the corpus Keycloak coverage helper.

The selected contracts contain only maps, arrays, strings, booleans, null and
integers.  Floating point values are deliberately rejected.
"""

from __future__ import annotations

import json


def dumps(value: object) -> bytes:
    def reject_floats(item: object) -> None:
        if isinstance(item, float):
            raise ValueError("floating point values are outside the Keycloak contract subset")
        if isinstance(item, dict):
            for key, child in item.items():
                if not isinstance(key, str):
                    raise ValueError("canonical object keys must be strings")
                reject_floats(child)
        elif isinstance(item, list):
            for child in item:
                reject_floats(child)

    reject_floats(value)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
