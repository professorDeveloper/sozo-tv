import Foundation

private final class ArtworkProtocol: URLProtocol, @unchecked Sendable {
    static let lock = NSLock()
    static var declaredLength: Int? = nil
    static var chunkCount = 32
    static var delivered = 0
    static var stoppedCount = 0
    private let stateLock = NSLock()
    private var stopped = false
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock(); let length = Self.declaredLength, count = Self.chunkCount; Self.lock.unlock()
        var headers = ["Cache-Control": "public, max-age=3600"]
        if let length { headers["Content-Length"] = String(length) }
        client?.urlProtocol(self, didReceive: HTTPURLResponse(url: request.url!, statusCode: 200,
            httpVersion: "HTTP/1.1", headerFields: headers)!, cacheStoragePolicy: .allowed)
        send(index: 0, count: count)
    }
    private func send(index: Int, count: Int) {
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.01) { [self] in
            stateLock.lock(); let canceled = stopped; stateLock.unlock()
            guard !canceled else { return }
            if index == count { client?.urlProtocolDidFinishLoading(self); return }
            Self.lock.lock(); Self.delivered += 1; Self.lock.unlock()
            client?.urlProtocol(self, didLoad: Data(repeating: 7, count: 512))
            send(index: index + 1, count: count)
        }
    }
    override func stopLoading() {
        stateLock.lock(); stopped = true; stateLock.unlock()
        Self.lock.lock(); Self.stoppedCount += 1; Self.lock.unlock()
    }
    static func reset(length: Int?, chunks: Int) {
        lock.lock(); defer { lock.unlock() }
        declaredLength = length; chunkCount = chunks; delivered = 0; stoppedCount = 0
    }
    static func counts() -> (Int, Int) {
        lock.lock(); defer { lock.unlock() }; return (delivered, stoppedCount)
    }
}

@main struct ArtworkDataChecks {
    static func main() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ArtworkProtocol.self]
        let cache = URLCache(memoryCapacity: 16 * 1024, diskCapacity: 64 * 1024)
        configuration.urlCache = cache
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let address = URL(string: "https://artwork.fixture/poster")!
        ArtworkProtocol.reset(length: nil, chunks: 32)
        do {
            _ = try await BoundedArtworkData.load(from: address, session: session, maximumBytes: 1024)
            fatalError("Oversized artwork accepted")
        } catch let error as URLError { precondition(error.code == .dataLengthExceedsMaximum) }
        try await Task.sleep(nanoseconds: 40_000_000)
        let unknown = ArtworkProtocol.counts()
        precondition(unknown.0 <= 3 && unknown.1 > 0,
                     "Unknown-length artwork fully downloaded before rejection: \(unknown)")
        precondition(cache.cachedResponse(for: URLRequest(url: address)) == nil,
                     "Canceled unknown-length artwork must not create a complete cache entry")
        ArtworkProtocol.reset(length: 16384, chunks: 32)
        do {
            _ = try await BoundedArtworkData.load(from: address, session: session, maximumBytes: 1024)
            fatalError("Oversized Content-Length accepted")
        } catch let error as URLError { precondition(error.code == .dataLengthExceedsMaximum) }
        try await Task.sleep(nanoseconds: 40_000_000)
        let declared = ArtworkProtocol.counts()
        precondition(declared.0 <= 1 && declared.1 > 0, "Oversized declared artwork body downloaded: \(declared)")
        precondition(cache.cachedResponse(for: URLRequest(url: address)) == nil,
                     "Rejected declared-length artwork must not create a cache entry")
        ArtworkProtocol.reset(length: 1024, chunks: 2)
        let data = try await BoundedArtworkData.load(from: address, session: session, maximumBytes: 1024)
        precondition(data.count == 1024 && data.allSatisfy { $0 == 7 }, "Valid artwork bytes changed")
        print("PASS: declared/unknown oversized artwork cancels before full body; exact-limit data preserved; rejected responses not cached")
    }
}
