#!/usr/bin/env python3
"""HTTPS for explicit bootstrap, preserving reviewed authority on isolated E2E."""
from __future__ import annotations

import http.client
import socket
import ssl
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, target):
        return None


class _LoopbackHTTPSConnection(http.client.HTTPSConnection):
    def connect(self) -> None:
        if self._tunnel_host:
            raise ValueError("Isolated bootstrap does not accept proxy tunnels")
        connection = socket.create_connection(
            ("127.0.0.1", self.port), self.timeout, source_address=self.source_address,
        )
        try:
            # Routing is isolated; the public organization hostname remains the
            # TLS SNI and certificate identity, and HTTP keeps the original Host.
            self.sock = self._context.wrap_socket(connection, server_hostname=self.host)
        except BaseException:
            connection.close()
            raise


class _LoopbackHTTPSHandler(urllib.request.HTTPSHandler):
    def https_open(self, request):
        return self.do_open(_LoopbackHTTPSConnection, request, context=self._context)


def open_https(
    request: urllib.request.Request, context: ssl.SSLContext, *,
    connect_loopback: bool = False, timeout: float = 20,
):
    authority = urllib.parse.urlsplit(request.full_url)
    if (authority.scheme != "https" or not authority.hostname or authority.username is not None
            or authority.password is not None or authority.fragment):
        raise ValueError("Bootstrap requires one HTTPS endpoint without user information or fragments")
    if not context.check_hostname or context.verify_mode != ssl.CERT_REQUIRED:
        raise ValueError("Bootstrap requires CA and hostname verification")
    handler = _LoopbackHTTPSHandler if connect_loopback else urllib.request.HTTPSHandler
    # Ambient proxy settings must not redirect a private bootstrap credential.
    opener = urllib.request.build_opener(
        urllib.request.ProxyHandler({}), handler(context=context), NoRedirect(),
    )
    return opener.open(request, timeout=timeout)
