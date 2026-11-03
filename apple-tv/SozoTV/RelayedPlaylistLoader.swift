import AVFoundation

/// Serves the playlists of a relayed stream to AVPlayer after HLSPlaylistRelay
/// has rewritten them. AVFoundation only hands playlists and keys to a resource
/// loader, so segments keep plain https URLs.
final class RelayedPlaylistLoader: NSObject, AVAssetResourceLoaderDelegate, @unchecked Sendable {
    let queue = DispatchQueue(label: "sozo.hls.playlists")
    private let headers: [String: String]
    private let endpoint: URL
    private let session: URLSession
    private let lock = NSLock()
    private var tasks: [ObjectIdentifier: URLSessionDataTask] = [:]

    init(headers: [String: String], endpoint: URL) {
        self.headers = headers
        self.endpoint = endpoint
        let config = URLSessionConfiguration.ephemeral
        config.httpShouldSetCookies = false
        session = URLSession(configuration: config)
        super.init()
    }

    deinit { session.invalidateAndCancel() }

    func resourceLoader(_ resourceLoader: AVAssetResourceLoader, shouldWaitForLoadingOfRequestedResource loadingRequest: AVAssetResourceLoadingRequest) -> Bool {
        guard let url = loadingRequest.request.url, let upstream = HLSPlaylistRelay.upstreamURL(url) else { return false }
        var request = URLRequest(url: upstream, timeoutInterval: 20)
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        let key = ObjectIdentifier(loadingRequest)
        let endpoint = endpoint
        let queue = queue
        let task = session.dataTask(with: request) { [weak self] data, response, error in
            self?.lock.withLock { _ = self?.tasks.removeValue(forKey: key) }
            queue.async {
                guard !loadingRequest.isCancelled else { return }
                guard let data, data.count <= HLSPlaylistRelay.maximumPlaylistBytes,
                      let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                      let text = String(data: data, encoding: .utf8),
                      let playlist = HLSPlaylistRelay.rewrite(text, base: http.url ?? upstream, endpoint: endpoint) else {
                    loadingRequest.finishLoading(with: error ?? URLError(.badServerResponse))
                    return
                }
                let body = Data(playlist.utf8)
                if let info = loadingRequest.contentInformationRequest {
                    info.contentType = "public.m3u-playlist"
                    info.contentLength = Int64(body.count)
                    info.isByteRangeAccessSupported = false
                }
                loadingRequest.dataRequest?.respond(with: body)
                loadingRequest.finishLoading()
            }
        }
        lock.withLock { tasks[key] = task }
        task.resume()
        return true
    }

    func resourceLoader(_ resourceLoader: AVAssetResourceLoader, didCancel loadingRequest: AVAssetResourceLoadingRequest) {
        lock.withLock { tasks.removeValue(forKey: ObjectIdentifier(loadingRequest)) }?.cancel()
    }
}
