import Foundation
import Security

struct DeviceCredentials: Codable, Equatable {
    let accessToken: String
    let refreshToken: String
    var sessionId: String?
}

struct ProfileDraft {
    var name: String
    var avatar: String? = nil
    var color: String? = nil
    var isKids: Bool = false
    /// nil leaves an existing PIN unchanged; an empty string removes it.
    var pin: String? = nil
    var body: [String: Any] {
        var value: [String: Any] = ["name": name, "isKids": isKids]
        value["avatar"] = avatar.map { $0 as Any } ?? NSNull()
        value["color"] = color.map { $0 as Any } ?? NSNull()
        if let pin { value["pin"] = pin }
        return value
    }
}

struct ProfileResponse: Decodable { let profile: HouseholdProfile }
struct MessageResponse: Decodable { let message: String? }
struct SuccessResponse: Decodable { let success: Bool? }

protocol CredentialStorage {
    func load() throws -> DeviceCredentials?
    func save(_ credentials: DeviceCredentials) throws
    func clear() throws
}

/// One atomic Keychain item keeps access and rotating refresh tokens together.
struct KeychainCredentialStorage: CredentialStorage {
    private let service: String
    init(service: String = "com.saikou.sozotv.device-session") { self.service = service }
    private let account = "current"
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service, kSecAttrAccount as String: account]
    }
    func load() throws -> DeviceCredentials? {
        var q = query
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(q as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw APIError.keychain(status) }
        return try JSONDecoder().decode(DeviceCredentials.self, from: data)
    }
    func save(_ credentials: DeviceCredentials) throws {
        let data = try JSONEncoder().encode(credentials)
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var q = query
            q[kSecValueData as String] = data
            q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            let inserted = SecItemAdd(q as CFDictionary, nil)
            guard inserted == errSecSuccess else { throw APIError.keychain(inserted) }
        } else if status != errSecSuccess { throw APIError.keychain(status) }
    }
    func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw APIError.keychain(status) }
    }
}
