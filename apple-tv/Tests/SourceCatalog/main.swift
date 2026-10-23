import Foundation
let catalog = try JSONDecoder().decode(ProviderCatalog.self, from: Data(#"{"items":[{"id":"z","name":"Zulu","mode":"server","category":"anime","lang":"ja"},{"id":"a","name":"Alpha","mode":"server","category":"movies","lang":"en"},{"id":"a","name":"Duplicate"},{"id":"phone","mode":"client"},{"id":"browse","browseOnly":true},{"id":""}]}"#.utf8)).items
precondition(SourceCatalog.unique(catalog).count == 4)
precondition(SourceCatalog.filtered(catalog, query: "").map(\.id) == ["a","z"])
precondition(SourceCatalog.filtered(catalog, query: "JAPANESE").map(\.id) == ["z"])
precondition(SourceCatalog.filtered(catalog, query: "", category: "movies", language: "en").map(\.id) == ["a"])
precondition(SourceCatalog.filtered(catalog, query: "missing").isEmpty)
precondition(SourceCatalog.defaultID(catalog) == "z")
print("PASS: source identity deduplication; native eligibility; search; category/language filters; default selection")

let vid = try JSONDecoder().decode(Provider.self, from: Data(#"{"id":"vidapi","mode":"server"}"#.utf8))
precondition(SourceCatalog.defaultID(catalog + [vid]) == "vidapi")
print("PASS: VidAPI is the default for new profiles while saved choices remain separate")

precondition(SourceCatalog.selectedID(catalog + [vid], preferred: "gone") == "vidapi")
precondition(SourceCatalog.selectedID(catalog + [vid], preferred: "phone") == "vidapi")
precondition(SourceCatalog.selectedID(catalog + [vid], preferred: "a") == "a")
print("PASS: removed or phone-only saved providers fall back to an available TV source")
