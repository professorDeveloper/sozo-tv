#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
check_dir=$(mktemp -d "${TMPDIR:-/tmp}/sozo-profile-check.XXXXXX")
trap 'rm -rf "$check_dir"' EXIT
swiftc SozoTV/HouseholdProfileSettings.swift Tests/ProfileSettings/main.swift -o "$check_dir/settings"
"$check_dir/settings"
swiftc SozoTV/PlaybackHistoryContext.swift Tests/PlaybackHistory/main.swift -o "$check_dir/history"
"$check_dir/history"
swiftc SozoTV/Models.swift SozoTV/APIClient.swift SozoTV/AuthTransport.swift SozoTV/SessionStore.swift SozoTV/HouseholdProfileSettings.swift Tests/SessionTransportChecks.swift -o "$check_dir/session"
"$check_dir/session"

swiftc SozoTV/Models.swift Tests/ContentContract/main.swift -o "$check_dir/contracts"
"$check_dir/contracts"
swiftc SozoTV/Models.swift SozoTV/PlaybackSourcePolicy.swift Tests/PlaybackSources/main.swift -o "$check_dir/sources"
"$check_dir/sources"
swiftc SozoTV/ProfileRequestState.swift Tests/ProfileRequestChecks.swift -o "$check_dir/requests"
"$check_dir/requests"

swiftc SozoTV/Models.swift SozoTV/PlaybackHistoryContext.swift SozoTV/ContinueWatching.swift Tests/ContinueWatching/main.swift -o "$check_dir/continuing"
"$check_dir/continuing"

swiftc SozoTV/Models.swift SozoTV/SourceCatalog.swift Tests/SourceCatalog/main.swift -o "$check_dir/sourceCatalog"
"$check_dir/sourceCatalog"

swiftc SozoTV/Models.swift SozoTV/PlaybackSourcePolicy.swift SozoTV/EpisodeCatalog.swift Tests/EpisodeCatalog/main.swift -o "$check_dir/episodes"
"$check_dir/episodes"

swiftc SozoTV/ProfileScreenState.swift Tests/ProfileScreenState/main.swift -o "$check_dir/screens"
"$check_dir/screens"
swiftc SozoTV/SidecarSubtitles.swift Tests/SidecarSubtitles/main.swift -o "$check_dir/captions"
"$check_dir/captions"

swiftc SozoTV/CatalogPagination.swift Tests/CatalogPagination/main.swift -o "$check_dir/pages"
"$check_dir/pages"

swiftc SozoTV/ProfileColorContrast.swift Tests/ProfileColorContrast/main.swift -o "$check_dir/contrast"
"$check_dir/contrast"

swiftc -parse-as-library SozoTV/BoundedArtworkData.swift Tests/ArtworkData/main.swift -o "$check_dir/artwork"
"$check_dir/artwork"
