import SwiftUI
import ImageIO

/// Posters retain their dimensions through loading/failure, keeping remote focus stationary.
struct TVArtwork: View {
    let url: String?
    let width: CGFloat
    let height: CGFloat
    var cornerRadius: CGFloat = 16
    @State private var image: UIImage?
    @State private var inViewport = false
    @State private var displayedIdentity: String?
    private var pixels: Int { min(Int(max(width, height) * 2), 2560) }
    private var resourceIdentity: String { "\(url ?? "")|\(pixels)" }
    private var loadIdentity: String { resourceIdentity + (inViewport ? "|visible" : "|offscreen") }
    var body: some View {
        ZStack {
            Color(white: 0.16)
            if displayedIdentity == resourceIdentity, let image { Image(uiImage: image).resizable().scaledToFill() }
            else { Image(systemName: "film").font(.system(size: 32)).foregroundStyle(.white.opacity(0.25)) }
        }.frame(width: width, height: height).clipped()
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .accessibilityHidden(true)
            .onGeometryChange(for: Bool.self) { geometry in
                geometry.frame(in: .global).intersects(UIScreen.main.bounds.insetBy(dx: -300, dy: -250))
            } action: { inViewport = $0 }
            .task(id: loadIdentity) {
                guard inViewport, displayedIdentity != resourceIdentity else { return }
                let requestedIdentity = resourceIdentity
                image = nil
                displayedIdentity = nil
                guard let url, let address = URL(string: url), ["http", "https"].contains(address.scheme?.lowercased() ?? "") else { return }
                do {
                    let next = try await TVArtworkCache.shared.load(address, pixels: pixels)
                    try Task.checkCancellation()
                    image = next
                    displayedIdentity = requestedIdentity
                } catch { /* A stable placeholder is preferable to moving or blank poster geometry. */ }
            }
    }
}

private actor TVArtworkCache {
    static let shared = TVArtworkCache()
    private let cache = NSCache<NSString, UIImage>()
    private let session: URLSession
    init() {
        cache.totalCostLimit = 64 * 1024 * 1024
        cache.countLimit = 80
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.urlCache = URLCache(memoryCapacity: 16 * 1024 * 1024, diskCapacity: 64 * 1024 * 1024)
        session = URLSession(configuration: config)
    }
    func load(_ url: URL, pixels: Int) async throws -> UIImage {
        let key = "\(url.absoluteString)|\(pixels)" as NSString
        if let image = cache.object(forKey: key) { return image }
        let data = try await BoundedArtworkData.load(from: url, session: session)
        try Task.checkCancellation()
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: min(pixels, 2560),
                kCGImageSourceShouldCacheImmediately: true
              ] as CFDictionary) else { throw URLError(.cannotDecodeContentData) }
        let image = UIImage(cgImage: thumbnail)
        cache.setObject(image, forKey: key, cost: thumbnail.bytesPerRow * thumbnail.height)
        return image
    }
}
