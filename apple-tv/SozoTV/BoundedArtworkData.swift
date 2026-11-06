import Foundation

/// Keep artwork downloads bounded before decoding an untrusted provider response.
enum BoundedArtworkData {
    static let maximumBytes = 15 * 1024 * 1024

    static func load(from url: URL, session: URLSession, maximumBytes: Int = maximumBytes) async throws -> Data {
        let (bytes, response) = try await session.bytes(from: url)
        defer { bytes.task.cancel() }
        guard let response = response as? HTTPURLResponse, 200..<300 ~= response.statusCode else {
            throw URLError(.badServerResponse)
        }
        guard maximumBytes >= 0, response.expectedContentLength <= maximumBytes else {
            throw URLError(.dataLengthExceedsMaximum)
        }
        var data = Data()
        if response.expectedContentLength > 0 { data.reserveCapacity(Int(response.expectedContentLength)) }
        for try await byte in bytes {
            try Task.checkCancellation()
            guard data.count < maximumBytes else { throw URLError(.dataLengthExceedsMaximum) }
            data.append(byte)
        }
        try Task.checkCancellation()
        return data
    }
}
