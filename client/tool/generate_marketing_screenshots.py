#!/usr/bin/env python3
"""Generate deterministic README/marketing screenshots for Weave.

The assets intentionally use SVG so they are small, reviewable, and stable in CI.
They mirror the active product-maturity app states without relying on live-stack or
pixel-golden Flutter rendering, which keeps core E2E validation isolated from
marketing artifact generation.
"""

from __future__ import annotations

from dataclasses import dataclass
from html import escape
from pathlib import Path
from textwrap import wrap

ROOT = Path(__file__).resolve().parents[2]
MARKETING_OUTPUT_DIR = ROOT / "docs" / "assets" / "marketing"
ROADMAP_OUTPUT_DIR = ROOT / "docs" / "assets" / "roadmap"
WIDTH = 1440
HEIGHT = 900


@dataclass(frozen=True)
class Metric:
    label: str
    value: str


@dataclass(frozen=True)
class Screen:
    output_dir: Path
    file_name: str
    title: str
    description: str
    active_nav: str
    hero: str
    subhero: str
    metrics: tuple[Metric, ...]
    cards: tuple[tuple[str, str, str], ...]
    status: str


SCREENS: tuple[Screen, ...] = (
    Screen(
        output_dir=MARKETING_OUTPUT_DIR,
        file_name="01-setup-start.svg",
        title="Weave setup start screen",
        description="A Weave welcome screen invites an admin to prepare a workspace through Weave-owned setup boundaries.",
        active_nav="Setup",
        hero="Set up your Weave workspace",
        subhero="Connect identity, collaboration, and workspace services through one guided setup path.",
        metrics=(
            Metric("Identity", "Connected through Weave"),
            Metric("Workspace", "Ready for review"),
            Metric("Client", "Desktop and mobile path"),
        ),
        cards=(
            ("1", "OIDC sign-in", "Use a configured identity provider through the Weave sign-in contract."),
            ("2", "Workspace services", "Review product endpoints and readiness before members join."),
            ("3", "Member workspace", "Open chat, files, and settings from one consistent navigation model."),
        ),
        status="Get Started",
    ),
    Screen(
        output_dir=MARKETING_OUTPUT_DIR,
        file_name="02-review-service-endpoints.svg",
        title="Weave service endpoint review screen",
        description="The setup review lists workspace service endpoints before finishing configuration.",
        active_nav="Setup",
        hero="Review workspace services",
        subhero="Weave keeps service choices behind clear product boundaries and explicit readiness states.",
        metrics=(
            Metric("Chat", "Provider connected"),
            Metric("Files", "Provider connected"),
            Metric("Backend", "Weave API ready"),
        ),
        cards=(
            ("ID", "Identity authority", "The configured identity provider owns login, sessions, and user identity claims."),
            ("API", "Backend facade", "Weave backend is the product API after sign-in."),
            ("UX", "Provider boundary", "Chat and files providers sit behind Weave member experiences."),
        ),
        status="Finish setup",
    ),
    Screen(
        output_dir=MARKETING_OUTPUT_DIR,
        file_name="03-chat-room.svg",
        title="Weave chat room screen",
        description="The custom Weave chat room shows a Release Room conversation and accessible message composer.",
        active_nav="Chat",
        hero="Release Room",
        subhero="Custom Weave chat surface with room list, readable message history, and a clear composer.",
        metrics=(
            Metric("Room", "#release-room"),
            Metric("Links", "Files and decisions"),
            Metric("State", "Connected"),
        ),
        cards=(
            ("A", "Alice", "Release checklist is ready for review."),
            ("W", "Weave", "Files and decisions are linked to this room."),
            ("?", "Help", "Workspace rooms are provisioned behind Weave-owned contracts."),
        ),
        status="Send message",
    ),
    Screen(
        output_dir=MARKETING_OUTPUT_DIR,
        file_name="04-files-documents.svg",
        title="Weave files documents screen",
        description="The Weave files screen lists folders and files through the backend files facade.",
        active_nav="Files",
        hero="/Documents",
        subhero="A Weave-owned files UI backed by the product backend and guarded provider contracts.",
        metrics=(
            Metric("Connection", "Connected"),
            Metric("Backend", "Files facade"),
            Metric("Path", "/Documents"),
        ),
        cards=(
            ("F", "Plans", "Folder · updated today"),
            ("D", "spec.pdf", "PDF · Delete action has a label"),
            ("+", "New folder", "Create folders without visiting raw provider UI."),
        ),
        status="Upload / create folder",
    ),
    Screen(
        output_dir=MARKETING_OUTPUT_DIR,
        file_name="05-settings.svg",
        title="Weave settings screen",
        description="The Weave settings screen shows saved service configuration and sign-out controls.",
        active_nav="Settings",
        hero="Workspace Settings",
        subhero="Review product configuration, update service endpoints, and manage the current session.",
        metrics=(
            Metric("OIDC issuer", "Configured"),
            Metric("Client ID", "weave-app"),
            Metric("Files", "Provider connected"),
        ),
        cards=(
            ("S", "Server configuration", "One persisted setup model shared by onboarding and settings."),
            ("A", "Account session", "Sign out clears chat and files module sessions safely."),
            ("L", "Labeled controls", "Controls use clear names, states, and predictable actions."),
        ),
        status="Save Changes",
    ),


    Screen(
        output_dir=ROADMAP_OUTPUT_DIR,
        file_name="06-calendar-roadmap-readiness.svg",
        title="Weave calendar roadmap readiness screen",
        description="The guarded Calendar roadmap shows active workspace/team/channel readiness copy for the shared scheduling path.",
        active_nav="Calendar",
        hero="Calendar channel schedule readiness",
        subhero="The shared Calendar path uses workspace, team, and channel scopes; channel event CRUD is validated through the backend Calendar facade.",
        metrics=(
            Metric("Scope", "Workspace · team · channel"),
            Metric("Access", "Private calendars blocked"),
            Metric("Live proof", "Channel event create/read/update/delete"),
        ),
        cards=(
            ("T", "Access model", "Workspace/team/channel scope metadata is visible; private personal calendars stay out of the core path."),
            ("C", "Credential readiness", "Backend actor credentials are not exposed to generated setup artifacts."),
            ("#", "Channel context", "Events carry channel scope metadata so meeting-thread context can attach without raw CalDAV concepts."),
        ),
        status="Guarded roadmap",
    ),
    Screen(
        output_dir=ROADMAP_OUTPUT_DIR,
        file_name="07-boards-feature-gate.svg",
        title="Weave boards feature-gated roadmap screen",
        description="A clearly labelled feature-gated boards/tasks roadmap screen with provider-neutral columns, tasks, and non-drag actions.",
        active_nav="Boards",
        hero="Boards/tasks feature-gated scope",
        subhero="A Weave-owned board model behind the backend facade. The live gate validates provider-neutral create, move, and complete actions without drag-and-drop.",
        metrics=(
            Metric("Release", "feature-gated roadmap"),
            Metric("Source", "backend-facade fixture"),
            Metric("Movement", "Non-drag actions validated"),
        ),
        cards=(
            ("K", "Keyboard path", "Move to another column, mark done, or mark blocked without pointer-only drag-and-drop."),
            ("P", "Provider boundary", "The app talks to Weave backend DTOs; no secret-bearing provider client is exposed."),
            ("A", "Readable state", "Columns, statuses, due dates, and priority use text labels, not color alone."),
        ),
        status="Feature gate",
    ),
    Screen(
        output_dir=ROADMAP_OUTPUT_DIR,
        file_name="08-matrixrtc-calls-readiness.svg",
        title="Weave MatrixRTC Calls readiness screen",
        description="A guarded Calls roadmap visual showing MatrixRTC Profile 0 signaling, independent RTC authorization, and a replaceable SFU boundary.",
        active_nav="Meetings",
        hero="MatrixRTC Calls readiness",
        subhero="MatrixRTC Profile 0 is the member signaling contract. Join and start stay blocked until RTC authorization, TURN, media E2EE, and physical-device evidence pass.",
        metrics=(
            Metric("Signaling", "MatrixRTC Profile 0"),
            Metric("Authorization", "Independent RTC Authorizer"),
            Metric("Default", "Fail-closed"),
        ),
        cards=(
            ("M", "Matrix-native", "Room, slot, member, and device state use the pinned MatrixRTC shape; no member Calls REST API exists."),
            ("E", "Media-E2EE gate", "Matrix room encryption alone does not prove media, recording, caption, transcript, or metadata confidentiality."),
            ("S", "Replaceable SFU", "LiveKit is the first southbound media adapter; URLs, keys, tokens, and provider errors stay server-side."),
        ),
        status="Guarded roadmap",
    ),
)


def line(text: str, x: int, y: int, size: int = 28, weight: int = 500, fill: str = "#0f172a") -> str:
    return f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" fill="{fill}">{escape(text)}</text>'


def multiline(text: str, x: int, y: int, *, width: int, size: int = 28, fill: str = "#334155", line_height: int = 38) -> str:
    chars = max(18, width // max(size // 2, 1))
    parts = []
    for index, segment in enumerate(wrap(text, chars)):
        parts.append(line(segment, x, y + index * line_height, size=size, weight=400, fill=fill))
    return "\n".join(parts)


def nav_item(label: str, x: int, y: int, active: bool) -> str:
    fill = "#e0f2fe" if active else "#ffffff"
    stroke = "#0891b2" if active else "#dbeafe"
    text_fill = "#075985" if active else "#475569"
    return f'''
    <rect x="{x}" y="{y}" width="180" height="56" rx="18" fill="{fill}" stroke="{stroke}" stroke-width="2"/>
    {line(label, x + 24, y + 37, size=22, weight=700, fill=text_fill)}
    '''


def metric_card(metric: Metric, x: int, y: int) -> str:
    return f'''
    <rect x="{x}" y="{y}" width="346" height="116" rx="28" fill="#ffffff" stroke="#dbeafe" stroke-width="2"/>
    {line(metric.label, x + 28, y + 42, size=21, weight=700, fill="#0369a1")}
    {multiline(metric.value, x + 28, y + 78, width=310, size=20, fill="#0f172a", line_height=27)}
    '''


def content_card(icon: str, title: str, body: str, x: int, y: int) -> str:
    return f'''
    <rect x="{x}" y="{y}" width="336" height="210" rx="32" fill="#ffffff" stroke="#e2e8f0" stroke-width="2"/>
    <circle cx="{x + 54}" cy="{y + 58}" r="28" fill="#ecfeff" stroke="#67e8f9" stroke-width="2"/>
    {line(icon, x + 42, y + 68, size=26, weight=800, fill="#0e7490")}
    {line(title, x + 96, y + 65, size=24, weight=800, fill="#0f172a")}
    {multiline(body, x + 32, y + 112, width=270, size=22, fill="#475569", line_height=30)}
    '''


def render(screen: Screen) -> str:
    nav = "\n".join(
        nav_item(label, 78 + index * 184, 116, screen.active_nav == label)
        for index, label in enumerate(("Setup", "Chat", "Files", "Settings", "Calendar", "Boards", "Meetings"))
    )
    metrics = "\n".join(metric_card(metric, 104 + index * 376, 402) for index, metric in enumerate(screen.metrics))
    cards = "\n".join(content_card(*card, x=104 + index * 376, y=566) for index, card in enumerate(screen.cards))
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}" role="img" aria-labelledby="title desc">
  <title id="title">{escape(screen.title)}</title>
  <desc id="desc">{escape(screen.description)}</desc>
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#eff6ff"/>
      <stop offset="0.52" stop-color="#ecfeff"/>
      <stop offset="1" stop-color="#f8fafc"/>
    </linearGradient>
    <filter id="shadow" x="-10%" y="-10%" width="120%" height="130%">
      <feDropShadow dx="0" dy="18" stdDeviation="22" flood-color="#0f172a" flood-opacity="0.12"/>
    </filter>
  </defs>
  <rect width="{WIDTH}" height="{HEIGHT}" fill="url(#bg)"/>
  <rect x="56" y="54" width="1328" height="792" rx="46" fill="#f8fafc" stroke="#bae6fd" stroke-width="3" filter="url(#shadow)"/>
  <circle cx="111" cy="96" r="13" fill="#38bdf8"/>
  <circle cx="149" cy="96" r="13" fill="#22d3ee"/>
  <circle cx="187" cy="96" r="13" fill="#14b8a6"/>
  {line("Weave", 234, 105, size=34, weight=900, fill="#075985")}
  {nav}
  <rect x="104" y="212" width="1096" height="150" rx="34" fill="#ffffff" stroke="#dbeafe" stroke-width="2"/>
  {line(screen.hero, 144, 270, size=42, weight=900, fill="#0f172a")}
  {multiline(screen.subhero, 144, 316, width=880, size=26, fill="#475569", line_height=34)}
  {metrics}
  {cards}
  <rect x="1012" y="96" width="300" height="54" rx="20" fill="#0f172a"/>
  {line(screen.status, 1042, 132, size=22, weight=800, fill="#ffffff")}
</svg>
'''


def main() -> None:
    for screen in SCREENS:
        screen.output_dir.mkdir(parents=True, exist_ok=True)
        path = screen.output_dir / screen.file_name
        svg = "\n".join(line.rstrip() for line in render(screen).splitlines()) + "\n"
        path.write_text(svg, encoding="utf-8")
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
