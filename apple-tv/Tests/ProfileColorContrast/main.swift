import Foundation

func require(_ result: Bool, _ message: String) {
    guard result else { fputs("FAIL: \(message)\n", stderr); exit(1) }
}
require(ProfileColorContrast.usesDarkForeground("#f5a623"), "Amber initial must use dark text")
require(ProfileColorContrast.usesDarkForeground("#ffffff"), "White initial must use dark text")
require(!ProfileColorContrast.usesDarkForeground("#000000"), "Black initial must use light text")
require(!ProfileColorContrast.usesDarkForeground("#8e44ad"), "Purple initial must use light text")
require(!ProfileColorContrast.usesDarkForeground(nil), "Missing color uses the accent fallback")
require(!ProfileColorContrast.usesDarkForeground("broken"), "Invalid color uses the accent fallback")
print("PASS: Initial avatar contrast for light/dark colors and invalid-color fallback")
